package ani.saikou.neo.shared.settings

import ani.saikou.neo.shared.catalogue.ShowFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Nearly every donghua is raw only and every TV short is under 15 minutes, so each needs the other setting to show.
class LinkedScheduleSettingsTest {
    private fun settings(schedule: ScheduleSettings) = Settings(schedule = schedule)

    @Test
    fun turningDonghuaOnTurnsRawOn() {
        val changed = settings(ScheduleSettings(rawEpisodes = false)).withTick(TickList.RELEASES, ScheduleRelease.RAW, false)
        val on = changed.with(Setting.CHINESE_ANIMATION, true).schedule
        assertTrue(on.chineseAnimation)
        assertTrue(on.rawEpisodes)
    }

    @Test
    fun turningRawOffTurnsDonghuaOff() {
        val off = settings(ScheduleSettings(chineseAnimation = true)).withTick(TickList.RELEASES, ScheduleRelease.RAW, false).schedule
        assertFalse(off.rawEpisodes)
        assertFalse(off.chineseAnimation)
    }

    @Test
    fun tickingTvShortTurnsShortEpisodesOn() {
        val start = ScheduleSettings(shortEpisodes = false, formats = setOf(ShowFormat.TV))
        val ticked = settings(start).withTick(TickList.FORMATS, ShowFormat.TV_SHORT, true).schedule
        assertEquals(setOf(ShowFormat.TV, ShowFormat.TV_SHORT), ticked.formats)
        assertTrue(ticked.shortEpisodes)
    }

    @Test
    fun turningShortEpisodesOffUnticksTvShort() {
        val off = settings(ScheduleSettings()).with(Setting.SHORT_EPISODES, false).schedule
        assertFalse(off.shortEpisodes)
        assertFalse(ShowFormat.TV_SHORT in off.formats)
    }

    @Test
    fun tvShortStaysWhenItsTheOnlyFormatTicked() {
        val off = settings(ScheduleSettings(formats = setOf(ShowFormat.TV_SHORT))).with(Setting.SHORT_EPISODES, false).schedule
        assertEquals(setOf(ShowFormat.TV_SHORT), off.formats)
    }

    @Test
    fun otherChangesLeaveTheLinkedSettingsAlone() {
        val start = ScheduleSettings(rawEpisodes = false, shortEpisodes = false, formats = setOf(ShowFormat.TV))
        val onaTicked = settings(start).withTick(TickList.FORMATS, ShowFormat.ONA, true)
        val changed = onaTicked.withTick(TickList.RELEASES, ScheduleRelease.DUB, false)
        assertEquals(start.copy(formats = setOf(ShowFormat.TV, ShowFormat.ONA), dubs = false), changed.schedule)
    }
}
