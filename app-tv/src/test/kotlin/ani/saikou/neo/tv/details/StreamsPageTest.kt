package ani.saikou.neo.tv.details

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AniZipEpisode
import ani.saikou.neo.shared.catalogue.AniZipEpisodes
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.details.DetailsState
import ani.saikou.neo.shared.details.episodeList
import ani.saikou.neo.shared.playback.BestPick
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.playback.FoundServer
import ani.saikou.neo.shared.playback.LastServer
import ani.saikou.neo.shared.playback.ServerEdition
import ani.saikou.neo.shared.playback.ServersState
import ani.saikou.neo.shared.playback.SourceAnswer
import ani.saikou.neo.shared.playback.SourceServers
import ani.saikou.neo.shared.settings.SubOrDub
import ani.saikou.neo.shared.sources.InstalledSource
import ani.saikou.neo.tv.theme.NeoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val demo = InstalledSource("com.example.demo", "Demo source", "1.0.0")
private val example = InstalledSource("com.example.example", "Example source", "1.0.0")
private val starter = InstalledSource("com.example.starter", "Starter source", "1.0.0")
private val sub = FoundServer("a", "Demo, Sub", ServerEdition.SUB, 24)
private val dub = FoundServer("b", "Demo, Dub", ServerEdition.DUB, null)

private val details =
    AnimeDetails(
        card = AnimeCard(7, AnimeTitle("Show 7", null, null), null, null, null, "TV", 12, null, null, null),
        description = null,
        status = "FINISHED",
        episodeMinutes = 24,
        genres = emptyList(),
        studios = emptyList(),
        nextEpisode = null,
    )

private val episodes =
    AniZipEpisodes(
        listOf(
            AniZipEpisode(1, "Rebirth", null, null, AirDate(2006, 10, 4), 23),
            AniZipEpisode(2, "Confrontation", null, null, AirDate(2006, 10, 11), 23),
        ),
        emptyList(),
    )

