package ani.saikou.neo.tv.player

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ani.saikou.neo.extension.api.Quality
import ani.saikou.neo.extension.api.Stream
import ani.saikou.neo.extension.api.StreamFormat
import ani.saikou.neo.shared.account.ListEntry
import ani.saikou.neo.shared.account.ListNotice
import ani.saikou.neo.shared.account.ListStatus
import ani.saikou.neo.shared.account.NoticeChange
import ani.saikou.neo.shared.account.PendingProgress
import ani.saikou.neo.shared.account.ShownNotice
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.shared.playback.ControlsViewModel
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.playback.FoundServer
import ani.saikou.neo.shared.playback.FoundStream
import ani.saikou.neo.shared.playback.PlayRequest
import ani.saikou.neo.shared.playback.PlayServer
import ani.saikou.neo.shared.playback.PlayerPhase
import ani.saikou.neo.shared.playback.PlayerState
import ani.saikou.neo.shared.playback.ServerEdition
import ani.saikou.neo.shared.playback.UpNext
import ani.saikou.neo.shared.playback.UpNextEpisode
import ani.saikou.neo.shared.sources.InstalledSource
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NOTICE_TAG
import ani.saikou.neo.tv.ui.NOTICE_UNDO_TAG
import ani.saikou.neo.tv.ui.NoticeActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val demo = InstalledSource("com.example.demo", "Demo source", "1.0.0")
private val one = PlayServer(demo, FoundServer("1", "Server 1", ServerEdition.SUB, 24), 2)
private val two = PlayServer(demo, FoundServer("2", "Server 2", ServerEdition.DUB, 24), 2)

private val show =
    AnimeDetails(
        card = AnimeCard(1535, AnimeTitle("Death Note", null, null), null, "#e4a15d", null, "TV", 37, null, null, null),
        description = null,
        status = null,
        episodeMinutes = 23,
        genres = emptyList(),
        studios = emptyList(),
        nextEpisode = null,
    )

