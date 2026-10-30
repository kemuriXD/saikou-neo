package ani.saikou.neo.tv.details

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AniZipEpisode
import ani.saikou.neo.shared.catalogue.AniZipEpisodes
import ani.saikou.neo.shared.catalogue.AniZipExtra
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.NextEpisode
import ani.saikou.neo.shared.catalogue.RelatedAnime
import ani.saikou.neo.shared.catalogue.Relation
import ani.saikou.neo.shared.details.DetailsSection
import ani.saikou.neo.shared.details.DetailsState
import ani.saikou.neo.shared.details.Ratings
import ani.saikou.neo.shared.details.episodeList
import ani.saikou.neo.shared.home.Logo
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.tv.NeoNavigation
import ani.saikou.neo.tv.home.HomeScreen
import ani.saikou.neo.tv.home.card
import ani.saikou.neo.tv.home.loaded
import ani.saikou.neo.tv.theme.NeoColors
import ani.saikou.neo.tv.theme.NeoDimensions
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.fakeImages
import ani.saikou.neo.tv.ui.fakeImagesNeverArrive
import ani.saikou.neo.tv.ui.forgetFakeImages
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailsScreenTest {
    private val hidden = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)
    private val today = AirDate(2026, 10, 26)

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        state: DetailsState,
        onOpen: (Int) -> Unit = {},
        onRecommendations: () -> Unit = {},
    ) {
        compose.setContent { NeoTheme { DetailsScreen(state, onOpen = onOpen, onRecommendations = onRecommendations) } }
    }

    /** Inside the app's navigation, opened from Home, as on the TV. */
    private fun showFromHome(state: DetailsState) {
        compose.setContent {
            NeoTheme {
                NeoNavigation(
                    home = { openDetails, _ -> HomeScreen(loaded, onOpen = openDetails) },
                    seeMore = { _, _ -> },
                    details = { _, openDetails, openRecommendations ->
                        DetailsScreen(state, onOpen = openDetails, onRecommendations = { openRecommendations(7) })
                    },
                    recommendations = { _, _ -> Text("All the recommendations") },
                )
            }
        }
        press(Key.DirectionCenter)
        compose.waitForIdle()
    }

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }

    private fun back() {
        compose.waitForIdle()
        // Through the window, as the remote's Back key arrives.
        Espresso.pressBack()
        compose.waitForIdle()
    }

    @Test
    fun theTopSaysWhatTheAnimeIs() {
        show(DetailsState.Loaded(details))

        compose.onNodeWithText("Show 7").assertExists()
        compose.onNodeWithText("Drama · Mystery · 12 episodes").assertExists()
        // The format and season wait for the header on the lifted page.
        compose.onNodeWithText("TV · Fall 2026 · 12 episodes").assertDoesNotExist()
    }

    @Test
    fun theOverviewHasASynopsisPreviewAndTheFacts() {
        show(DetailsState.Loaded(details))

        compose.onNodeWithTag(DETAILS_OVERVIEW_TAB_TAG).assertIsSelected()
        compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).assertExists()
        compose.onNodeWithText("Fall 2026").assertExists()
        compose.onNodeWithText("OLM").assertExists()
        // AniList's score is there before the other sites answer.
        compose.onNodeWithTag(ratingTag("AniList"), useUnmergedTree = true).assertContentDescriptionEquals("AniList 8.5")
        // The editors' note waits for the reader.
        compose.onNodeWithText("Streamed early.").assertDoesNotExist()
    }

    // Like each fact, the synopsis has a caption saying what it is. It sits on the grid margin, outside the focus outline.
    @Test
    fun theSynopsisHasACaption() {
        show(DetailsState.Loaded(details))

        val caption = compose.onNodeWithText("Synopsis").assertLeftPositionInRootIsEqualTo(58.dp).getUnclippedBoundsInRoot()
        val synopsis = compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).getUnclippedBoundsInRoot()
        // Clear of the outline focus draws around the synopsis.
        assertTrue(caption.bottom + 6.dp <= synopsis.top)
        // Still on screen under the facts.
        assertTrue(synopsis.bottom <= 540.dp)
    }

    @Test
    fun downMovesToTheSynopsisAndBringsTheOverviewUp() {
        show(DetailsState.Loaded(details))

        press(Key.DirectionDown)
        press(Key.DirectionDown)

        compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).assertIsFocused()
        compose.onNodeWithTag(DETAILS_OVERVIEW_TAB_TAG).assertTopPositionInRootIsEqualTo(76.dp)
        // A small header stays at the top.
        compose.onNodeWithTag(DETAILS_HEADER_TAG).assertTopPositionInRootIsEqualTo(24.dp).assertHeightIsEqualTo(40.dp)
        // The facts below the synopsis come into view with it.
        compose.onNodeWithText("OLM").assertIsDisplayed()
        compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).assertHeightIsEqualTo((12 + 2 * 20 + 12).dp)
    }

    // Lifted, the tabs sit up and to the right of the synopsis. Sideways from a section never jumps up to them.
    @Test
    fun sidewaysFromTheSynopsisStaysOnIt() {
        show(DetailsState.Loaded(everything))
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        press(Key.DirectionRight)
        compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).assertIsFocused()
        press(Key.DirectionLeft)
        compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).assertIsFocused()
    }

    // Keys sent together arrive before the screen redraws, as when the remote is quick.
    private fun pressQuickly(keys: List<Key>) = compose.onRoot().performKeyInput { keys.forEach { pressKey(it) } }

    // Down straight after moving to a tab never lands in the section it's leaving, which would then vanish.
    @Test
    fun quickDownAfterMovingTabsDoesNotLoseFocus() {
        show(DetailsState.Loaded(franchise))
        press(Key.DirectionDown)

        pressQuickly(listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionDown))
        compose.waitForIdle()

        compose.onNodeWithTag(detailsTabTag(DetailsSection.RELATED)).assertIsSelected()
        assertTrue(focusedTag() in setOf(detailsTabTag(DetailsSection.RELATED), relatedPosterTag(8)))
    }

    /** The test tag of whatever has focus, or null when nothing has. */
    private fun focusedTag(): String? =
        compose
            .onAllNodes(isFocused())
            .fetchSemanticsNodes()
            .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.TestTag) }

    // Down straight after the main button goes towards the episodes it chose, not back to the Overview.
    @Test
    fun quickDownAfterTheMainButtonStaysWithTheEpisodes() {
        show(DetailsState.Loaded(details, episodes = episodeList(details, deathNote, today)))

        pressQuickly(listOf(Key.DirectionCenter, Key.DirectionDown, Key.DirectionDown))
        compose.waitForIdle()

        compose.onNodeWithTag(detailsTabTag(DetailsSection.EPISODES)).assertIsSelected()
        compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).assertDoesNotExist()
    }

    @Test
    fun upBringsTheTopBack() {
        show(DetailsState.Loaded(details))

        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionUp)

        compose.onNodeWithTag(DETAILS_OVERVIEW_TAB_TAG).assertIsFocused().assertTopPositionInRootIsEqualTo(352.dp)
        compose.onNodeWithTag(DETAILS_COMPACT_TITLE_TAG).assertDoesNotExist()
    }

    @Test
    fun theFocusedSynopsisGlowsAroundItsBorderButNotInsideIt() {
        show(DetailsState.Loaded(details))
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        // Robolectric doesn't always redraw the window on its own before a capture.
        compose.runOnUiThread {
            compose.activity.window.decorView
                .invalidate()
        }
        compose.waitForIdle()
        val screen = compose.onRoot().captureToImage().toPixelMap()
        val box = compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).getUnclippedBoundsInRoot()
        val density = compose.density.density

        fun brightness(y: Dp) = screen[(300 * density).toInt(), (y.value * density).toInt()].red
        // The page's shading only changes from left to right, so these are all over the same page colour.
        val page = brightness(box.top - 40.dp)
        assertEquals("inside, in the padding above the text", page, brightness(box.top + 7.dp), 0.01f)
        assertTrue("just outside the border", brightness(box.top - 6.dp) > page + 0.03f)
    }

    @Test
    fun selectingTheSynopsisOpensTheWholeOfIt() {
        show(DetailsState.Loaded(details))

        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        compose.onNodeWithTag(DETAILS_READER_TAG).assertIsFocused()
        // The preview stays underneath, so look for the story inside the reader.
        compose
            .onNode(
                hasText("Maomao returns to the pleasure district.\n\nOne day, she senses an anomaly.") and
                    hasAnyAncestor(hasTestTag(DETAILS_READER_TAG)),
            ).assertExists()
        compose.onNodeWithText("Streamed early.").assertExists()
        compose.onNodeWithText("Source: Crunchyroll").assertExists()
    }

    @Test
    fun theNoteLabelSitsAGapAboveTheNote() {
        show(DetailsState.Loaded(details))

        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.waitForIdle()

        val label = compose.onNodeWithText("NOTE").getUnclippedBoundsInRoot()
        val note = compose.onNodeWithText("Streamed early.").getUnclippedBoundsInRoot()
        assertEquals(4.dp, note.top - label.bottom)
        assertEquals(18.dp, label.bottom - label.top)
    }

    @Test
    fun backClosesTheReaderAndReturnsToTheSynopsis() {
        showFromHome(DetailsState.Loaded(details))

        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        back()

        compose.onNodeWithTag(DETAILS_READER_TAG).assertDoesNotExist()
        compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).assertIsFocused()
        // Focus turns the muted text paper. It once had focus but drew unfocused, as it missed the focus event.
        val preview = compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).captureToImage().toPixelMap()
        val paper = NeoColors().paper
        val brightest = (0 until preview.width).flatMap { x -> (0 until preview.height).map { y -> preview[x, y].red } }.max()
        assertEquals(paper.red, brightest, 0.02f)
    }

    @Test
    fun leavesOutWhatAniListDoesNotKnow() {
        val bare = card(7).copy(format = null, episodes = null, season = null, averageScore = null)
        show(DetailsState.Loaded(details.copy(card = bare, genres = emptyList(), studios = emptyList(), description = null)))

        // The year alone in the facts, and no line about it at the top.
        compose.onAllNodesWithText("2026").assertCountEquals(1)
        compose.onNodeWithTag(DETAILS_META_TAG).assertDoesNotExist()
        compose.onNodeWithTag(DETAILS_SYNOPSIS_TAG).assertDoesNotExist()
        compose.onNodeWithTag(DETAILS_RATING_TAG).assertDoesNotExist()
        compose.onNodeWithTag(DETAILS_RATINGS_TAG).assertDoesNotExist()
    }

    @Test
    fun theMeanRatingSitsAboveTheMainButtonWithAStar() {
        show(DetailsState.Loaded(details, ratings = Ratings(aniList = 84, mal = 86, imdb = 89)))

        val rating = compose.onNodeWithTag(DETAILS_RATING_TAG).assertContentDescriptionEquals("Rated 8.6 out of 10")
        compose.onNodeWithText("/10", useUnmergedTree = true).assertExists()
        val button = compose.onNodeWithTag(DETAILS_MAIN_BUTTON_TAG).getUnclippedBoundsInRoot()
        assertTrue(rating.getUnclippedBoundsInRoot().bottom <= button.top)
    }

    @Test
    fun eachSiteShowsItsLogoAndItsRatingOutOfTen() {
        show(DetailsState.Loaded(details, ratings = Ratings(aniList = 84, mal = 86, imdb = 89)))

        compose.onNodeWithTag(ratingTag("AniList"), useUnmergedTree = true).assertContentDescriptionEquals("AniList 8.4")
        compose.onNodeWithTag(ratingTag("MyAnimeList"), useUnmergedTree = true).assertContentDescriptionEquals("MyAnimeList 8.6")
        compose.onNodeWithTag(ratingTag("IMDb"), useUnmergedTree = true).assertContentDescriptionEquals("IMDb 8.9")
        compose.onNodeWithTag(ratingTag("Rotten Tomatoes"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aFilmShowsRottenTomatoesBesideTheStar() {
        show(DetailsState.Loaded(details, ratings = Ratings(aniList = 86, mal = 88, imdb = 86, rottenTomatoes = 96)))

        val star = compose.onNodeWithTag(DETAILS_RATING_TAG).assertContentDescriptionEquals("Rated 8.9 out of 10")
        val tomatoes =
            compose
                .onNodeWithTag(ratingTag("Rotten Tomatoes"), useUnmergedTree = true)
                .assertContentDescriptionEquals("Rotten Tomatoes 9.6")
        val starBounds = star.getUnclippedBoundsInRoot()
        val tomatoBounds = tomatoes.getUnclippedBoundsInRoot()
        assertTrue(tomatoBounds.left > starBounds.right)
        assertTrue(tomatoBounds.top < compose.onNodeWithTag(DETAILS_RATINGS_TAG).getUnclippedBoundsInRoot().top)
    }

    // Each logo has its score beside it, and the sites follow one another on the same line.
    @Test
    fun theSitesSitSideBySide() {
        show(DetailsState.Loaded(details, ratings = Ratings(aniList = 84, mal = 86, imdb = 89)))

        val aniList = compose.onNodeWithTag(ratingTag("AniList"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val imdb = compose.onNodeWithTag(ratingTag("IMDb"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val mal = compose.onNodeWithTag(ratingTag("MyAnimeList"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(imdb.left > aniList.right)
        // Centred on one line, though MAL's letters are taller than AniList's mark.
        assertEquals(((aniList.top + aniList.bottom) / 2).value, ((mal.top + mal.bottom) / 2).value, 1f)
        // One line: the logo and its score beside it.
        assertTrue(imdb.bottom - imdb.top < 24.dp)
    }

    @Test
    fun theLayoutFollowsTheDesign() {
        show(DetailsState.Loaded(details))

        compose.onNodeWithTag(DETAILS_TITLE_TAG).assertLeftPositionInRootIsEqualTo(58.dp)
        compose.onNodeWithTag(DETAILS_OVERVIEW_TAB_TAG).assertTopPositionInRootIsEqualTo(352.dp)
        assertButtonsSitOverTheTabs()
    }

    /** The title block sits on the tabs, so the buttons end 40 dp above them however tall the title is. */
    private fun assertButtonsSitOverTheTabs() {
        val button = compose.onNodeWithTag(DETAILS_MAIN_BUTTON_TAG).getUnclippedBoundsInRoot()
        assertEquals(312f, button.bottom.value, 0.01f)
    }

    @Test
    fun overviewIsTheCurrentTabWithItsLabelOnTheGridMargin() {
        show(DetailsState.Loaded(details))

        compose
            .onNodeWithTag(DETAILS_OVERVIEW_TAB_TAG)
            .assertIsSelected()
            .assertHeightIsEqualTo(36.dp)
        compose.onNodeWithText("Overview", useUnmergedTree = true).assertLeftPositionInRootIsEqualTo(58.dp)
    }

    @Test
    fun detailsOpensOnPlayWithoutProgress() {
        show(DetailsState.Loaded(details))

        compose.onNodeWithTag(DETAILS_MAIN_BUTTON_TAG).assertIsFocused().assertTextEquals("Play")
        compose.onNodeWithTag(DETAILS_EPISODES_BUTTON_TAG).assertTextEquals("Episodes")
    }

    @Test
    fun detailsOpensOnResumeWithProgress() {
        show(DetailsState.Loaded(details, progress = WatchProgress(7, 12.minutes, 24.minutes, null)))

        compose.onNodeWithTag(DETAILS_MAIN_BUTTON_TAG).assertIsFocused().assertTextEquals("Resume E7 · 12 min left")
    }

    // Focus grows the main button from its middle, so the room after it grows too, and the gap stays 16 dp.
    @Test
    fun theFocusedMainButtonKeepsItsGap() {
        show(DetailsState.Loaded(details, progress = WatchProgress(7, 12.minutes, 24.minutes, null)))

        val main = compose.onNodeWithTag(DETAILS_MAIN_BUTTON_TAG).assertIsFocused().getUnclippedBoundsInRoot()
        val episodes = compose.onNodeWithTag(DETAILS_EPISODES_BUTTON_TAG).getUnclippedBoundsInRoot()
        val grown = (main.right - main.left) * (NeoDimensions().focusScaleButton - 1) / 2
        // Within half a dp: the room is rounded to whole pixels.
        assertTrue(episodes.left - (main.right + grown) >= 15.5.dp)
    }

    @Test
    fun theEpisodesButtonGoesToTheEpisodes() {
        show(DetailsState.Loaded(details, episodes = episodeList(details, deathNote, today)))

        press(Key.DirectionRight)
        compose.onNodeWithTag(DETAILS_EPISODES_BUTTON_TAG).assertIsFocused()
        press(Key.DirectionCenter)

        compose.onNodeWithTag(detailsTabTag(DetailsSection.EPISODES)).assertIsSelected()
        compose.onNodeWithTag(episodeTag(1)).assertIsFocused()
    }

    @Test
    fun downFromTheMainButtonReachesTheTabs() {
        show(DetailsState.Loaded(details))

        press(Key.DirectionDown)

        compose.onNodeWithTag(DETAILS_OVERVIEW_TAB_TAG).assertIsFocused()
    }

    @Test
    fun theMainButtonGoesToTheEpisodes() {
        show(DetailsState.Loaded(details, episodes = episodeList(details, deathNote, today)))

        press(Key.DirectionCenter)

        compose.onNodeWithTag(detailsTabTag(DetailsSection.EPISODES)).assertIsSelected()
        compose.onNodeWithTag(episodeTag(1)).assertIsFocused()
        compose.onNodeWithTag(detailsTabTag(DetailsSection.EPISODES)).assertTopPositionInRootIsEqualTo(76.dp)
    }

    @Test
    fun anEpisodeShowsItsTitleDateLengthAndSynopsis() {
        showEpisodes(DetailsState.Loaded(details, episodes = episodeList(details, deathNote, today)))

        compose.onNodeWithText("12 episodes").assertExists()
        compose.onNodeWithText("1 · Rebirth").assertExists()
        compose.onNodeWithText("23 min · Aired 4 Oct 2006").assertExists()
        compose.onNodeWithText("A notebook falls from the sky.").assertExists()
        // AniZip doesn't know the second, so it has only its number.
        compose.onNodeWithText("Episode 2").assertExists()
    }

    @Test
    fun episodesSayWhichAreWatchedAndHowMuchIsLeft() {
        val progress = WatchProgress(3, 12.minutes, 24.minutes, null)
        showEpisodes(DetailsState.Loaded(details, episodes = episodeList(details, deathNote, today), progress = progress))

        // The list opens on the episode in progress, with the one before it watched.
        compose.onNodeWithTag(episodeTag(2)).assert(hasText("✓ Watched"))
        compose.onNodeWithTag(episodeTag(3)).assert(hasText("12 min left"))
        compose.onNodeWithTag(episodeTag(4)).assert(!hasText("✓ Watched") and !hasText("12 min left"))
    }

    @Test
    fun theMainButtonLandsOnTheEpisodeInProgress() {
        val progress = WatchProgress(9, 12.minutes, 24.minutes, null)
        show(DetailsState.Loaded(details, episodes = episodeList(details, deathNote, today), progress = progress))

        press(Key.DirectionCenter)

        compose.onNodeWithTag(episodeTag(9)).assertIsFocused()
    }

    @Test
    fun downIntoTheEpisodesLandsOnTheNextToWatchInItsRange() {
        val twoCours = details.copy(card = details.card.copy(episodes = 22))
        val progress = WatchProgress(15, 12.minutes, 24.minutes, null)
        showEpisodes(DetailsState.Loaded(twoCours, episodes = episodeList(twoCours, null, today), progress = progress))

        compose.onNodeWithText("1–12").assertExists()
        compose.onNodeWithText("13–22").assertExists()
        // Down to the ranges lands on the one with episode 15, then down again on the episode.
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag(episodeTag(15)).assertIsFocused()
    }

    @Test
    fun theHeaderSaysHowManyHaveAiredWhileSomeAreStillToCome() {
        val airing = AniZipEpisodes(listOf(AniZipEpisode(4, null, null, null, AirDate(2026, 11, 2), 24)), emptyList())
        showEpisodes(DetailsState.Loaded(details, episodes = episodeList(details, airing, today)))

        compose.onNodeWithText("12 episodes · 11 aired").assertExists()
    }

    @Test
    fun withoutEpisodeDataTheRowsAreNumberedAndTheHeaderSaysWhatIsMissing() {
        showEpisodes(DetailsState.Loaded(details, episodes = episodeList(details, null, today)))

        compose.onNodeWithText("12 episodes · Titles and air dates unavailable").assertExists()
        compose.onNodeWithText("Episode 1").assertExists()
    }

    @Test
    fun aLongSeriesJumpsInRangesOfFifty() {
        val long = details.copy(card = details.card.copy(episodes = null), nextEpisode = NextEpisode(202, 0))
        showEpisodes(DetailsState.Loaded(long, episodes = episodeList(long, null, today)))

        compose.onNodeWithText("201 episodes so far · Titles and air dates unavailable").assertExists()
        compose.onNodeWithText("151–201").assertExists()
        // Down from the tabs lands on the first range; Right moves to the next and shows it.
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        compose.onNodeWithText("Episode 51").assertExists()
        compose.onNodeWithText("Episode 1").assertDoesNotExist()
    }

    // As with posters, the ranges run from the screen's edge and stop a margin short of the other, so focus can grow.
    @Test
    fun theRangesHaveRoomToGrowAtBothEnds() {
        val long = details.copy(card = details.card.copy(episodes = 1180))
        showEpisodes(DetailsState.Loaded(long, episodes = episodeList(long, null, today)))

        compose.onNodeWithTag(EPISODE_RANGES_TAG).assertLeftPositionInRootIsEqualTo(0.dp).assertWidthIsEqualTo(960.dp)
        // The chip, which holds its label, starts on the grid margin.
        compose.onNodeWithText("1–50").assertLeftPositionInRootIsEqualTo(58.dp)
    }

    // Moving along the ranges keeps the focused one inside the margins, and the last ends on the right margin.
    @Test
    fun theFocusedRangeStaysInsideTheMargins() {
        val long = details.copy(card = details.card.copy(episodes = 1180))
        showEpisodes(DetailsState.Loaded(long, episodes = episodeList(long, null, today)))
        press(Key.DirectionDown)

        repeat(10) { press(Key.DirectionRight) }
        compose.waitForIdle()
        val eleventh = compose.onNodeWithText("501–550").getUnclippedBoundsInRoot()
        assertTrue("the 11th range ends at ${eleventh.right}", eleventh.right <= 902.dp)

        repeat(13) { press(Key.DirectionRight) }
        compose.waitForIdle()
        compose.onNode(hasText("1151–1180") and isFocused()).assertExists()
        val last = compose.onNodeWithText("1151–1180").getUnclippedBoundsInRoot()
        assertEquals(902f, last.right.value, 0.5f)

        repeat(23) { press(Key.DirectionLeft) }
        compose.waitForIdle()
        compose.onNodeWithText("1–50").assertLeftPositionInRootIsEqualTo(58.dp)
    }

    // A range partly past a margin fades out across it, so none is cut off at the screen's edge.
    @Test
    fun rangesFadeOutBeforeTheScreensEdge() {
        val long = details.copy(card = details.card.copy(episodes = 1180))
        showEpisodes(DetailsState.Loaded(long, episodes = episodeList(long, null, today)))
        press(Key.DirectionDown)
        // The fade is linear, so a bright glyph 3 dp in still shows faintly.
        assertNothingDrawn(from = 958.dp, to = 959.dp)

        repeat(23) { press(Key.DirectionRight) }
        assertNothingDrawn(from = 0.dp, to = 2.dp)
    }

    // The fade mustn't cut off the focused range's growth and glow above and below the row.
    @Test
    fun theFocusedRangeGlowsAboveAndBelowTheRow() {
        val long = details.copy(card = details.card.copy(episodes = 1180))
        showEpisodes(DetailsState.Loaded(long, episodes = episodeList(long, null, today)))
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnUiThread {
            compose.activity.window.decorView
                .invalidate()
        }
        compose.waitForIdle()
        val screen = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.density.density
        val chip = compose.onNodeWithText("101–150").getUnclippedBoundsInRoot()
        val x = (((chip.left + chip.right) / 2).value * density).toInt()

        fun brightness(y: Dp) = screen[x, (y.value * density).toInt()].red
        // The page's shading only changes from left to right, and the glow is gone 30 dp out.
        val page = brightness(chip.top - 30.dp)
        assertTrue("just above the row", brightness(chip.top - 3.dp) > page + 0.03f)
        assertTrue("just below the row", brightness(chip.bottom + 3.dp) > page + 0.03f)
    }

    /** Across the ranges' height, each column from [from] to [to] is the page's colour, as just above them. */
    private fun assertNothingDrawn(
        from: Dp,
        to: Dp,
    ) {
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnUiThread {
            compose.activity.window.decorView
                .invalidate()
        }
        compose.waitForIdle()
        val screen = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.density.density
        val chip = compose.onNodeWithTag(EPISODE_RANGES_TAG).getUnclippedBoundsInRoot()
        var x = from
        while (x <= to) {
            val column = (x.value * density).toInt()
            // The page's shading only changes from left to right.
            val page = screen[column, ((chip.top - 4.dp).value * density).toInt()]
            var y = chip.top
            while (y < chip.bottom) {
                val pixel = screen[column, (y.value * density).toInt()]
                assertEquals("red at $x, $y", page.red, pixel.red, 0.02f)
                assertEquals("green at $x, $y", page.green, pixel.green, 0.02f)
                y += 2.dp
            }
            x += 1.dp
        }
    }

    // Holding Right across a long series' ranges shouldn't rebuild the list for every range it passes.
    @Test
    fun aRangeShowsOnceFocusRestsOnIt() {
        val long = details.copy(card = details.card.copy(episodes = 201))
        showEpisodes(DetailsState.Loaded(long, episodes = episodeList(long, null, today)))
        press(Key.DirectionDown)
        compose.mainClock.autoAdvance = false

        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Episode 1").assertExists()

        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("Episode 101").assertExists()
        compose.onNodeWithText("Episode 51").assertDoesNotExist()
    }

    // Only the list waits for focus to rest. The range marked as shown moves with focus, so two never look chosen.
    @Test
    fun theChosenRangeMovesWithFocusAtOnce() {
        val long = details.copy(card = details.card.copy(episodes = 201))
        showEpisodes(DetailsState.Loaded(long, episodes = episodeList(long, null, today)))
        press(Key.DirectionDown)
        compose.mainClock.autoAdvance = false

        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.waitForIdle()

        compose.onNodeWithText("101–150").assertIsSelected()
        compose.onNodeWithText("1–50").assertIsNotSelected()
    }

    // Down straight after moving goes into the range now chosen, not the one still showing.
    @Test
    fun downBeforeARangeShowsGoesIntoThatRange() {
        val long = details.copy(card = details.card.copy(episodes = 201))
        showEpisodes(DetailsState.Loaded(long, episodes = episodeList(long, null, today)))
        press(Key.DirectionDown)
        compose.mainClock.autoAdvance = false

        press(Key.DirectionRight)
        compose.waitForIdle()
        press(Key.DirectionDown)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        compose.onNodeWithTag(episodeTag(51)).assertIsFocused()
    }

    @Test
    fun aFilmListsItselfThenItsExtras() {
        val film = details.copy(card = details.card.copy(format = "MOVIE", episodes = 1))
        val aniZip =
            AniZipEpisodes(
                listOf(AniZipEpisode(1, "Complete Movie", null, null, AirDate(2001, 7, 20), 125)),
                listOf(AniZipExtra("Behind the microphone", 6)),
            )
        showEpisodes(DetailsState.Loaded(film, episodes = episodeList(film, aniZip, today)))

        compose.onNodeWithText("Film · 1 extra").assertExists()
        compose.onNodeWithText("Complete Movie").assertExists()
        compose.onNodeWithText("2 h 5 min · Released 20 Jul 2001").assertExists()
        compose.onNodeWithText("EXTRAS").assertExists()
        compose.onNodeWithText("Behind the microphone").assertExists()
    }

    @Test
    fun theSectionShownSurvivesRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { NeoTheme { DetailsScreen(DetailsState.Loaded(details, episodes = episodeList(details, null, today))) } }
        press(Key.DirectionDown)
        press(Key.DirectionRight)

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag(detailsTabTag(DetailsSection.EPISODES)).assertIsSelected()
    }

    /** Shows [state], runs [before], then recreates Details as after the app was killed: loading first, then [reloaded]. */
    private fun recreate(
        state: DetailsState,
        reloaded: DetailsState = state,
        before: () -> Unit,
    ) {
        val restoration = StateRestorationTester(compose)
        var compositions = 0
        restoration.setContent {
            // remember starts over in the recreated composition, so only that one loads again.
            val first = remember { compositions++ == 0 }
            var shown by remember { mutableStateOf(if (first) state else DetailsState.Loading) }
            if (!first) LaunchedEffect(Unit) { shown = reloaded }
            NeoTheme { DetailsScreen(shown) }
        }
        before()
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
    }

    @Test
    fun focusStaysOnTheEpisodeAfterRecreation() {
        recreate(DetailsState.Loaded(details, episodes = episodeList(details, null, today))) {
            press(Key.DirectionCenter)
            press(Key.DirectionDown)
            press(Key.DirectionDown)
            compose.onNodeWithTag(episodeTag(3)).assertIsFocused()
        }

        compose.onNodeWithTag(episodeTag(3)).assertIsFocused()
    }

    @Test
    fun focusStaysOnASectionTabAfterRecreation() {
        recreate(DetailsState.Loaded(franchise)) {
            press(Key.DirectionDown)
            press(Key.DirectionRight)
            press(Key.DirectionRight)
        }

        compose.onNodeWithTag(detailsTabTag(DetailsSection.RELATED)).assertIsFocused()
    }

    @Test
    fun focusStaysOnARelatedPosterAfterRecreation() {
        recreate(DetailsState.Loaded(franchise)) {
            press(Key.DirectionDown)
            press(Key.DirectionRight)
            press(Key.DirectionRight)
            press(Key.DirectionDown)
            press(Key.DirectionRight)
        }

        compose.onNodeWithTag(relatedPosterTag(9)).assertIsFocused()
    }

    // The show reloaded without its related shows, so the poster that had focus isn't there to take it back.
    @Test
    fun focusStartsAsUsualWhenWhatHadItIsGoneAfterRecreation() {
        recreate(DetailsState.Loaded(franchise), reloaded = DetailsState.Loaded(details)) {
            press(Key.DirectionDown)
            press(Key.DirectionRight)
            press(Key.DirectionRight)
            press(Key.DirectionDown)
            press(Key.DirectionRight)
            compose.onNodeWithTag(relatedPosterTag(9)).assertIsFocused()
        }

        compose.onNodeWithTag(DETAILS_MAIN_BUTTON_TAG).assertIsFocused()
    }

    @Test
    fun relatedShowsPostersSayingHowEachIsRelated() {
        show(DetailsState.Loaded(franchise))
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)

        compose.onNodeWithTag(detailsTabTag(DetailsSection.RELATED)).assertIsSelected()
        compose.onNodeWithText("Show 8").assertExists()
        // How each is related sits above its title, and its format and year below.
        compose.onNodeWithText("PREQUEL").assertExists()
        compose.onNodeWithText("TV · 2025").assertExists()
        compose.onNodeWithText("SIDE STORY").assertExists()
        compose.onNodeWithText("Movie · 2026").assertExists()
    }

    // A row clips at its own edges, so it runs from the screen's edge and the first poster can grow on focus.
    @Test
    fun theFirstPosterHasRoomToGrowOnTheLeft() {
        show(DetailsState.Loaded(franchise))
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)

        compose.onNodeWithTag(DETAILS_POSTER_ROW_TAG).assertLeftPositionInRootIsEqualTo(0.dp)
        compose.onNodeWithTag(relatedPosterTag(8)).assertLeftPositionInRootIsEqualTo(58.dp)
    }

    @Test
    fun theFirstRecommendationHasRoomToGrowOnTheLeft() {
        show(DetailsState.Loaded(recommended))
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)

        compose.onNodeWithTag(DETAILS_POSTER_ROW_TAG).assertLeftPositionInRootIsEqualTo(0.dp)
        compose.onNodeWithTag(relatedPosterTag(10)).assertLeftPositionInRootIsEqualTo(58.dp)
    }

    // The Related tab sits over the second poster, but the row starts at its first.
    @Test
    fun downFromTheTabsLandsOnTheFirstPoster() {
        show(DetailsState.Loaded(franchise))
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)

        press(Key.DirectionDown)

        compose.onNodeWithTag(relatedPosterTag(8)).assertIsFocused()
    }

    @Test
    fun selectingARelatedPosterOpensItsDetails() {
        var opened: Int? = null
        show(DetailsState.Loaded(franchise), onOpen = { opened = it })
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)

        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)

        assertEquals(9, opened)
    }

    @Test
    fun backFromARelatedShowReturnsToItsPoster() {
        showFromHome(DetailsState.Loaded(franchise))
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.waitForIdle()

        back()

        compose.onNodeWithTag(relatedPosterTag(9)).assertIsFocused()
        compose.onNodeWithTag(detailsTabTag(DetailsSection.RELATED)).assertIsSelected().assertTopPositionInRootIsEqualTo(76.dp)
    }

    // Down in a section, Back brings the page back to the top before it leaves Details.
    @Test
    fun backOnALiftedPageGoesToTheTopFirst() {
        showFromHome(DetailsState.Loaded(details))
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        back()
        compose.onNodeWithTag(DETAILS_MAIN_BUTTON_TAG).assertIsFocused()
        compose.onNodeWithTag(DETAILS_OVERVIEW_TAB_TAG).assertTopPositionInRootIsEqualTo(352.dp)

        back()
        compose.onNodeWithTag(DETAILS_MAIN_BUTTON_TAG).assertDoesNotExist()
    }

    // Back from a related show finds the first one on its poster, then takes it to the top, then leaves.
    @Test
    fun backAfterARelatedShowStepsUpThenOut() {
        showFromHome(DetailsState.Loaded(franchise))
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.waitForIdle()

        back()
        back()
        compose.onNodeWithTag(DETAILS_MAIN_BUTTON_TAG).assertIsFocused()
        compose.onNodeWithTag(detailsTabTag(DetailsSection.RELATED)).assertIsSelected().assertTopPositionInRootIsEqualTo(352.dp)
    }

    /** Details with its Watch order tab chosen, and focus on the tab. */
    private fun showWatchOrder(
        state: DetailsState,
        onOpen: (Int) -> Unit = {},
    ) {
        show(state, onOpen = onOpen)
        press(Key.DirectionDown)
        repeat(3) { press(Key.DirectionRight) }
    }

    @Test
    fun watchOrderNumbersTheSeriesInReleaseOrderAndSaysWhereYouAre() {
        showWatchOrder(DetailsState.Loaded(franchise, progress = WatchProgress(3, 10.minutes, 24.minutes, null)))

        compose.onNodeWithTag(detailsTabTag(DetailsSection.WATCH_ORDER)).assertIsSelected()
        compose.onNodeWithText("Release order from AniList relations.").assertExists()
        // The prequel came out in 2025, before this show and its film in 2026.
        val left = { id: Int -> compose.onNodeWithTag(watchOrderTag(id)).getUnclippedBoundsInRoot().left }
        assertTrue(left(8) < left(7))
        assertTrue(left(7) < left(9))
        compose.onNodeWithText("YOU ARE HERE").assertExists()
        compose.onNodeWithText("E3 · TV · 12 eps").assertExists()
        compose.onNodeWithText("Movie · 12 eps").assertExists()
        // The others say how they're related to this show.
        compose.onNodeWithText("PREQUEL").assertExists()
        compose.onNodeWithText("SIDE STORY").assertExists()
    }

    @Test
    fun downIntoTheWatchOrderLandsOnTheStepAfterThisShow() {
        var opened: Int? = null
        showWatchOrder(DetailsState.Loaded(franchise), onOpen = { opened = it })

        press(Key.DirectionDown)
        compose.onNodeWithTag(watchOrderTag(9)).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(9, opened)
    }

    @Test
    fun theLineFillsUpToTheStepFocusIsOn() {
        showWatchOrder(DetailsState.Loaded(franchise))
        val pink = NeoColors().pink

        // A dot's colour just inside its ring, clear of its number.
        fun dot(id: Int) =
            compose
                .onNodeWithTag(watchOrderDotTag(id))
                .captureToImage()
                .toPixelMap()
                .let { it[it.width / 5, it.height / 2] }

        fun filled(id: Int) = (dot(id).red - pink.red).let { it * it } < 0.01f && (dot(id).green - pink.green).let { it * it } < 0.01f
        // From the tabs, the line runs up to this show.
        assertTrue(filled(8))
        assertTrue(filled(7))
        assertTrue(!filled(9))
        // On the film after it, it runs on to the film.
        press(Key.DirectionDown)
        compose.waitForIdle()
        assertTrue(filled(9))
    }

    @Test
    fun selectingThisShowInTheWatchOrderStaysHere() {
        var opened: Int? = null
        showWatchOrder(DetailsState.Loaded(franchise), onOpen = { opened = it })

        press(Key.DirectionDown)
        press(Key.DirectionLeft)
        compose.onNodeWithTag(watchOrderTag(7)).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(null, opened)
    }

    @Test
    fun recommendationsShowPostersThenSeeMore() {
        var seeMore = 0
        show(DetailsState.Loaded(recommended), onRecommendations = { seeMore++ })
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)

        compose.onNodeWithTag(detailsTabTag(DetailsSection.RECOMMENDATIONS)).assertIsSelected()
        compose.onNodeWithText("Show 10").assertExists()
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithTag(DETAILS_SEE_MORE_TAG).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, seeMore)
    }

    @Test
    fun withNoMoreRecommendationsThereIsNoSeeMore() {
        show(DetailsState.Loaded(recommended.copy(moreRecommendations = false)))
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)

        compose.onNodeWithText("Show 10").assertExists()
        compose.onNodeWithTag(DETAILS_SEE_MORE_TAG).assertDoesNotExist()
    }

    @Test
    fun backFromAllTheRecommendationsReturnsToSeeMore() {
        showFromHome(DetailsState.Loaded(recommended))
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.waitForIdle()
        compose.onNodeWithText("All the recommendations").assertExists()

        back()

        compose.onNodeWithTag(DETAILS_SEE_MORE_TAG).assertIsFocused()
    }

    /** Details with its Episodes tab chosen, and focus on the tab. */
    private fun showEpisodes(state: DetailsState) {
        show(state)
        press(Key.DirectionDown)
        press(Key.DirectionRight)
    }

    @Test
    fun theLiftedHeaderSaysWhatTheShowIs() {
        show(
            DetailsState.Loaded(
                details,
                logo = Logo.None,
                ratings = Ratings(aniList = 84),
                progress = WatchProgress(7, 12.minutes, 24.minutes, null),
            ),
        )
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        compose.onNodeWithTag(DETAILS_COMPACT_TITLE_TAG).assertTextEquals("Show 7")
        compose.onNodeWithText("★ 8.4/10 · TV · 2026 · 12 eps").assertExists()
        // The title is already in the row, so there's no line under it.
        compose.onNodeWithTag(DETAILS_HEADER_TITLE_TAG).assertDoesNotExist()
        // Nothing could move focus up to a button there, so it has none.
        compose.onNode(hasText("Resume", substring = true) and hasAnyAncestor(hasTestTag(DETAILS_HEADER_TAG))).assertDoesNotExist()
    }

    // A long title takes the room the header has, rather than half of it.
    @Test
    fun aLongTitleUsesTheHeadersRoom() {
        val name = "Frieren: Beyond Journey's End, the Complete Second Season and Its Specials"
        val long = details.copy(card = card(7).copy(title = card(7).title.copy(english = name)))
        show(DetailsState.Loaded(long, logo = Logo.None))
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        // Everything but the meta: 854 dp across, less about 200 for it.
        val title = compose.onNodeWithTag(DETAILS_COMPACT_TITLE_TAG).getUnclippedBoundsInRoot()
        assertTrue(title.right - title.left > 450.dp)
    }

    @Test
    fun theLiftedHeaderShowsTheLogo() {
        imagesAre(800, 310)
        show(DetailsState.Loaded(details, logo = Logo.Image(LOGO)))
        compose.waitUntil { compose.onAllNodesWithTag(DETAILS_TITLE_TAG).fetchSemanticsNodes().isEmpty() }
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        compose
            .onNodeWithTag(DETAILS_HEADER_LOGO_TAG)
            .assertContentDescriptionEquals("Show 7")
            .assertHeightIsEqualTo(40.dp)
        compose.onNode(hasText("Play") and hasAnyAncestor(hasTestTag(DETAILS_HEADER_TAG))).assertDoesNotExist()
        // A logo can be hard to read small, so the title follows it in words, under the header's row.
        compose.onNodeWithTag(DETAILS_HEADER_TITLE_TAG).assertTextEquals("Show 7").assertTopPositionInRootIsEqualTo(74.dp)
        assertTabsSitUnderTheHeaderTitle()
    }

    /** The lifted tabs make room for the title under the logo, 12 dp below it. */
    private fun assertTabsSitUnderTheHeaderTitle() {
        val title = compose.onNodeWithTag(DETAILS_HEADER_TITLE_TAG).getUnclippedBoundsInRoot()
        compose.onNodeWithTag(DETAILS_OVERVIEW_TAB_TAG).assertTopPositionInRootIsEqualTo(title.bottom + 12.dp)
    }

    // A long title wraps inside the title block's width, rather than running across the screen, and the tabs make room.
    @Test
    fun aLongTitleUnderTheLogoWraps() {
        imagesAre(800, 310)
        val name = "Frieren: Beyond Journey's End, the Complete Second Season and Its Specials"
        val long = details.copy(card = card(7).copy(title = card(7).title.copy(english = name)))
        show(DetailsState.Loaded(long, logo = Logo.Image(LOGO)))
        compose.waitUntil { compose.onAllNodesWithTag(DETAILS_TITLE_TAG).fetchSemanticsNodes().isEmpty() }
        press(Key.DirectionDown)
        press(Key.DirectionDown)

        // Two lines, as tall as the label's two lines: 19.5 dp for the first, 20 for the next.
        val title = compose.onNodeWithTag(DETAILS_HEADER_TITLE_TAG).assertHeightIsEqualTo(39.5.dp).getUnclippedBoundsInRoot()
        assertTrue(title.right - title.left <= 440.dp)
        assertTabsSitUnderTheHeaderTitle()
    }

    @Test
    fun theTitleIsTheLogoWhenThereIsOne() {
        imagesAre(800, 310)
        show(DetailsState.Loaded(details, logo = Logo.Image(LOGO)))

        compose.waitUntil { compose.onAllNodesWithTag(DETAILS_TITLE_TAG).fetchSemanticsNodes().isEmpty() }
        compose
            .onNodeWithTag(DETAILS_LOGO_TAG)
            .assertContentDescriptionEquals("Show 7")
            .assertHeightIsEqualTo(80.dp)
        assertButtonsSitOverTheTabs()
    }

    // Spirited Away's logo is almost ten times as wide as it is tall, so it's drawn 360 × 37.
    @Test
    fun aWideLogosSpaceIsOnlyAsTallAsItIsDrawn() {
        imagesAre(3600, 370)
        show(DetailsState.Loaded(details, logo = Logo.Image(LOGO)))

        compose.waitUntil { compose.onAllNodesWithTag(DETAILS_TITLE_TAG).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag(DETAILS_LOGO_TAG).assertWidthIsEqualTo(360.dp).assertHeightIsEqualTo(37.dp)
        assertTitleGaps(DETAILS_LOGO_TAG)
    }

    // Tensei Kizoku's logo is 0.8:1, so it's drawn 64 × 80.
    @Test
    fun aTallLogoTakesTheFullHeight() {
        imagesAre(600, 750)
        show(DetailsState.Loaded(details, logo = Logo.Image(LOGO)))

        compose.waitUntil { compose.onAllNodesWithTag(DETAILS_TITLE_TAG).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag(DETAILS_LOGO_TAG).assertWidthIsEqualTo(64.dp).assertHeightIsEqualTo(80.dp)
        assertTitleGaps(DETAILS_LOGO_TAG)
    }

    @Test
    fun aTitleInWordsHasTheSameGaps() {
        show(DetailsState.Loaded(details, logo = Logo.None))

        assertTitleGaps(DETAILS_TITLE_TAG)
    }

    /** As on the spotlight, the title comes first and its genres and episodes sit 10 dp under it. */
    private fun assertTitleGaps(titleTag: String) {
        val title = compose.onNodeWithTag(titleTag).getUnclippedBoundsInRoot()
        val meta = compose.onNodeWithTag(DETAILS_META_TAG).getUnclippedBoundsInRoot()
        assertEquals(10f, (meta.top - title.bottom).value, 0.01f)
    }

    // :shared picks logos from the sizes the artwork sites report, so the TV app shows any shape it's given.
    @Test
    fun aSquareLogoIsShown() {
        imagesAre(680, 680)
        show(DetailsState.Loaded(details, logo = Logo.Image(LOGO)))

        compose.waitUntil { compose.onAllNodesWithTag(DETAILS_TITLE_TAG).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag(DETAILS_LOGO_TAG).assertExists()
    }

    @Test
    fun aSolidLogoIsShown() {
        imagesAre(800, 310, color = Color.DKGRAY)
        show(DetailsState.Loaded(details, logo = Logo.Image(LOGO)))

        compose.waitUntil { compose.onAllNodesWithTag(DETAILS_TITLE_TAG).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag(DETAILS_LOGO_TAG).assertExists()
    }

    // A first visit downloads the logo once Details has its art. The title waits unseen for it a moment.
    @Test
    fun aLogoThatLoadsWithinTheWaitIsShown() {
        val ready = CompletableDeferred<Unit>()
        imagesAre(800, 310, ready = ready)
        show(DetailsState.Loaded(details, logo = Logo.Image(LOGO)))

        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag(DETAILS_HERO_TAG).assert(hidden)
        ready.complete(Unit)
        compose.waitForIdle()

        compose.onNodeWithTag(DETAILS_LOGO_TAG).assertExists()
        compose.onNodeWithTag(DETAILS_HERO_TAG).assert(!hidden)
    }

    // If it isn't ready in time, the title stays in words for the visit rather than swapping in front of the viewer.
    @Test
    fun aLogoThatIsNotReadyInTimeLeavesTheTitleInWordsForTheVisit() {
        val ready = CompletableDeferred<Unit>()
        imagesAre(800, 310, ready = ready)
        show(DetailsState.Loaded(details, logo = Logo.Image(LOGO)))

        compose.mainClock.advanceTimeBy(1_600)
        compose.onNodeWithTag(DETAILS_TITLE_TAG).assertTextEquals("Show 7")
        compose.onNodeWithTag(DETAILS_HERO_TAG).assert(!hidden)
        ready.complete(Unit)
        compose.waitForIdle()

        compose.onNodeWithTag(DETAILS_LOGO_TAG).assertDoesNotExist()
        compose.onNodeWithTag(DETAILS_TITLE_TAG).assertTextEquals("Show 7")
    }

    @Test
    fun theTitleIsInWordsWhileTheLogoLoads() {
        imagesNeverArrive()
        show(DetailsState.Loaded(details, logo = Logo.Image(LOGO)))

        compose.onNodeWithTag(DETAILS_TITLE_TAG).assertTextEquals("Show 7").assertIsDisplayed()
    }

    @Test
    fun theTitleIsInWordsWhileTheArtIsFound() {
        show(DetailsState.Loaded(details, logo = Logo.Loading))

        compose.onNodeWithTag(DETAILS_TITLE_TAG).assertTextEquals("Show 7")
        compose.onNodeWithTag(DETAILS_LOGO_TAG).assertDoesNotExist()
    }

    @Test
    fun withoutALogoTheTitleIsInWords() {
        show(DetailsState.Loaded(details, logo = Logo.None))

        compose.onNodeWithTag(DETAILS_TITLE_TAG).assertTextEquals("Show 7")
        compose.onNodeWithTag(DETAILS_LOGO_TAG).assertDoesNotExist()
    }

    private fun imagesAre(
        width: Int,
        height: Int,
        color: Int = Color.TRANSPARENT,
        ready: Deferred<Unit>? = null,
    ) = fakeImages(compose.activity, width, height, color, ready)

    private fun imagesNeverArrive() = fakeImagesNeverArrive(compose.activity)

    @After
    fun forgetImages() = forgetFakeImages()

    @Test
    fun loadingShowsPlaceholdersInsteadOfText() {
        show(DetailsState.Loading)

        compose.onNodeWithTag(DETAILS_LOADING_TAG).assertExists()
        compose.onNodeWithText("Show 7").assertDoesNotExist()
    }

    @Test
    fun aFailedLoadSaysSo() {
        show(DetailsState.Failed)

        compose.onNodeWithText("Couldn't load this anime.").assertExists()
    }

    private companion object {
        const val LOGO = "https://logos.test/logo.png"

        // Built from details, which comes later in this object.
        val franchise
            get() =
                details.copy(
                    related =
                        listOf(
                            RelatedAnime(card(8).copy(seasonYear = 2025), Relation.PREQUEL),
                            RelatedAnime(card(9).copy(format = "MOVIE"), Relation.SIDE_STORY),
                        ),
                )

        // Every tab, so the Recommendations tab sits up and to the right of the synopsis, as on the TV.
        val everything
            get() = franchise.copy(recommendations = listOf(card(10), card(11)), moreRecommendations = true)

        val recommended
            get() = details.copy(recommendations = listOf(card(10), card(11)), moreRecommendations = true)

        val deathNote =
            AniZipEpisodes(
                listOf(AniZipEpisode(1, "Rebirth", "A notebook falls from the sky.", null, AirDate(2006, 10, 4), 23)),
                emptyList(),
            )

        val details =
            AnimeDetails(
                card = card(7).copy(averageScore = 85, season = AnimeSeason.FALL),
                description =
                    "Maomao returns to the <i>pleasure district</i>.\n<br><br>\nOne day, she senses an anomaly." +
                        "<br><br><i>Note: Streamed early.</i><br><br>(Source: Crunchyroll)",
                status = "RELEASING",
                episodeMinutes = 24,
                genres = listOf("Drama", "Mystery"),
                studios = listOf("OLM"),
                nextEpisode = null,
            )
    }
}