private val servers =
    ServersState(
        sources = listOf(SourceServers(demo, SourceAnswer.Found(listOf(sub, dub))), SourceServers(example, SourceAnswer.Looking)),
        best = BestPick(demo, sub, lastUsed = true, answeredFirst = true),
        lastUsed = LastServer(demo.id, sub.name, sub.edition),
        subOrDub = SubOrDub.BOTH,
    )

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StreamsPageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = mutableStateOf(servers)
    private val picked = mutableListOf<Pair<String, String>>()
    private val filters = mutableListOf<String?>()
    private var askedFor: Int? = null
    private var retries = 0
    private var addedSource = false

    private fun show() {
        compose.setContent {
            NeoTheme {
                DetailsScreen(
                    DetailsState.Loaded(details, episodes = episodeList(details, episodes, AirDate(2026, 10, 26))),
                    streams = { episode, back, modifier ->
                        askedFor = episode
                        StreamsPage(
                            state = state.value,
                            episode = episode,
                            title = "Show 7",
                            onPick = { source, server -> picked += source.id to server.name },
                            onFilter = { filters += it },
                            actions = StreamsActions(onRetry = { retries++ }, onBack = back, onAddSource = { addedSource = true }),
                            modifier = modifier,
                        )
                    },
                )
            }
        }
    }

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }

    /** From the main button into Episodes, then Select on episode 2. */
    private fun openEpisodeTwo() {
        show()
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        compose.onNodeWithTag(episodeTag(2)).assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitForIdle()
    }

    @Test
    fun choosingAnEpisodeOpensItsStreams() {
        openEpisodeTwo()

        assertEquals(2, askedFor)
        compose.onNodeWithTag(DETAILS_STREAMS_CRUMB_TAG).assert(hasText("E2 · Confrontation"))
        compose.onNodeWithTag(episodeTag(2)).assertDoesNotExist()
        compose.onNodeWithTag(STREAMS_BEST_TAG).assertIsFocused()
    }

    @Test
    fun backReturnsToTheEpisode() {
        openEpisodeTwo()

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithTag(DETAILS_STREAMS_CRUMB_TAG).assertDoesNotExist()
        compose.onNodeWithTag(episodeTag(2)).assertIsFocused()
    }

    @Test
    fun theBestPickSaysWhy() {
        openEpisodeTwo()

        compose.onNodeWithTag(STREAMS_BEST_TAG).assert(hasText("Demo source · Demo, Sub"))
        compose.onNodeWithTag(STREAMS_BEST_TAG).assert(hasText("Sub · last used, answered first"))
    }

    @Test
    fun serversAreGroupedBySourceWithTheLastUsedMarked() {
        openEpisodeTwo()

        compose.onNodeWithText("Demo source · 2 servers").assertExists()
        compose.onNodeWithTag(serverTag(demo.id, sub.id)).assert(hasText("Last used") and hasText("SUB") and hasText("24 min"))
        compose.onNodeWithTag(serverTag(demo.id, dub.id)).assert(!hasText("Last used") and hasText("DUB"))
    }

    @Test
    fun aSourceStillLookingSaysSo() {
        openEpisodeTwo()

        compose.onNodeWithText("Example source · still looking").assertExists()
        compose.onNodeWithText("Sub first, from Settings. 1 of 2 sources has answered.").assertExists()
    }

    @Test
    fun pickingAServerPassesItOn() {
        openEpisodeTwo()

        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        compose.onNodeWithTag(serverTag(demo.id, dub.id)).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(listOf(demo.id to sub.name, demo.id to dub.name), picked)
    }

    @Test
    fun theChipsShowOneSource() {
        openEpisodeTwo()
        compose.onNodeWithTag(sourceChipTag(null)).assertIsSelected()

        press(Key.DirectionDown)
        press(Key.DirectionRight)
        compose.onNodeWithTag(sourceChipTag(demo.id)).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(listOf(demo.id), filters)
    }

    @Test
    fun withNoSourcesItOffersToAddOne() {
        state.value = ServersState(emptyList(), ready = true)
        openEpisodeTwo()

        compose.onNodeWithText("No sources installed yet").assertExists()
        compose.onNodeWithText("Add one with a link or code in Settings00A0>00A0Sources", substring = true).assertExists()
        compose.onNodeWithTag(STREAMS_ADD_SOURCE_TAG).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(true, addedSource)
    }

    @Test
    fun whenNothingPlaysEachSourceSaysWhy() {
        state.value =
            ServersState(
                listOf(
                    SourceServers(demo, SourceAnswer.Failed(FailReason.NoAnswer)),
                    SourceServers(example, SourceAnswer.Failed(FailReason.NotFound)),
                    SourceServers(starter, SourceAnswer.Found(emptyList())),
                ),
                ready = true,
            )
        openEpisodeTwo()

        compose.onNodeWithText("No source could play this episode").assertExists()
        compose.onNodeWithText("Didn’t answer in 25 s").assertExists()
        compose.onNodeWithText("Couldn’t find Show 7. Try another source.").assertExists()
        compose.onNodeWithText("No servers for this episode").assertExists()
        compose.onNodeWithTag(STREAMS_TRY_AGAIN_TAG).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(1, retries)
    }

    @Test
    fun aRateLimitSaysWhenToTryAgain() {
        state.value =
            ServersState(
                listOf(
                    SourceServers(demo, SourceAnswer.Failed(FailReason.RateLimited(30))),
                    SourceServers(example, SourceAnswer.Failed(FailReason.RateLimited(150))),
                    SourceServers(starter, SourceAnswer.Failed(FailReason.RateLimited(null))),
                ),
                ready = true,
            )
        openEpisodeTwo()

        compose.onNodeWithText("Too many requests. Try again in 30 s").assertExists()
        compose.onNodeWithText("Too many requests. Try again in 3 min").assertExists()
        compose.onNodeWithText("Too many requests. Try again later").assertExists()
    }

    @Test
    fun backToEpisodesReturnsToTheEpisode() {
        state.value = ServersState(listOf(SourceServers(demo, SourceAnswer.Failed(FailReason.Blocked))), ready = true)
        openEpisodeTwo()

        press(Key.DirectionRight)
        compose.onNodeWithTag(STREAMS_BACK_TAG).assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitForIdle()

        compose.onNodeWithTag(DETAILS_STREAMS_CRUMB_TAG).assertDoesNotExist()
        compose.onNodeWithTag(episodeTag(2)).assertIsFocused()
    }

    @Test
    fun noDubIsPlainTextWithOneBack() {
        state.value =
            ServersState(listOf(SourceServers(demo, SourceAnswer.Found(emptyList(), hidden = 2))), subOrDub = SubOrDub.DUB, ready = true)
        openEpisodeTwo()

        compose.onNodeWithText("No Dub yet for episode 2").assertExists()
        compose.onNodeWithText("Your sources only have Sub for this one.", substring = true).assertExists()
        compose.onNodeWithTag(STREAMS_BACK_TAG).assertIsFocused()
        compose.onNodeWithTag(STREAMS_TRY_AGAIN_TAG).assertDoesNotExist()
    }

    @Test
    fun aFailedSourceAmongServersSaysWhy() {
        state.value =
            servers.copy(
                sources =
                    listOf(
                        SourceServers(demo, SourceAnswer.Found(listOf(sub, dub))),
                        SourceServers(example, SourceAnswer.Failed(FailReason.Blocked)),
                        SourceServers(starter, SourceAnswer.Found(emptyList(), hidden = 1)),
                    ),
                subOrDub = SubOrDub.SUB,
            )
        openEpisodeTwo()

        compose.onNodeWithText("The site blocked the request").assertExists()
        compose.onNodeWithText("Only Dub here").assertExists()
    }
}