private val request = PlayRequest(show, 7, "Rebirth", listOf(one, two), picked = 0)
private val answered = FoundStream(Stream(listOf(Quality(1080, "https://example.com/1080.m3u8", StreamFormat.Hls))), 1080)

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = mutableStateOf(PlayerState<Stream>(one, PlayerPhase.Starting(null), willTryNext = true))
    private val pressed = mutableListOf<String>()
    private val playback = FakePlayback()
    private val controls = ControlsViewModel()

    private fun show(request: PlayRequest = ani.saikou.neo.tv.player.request) {
        compose.setContent {
            NeoTheme {
                PlayerScreen(
                    state.value,
                    request,
                    PlayerActions(
                        onResume = { pressed += "resume" },
                        onStartOver = { pressed += "start over" },
                        onTryNext = { pressed += "next" },
                        onTryAgain = { pressed += "again" },
                        onChooseAnother = { pressed += "choose" },
                    ),
                    playback,
                    controls,
                    video = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }

    // ADR 0053: an episode waiting on an answer asks before the player goes, with Update AniList focused first.
    @Test
    fun backWithAnEpisodeHeldAsksWhetherToUpdateAniList() {
        val answers = mutableListOf<Boolean?>()
        state.value = state.value.copy(phase = PlayerPhase.Playing(answered), holdsForAniList = true)
        compose.setContent {
            NeoTheme {
                PlayerScreen(
                    state.value,
                    request,
                    PlayerActions(onLeave = { pressed += "leave" }, onAnswer = { answers += it }),
                    playback,
                    controls,
                    video = {},
                )
            }
        }

        back()
        assertEquals(listOf("leave"), pressed)

        state.value = state.value.copy(askAniList = 7)
        compose.waitForIdle()
        compose.onNodeWithTag(ASK_ANILIST_TAG).assertIsDisplayed()
        compose.onNodeWithText("DEATH NOTE").assertExists()
        compose
            .onNodeWithText(
                "You finished episode 7. Should finishing episodes here move your progress for this show? This TV remembers your answer.",
            ).assertExists()
        compose.onNodeWithTag(ASK_ANILIST_UPDATE_TAG).assertIsFocused()
        assertEquals(false, playback.playing)

        press(Key.DirectionDown)
        compose.onNodeWithTag(ASK_ANILIST_NOT_TAG).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag(ASK_ANILIST_NOT_TAG).assertIsFocused()
        press(Key.DirectionCenter)
        back()

        assertEquals(listOf(false, null), answers)
    }

    // #481: Back leaves the same way with nothing held, so the app can go back to the servers of the episode playing.
    @Test
    fun backLeavesThroughTheWayOut() {
        state.value = state.value.copy(phase = PlayerPhase.Playing(answered))
        compose.setContent {
            NeoTheme {
                PlayerScreen(state.value, request, PlayerActions(onLeave = { pressed += "leave" }), playback, controls, video = {})
            }
        }
        compose.waitForIdle()

        back()

        assertEquals(listOf("leave"), pressed)
    }

    private val next = UpNextEpisode(8, "The Cleanup Crew", "A delivery job turns out to be a trap.", null)

    private val notice = mutableStateOf<ShownNotice?>(null)

    private fun showUpNext(upNext: UpNext?) {
        state.value = state.value.copy(phase = PlayerPhase.Playing(answered), next = next, upNext = upNext)
        compose.setContent {
            NeoTheme {
                PlayerScreen(
                    state.value,
                    request,
                    PlayerActions(
                        onNext = { pressed += "play" },
                        onUpNext = { pressed += "up next" },
                        onWatchCredits = { pressed += "credits" },
                        onLeave = { pressed += "leave" },
                    ),
                    playback,
                    controls,
                    video = {},
                    notice = notice.value,
                    noticeActions = NoticeActions(onUndo = { pressed += "undo" }, onUndoFocused = { if (it) pressed += "undo focused" }),
                )
            }
        }
        compose.waitForIdle()
    }

    private val watched =
        ListNotice(
            1535,
            1,
            show.card.title,
            NoticeChange.Updated(ListStatus.WATCHING, 7, byEpisode = true),
            ListEntry(ListStatus.WATCHING, 6, hasStartDate = true),
            fromWatching = true,
        )

    // ADR 0056: the AniList notice shows bottom left of Up next. Focus stays on Play, and Down reaches Undo.
    @Test
    fun upNextShowsTheAniListNoticeAndDownReachesUndo() {
        notice.value = ShownNotice(watched)
        showUpNext(UpNext(secondsLeft = 8))

        compose.onNodeWithTag(NOTICE_TAG).assertIsDisplayed()
        compose.onNodeWithText("Death Note · episode 7 on AniList").assertExists()
        compose.onNodeWithText("It was episode 6").assertExists()
        compose.onNodeWithTag(UP_NEXT_PLAY_TAG).assertIsFocused()

        press(Key.DirectionDown)
        compose.onNodeWithTag(NOTICE_UNDO_TAG).assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionUp)
        compose.onNodeWithTag(UP_NEXT_PLAY_TAG).assertIsFocused()

        assertEquals(listOf("undo focused", "undo"), pressed)
    }

    @Test
    fun anUpdateSavedForLaterSaysItWillBeSent() {
        val pending = PendingProgress(1535, 7, 1)
        notice.value = ShownNotice(watched.copy(change = NoticeChange.SavedForLater(pending, null), before = null))
        showUpNext(UpNext(secondsLeft = 8))

        compose.onNodeWithText("Death Note · episode 7 saved for later").assertExists()
        compose.onNodeWithText("Sent to AniList when this TV is back online").assertExists()
        compose.onNodeWithTag(NOTICE_UNDO_TAG).assertExists()
    }

    // Undo gone from under focus, focus goes back to Play.
    @Test
    fun anUndoThatFailsSaysSoAndFocusGoesBackToPlay() {
        notice.value = ShownNotice(watched)
        showUpNext(UpNext(secondsLeft = 8))
        press(Key.DirectionDown)

        notice.value = ShownNotice(watched, failed = true)
        compose.waitForIdle()

        compose.onNodeWithText("Couldn't undo Death Note").assertExists()
        compose.onNodeWithText("AniList changed since, or can't be reached").assertExists()
        compose.onNodeWithTag(NOTICE_UNDO_TAG).assertDoesNotExist()
        compose.onNodeWithTag(UP_NEXT_PLAY_TAG).assertIsFocused()
    }

    // #77: Up next shows the next episode beside the shrunk video, with Play focused and counting down.
    @Test
    fun upNextOffersTheNextEpisodeWithPlayFocused() {
        showUpNext(UpNext(secondsLeft = 8))

        compose.onNodeWithTag(UP_NEXT_TAG).assertIsDisplayed()
        compose.onNodeWithText("UP NEXT").assertExists()
        compose.onNodeWithText("E8 · The Cleanup Crew").assertExists()
        compose.onNodeWithText("A delivery job turns out to be a trap.").assertExists()
        compose.onNodeWithText("E7 plays on here").assertExists()
        compose.onNodeWithText("8").assertExists()
        compose.onNodeWithTag(UP_NEXT_PLAY_TAG).assertIsFocused()

        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        compose.onNodeWithTag(UP_NEXT_CREDITS_TAG).assertIsFocused()
        press(Key.DirectionCenter)
        back()

        assertEquals(listOf("play", "credits", "credits"), pressed)
    }

    @Test
    fun upNextShowsThirtySecondsBeforeTheEnd() {
        playback.positionMs = playback.durationMs - 40_000
        showUpNext(upNext = null)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(emptyList<String>(), pressed)

        playback.positionMs = playback.durationMs - 30_000
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("up next", pressed.firstOrNull())
    }

    // #458: after a run, Up next asks if you're still watching, with no countdown, and Stop leaves.
    @Test
    fun afterARunUpNextAsksIfYoureStillWatching() {
        showUpNext(UpNext(secondsLeft = null, stillWatching = 2))

        compose.onNodeWithText("STILL WATCHING?").assertExists()
        compose.onNodeWithText("2 episodes have played in a row. E8 waits until you press Play.").assertExists()
        compose.onNodeWithText("A delivery job turns out to be a trap.").assertDoesNotExist()
        compose.onNodeWithText("Watch credits").assertDoesNotExist()
        compose.onNodeWithTag(UP_NEXT_PLAY_TAG).assertIsFocused()

        press(Key.DirectionRight)
        compose.onNodeWithTag(UP_NEXT_STOP_TAG).assertIsFocused()
        press(Key.DirectionCenter)
        back()

        assertEquals(listOf("leave", "credits"), pressed)
    }

    // ADR 0053, moved by 0054: the AniList question shows over Up next, and the countdown waits.
    @Test
    fun theAniListQuestionShowsOverUpNext() {
        state.value = state.value.copy(askAniList = 7)
        showUpNext(UpNext(secondsLeft = null))

        compose.onNodeWithTag(UP_NEXT_TAG).assertExists()
        compose.onNodeWithTag(ASK_ANILIST_UPDATE_TAG).assertIsFocused()
        compose.onNodeWithText("8").assertDoesNotExist()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun startingSaysWhereItFoundTheServerAndWhoItsAsking() {
        show()

        compose.onNodeWithText("DEATH NOTE").assertExists()
        compose.onNodeWithText("E7 · Rebirth").assertExists()
        compose.onNodeWithText("Found on Demo source · 2 servers").assertExists()
        compose.onNodeWithText("Asking Server 1").assertExists()
        compose.onNodeWithText("If Server 1 fails before the video starts, the next server is tried once.").assertExists()
        compose.onNodeWithTag(PLAYER_CHOOSE_TAG).assertIsFocused()
    }

    @Test
    fun onceTheServerAnswersItBuffers() {
        state.value = state.value.copy(phase = PlayerPhase.Starting(answered))
        show()

        compose.onNodeWithText("Server 1 answered · 1080p Sub").assertExists()
        compose.onNodeWithText("Buffering").assertExists()
    }

    @Test
    fun theNoteGoesOnceTheNextServerWontBeTried() {
        state.value = state.value.copy(willTryNext = false)
        show()

        compose.onNodeWithText("If Server 1 fails before the video starts, the next server is tried once.").assertDoesNotExist()
    }

    @Test
    fun chooseAnotherServerGoesBack() {
        show()

        press(Key.DirectionCenter)

        assertEquals(listOf("choose"), pressed)
    }

    @Test
    fun playingShowsNothingOverTheVideo() {
        state.value = state.value.copy(phase = PlayerPhase.Playing(answered))
        show()

        compose.onNodeWithTag(PLAYER_STARTING_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PLAYER_STOPPED_TAG).assertDoesNotExist()
    }

    @Test
    fun aQuietStartShowsNothingOverTheVideo() {
        state.value = state.value.copy(phase = PlayerPhase.Starting(null, quiet = true))
        show()

        compose.onNodeWithTag(PLAYER_STARTING_TAG).assertDoesNotExist()
    }

    @Test
    fun stoppedSaysWhyAndOffersTheNextServerFirst() {
        state.value = state.value.copy(phase = PlayerPhase.Stopped(FailReason.SourceDown, 503, 724_000, two))
        show()

        compose.onNodeWithText("Server 1 stopped answering").assertExists()
        compose.onNodeWithText("The site isn’t working right now. E7 stopped at 12:04.").assertExists()
        compose.onNodeWithText("Demo source · Server 1 · error 503").assertExists()
        compose.onNodeWithTag(PLAYER_TRY_NEXT_TAG).assertIsFocused()
        compose.onNodeWithText("Try Server 2").assertExists()
        compose.onNodeWithText("Choose another server").assertExists()
        compose.onNodeWithText("Try Server 1 again").assertExists()
    }

    @Test
    fun stoppedWithNoConnectionSaysTheTvIsOffline() {
        state.value = state.value.copy(phase = PlayerPhase.Stopped(FailReason.Offline, null, 724_000, two))
        show()

        compose.onNodeWithText("No internet connection").assertExists()
        compose.onNodeWithText("This TV isn’t online. Check its Wi-Fi or cable. E7 stopped at 12:04.").assertExists()
        compose.onNodeWithText("Server 1 stopped answering").assertDoesNotExist()
        compose.onNodeWithText("Try Server 1 again").assertExists()
    }

    @Test
    fun eachStoppedButtonDoesItsJob() {
        state.value = state.value.copy(phase = PlayerPhase.Stopped(FailReason.SourceDown, null, null, two))
        show()

        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        compose.onNodeWithTag(PLAYER_CHOOSE_TAG).assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        compose.onNodeWithTag(PLAYER_TRY_AGAIN_TAG).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(listOf("next", "choose", "again"), pressed)
    }

    @Test
    fun withoutANextServerChooseAnotherComesFirst() {
        state.value = state.value.copy(phase = PlayerPhase.Stopped(FailReason.NoAnswer, null, null, null))
        show()

        compose.onNodeWithTag(PLAYER_TRY_NEXT_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PLAYER_CHOOSE_TAG).assertIsFocused()
        compose.onNodeWithText("Didn’t answer in 25 s.").assertExists()
        compose.onNodeWithText("Demo source · Server 1").assertExists()
    }

    @Test
    fun aServerSharingItsNameIsToldApartByEdition() {
        val sub = PlayServer(demo, FoundServer("sub", "Demo", ServerEdition.SUB, 24), 2)
        val dub = PlayServer(demo, FoundServer("dub", "Demo", ServerEdition.DUB, 24), 2)
        state.value = PlayerState(sub, PlayerPhase.Stopped(FailReason.Blocked, null, null, dub))
        show()

        compose.onNodeWithText("Try Demo, Dub").assertExists()
    }

    @Test
    fun aPartWatchedEpisodeAsksToResumeWithResumeFocused() {
        state.value = state.value.copy(phase = PlayerPhase.Resume(724_000))
        show(request.copy(progress = WatchProgress(7, 12.minutes + 4.seconds, 24.minutes, null)))

        compose.onNodeWithTag(PLAYER_RESUME_TAG).assertExists()
        compose.onNodeWithText("E7 · Rebirth").assertExists()
        compose.onNodeWithText("You stopped at 12:04").assertExists()
        compose.onNodeWithText("Start from the beginning").assertExists()
        compose.onNodeWithTag(PLAYER_RESUME_FROM_TAG).assertIsFocused()
        compose.onNodeWithText("Resume from 12:04").assertExists()
    }

    @Test
    fun eachResumeButtonDoesItsJob() {
        state.value = state.value.copy(phase = PlayerPhase.Resume(724_000))
        show()

        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        compose.onNodeWithTag(PLAYER_START_OVER_TAG).assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        compose.onNodeWithTag(PLAYER_CHOOSE_TAG).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(listOf("resume", "start over", "choose"), pressed)
    }

    @Test
    fun aResumedStartSaysWhereItStartsAndCanStillChooseAnotherServer() {
        state.value = state.value.copy(startAtMs = 724_000)
        show()

        compose.onNodeWithText("Starts at 12:04, where you stopped.").assertExists()
        compose.onNodeWithTag(PLAYER_CHOOSE_TAG).assertIsFocused()
    }

    @Test
    fun anExpiredLinkOffersTheSameServerAgainFirstAndKeepsThePlace() {
        state.value = state.value.copy(phase = PlayerPhase.Stopped(FailReason.LinkExpired, null, 724_000, two))
        show()

        compose.onNodeWithTag(PLAYER_TRY_AGAIN_TAG).assertIsFocused()
        compose.onNodeWithText("stopped at 12:04", substring = true).assertExists()
        press(Key.DirectionDown)
        compose.onNodeWithTag(PLAYER_TRY_NEXT_TAG).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag(PLAYER_CHOOSE_TAG).assertIsFocused()
    }
}
