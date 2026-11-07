package ani.saikou.neo.shared.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
