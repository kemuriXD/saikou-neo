package ani.saikou.neo.shared.schedule

import ani.saikou.neo.shared.account.AniListAccountClient
import ani.saikou.neo.shared.account.SignedInAccount
import ani.saikou.neo.shared.account.WatchListShow
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeScheduleClient
import ani.saikou.neo.shared.catalogue.AnimeScheduleResult
import ani.saikou.neo.shared.catalogue.ShowLookup
import ani.saikou.neo.shared.catalogue.ShowMatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Where Schedule gets its week, your list and show matches. */
public interface ScheduleSource {
    /** This week's timetable in [zone]. */
    public suspend fun week(zone: ScheduleZone): AnimeScheduleResult

    public suspend fun watchList(account: SignedInAccount): AniListResult<List<WatchListShow>>

    /** AnimeSchedule.net's route for an AniList show. Only for list shows AniList says are airing. */
    public suspend fun routeFor(aniListId: Int): ShowLookup<String>

    public suspend fun aniListIdFor(route: String): ShowLookup<Int>
}

/** The real [ScheduleSource] (ADR 0041). The app keeps one, so the week it fetched lasts while Schedule is closed. */
public class AnimeScheduleSource(
    client: AnimeScheduleClient,
    private val matcher: ShowMatcher,
    private val account: AniListAccountClient,
) : ScheduleSource {
    private val cache = WeekCache(fetch = client::week, now = { Clock.System.now() })

    override suspend fun week(zone: ScheduleZone): AnimeScheduleResult = cache.week(zone)

    override suspend fun watchList(account: SignedInAccount): AniListResult<List<WatchListShow>> =
        this.account.watchList(account.token, account.profile.id)

    override suspend fun routeFor(aniListId: Int): ShowLookup<String> = matcher.routeFor(aniListId)

    override suspend fun aniListIdFor(route: String): ShowLookup<Int> = matcher.aniListIdFor(route)
}

/** Keeps a fetched week for 15 minutes, as long as it's still the same week. Failures aren't kept. */
internal class WeekCache(
    private val fetch: suspend (zone: String) -> AnimeScheduleResult,
    private val now: () -> Instant,
) {
    private class Kept(
        val zone: String,
        val week: Long,
        val fetchedAt: Instant,
        val result: AnimeScheduleResult.Success,
    )

    private val lock = Mutex()
    private var kept: Kept? = null

    suspend fun week(zone: ScheduleZone): AnimeScheduleResult =
        lock.withLock {
            val at = now()
            val week = weekOf(at, zone)
            kept
                ?.takeIf { it.zone == zone.id && it.week == week && at - it.fetchedAt < KEEP_FOR }
                ?.let { return@withLock it.result }
            fetch(zone.id).also { if (it is AnimeScheduleResult.Success) kept = Kept(zone.id, week, at, it) }
        }

    private companion object {
        val KEEP_FOR = 15.minutes
    }
}
