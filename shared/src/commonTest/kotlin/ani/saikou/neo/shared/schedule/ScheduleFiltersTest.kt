package ani.saikou.neo.shared.schedule

import ani.saikou.neo.shared.catalogue.AirType
import ani.saikou.neo.shared.catalogue.ShowFormat
import ani.saikou.neo.shared.catalogue.TimetableEpisode
import ani.saikou.neo.shared.settings.ScheduleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ScheduleFiltersTest {
    private fun rows(
        settings: ScheduleSettings,
        vararg episodes: TimetableEpisode,
    ) = scheduleRows(episodes.toList().shownWith(settings))

    @Test
    fun theDefaultsHideOnlyChineseAnimation() {
        val shown =
            rows(
                ScheduleSettings(),
                episode("frieren", 5, AirType.SUB, "2026-11-18T15:00:00Z"),
                episode("psyren", 2, AirType.RAW, "2026-11-18T10:00:00Z", lengthMinutes = 5),
                episode("bleach", 3, AirType.DUB, "2026-11-19T10:00:00Z"),
                episode("ling-wu-dalu", 233, AirType.RAW, "2026-11-18T02:00:00Z", format = ShowFormat.ONA, donghua = true),
            )

        assertEquals(listOf("psyren", "frieren", "bleach"), shown.map { it.route })
    }

    @Test
    fun chineseAnimationShowsWhenItsOn() {
        val shown =
            rows(
                ScheduleSettings(chineseAnimation = true),
                episode("ling-wu-dalu", 233, AirType.RAW, "2026-11-18T02:00:00Z", donghua = true),
            )

        assertEquals(listOf("ling-wu-dalu"), shown.map { it.route })
    }

    // AnimeSchedule lists donghua as ONA, so the Formats ticks would otherwise hide every one.
    @Test
    fun donghuaOnShowsDonghuaWhateverFormatsAreTicked() {
        val shown =
            rows(
                ScheduleSettings(chineseAnimation = true, formats = setOf(ShowFormat.TV)),
                episode("ling-wu-dalu", 233, AirType.RAW, "2026-11-18T02:00:00Z", format = ShowFormat.ONA, donghua = true),
                episode("a-japanese-ona", 4, AirType.SUB, "2026-11-18T03:00:00Z", format = ShowFormat.ONA),
            )

        assertEquals(listOf("ling-wu-dalu"), shown.map { it.route })
    }

    @Test
    fun releasesAndShortEpisodesStillApplyToDonghua() {
        val shown =
            rows(
                ScheduleSettings(chineseAnimation = true, rawEpisodes = false, shortEpisodes = false, formats = setOf(ShowFormat.TV)),
                episode("ling-wu-dalu", 233, AirType.RAW, "2026-11-18T02:00:00Z", format = ShowFormat.ONA, donghua = true),
                episode(
                    "wu-shang-shen-di",
                    7,
                    AirType.SUB,
                    "2026-11-18T04:00:00Z",
                    format = ShowFormat.ONA,
                    lengthMinutes = 7,
                    donghua = true,
                ),
            )

        assertEquals(emptyList(), shown.map { it.route })
    }

    @Test
    fun rawOffHidesRawOnlyEpisodesButNotSubs() {
        val shown =
            rows(
                ScheduleSettings(rawEpisodes = false),
                episode("frieren", 5, AirType.RAW, "2026-11-18T14:00:00Z"),
                episode("frieren", 5, AirType.SUB, "2026-11-18T15:00:00Z"),
                episode("psyren", 2, AirType.RAW, "2026-11-18T10:00:00Z"),
            )

        assertEquals(listOf("frieren"), shown.map { it.route })
        assertEquals(AirType.SUB, shown.single().release)
    }

    // A raw with its dub the same week is more than a raw-only episode, so the dub keeps it.
    @Test
    fun rawOffLeavesARawEpisodesDubAsItsOwnRow() {
        val shown =
            rows(
                ScheduleSettings(rawEpisodes = false),
                episode("psyren", 2, AirType.RAW, "2026-11-18T10:00:00Z"),
                episode("psyren", 2, AirType.DUB, "2026-11-20T10:00:00Z"),
            )

        assertEquals(AirType.DUB, shown.single().release)
        assertEquals(Instant.parse("2026-11-20T10:00:00Z"), shown.single().airsAt)
    }

    @Test
    fun dubsOffHidesDubRowsAndTheDubTime() {
        val shown =
            rows(
                ScheduleSettings(dubs = false),
                episode("frieren", 5, AirType.SUB, "2026-11-18T15:00:00Z"),
                episode("frieren", 5, AirType.DUB, "2026-11-21T18:00:00Z"),
                episode("bleach", 3, AirType.DUB, "2026-11-19T10:00:00Z"),
            )

        assertEquals(listOf("frieren"), shown.map { it.route })
        assertEquals(null, shown.single().dubAt)
    }

    @Test
    fun aFormatThatsOffIsHidden() {
        val shown =
            rows(
                ScheduleSettings(formats = setOf(ShowFormat.TV)),
                episode("frieren", 5, AirType.SUB, "2026-11-18T15:00:00Z", format = ShowFormat.TV),
                episode("film", 1, AirType.SUB, "2026-11-18T16:00:00Z", format = ShowFormat.MOVIE),
                episode("short", 1, AirType.SUB, "2026-11-18T17:00:00Z", format = ShowFormat.TV_SHORT),
            )

        assertEquals(listOf("frieren"), shown.map { it.route })
    }

    @Test
    fun aShowWithNoFormatAlwaysShows() {
        val shown =
            rows(
                ScheduleSettings(formats = setOf(ShowFormat.MOVIE)),
                episode("mystery", 1, AirType.SUB, "2026-11-18T15:00:00Z", format = null),
            )

        assertEquals(listOf("mystery"), shown.map { it.route })
    }

    @Test
    fun shortEpisodesOffHidesEpisodesUnderFifteenMinutes() {
        val shown =
            rows(
                ScheduleSettings(shortEpisodes = false),
                episode("five", 1, AirType.SUB, "2026-11-18T10:00:00Z", lengthMinutes = 5),
                episode("fourteen", 1, AirType.SUB, "2026-11-18T11:00:00Z", lengthMinutes = 14),
                episode("fifteen", 1, AirType.SUB, "2026-11-18T12:00:00Z", lengthMinutes = 15),
                episode("unknown", 1, AirType.SUB, "2026-11-18T13:00:00Z", lengthMinutes = null),
            )

        assertEquals(listOf("fifteen", "unknown"), shown.map { it.route })
    }
}
