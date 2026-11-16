package ani.saikou.neo.shared.catalogue

import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** One saved answer. [value] is null when AnimeSchedule.net had no match at [checkedAt]. */
public data class SavedMatch<out T : Any>(
    val value: T?,
    val checkedAt: Instant,
)

/** Saved matches between AniList ids and AnimeSchedule.net routes, kept on the device. */
public interface ShowMatchStore {
    public fun route(aniListId: Int): SavedMatch<String>?

    public fun aniListId(route: String): SavedMatch<Int>?

    public fun saveRoute(
        aniListId: Int,
        match: SavedMatch<String>,
    )

    public fun saveAniListId(
        route: String,
        match: SavedMatch<Int>,
    )
}

/**
 * Matches shows between AniList and AnimeSchedule.net (ADR 0041). Each show is asked about once; a match is saved both
 * ways. A show with no match is asked about again after a week. Failed calls aren't saved.
 */
public class ShowMatcher internal constructor(
    private val client: AnimeScheduleClient,
    private val store: ShowMatchStore,
    private val now: () -> Instant,
) {
    public constructor(client: AnimeScheduleClient, store: ShowMatchStore) : this(client, store, { Clock.System.now() })

    /** AnimeSchedule's route for an AniList show. Use it only for list shows AniList says are airing. */
    public suspend fun routeFor(aniListId: Int): ShowLookup<String> {
        store.route(aniListId)?.let { saved -> saved.answer()?.let { return it } }
        val lookup = client.routeFor(aniListId)
        when (lookup) {
            is ShowLookup.Found -> saveMatch(aniListId, lookup.value)
            ShowLookup.NoMatch -> store.saveRoute(aniListId, SavedMatch(null, now()))
            is ShowLookup.Failure -> Unit
        }
        return lookup
    }

    /** The AniList id for an AnimeSchedule route. */
    public suspend fun aniListIdFor(route: String): ShowLookup<Int> {
        store.aniListId(route)?.let { saved -> saved.answer()?.let { return it } }
        val lookup = client.aniListIdFor(route)
        when (lookup) {
            is ShowLookup.Found -> saveMatch(lookup.value, route)
            ShowLookup.NoMatch -> store.saveAniListId(route, SavedMatch(null, now()))
            is ShowLookup.Failure -> Unit
        }
        return lookup
    }

    private fun saveMatch(
        aniListId: Int,
        route: String,
    ) {
        val checkedAt = now()
        store.saveRoute(aniListId, SavedMatch(route, checkedAt))
        store.saveAniListId(route, SavedMatch(aniListId, checkedAt))
    }

    /** The saved answer, or null when a "no match" is old enough to ask again. */
    private fun <T : Any> SavedMatch<T>.answer(): ShowLookup<T>? =
        when {
            value != null -> ShowLookup.Found(value)
            now() - checkedAt < NO_MATCH_RETRY -> ShowLookup.NoMatch
            else -> null
        }

    private companion object {
        val NO_MATCH_RETRY = 7.days
    }
}
