package ani.saikou.neo.shared.schedule

import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AirType
import ani.saikou.neo.shared.catalogue.AiringStatus
import ani.saikou.neo.shared.catalogue.TimetableEpisode
import ani.saikou.neo.shared.settings.ScheduleSettings
import kotlin.time.Duration
import kotlin.time.Instant

/** The device's time zone. Each time uses the offset in force then, so a clock change mid-week lands on the right day. */
public interface ScheduleZone {
    /** Its IANA id, such as `Europe/London`. */
    public val id: String

    public fun offsetSecondsAt(instant: Instant): Int
}

/** One episode on Schedule. */
public data class ScheduleRow(
    /** AnimeSchedule.net's id for the show. */
    val route: String,
    val title: String,
    val imageUrl: String?,
    val episodeNumber: Int?,
    /** [AirType.SUB] or [AirType.RAW] for an episode's row, or [AirType.DUB] for a dub with a row of its own. */
    val release: AirType,
    val airsAt: Instant,
    /** When the dub of this episode airs this week, or null when it doesn't. */
    val dubAt: Instant?,
    val delayed: Boolean,
)

/** One day of the week and its rows, in air-time order. */
public data class ScheduleDay(
    val date: AirDate,
    val rows: List<ScheduleRow>,
)

/**
 * Where this day's list starts at [now]: the next episode to air, or the last one once they've all aired. Null when the
 * day has none.
 */
public fun ScheduleDay.nowIndex(now: Instant): Int? =
    if (rows.isEmpty()) null else rows.indexOfFirst { it.airsAt > now }.takeIf { it >= 0 } ?: rows.lastIndex

/** Whether a time has come yet. */
public sealed interface Countdown {
    public data object Out : Countdown

    public data class In(
        val left: Duration,
    ) : Countdown
}

/** Whether [at] has come by [now]. Work it out again as the clock moves; nothing needs fetching. */
public fun countdown(
    at: Instant,
    now: Instant,
): Countdown = if (at <= now) Countdown.Out else Countdown.In(at - now)

/**
 * One row per episode, in air-time order. A row has the sub time, or the raw time when there's no sub. A dub of the same
 * episode goes in that row; a dub with no sub or raw this week gets its own row.
 */
internal fun scheduleRows(episodes: List<TimetableEpisode>): List<ScheduleRow> =
    episodes
        .groupBy { it.route to it.episodeNumber }
        .values
        .flatMap { releases ->
            val first = { type: AirType -> releases.filter { it.airType == type }.minByOrNull { it.episodeDate } }
            val dub = first(AirType.DUB)
            val main = first(AirType.SUB) ?: first(AirType.RAW)
            when {
                main != null -> listOf(main.toRow(dubAt = dub?.episodeDate))
                dub != null -> listOf(dub.toRow(dubAt = null))
                else -> emptyList()
            }
        }.sortedWith(compareBy({ it.airsAt }, { it.title }))

private fun TimetableEpisode.toRow(dubAt: Instant?) =
    ScheduleRow(
        route = route,
        title = title ?: english ?: romaji ?: route,
        imageUrl = imageUrl,
        episodeNumber = episodeNumber,
        release = airType,
        airsAt = episodeDate,
        dubAt = dubAt,
        delayed = airingStatus == AiringStatus.DELAYED,
    )

/**
 * The episodes [settings] lets through, before they become rows. With raw off a raw release is dropped, so an episode
 * with only a raw goes; with dubs off every dub release is dropped, so dub rows and dub times on rows go too.
 */
internal fun List<TimetableEpisode>.shownWith(settings: ScheduleSettings): List<TimetableEpisode> =
    filter { episode ->
        when {
            episode.airType == AirType.RAW && !settings.rawEpisodes -> false
            episode.airType == AirType.DUB && !settings.dubs -> false
            episode.donghua && !settings.chineseAnimation -> false
            episode.format != null && episode.format !in settings.formats -> false
            else -> settings.shortEpisodes || (episode.lengthMinutes ?: Int.MAX_VALUE) >= ScheduleSettings.SHORT_EPISODE_MINUTES
        }
    }

/** Monday to Sunday of the week [now] is in, and which of them is today. */
internal data class Week(
    val days: List<ScheduleDay>,
    val today: Int,
)

/** Puts [rows] on the days of this week in [zone]. Rows from another week are left out. */
internal fun scheduleWeek(
    rows: List<ScheduleRow>,
    zone: ScheduleZone,
    now: Instant,
): Week {
    val today = localDay(now, zone)
    val monday = mondayOf(today)
    val byDay = rows.groupBy { localDay(it.airsAt, zone) }
    val days = (0 until DAYS_IN_WEEK).map { ScheduleDay(AirDate.at((monday + it) * SECONDS_PER_DAY, 0), byDay[monday + it].orEmpty()) }
    return Week(days, (today - monday).toInt())
}

/** The Monday of the week [now] is in, as days since 1 January 1970 in [zone]. */
internal fun weekOf(
    now: Instant,
    zone: ScheduleZone,
): Long = mondayOf(localDay(now, zone))

private fun localDay(
    instant: Instant,
    zone: ScheduleZone,
): Long = (instant.epochSeconds + zone.offsetSecondsAt(instant)).floorDiv(SECONDS_PER_DAY)

// 1 January 1970 was a Thursday, three days after a Monday.
private fun mondayOf(day: Long): Long = day - (day + 3).mod(DAYS_IN_WEEK.toLong())

private const val SECONDS_PER_DAY = 86_400L
private const val DAYS_IN_WEEK = 7
