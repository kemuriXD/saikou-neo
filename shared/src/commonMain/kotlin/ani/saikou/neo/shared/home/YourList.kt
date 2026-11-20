package ani.saikou.neo.shared.home

import ani.saikou.neo.shared.account.ContinueListClient
import ani.saikou.neo.shared.account.ListedProgress
import ani.saikou.neo.shared.account.SignedInAccount
import ani.saikou.neo.shared.catalogue.AniListResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// An episode's length when AniList doesn't know it: the usual TV episode.
internal val USUAL_EPISODE = 24.minutes
private val LIST_TIMEOUT = 10.seconds

// Home asks again whenever the account may have changed, which at start is more than once.
private const val KEEP_FOR_SECONDS = 60L

/** The shows on the signed-in user's AniList list that Continue watching adds to this TV's history (ADR 0049). */
public fun interface YourList {
    /** Their Watching and Rewatching progress. Empty when signed out or when the list can't be had. */
    public suspend fun shows(): List<ListedProgress>

    /** [animeId]'s AniList progress was just set to [progress] from this TV. */
    public suspend fun moved(
        animeId: Int,
        progress: Int,
    ) {}
}

/**
 * Reads [account]'s list from AniList with [client]. An expired token isn't sent. A list AniList refuses or doesn't send
 * within 10 seconds is left out, so Continue watching still shows this TV's history. A list AniList sent is kept for a
 * minute for the same user.
 */
public class AniListYourList(
    private val account: suspend () -> SignedInAccount?,
    private val client: ContinueListClient,
    /** The time now, in seconds since 1970. */
    private val now: () -> Long = { Clock.System.now().epochSeconds },
) : YourList {
    private class Kept(
        val userId: Int,
        val at: Long,
        val shows: List<ListedProgress>,
    )

    private val lock = Mutex()
    private var kept: Kept? = null

    override suspend fun shows(): List<ListedProgress> =
        lock.withLock {
            val signedIn = account() ?: return@withLock emptyList()
            val at = now()
            if (signedIn.token.expiresAtEpochSeconds <= at) return@withLock emptyList()
            kept?.takeIf { it.userId == signedIn.profile.id && at - it.at < KEEP_FOR_SECONDS }?.let { return@withLock it.shows }
            val sent = withTimeoutOrNull(LIST_TIMEOUT) { client.continueList(signedIn.token, signedIn.profile.id) }
            val shows = (sent as? AniListResult.Success)?.value ?: return@withLock emptyList()
            kept = Kept(signedIn.profile.id, at, shows)
            shows
        }

    // Kept for the rest of its minute where it moved, so Details doesn't ask about the same show again.
    override suspend fun moved(
        animeId: Int,
        progress: Int,
    ): Unit =
        lock.withLock {
            val list = kept ?: return@withLock
            val shows = list.shows.map { if (it.animeId == animeId) it.copy(progress = progress, updatedAt = now()) else it }
            kept = Kept(list.userId, list.at, shows)
        }
}

/**
 * [history] with [listed] added, one place per show (ADR 0049). An AniList show picks up after its progress. Where a show
 * is in both, the place that changed last wins, but the TV's place is kept when both pick up at the same episode, with
 * the later time.
 */
internal fun withYourList(
    history: Map<Int, WatchProgress>,
    listed: List<ListedProgress>,
): Map<Int, WatchProgress> {
    val merged = history.toMutableMap()
    for (show in listed) {
        val length = show.episodeMinutes?.minutes ?: USUAL_EPISODE
        val fromList = WatchProgress(show.progress, length, length, null, show.updatedAt)
        val onTv = history[show.animeId]
        merged[show.animeId] =
            when {
                onTv == null -> fromList
                show.updatedAt == null || (onTv.savedAt ?: Long.MIN_VALUE) >= show.updatedAt -> onTv
                onTv.picksUpAt == show.progress + 1 -> onTv.copy(savedAt = show.updatedAt)
                else -> fromList
            }
    }
    return merged
}

// The episode this place carries on with, whether or not it's out.
internal val WatchProgress.picksUpAt: Int get() = if (watchedToEnd) episode + 1 else episode

/**
 * This history's shows, with [yourList]'s added once it's read. The list is read again each time [reads] sends. Without
 * a list, just the history.
 */
internal fun WatchHistory.withYourList(
    yourList: YourList?,
    reads: Flow<Any?> = flowOf(Unit),
): Flow<Map<Int, WatchProgress>> {
    if (yourList == null) return shows
    return combine(shows, reads.map { yourList.shows() }) { history, listed -> withYourList(history, listed) }
}
