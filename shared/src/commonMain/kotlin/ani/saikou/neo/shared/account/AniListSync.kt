package ani.saikou.neo.shared.account

import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/** An episode of [animeId] watched while signed in as [userId], waiting to reach AniList. */
public data class PendingProgress(
    val animeId: Int,
    val episode: Int,
    val userId: Int,
)

/** Updates waiting to reach AniList, one per show. */
public interface SyncQueue {
    public suspend fun all(): List<PendingProgress>

    /** Keeps [pending] in place of any update waiting for the same show. */
    public suspend fun put(pending: PendingProgress)

    public suspend fun remove(animeId: Int)
}

/**
 * Moves the signed-in user's AniList progress as episodes are watched, following ADR 0047. Each update waits in
 * [queue] until AniList takes it, so one that fails is sent later, and only while the same user is signed in. Work runs
 * in [scope], one update at a time, so it carries on once the player has gone.
 */
public class AniListSync(
    private val scope: CoroutineScope,
    private val account: suspend () -> SignedInAccount?,
    private val client: ProgressClient,
    private val queue: SyncQueue,
    private val today: () -> ListDate,
    /** The time now, in seconds since 1970. */
    private val now: () -> Long = { Clock.System.now().epochSeconds },
) {
    private val lock = Mutex()

    /** [episode] of [animeId] was watched. With no account on the TV, nothing happens. */
    public fun watched(
        animeId: Int,
        episode: Int,
    ) {
        locked {
            val signedIn = account() ?: return@locked
            val waiting = queue.all().firstOrNull { it.animeId == animeId && it.userId == signedIn.profile.id }
            // Watching an earlier episode again doesn't replace a later one still waiting.
            if (waiting == null || waiting.episode < episode) queue.put(PendingProgress(animeId, episode, signedIn.profile.id))
            send(signedIn)
        }
    }

    /** Sends the updates still waiting, such as when the app starts. */
    public fun flush() {
        locked { account()?.let { send(it) } }
    }

    // One at a time.
    private fun locked(work: suspend () -> Unit) {
        scope.launch {
            try {
                lock.withLock { work() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Such as a queue file that can't be read or written. What's waiting is tried next time, rather than
                // crashing the app.
            }
        }
    }

    private suspend fun send(signedIn: SignedInAccount) {
        // AniList would refuse an expired token. What's waiting stays, for when this user signs in again.
        if (signedIn.token.expiresAtEpochSeconds <= now()) return
        for (pending in queue.all()) {
            if (pending.userId != signedIn.profile.id) {
                queue.remove(pending.animeId)
                continue
            }
            val failure = sendOne(signedIn.token, pending) ?: continue
            if (!failure.worthRetrying) queue.remove(pending.animeId)
        }
    }

    // Null once the update needs nothing more: it was saved, or AniList is already there. Otherwise why it failed.
    private suspend fun sendOne(
        token: AniListToken,
        pending: PendingProgress,
    ): AniListError? {
        val show =
            when (val read = client.showEntry(token, pending.animeId)) {
                is AniListResult.Success -> read.value
                is AniListResult.Failure -> return read.error
            }
        val update = progressUpdate(show, pending.episode, today())
        if (update != null) {
            val saved = client.saveProgress(token, pending.animeId, update)
            if (saved is AniListResult.Failure) return saved.error
        }
        queue.remove(pending.animeId)
        return null
    }
}

// A refused token is kept too: signing in again as the same user sends it.
private val AniListError.worthRetrying: Boolean
    get() =
        when (this) {
            is AniListError.Network, is AniListError.RateLimited, AniListError.SignedOut -> true
            is AniListError.Http -> statusCode >= 500 || statusCode == 408
            is AniListError.Unexpected -> false
        }
