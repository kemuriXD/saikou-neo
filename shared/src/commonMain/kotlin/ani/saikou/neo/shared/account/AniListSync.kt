package ani.saikou.neo.shared.account

import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.settings.Settings
import ani.saikou.neo.shared.settings.UpdateAniList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
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

/**
 * [animeId] was set to [status] on the list of [userId] from this TV, or taken off it with null, and its progress is now
 * [progress] where that's known.
 */
public data class ListChange(
    val animeId: Int,
    val status: ListStatus?,
    val userId: Int,
    val progress: Int? = null,
)

/** What a change from this TV did to an entry, for the notice (ADR 0056). */
public sealed interface NoticeChange {
    /** The entry is now [status] at [progress]. [byEpisode] when an episode was watched or marked, not a status picked. */
    public data class Updated(
        val status: ListStatus,
        val progress: Int,
        val byEpisode: Boolean,
    ) : NoticeChange

    public data object Removed : NoticeChange

    /** AniList couldn't be reached, so [pending] waits in the queue, in place of the update it [replaced]. */
    public data class SavedForLater(
        val pending: PendingProgress,
        val replaced: PendingProgress?,
    ) : NoticeChange
}

/**
 * The TV changed [animeId] on the list of [userId], as [change] says. [before] is the entry as it was, or null when the
 * show wasn't on the list, which Undo puts back. [fromWatching] when the player made it, so it waits for Up next.
 */
public data class ListNotice(
    val animeId: Int,
    val userId: Int,
    val title: AnimeTitle?,
    val change: NoticeChange,
    val before: ListEntry?,
    val fromWatching: Boolean = false,
)

/** Marking episodes watched or unwatched from Details (ADR 0052), for the user whose entry was read. */
public interface WatchedEpisodes : YourListEntries {
    /** Moves the progress to [upTo], as watching it would. False if it wasn't saved, or someone else is signed in. */
    public suspend fun markWatched(
        animeId: Int,
        upTo: Int,
        forUser: Int,
    ): Boolean

    /** Sets the progress to the episode before [episode], and nothing else. */
    public suspend fun markUnwatched(
        animeId: Int,
        episode: Int,
        forUser: Int,
    ): Boolean
}

/** How watching in the player reaches AniList, behind the Update AniList setting (ADR 0053). */
public interface WatchingUpdates {
    /** [episode] of [animeId], called [title], counts as watched in the player. */
    public fun watching(
        animeId: Int,
        episode: Int,
        title: AnimeTitle? = null,
    )

    /** The user to ask before [animeId]'s first episode goes to AniList, or null when there's nothing to ask. */
    public suspend fun asksFirst(animeId: Int): Int?

    /** [forUser] answered whether to [update] AniList for [animeId]. Yes sends [episode] too. */
    public fun answer(
        animeId: Int,
        forUser: Int,
        update: Boolean,
        episode: Int,
        title: AnimeTitle? = null,
    )
}

/** Each user's answer to "Update AniList as you watch?" for a show, kept on this TV. */
public interface ShowAnswers {
    public suspend fun answer(
        userId: Int,
        animeId: Int,
    ): Boolean?

    public suspend fun save(
        userId: Int,
        animeId: Int,
        update: Boolean,
    )
}

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
 * in [scope], one update at a time, so it carries on once the player has gone. The [settings] gate it (ADR 0053):
 * Update AniList decides what watching sends, with [answers] for Ask for each show, and private mode stops every write.
 */
