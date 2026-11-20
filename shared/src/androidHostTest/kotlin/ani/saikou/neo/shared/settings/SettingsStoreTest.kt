package ani.saikou.neo.shared.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import ani.saikou.neo.shared.catalogue.ShowFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsStoreTest {
    private val folder = createTempDirectory("settings").toFile()
    private val file = File(folder, "settings.preferences_pb")
    private val scopes = mutableListOf<CoroutineScope>()

    // Each store gets its own scope, so closing one lets another open the same file, as after a restart.
    private fun store(): Pair<SettingsStore, CoroutineScope> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scopes += scope
        return SettingsStore.at(file.absolutePath, scope) to scope
    }

    @AfterTest
    fun cleanUp() {
        scopes.forEach { it.cancel() }
        folder.deleteRecursively()
    }

    @Test
    fun aNewInstallHasTheDefaults() =
        runBlocking {
            val (store, _) = store()

            assertEquals(
                Settings(showUnairedAnime = false, showUpcomingEpisodes = true, slowTv = false, subOrDub = SubOrDub.BOTH),
                store.settings.first(),
            )
        }

    @Test
    fun aChangeIsKeptAfterARestart() =
        runBlocking {
            val (first, scope) = store()
            first.update { it.copy(showUnairedAnime = true, slowTv = true, subOrDub = SubOrDub.DUB) }
            // DataStore lets the file go once its scope ends.
            scope.coroutineContext.job.cancelAndJoin()

            val (second, _) = store()

            assertEquals(
                Settings(showUnairedAnime = true, showUpcomingEpisodes = true, slowTv = true, subOrDub = SubOrDub.DUB),
                second.settings.first(),
            )
        }

    @Test
    fun aNewInstallHasTheScheduleDefaults() =
        runBlocking {
            val (store, _) = store()

            assertEquals(
                ScheduleSettings(
                    rawEpisodes = true,
                    dubs = true,
                    formats = ShowFormat.entries.toSet(),
                    chineseAnimation = false,
                    shortEpisodes = true,
                ),
                store.settings.first().schedule,
            )
        }

    @Test
    fun everyScheduleSettingIsKeptAfterARestart() =
        runBlocking {
            val (first, scope) = store()
            val changed =
                ScheduleSettings(
                    rawEpisodes = false,
                    dubs = false,
                    formats = setOf(ShowFormat.TV, ShowFormat.MOVIE),
                    chineseAnimation = true,
                    shortEpisodes = false,
                )
            first.update { it.copy(schedule = changed) }
            scope.coroutineContext.job.cancelAndJoin()

            val (second, _) = store()

            assertEquals(Settings(schedule = changed), second.settings.first())
        }

    @Test
    fun theLastFormatStaysOn() {
        val tvOnly = ScheduleSettings(formats = setOf(ShowFormat.TV))

        assertEquals(tvOnly, tvOnly.withFormat(ShowFormat.TV, on = false))
        assertEquals(setOf(ShowFormat.TV, ShowFormat.OVA), tvOnly.withFormat(ShowFormat.OVA, on = true).formats)
        assertEquals(setOf(ShowFormat.OVA), tvOnly.withFormat(ShowFormat.OVA, on = true).withFormat(ShowFormat.TV, on = false).formats)
    }

    @Test
    fun noFormatsSavedReadsAsEveryFormat() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val dataStore = PreferenceDataStoreFactory.createWithPath(scope = scope) { file.absolutePath.toPath() }
            dataStore.edit { it[stringPreferencesKey("schedule_formats")] = "BOOK," }
            dataStore.data.first()
            scope.coroutineContext.job.cancelAndJoin()

            val (store, _) = store()

            assertEquals(
                ShowFormat.entries.toSet(),
                store.settings
                    .first()
                    .schedule.formats,
            )
        }

    @Test
    fun aNewInstallHasTheSubtitleDefaults() =
        runBlocking {
            val (store, _) = store()

            assertEquals(SubtitleSettings(), store.subtitles.first())
        }

    @Test
    fun everySubtitleSettingIsKeptAfterARestart() =
        runBlocking {
            val (first, scope) = store()
            first.updateSubtitles { everySubtitleSettingChanged }
            scope.coroutineContext.job.cancelAndJoin()

            val (second, _) = store()

            assertEquals(everySubtitleSettingChanged, second.subtitles.first())
        }

    @Test
    fun resettingSubtitlesLeavesOtherSettingsAlone() =
        runBlocking {
            val (store, _) = store()
            store.update { it.copy(slowTv = true) }
            store.updateSubtitles { everySubtitleSettingChanged }

            store.updateSubtitles { SubtitleSettings() }

            assertEquals(SubtitleSettings(), store.subtitles.first())
            assertEquals(Settings(slowTv = true), store.settings.first())
        }

    @Test
    fun aSubtitleValueThatCantBeReadIsItsDefault() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val dataStore = PreferenceDataStoreFactory.createWithPath(scope = scope) { file.absolutePath.toPath() }
            dataStore.edit {
                it[stringPreferencesKey("subtitle_size")] = "P999"
                it[stringPreferencesKey("subtitle_look")] = "mine:2"
                it[stringPreferencesKey("subtitle_position")] = "TOP"
                it[stringPreferencesKey("subtitle_mine_1")] = "PURPLE;P75;-;false"
            }
            dataStore.data.first()
            scope.coroutineContext.job.cancelAndJoin()

            val (store, _) = store()
            val subtitles = store.subtitles.first()

            assertEquals(SubtitleSize.P100, subtitles.size)
            // My look 2 doesn't exist, so Classic is in use.
            assertEquals(LookId.BuiltIn(BuiltInLook.CLASSIC), subtitles.look)
            assertEquals(SubtitlePosition.TOP, subtitles.position)
            assertEquals(LookStyle(textOpacity = Opacity.P75, bold = false), subtitles.myLooks[1])
        }

    @Test
    fun theAniListSettingsAreKeptAfterARestart() =
        runBlocking {
            val (first, scope) = store()
            assertEquals(UpdateAniList.ALWAYS, first.settings.first().updateAniList)
            first.update { it.copy(updateAniList = UpdateAniList.ASK, privateMode = true) }
            scope.coroutineContext.job.cancelAndJoin()

            val (second, _) = store()

            assertEquals(Settings(updateAniList = UpdateAniList.ASK, privateMode = true), second.settings.first())
        }

    @Test
    fun stillWatchingStartsAfterTwoAndIsKeptAfterARestart() =
        runBlocking {
            val (first, scope) = store()
            assertEquals(StillWatching.AFTER_2, first.settings.first().stillWatching)
            first.update { it.copy(stillWatching = StillWatching.NEVER) }
            scope.coroutineContext.job.cancelAndJoin()

            val (second, _) = store()

            assertEquals(Settings(stillWatching = StillWatching.NEVER), second.settings.first())
        }

    @Test
    fun aShowsAnswerIsKeptForItsUserAfterARestart() =
        runBlocking {
            val (first, scope) = store()
            assertNull(first.answer(userId = 1, animeId = 101))
            first.save(userId = 1, animeId = 101, update = true)
            first.save(userId = 1, animeId = 202, update = false)
            first.save(userId = 1, animeId = 202, update = true)
            first.save(userId = 2, animeId = 101, update = false)
            scope.coroutineContext.job.cancelAndJoin()

            val (second, _) = store()

            assertEquals(true, second.answer(userId = 1, animeId = 101))
            assertEquals(true, second.answer(userId = 1, animeId = 202))
            assertEquals(false, second.answer(userId = 2, animeId = 101))
            assertNull(second.answer(userId = 2, animeId = 202))
        }

    @Test
    fun eachSettingIsSavedOnItsOwn() =
        runBlocking {
            val (store, _) = store()

            store.update { it.copy(showUpcomingEpisodes = false) }

            assertEquals(Settings(showUpcomingEpisodes = false), store.settings.first())
        }

    private companion object {
        val mine = LookStyle(TextColour.CYAN, Opacity.P50, SubtitleFont.CASUAL, false, Edge.SUNKEN, EdgeThickness.THICK, EdgeColour.WHITE)

        // Every value away from its default, and a show title holding the separators.
        val everySubtitleSettingChanged =
            SubtitleSettings(
                look = LookId.Mine(2),
                size = SubtitleSize.P175,
                startFromTvTextSize = true,
                position = SubtitlePosition.RAISED,
                fineTunePercent = -3,
                styledSubtitles = StyledSubtitles.USE_MY_LOOK,
                mySizeOnStyled = false,
                changesWhileWatching = WhileWatching.THIS_SHOW_ONLY,
                hdrBrightness = HdrBrightness.P60,
                changedLooks =
                    mapOf(
                        BuiltInLook.BOXED to LookStyle(box = Box(BoxColour.WHITE, Opacity.P25), band = Band(BandColour.GREY, Opacity.P75)),
                    ),
                myLooks =
                    mapOf(
                        1 to mine,
                        2 to mine.copy(textColour = TextColour.RED, font = SubtitleFont.MONOSPACE),
                        3 to mine.copy(edge = Edge.RAISED, edgeThickness = EdgeThickness.THIN, edgeColour = EdgeColour.DARK_GREY),
                    ),
                shows =
                    mapOf(
                        21 to
                            ShowSubtitles("A show; with: separators", SubtitlePick(LookId.Mine(3), SubtitleSize.P60, SubtitlePosition.TOP)),
                        7 to
                            ShowSubtitles(
                                "Another",
                                SubtitlePick(LookId.BuiltIn(BuiltInLook.MATCH_TV), SubtitleSize.P200, SubtitlePosition.BOTTOM),
                            ),
                    ),
            )
    }
}
