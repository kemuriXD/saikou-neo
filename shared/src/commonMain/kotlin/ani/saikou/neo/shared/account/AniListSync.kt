package ani.saikou.neo.shared.account

import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

/** [show] as read from the list of the AniList user [userId]. */
public data class OnYourList(
    val userId: Int,
    val show: ShowEntry,
)

/** [animeId] was set to [status] on the list of [userId] from this TV, or taken off it with null. */
public data class ListChange(
    val animeId: Int,
    val status: ListStatus?,
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
) : YourListEntries {
    private val lock = Mutex()
    private val changed = MutableSharedFlow<ListChange>(extraBufferCapacity = CHANGES_KEPT)

    override val changes: SharedFlow<ListChange> = changed.asSharedFlow()

    /**
     * [episode] of [animeId] was watched. With no account on the TV, or one other than [forUser] where that's given,
     * nothing happens.
     */
    public fun watched(
        animeId: Int,
        episode: Int,
        forUser: Int? = null,
    ) {
        locked {
            val signedIn = account() ?: return@locked
            if (forUser != null && signedIn.profile.id != forUser) return@locked
            val waiting = queue.all().firstOrNull { it.animeId == animeId && it.userId == signedIn.profile.id }
            // Watching an earlier episode again doesn't replace a later one still waiting.
            if (waiting == null || waiting.episode < episode) queue.put(PendingProgress(animeId, episode, signedIn.profile.id))
            send(signedIn)
        }
    }

    /**
     * Sets [animeId]'s progress back to [progress], as asked for where AniList is further on than this TV (ADR 0050).
     * It's sent once, progress only, and only while [forUser], whose list was read, is signed in. Once AniList takes it,
     * an update waiting for the show is dropped so it can't undo this. False if it wasn't saved, so Details asks again.
     */
    public suspend fun movedBack(
        animeId: Int,
        progress: Int,
        forUser: Int?,
    ): Boolean =
        scope
            .async {
                lock.withLock {
                    val signedIn = account()
                    if (signedIn == null || signedIn.profile.id != forUser || signedIn.token.expiresAtEpochSeconds <= now()) {
                        return@withLock false
                    }
                    val saved = client.saveProgress(signedIn.token, animeId, ProgressUpdate(progress, null, null))
                    if (saved !is AniListResult.Success) return@withLock false
                    if (queue.all().any { it.animeId == animeId && it.userId == forUser }) queue.remove(animeId)
                    true
                }
            }.await()

    /**
     * [animeId] and the signed-in user's entry for it, for the list button. Null when signed out, with an expired token,
     * or when AniList can't be reached.
     */
    override suspend fun entry(animeId: Int): OnYourList? {
        val signedIn = liveAccount() ?: return null
        val read = client.showEntry(signedIn.token, animeId) as? AniListResult.Success ?: return null
        return OnYourList(signedIn.profile.id, read.value)
    }

    /**
     * Sets [animeId]'s status on the list of [forUser], whose entry the button showed, from AniList's entry now (ADR
     * 0051). False if it wasn't saved, or someone else is signed in.
     */
    override suspend fun changeStatus(
        animeId: Int,
        to: ListStatus,
        forUser: Int,
    ): Boolean =
        once(forUser) { token ->
            val read = client.showEntry(token, animeId) as? AniListResult.Success ?: return@once false
            if (client.saveProgress(token, animeId, statusUpdate(read.value, to, today())) !is AniListResult.Success) return@once false
            changed.emit(ListChange(animeId, to, forUser))
            true
        }

    /**
     * Takes [animeId] off the list of [forUser], and drops an update waiting for it, which would add it back. True once
     * it's off, including when it wasn't on.
     */
    override suspend fun remove(
        animeId: Int,
        forUser: Int,
    ): Boolean =
        once(forUser) { token ->
            val read = client.showEntry(token, animeId) as? AniListResult.Success ?: return@once false
            val id = read.value.entry?.id
            if (id != null && client.removeEntry(token, id) !is AniListResult.Success) return@once false
            if (queue.all().any { it.animeId == animeId && it.userId == forUser }) queue.remove(animeId)
            changed.emit(ListChange(animeId, null, forUser))
            true
        }

    // Runs in [scope], so it finishes once the screen has gone, after any update ahead of it, while [forUser] is signed in.
    private suspend fun once(
        forUser: Int,
        work: suspend (AniListToken) -> Boolean,
    ): Boolean =
        scope
            .async {
                lock.withLock {
                    val signedIn = liveAccount()
                    if (signedIn == null || signedIn.profile.id != forUser) false else work(signedIn.token)
                }
            }.await()

    private suspend fun liveAccount(): SignedInAccount? = account()?.takeIf { it.token.expiresAtEpochSeconds > now() }

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

// Changes from buttons on screens that aren't collecting yet aren't needed: those read the entry when they open.
private const val CHANGES_KEPT = 8

// A refused token is kept too: signing in again as the same user sends it.
private val AniListError.worthRetrying: Boolean
    get() =
        when (this) {
            is AniListError.Network, is AniListError.RateLimited, AniListError.SignedOut -> true
            is AniListError.Http -> statusCode >= 500 || statusCode == 408
            is AniListError.Unexpected -> false
        }