public class AniListSync(
    private val scope: CoroutineScope,
    private val account: suspend () -> SignedInAccount?,
    private val client: ProgressClient,
    private val queue: SyncQueue,
    private val today: () -> ListDate,
    /** The time now, in seconds since 1970. */
    private val now: () -> Long = { Clock.System.now().epochSeconds },
    private val settings: suspend () -> Settings = { Settings() },
    private val answers: ShowAnswers? = null,
) : WatchedEpisodes,
    WatchingUpdates {
    private val lock = Mutex()
    private val changed = MutableSharedFlow<ListChange>(extraBufferCapacity = CHANGES_KEPT, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val changes: SharedFlow<ListChange> = changed.asSharedFlow()

    private val noticed = MutableSharedFlow<ListNotice>(extraBufferCapacity = CHANGES_KEPT, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Each change the TV makes to the list, for the notice with Undo (ADR 0056). Updates sent later from the queue say nothing. */
    public val notices: SharedFlow<ListNotice> = noticed.asSharedFlow()

    /**
     * [episode] of [animeId] was watched. With no account on the TV, or one other than [forUser] where that's given,
     * nothing happens. Watched in the player, a notice says what changed, with the show's [title] for when AniList can't
     * be reached.
     */
    public fun watched(
        animeId: Int,
        episode: Int,
        forUser: Int? = null,
        title: AnimeTitle? = null,
        inPlayer: Boolean = false,
    ) {
        locked {
            val signedIn = account() ?: return@locked
            if (forUser != null && signedIn.profile.id != forUser) return@locked
            if (settings().privateMode) return@locked
            val waiting = queue.all().firstOrNull { it.animeId == animeId && it.userId == signedIn.profile.id }
            val pending = PendingProgress(animeId, episode, signedIn.profile.id)
            // Watching an earlier episode again doesn't replace a later one still waiting.
            val replaces = waiting == null || waiting.episode < episode
            if (replaces) queue.put(pending)
            send(signedIn, Told(pending, waiting, title).takeIf { replaces && inPlayer })
        }
    }

    /**
     * Puts back what [notice] changed, while AniList still shows what the TV set and the same user is signed in. Saved for
     * later, it takes the update out of the queue instead. False if nothing was put back.
     */
    public suspend fun undo(notice: ListNotice): Boolean {
        val change = notice.change
        if (change is NoticeChange.SavedForLater) {
            return failsSafely {
                if (account()?.profile?.id != notice.userId || settings().privateMode) return@failsSafely false
                // Sent since, it's on AniList and no longer here to take back.
                if (change.pending !in queue.all()) return@failsSafely false
                change.replaced?.let { queue.put(it) } ?: queue.remove(notice.animeId)
                true
            }
        }
        return once(notice.userId) { token ->
            val read = client.showEntry(token, notice.animeId) as? AniListResult.Success ?: return@once false
            val now = read.value.entry
            val unchanged =
                when (change) {
                    // What the TV never changes must be as it was too, or Undo would put back an old score or note.
                    is NoticeChange.Updated -> {
                        val before = notice.before
                        now?.status == change.status &&
                            now.progress == change.progress &&
                            (before == null || (now.score == before.score && now.notes == before.notes && now.private == before.private))
                    }

                    else -> {
                        now == null
                    }
                }
            if (!unchanged) return@once false
            val before = notice.before
            val putBack =
                if (before == null) {
                    now?.id?.let { client.removeEntry(token, it) is AniListResult.Success } ?: true
                } else {
                    restore(token, notice.animeId, now, before)
                }
            if (!putBack) return@once false
            // One still waiting would change it again.
            if (queue.all().any { it.animeId == notice.animeId && it.userId == notice.userId }) queue.remove(notice.animeId)
            changed.tryEmit(ListChange(notice.animeId, before?.status, notice.userId, before?.progress))
            true
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
        failsSafely {
            val signedIn = account()
            if (signedIn == null || signedIn.profile.id != forUser || signedIn.token.expiresAtEpochSeconds <= now()) {
                return@failsSafely false
            }
            if (settings().privateMode) return@failsSafely false
            val saved = client.saveProgress(signedIn.token, animeId, ProgressUpdate(progress, null, null))
            if (saved !is AniListResult.Success) return@failsSafely false
            if (queue.all().any { it.animeId == animeId && it.userId == forUser }) queue.remove(animeId)
            true
        }

    /**
     * [animeId] and the signed-in user's entry for it, for the list button. Null when signed out, with an expired token,
     * or when AniList can't be reached.
     */
    override suspend fun entry(animeId: Int): OnYourList? {
        // In private mode the list button and marks hide, as when AniList can't be reached.
        if (settings().privateMode) return null
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
            val update = statusUpdate(read.value, to, today())
            if (client.saveProgress(token, animeId, update) !is AniListResult.Success) return@once false
            changed.tryEmit(ListChange(animeId, to, forUser, update.progress))
            tell(read.value, animeId, forUser, NoticeChange.Updated(to, update.progress, byEpisode = false))
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
            changed.tryEmit(ListChange(animeId, null, forUser))
            if (id != null) tell(read.value, animeId, forUser, NoticeChange.Removed)
            true
        }

    override suspend fun markWatched(
        animeId: Int,
        upTo: Int,
        forUser: Int,
    ): Boolean =
        once(forUser) { token ->
            val read = client.showEntry(token, animeId) as? AniListResult.Success ?: return@once false
            val update = markedUpdate(read.value, upTo, today())
            if (update != null && client.saveProgress(token, animeId, update) !is AniListResult.Success) return@once false
            // One waiting for an episode the mark covers would change nothing.
            if (queue.all().any { it.animeId == animeId && it.userId == forUser && it.episode <= upTo }) queue.remove(animeId)
            val status = update?.status ?: read.value.entry?.status
            changed.tryEmit(ListChange(animeId, status, forUser, update?.progress ?: read.value.entry?.progress))
            if (update != null &&
                status != null
            ) {
                tell(read.value, animeId, forUser, NoticeChange.Updated(status, update.progress, byEpisode = true))
            }
            true
        }

    override suspend fun markUnwatched(
        animeId: Int,
        episode: Int,
        forUser: Int,
    ): Boolean =
        once(forUser) { token ->
            val read = client.showEntry(token, animeId) as? AniListResult.Success ?: return@once false
            // Taken off the list since Details read it, a save would add it back. Moved below the episode since, it would
            // move the progress up.
            if (read.value.entry == null || read.value.entry.progress < episode) return@once false
            val progress = episode - 1
            if (client.saveProgress(token, animeId, ProgressUpdate(progress, null, null)) !is AniListResult.Success) return@once false
            // One still waiting would move it forward again.
            if (queue.all().any { it.animeId == animeId && it.userId == forUser }) queue.remove(animeId)
            changed.tryEmit(ListChange(animeId, read.value.entry.status, forUser, progress))
            tell(read.value, animeId, forUser, NoticeChange.Updated(read.value.entry.status, progress, byEpisode = true))
            true
        }

    /** Off sends nothing, and Ask only sends once the show is answered yes. */
    override fun watching(
        animeId: Int,
        episode: Int,
        title: AnimeTitle?,
    ) {
        // Queued behind any update ahead of it, and a settings file that can't be read sends nothing.
        locked {
            val signedIn = account() ?: return@locked
            val send =
                when (settings().updateAniList) {
                    UpdateAniList.ALWAYS -> true
                    UpdateAniList.ASK -> answers?.answer(signedIn.profile.id, animeId) == true
                    UpdateAniList.OFF -> false
                }
            if (send) watched(animeId, episode, signedIn.profile.id, title, inPlayer = true)
        }
    }

    override suspend fun asksFirst(animeId: Int): Int? {
        val now = settings()
        if (now.privateMode || now.updateAniList != UpdateAniList.ASK) return null
        val signedIn = account() ?: return null
        return signedIn.profile.id.takeIf { answers?.answer(it, animeId) == null }
    }

    override fun answer(
        animeId: Int,
        forUser: Int,
        update: Boolean,
        episode: Int,
        title: AnimeTitle?,
    ) {
        locked {
            // Only the user who was asked can answer for their list.
            if (account()?.profile?.id != forUser || settings().privateMode) return@locked
            answers?.save(forUser, animeId, update)
            if (update) watched(animeId, episode, forUser, title, inPlayer = true)
        }
    }

    // Puts [animeId]'s entry back from [now] to [before]. AniList adds a rewatch itself when a Rewatching entry becomes
    // Completed, whatever count the save sends, so then the count is saved again once it's Completed.
    private suspend fun restore(
        token: AniListToken,
        animeId: Int,
        now: ListEntry?,
        before: ListEntry,
    ): Boolean {
        if (client.restoreEntry(token, animeId, before) !is AniListResult.Success) return false
        if (now?.status != ListStatus.REWATCHING || before.status != ListStatus.COMPLETED) return true
        val rewatches = ProgressUpdate(before.progress, null, null, repeat = before.repeat)
        return client.saveProgress(token, animeId, rewatches) is AniListResult.Success
    }

    // Runs in [scope], so it finishes once the screen has gone, after any update ahead of it, while [forUser] is signed in.
    private suspend fun once(
        forUser: Int,
        work: suspend (AniListToken) -> Boolean,
    ): Boolean =
        failsSafely {
            val signedIn = liveAccount()
            if (signedIn == null || signedIn.profile.id != forUser || settings().privateMode) false else work(signedIn.token)
        }

    // One at a time, in [scope]. An error such as a queue file that can't be read fails the change, as AniList refusing
    // it would, rather than crashing the screen that asked for it.
    private suspend fun failsSafely(work: suspend () -> Boolean): Boolean =
        scope
            .async {
                try {
                    lock.withLock { work() }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
            }.await()

    private suspend fun liveAccount(): SignedInAccount? = account()?.takeIf { it.token.expiresAtEpochSeconds > now() }

    private fun tell(
        show: ShowEntry,
        animeId: Int,
        userId: Int,
        change: NoticeChange,
    ) {
        noticed.tryEmit(ListNotice(animeId, userId, show.title, change, show.entry))
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

    // [told] is the update just watched in the player, which says what happened to it.
    private suspend fun send(
        signedIn: SignedInAccount,
        told: Told? = null,
    ) {
        // AniList would refuse an expired token. What's waiting stays, for when this user signs in again.
        if (signedIn.token.expiresAtEpochSeconds <= now()) return
        for (pending in queue.all()) {
            // Updates made before private mode, or left when it's turned on part way through, wait until it's off.
            if (settings().privateMode) return
            if (pending.userId != signedIn.profile.id) {
                queue.remove(pending.animeId)
                continue
            }
            val failure = sendOne(signedIn.token, pending, told?.takeIf { it.pending == pending }) ?: continue
            if (!failure.worthRetrying) {
                queue.remove(pending.animeId)
            } else if (told?.pending == pending) {
                noticed.tryEmit(
                    ListNotice(
                        pending.animeId,
                        pending.userId,
                        told.title,
                        NoticeChange.SavedForLater(pending, told.replaced),
                        null,
                        fromWatching = true,
                    ),
                )
            }
        }
    }

    // Null once the update needs nothing more: it was saved, or AniList is already there. Otherwise why it failed.
    private suspend fun sendOne(
        token: AniListToken,
        pending: PendingProgress,
        told: Told?,
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
            val status = update.status ?: show.entry?.status ?: ListStatus.WATCHING
            changed.tryEmit(ListChange(pending.animeId, status, pending.userId, update.progress))
            if (told != null) {
                val notice =
                    ListNotice(
                        pending.animeId,
                        pending.userId,
                        told.title ?: show.title,
                        NoticeChange.Updated(status, update.progress, true),
                        show.entry,
                        true,
                    )
                noticed.tryEmit(notice)
            }
        }
        queue.remove(pending.animeId)
        return null
    }
}

// An update watched in the player: what it [replaced] in the queue, and the show's [title] in case AniList can't be read.
private data class Told(
    val pending: PendingProgress,
    val replaced: PendingProgress?,
    val title: AnimeTitle?,
)

// Changes are never waited on, so a screen that's slow to collect can't hold up sync. Screens that miss one read the
// entry when they open.
private const val CHANGES_KEPT = 8

// A refused token is kept too: signing in again as the same user sends it.
private val AniListError.worthRetrying: Boolean
    get() =
        when (this) {
            is AniListError.Network, is AniListError.RateLimited, AniListError.SignedOut -> true
            is AniListError.Http -> statusCode >= 500 || statusCode == 408
            is AniListError.Unexpected -> false
        }
