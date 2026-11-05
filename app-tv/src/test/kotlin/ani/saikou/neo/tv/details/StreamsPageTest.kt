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
import androidx.compose.ui.test.requestFocus
import androidx.test.espresso.Espresso
import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AniZipEpisode
import ani.saikou.neo.shared.catalogue.AniZipEpisodes
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.details.DetailsState
import ani.saikou.neo.shared.details.episodeList
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.playback.FoundServer
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
private val sub = FoundServer("a", "Demo, Sub", ServerEdition.SUB, 24, qualities = listOf(1080, 720))
private val dub = FoundServer("b", "Demo, Dub", ServerEdition.DUB, null)
private val hard = FoundServer("c", "Demo, Hard", ServerEdition.HARD_SUB, null, qualities = listOf(720))

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
    private val editions = mutableListOf<SubOrDub>()
    private val retried = mutableListOf<String>()
    private var askedFor: Int? = null
    private var retries = 0
    private var opens = 0
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
                            onEdition = { editions += it },
                            onRetry = { retried += it.id },
                            actions =
                                StreamsActions(
                                    onRetry = { retries++ },
                                    onBack = back,
                                    onAddSource = { addedSource = true },
                                    onOpen = { opens++ },
                                ),
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
        compose.onNodeWithTag(sourceTag(demo.id)).assertIsFocused()
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
    fun allShowsSubAndDubUnderHeadingsWithNoBadge() {
        openEpisodeTwo()

        compose.onNodeWithTag(editionTag(SubOrDub.BOTH)).assertIsSelected()
        compose.onNodeWithText("SUB · 1").assertExists()
        compose.onNodeWithText("DUB · 1").assertExists()
        compose.onNodeWithTag(serverTag(demo.id, sub.id)).assert(hasText("1080p · 720p · 24 min") and !hasText("SUB"))
        compose.onNodeWithTag(serverTag(demo.id, dub.id)).assert(!hasText("DUB"))
    }

    @Test
    fun aServerShowsItsQualitiesAndNothingWhenItHasNone() {
        state.value =
            servers.copy(sources = listOf(SourceServers(demo, SourceAnswer.Found(listOf(sub, hard, dub)))))
        openEpisodeTwo()

        compose.onNodeWithTag(serverTag(demo.id, sub.id)).assert(hasText("1080p · 720p · 24 min"))
        compose.onNodeWithTag(serverTag(demo.id, hard.id)).assert(hasText("720p"))
        compose.onNodeWithTag(serverTag(demo.id, dub.id)).assert(!hasText("p", substring = true) and !hasText("min", substring = true))
    }

    @Test
    fun subShowsOneListWithHardSubTaggedAtTheEnd() {
        state.value =
            servers.copy(sources = listOf(SourceServers(demo, SourceAnswer.Found(listOf(hard, dub, sub)))), subOrDub = SubOrDub.SUB)
        openEpisodeTwo()

        compose.onNodeWithText("SUB · 2").assertDoesNotExist()
        compose.onNodeWithTag(serverTag(demo.id, dub.id)).assertDoesNotExist()
        compose.onNodeWithTag(serverTag(demo.id, hard.id)).assert(hasText("Hard Sub"))
        press(Key.DirectionRight)
        compose.onNodeWithTag(serverTag(demo.id, sub.id)).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag(serverTag(demo.id, hard.id)).assertIsFocused()
    }

    @Test
    fun eachSourceSaysHowItsDoing() {
        openEpisodeTwo()

        compose.onNodeWithTag(sourceTag(demo.id)).assert(hasText("Demo source") and hasText("2 servers"))
        compose.onNodeWithTag(sourceTag(example.id)).assert(hasText("Looking…"))
    }

    @Test
    fun pickingAServerPassesItOn() {
        openEpisodeTwo()

        press(Key.DirectionRight)
        compose.onNodeWithTag(serverTag(demo.id, sub.id)).assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        compose.onNodeWithTag(serverTag(demo.id, dub.id)).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(listOf(demo.id to sub.name, demo.id to dub.name), picked)
    }

    @Test
    fun leftFromTheServersReturnsToTheirSource() {
        openEpisodeTwo()

        press(Key.DirectionRight)
        press(Key.DirectionLeft)

        compose.onNodeWithTag(sourceTag(demo.id)).assertIsFocused()
    }

    @Test
    fun thePillsChangeSubOrDubForThePage() {
        openEpisodeTwo()

        compose.onNodeWithTag(editionTag(SubOrDub.DUB)).requestFocus()
        press(Key.DirectionCenter)

        assertEquals(listOf(SubOrDub.DUB), editions)
    }

    @Test
    fun theListScrollsToAnyNumberOfSources() {
        val many = (1..12).map { InstalledSource("com.example.s$it", "Source $it", "1.0.0") }
        state.value = servers.copy(sources = many.map { SourceServers(it, SourceAnswer.Found(listOf(sub))) })
        openEpisodeTwo()

        compose.onNodeWithTag(sourceTag(many.first().id)).assertIsFocused()
        repeat(11) { press(Key.DirectionDown) }

        compose.onNodeWithTag(sourceTag(many.last().id)).assertIsFocused()
    }

    private val failing =
        servers.copy(
            sources =
                listOf(
                    SourceServers(demo, SourceAnswer.Found(listOf(sub))),
                    SourceServers(example, SourceAnswer.Failed(FailReason.NoAnswer)),
                ),
        )

    /** Down to the failed source, then Right to its Retry. */
    private fun openTheFailedSource() {
        state.value = failing
        openEpisodeTwo()
        press(Key.DirectionDown)
        press(Key.DirectionRight)
    }

    @Test
    fun rightFromAFailedSourceShowsWhyAndRetry() {
        openTheFailedSource()

        compose.onNodeWithTag(STREAMS_RETRY_TAG).assertIsFocused()
        compose.onNodeWithText("Ask it again, or pick another source on the left.").assertExists()
        press(Key.DirectionCenter)

        assertEquals(listOf(example.id), retried)
        compose.onNodeWithTag(sourceTag(example.id)).assertIsFocused()
    }

    @Test
    fun aSourceThatAnswersAfterRetryTakesFocusToItsFirstServer() {
        openTheFailedSource()
        press(Key.DirectionCenter)

        state.value = failing.copy(sources = failing.sources.take(1) + SourceServers(example, SourceAnswer.Looking))
        compose.waitForIdle()
        state.value = failing.copy(sources = failing.sources.take(1) + SourceServers(example, SourceAnswer.Found(listOf(dub))))
        compose.waitForIdle()

        compose.onNodeWithTag(serverTag(example.id, dub.id)).assertIsFocused()
    }

    @Test
    fun afterMovingOnFocusStaysPutWhenTheRetryAnswers() {
        openTheFailedSource()
        press(Key.DirectionCenter)
        press(Key.DirectionUp)

        state.value = failing.copy(sources = failing.sources.take(1) + SourceServers(example, SourceAnswer.Found(listOf(dub))))
        compose.waitForIdle()

        compose.onNodeWithTag(sourceTag(demo.id)).assertIsFocused()
        compose.onNodeWithTag(serverTag(example.id, dub.id)).assertDoesNotExist()
    }

    @Test
    fun aRateLimitedSourceCountsDownWithoutRetry() {
        state.value =
            failing.copy(
                sources =
                    failing.sources.take(1) + SourceServers(example, SourceAnswer.Failed(FailReason.RateLimited(30), waitSeconds = 12)),
            )
        openEpisodeTwo()
        press(Key.DirectionDown)

        compose.onNodeWithTag(sourceTag(example.id)).assert(hasText("Too many requests. Try again in 12 s"))
        compose.onNodeWithTag(STREAMS_RETRY_TAG).assertDoesNotExist()
        compose.onNodeWithTag(sourceTag(example.id)).assertIsFocused()
    }

    @Test
    fun withNoSourcesItOffersToAddOne() {
        state.value = ServersState(emptyList(), ready = true)
        openEpisodeTwo()

        compose.onNodeWithText("No sources installed yet").assertExists()
        compose.onNodeWithText("Add one with a link or code in Settings\u00A0>\u00A0Sources", substring = true).assertExists()
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
    fun comingBackToTheEpisodeAsksAgain() {
        openEpisodeTwo()
        assertEquals(1, opens)

        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag(episodeTag(2)).assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitForIdle()

        assertEquals(2, opens)
    }

    @Test
    fun aNewAnswerIsNotOpeningThePageAgain() {
        openEpisodeTwo()

        state.value = servers.copy(subOrDub = SubOrDub.DUB)
        compose.waitForIdle()

        assertEquals(1, opens)
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
            ServersState(listOf(SourceServers(demo, SourceAnswer.Found(listOf(sub)))), subOrDub = SubOrDub.DUB, ready = true)
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
                        SourceServers(starter, SourceAnswer.Found(listOf(dub))),
                    ),
                subOrDub = SubOrDub.SUB,
            )
        openEpisodeTwo()

        compose.onNodeWithTag(sourceTag(example.id)).assert(hasText("The site blocked the request"))
        compose.onNodeWithTag(sourceTag(starter.id)).assert(hasText("Only Dub here"))
    }
}
