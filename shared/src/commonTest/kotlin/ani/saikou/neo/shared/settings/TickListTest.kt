package ani.saikou.neo.shared.settings

import ani.saikou.neo.shared.catalogue.ShowFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class TickListTest {
    @Test
    fun releasesAreRawEpisodesThenDubs() {
        assertEquals(listOf(ScheduleRelease.RAW, ScheduleRelease.DUB), TickList.RELEASES.items)
        assertEquals(ShowFormat.entries, TickList.FORMATS.items)
    }

    @Test
    fun releasesReadTheirTicksFromTheScheduleSettings() {
        val settings = Settings(schedule = ScheduleSettings(rawEpisodes = false, dubs = true))

        assertEquals(setOf<Any>(ScheduleRelease.DUB), settings.ticked(TickList.RELEASES))
    }

    @Test
    fun formatsReadTheirTicksFromTheScheduleSettings() {
        val settings = Settings(schedule = ScheduleSettings(formats = setOf(ShowFormat.TV, ShowFormat.MOVIE)))

        assertEquals(setOf<Any>(ShowFormat.TV, ShowFormat.MOVIE), settings.ticked(TickList.FORMATS))
    }

    @Test
    fun tickingAReleaseTurnsItOnOrOff() {
        val none = Settings().withTick(TickList.RELEASES, ScheduleRelease.RAW, false).withTick(TickList.RELEASES, ScheduleRelease.DUB, false)

        assertEquals(ScheduleSettings(rawEpisodes = false, dubs = false), none.schedule)
        assertEquals(true, none.withTick(TickList.RELEASES, ScheduleRelease.DUB, true).schedule.dubs)
    }

    // Formats keep at least one ticked, as ScheduleSettings.withFormat does.
    @Test
    fun theLastFormatStaysTicked() {
        val onlyTv = Settings(schedule = ScheduleSettings(formats = setOf(ShowFormat.TV)))

        assertEquals(onlyTv, onlyTv.withTick(TickList.FORMATS, ShowFormat.TV, false))
        assertEquals(
            setOf(ShowFormat.TV, ShowFormat.OVA),
            onlyTv.withTick(TickList.FORMATS, ShowFormat.OVA, true).schedule.formats,
        )
    }

    @Test
    fun allTickedSumsUpAsAll() {
        assertEquals(TickSummary.All, TickList.FORMATS.summary(ShowFormat.entries.toSet()))
        assertEquals(TickSummary.All, TickList.RELEASES.summary(ScheduleRelease.entries.toSet()))
    }

    @Test
    fun oneOrTwoTickedSumUpAsTheirNamesInListOrder() {
        assertEquals(TickSummary.Names(listOf(ShowFormat.TV)), TickList.FORMATS.summary(setOf(ShowFormat.TV)))
        assertEquals(
            TickSummary.Names(listOf(ShowFormat.TV, ShowFormat.MOVIE)),
            TickList.FORMATS.summary(setOf(ShowFormat.MOVIE, ShowFormat.TV)),
        )
        assertEquals(TickSummary.Names(listOf(ScheduleRelease.DUB)), TickList.RELEASES.summary(setOf(ScheduleRelease.DUB)))
    }

    @Test
    fun moreThanTwoButNotAllSumUpAsACount() {
        val four = setOf(ShowFormat.TV, ShowFormat.ONA, ShowFormat.MOVIE, ShowFormat.OVA)

        assertEquals(TickSummary.Count(4, 6), TickList.FORMATS.summary(four))
    }

    @Test
    fun nothingTickedSumsUpAsNone() {
        assertEquals(TickSummary.None, TickList.RELEASES.summary(emptySet()))
    }

    // The "keep at least one" note shows on the one format left ticked.
    @Test
    fun onlyTheLastTickedFormatIsLocked() {
        val onlyTv = Settings(schedule = ScheduleSettings(formats = setOf(ShowFormat.TV)))

        assertEquals(true, onlyTv.isLastTick(TickList.FORMATS, ShowFormat.TV))
        assertEquals(false, onlyTv.isLastTick(TickList.FORMATS, ShowFormat.OVA))
        assertEquals(false, Settings().isLastTick(TickList.FORMATS, ShowFormat.TV))
        // Releases can all be unticked: Sub always shows.
        val onlyDub = Settings(schedule = ScheduleSettings(rawEpisodes = false))
        assertEquals(false, onlyDub.isLastTick(TickList.RELEASES, ScheduleRelease.DUB))
    }

    @Test
    fun chineseAnimationAndShortEpisodesAreOnOffSettings() {
        val settings = Settings(schedule = ScheduleSettings(chineseAnimation = true, shortEpisodes = false))

        assertEquals(true, settings[Setting.CHINESE_ANIMATION])
        assertEquals(false, settings[Setting.SHORT_EPISODES])
        assertEquals(listOf(false, true), Setting.CHINESE_ANIMATION.choices)
        assertEquals(listOf(false, true), Setting.SHORT_EPISODES.choices)
    }
}
