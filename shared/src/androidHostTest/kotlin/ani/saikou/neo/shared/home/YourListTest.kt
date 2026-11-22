package ani.saikou.neo.shared.home

import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.shared.account.AniListToken
import ani.saikou.neo.shared.account.ContinueListClient
import ani.saikou.neo.shared.account.ListedProgress
import ani.saikou.neo.shared.account.SignedInAccount
import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class YourListTest {
    private fun tv(
        episode: Int,
        savedAt: Long?,
        watched: Duration = 12.minutes,
    ) = WatchProgress(episode, watched, 24.minutes, "still.jpg", savedAt)

    // AniList's progress is the last episode watched, so it's kept as that episode watched to the end.
    @Test
    fun aListShowPicksUpAfterItsProgress() {
        val merged = withYourList(emptyMap(), listOf(ListedProgress(201, progress = 3, updatedAt = 500, episodeMinutes = 45)))

        assertEquals(mapOf(201 to WatchProgress(3, 45.minutes, 45.minutes, null, 500)), merged)
        assertEquals(WatchProgress(4, Duration.ZERO, 45.minutes, null, 500), merged.getValue(201).upNext(12))
    }

    @Test
    fun aListShowNotStartedPicksUpAtEpisodeOne() {
        val merged = withYourList(emptyMap(), listOf(ListedProgress(201, progress = 0, updatedAt = 500, episodeMinutes = 24)))

        assertEquals(1, merged.getValue(201).upNext(12)?.episode)
    }

    @Test
    fun aListShowWithoutALengthTakesTwentyFourMinutes() {
        val merged = withYourList(emptyMap(), listOf(ListedProgress(201, progress = 3, updatedAt = 500, episodeMinutes = null)))

        assertEquals(24.minutes, merged.getValue(201).length)
    }

    @Test
    fun showsInOnlyOnePlaceAreAllKept() {
        val merged = withYourList(mapOf(100 to tv(2, savedAt = 300)), listOf(ListedProgress(201, 3, 500, 24)))

        assertEquals(setOf(100, 201), merged.keys)
    }

    @Test
    fun theTvsPlaceWinsWhenItChangedLast() {
        val merged =
            withYourList(
                mapOf(201 to tv(5, savedAt = 600)),
                listOf(ListedProgress(201, progress = 7, updatedAt = 500, episodeMinutes = 24)),
            )

        assertEquals(tv(5, savedAt = 600), merged.getValue(201))
    }

    // Watched elsewhere since, such as a rewatch started on another app.
    @Test
    fun aniListsPlaceWinsWhenItChangedLast() {
        val merged =
            withYourList(
                mapOf(201 to tv(5, savedAt = 400)),
                listOf(ListedProgress(201, progress = 2, updatedAt = 500, episodeMinutes = 24)),
            )

        assertEquals(WatchProgress(2, 24.minutes, 24.minutes, null, 500), merged.getValue(201))
    }

    // The last of 13 episodes was watched here, then Undo put AniList back at 5 (#496). The show isn't finished.
    @Test
    fun aniListsNewerPlaceBringsBackAShowFinishedOnThisTv() {
        val merged =
            withYourList(
                mapOf(201 to tv(13, savedAt = 400, watched = 24.minutes)),
                listOf(ListedProgress(201, progress = 5, updatedAt = 417, episodeMinutes = 24)),
            )

        assertEquals(WatchProgress(6, Duration.ZERO, 24.minutes, null, 417), merged.getValue(201).upNext(13))
    }

    // The TV sent episode 6 to AniList, which saved it a moment after the TV's last save. The TV's place has the still.
    @Test
    fun theTvsPlaceIsKeptWhenBothPickUpAtTheSameEpisode() {
        val merged =
            withYourList(
                mapOf(201 to tv(7, savedAt = 400)),
                listOf(ListedProgress(201, progress = 6, updatedAt = 500, episodeMinutes = 24)),
            )

        assertEquals(tv(7, savedAt = 500), merged.getValue(201))
    }

    @Test
    fun anEntryWithoutATimeLeavesTheTvsPlace() {
        val merged =
            withYourList(
                mapOf(201 to tv(5, savedAt = 400)),
                listOf(ListedProgress(201, progress = 9, updatedAt = null, episodeMinutes = 24)),
            )

        assertEquals(tv(5, savedAt = 400), merged.getValue(201))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AniListYourListTest {
    private val me = SignedInAccount(AniListProfile(7, "Kemuri", null), AniListToken("the-token", 2_000_000_000))
    private val shows = listOf(ListedProgress(201, 3, 500, 24))

    private class FakeClient(
        private val answer: suspend () -> AniListResult<List<ListedProgress>>,
    ) : ContinueListClient {
        val asked = mutableListOf<Pair<String, Int>>()

        override suspend fun continueList(
            token: AniListToken,
            userId: Int,
        ): AniListResult<List<ListedProgress>> {
            asked += token.accessToken to userId
            return answer()
        }
    }

    @Test
    fun readsTheSignedInUsersList() =
        runTest {
            val client = FakeClient { AniListResult.Success(shows) }

            val listed = AniListYourList({ me }, client, now = { 1_000 }).shows()

            assertEquals(shows, listed)
            assertEquals(listOf("the-token" to 7), client.asked)
        }

    @Test
    fun signedOutThereIsNoList() =
        runTest {
            val client = FakeClient { AniListResult.Success(shows) }

            assertEquals(emptyList(), AniListYourList({ null }, client, now = { 1_000 }).shows())
            assertEquals(emptyList(), client.asked)
        }

    @Test
    fun anExpiredTokenIsNotSent() =
        runTest {
            val client = FakeClient { AniListResult.Success(shows) }

            assertEquals(emptyList(), AniListYourList({ me }, client, now = { 2_000_000_000 }).shows())
            assertEquals(emptyList(), client.asked)
        }

    @Test
    fun aListThatFailsIsNoList() =
        runTest {
            val client = FakeClient { AniListResult.Failure(AniListError.Network("down")) }

            assertEquals(emptyList(), AniListYourList({ me }, client, now = { 1_000 }).shows())
        }

    // Home asks again whenever the account might have changed, which at start is twice.
    @Test
    fun theSameUsersListIsKeptForAMinute() =
        runTest {
            var now = 1_000L
            val client = FakeClient { AniListResult.Success(shows) }
            val yourList = AniListYourList({ me }, client, now = { now })

            yourList.shows()
            now += 59
            assertEquals(shows, yourList.shows())
            assertEquals(1, client.asked.size)

            now += 1
            yourList.shows()
            assertEquals(2, client.asked.size)
        }

    @Test
    fun anotherUserIsAskedForAnew() =
        runTest {
            var account = me
            val client = FakeClient { AniListResult.Success(shows) }
            val yourList = AniListYourList({ account }, client, now = { 1_000 })

            yourList.shows()
            account = SignedInAccount(AniListProfile(8, "Other", null), AniListToken("other-token", 2_000_000_000))
            yourList.shows()

            assertEquals(listOf("the-token" to 7, "other-token" to 8), client.asked)
        }

    @Test
    fun aListThatFailedIsAskedForAgain() =
        runTest {
            var answer: AniListResult<List<ListedProgress>> = AniListResult.Failure(AniListError.Network("down"))
            val client = FakeClient { answer }
            val yourList = AniListYourList({ me }, client, now = { 1_000 })

            yourList.shows()
            answer = AniListResult.Success(shows)

            assertEquals(shows, yourList.shows())
        }

    @Test
    fun noAnswerWithinTenSecondsIsNoList() =
        runTest {
            val client = FakeClient { awaitCancellation() }

            assertEquals(emptyList(), AniListYourList({ me }, client, now = { 1_000 }).shows())
            assertEquals(10_000, currentTime)
        }

    // Picking this TV's place moves AniList, so the kept list mustn't ask the same question again (ADR 0050).
    @Test
    fun aMovedShowIsKeptWhereItMoved() =
        runTest {
            val client = FakeClient { AniListResult.Success(shows) }
            val yourList = AniListYourList({ me }, client, now = { 1_000 })

            yourList.shows()
            yourList.moved(201, 1)

            assertEquals(listOf(ListedProgress(201, 1, 1_000, 24)), yourList.shows())
            assertEquals(1, client.asked.size)
        }

    // A status changed from the list button can take a show on or off the list (ADR 0051).
    @Test
    fun aForgottenListIsReadAgain() =
        runTest {
            val client = FakeClient { AniListResult.Success(shows) }
            val yourList = AniListYourList({ me }, client, now = { 1_000 })

            yourList.shows()
            yourList.forget()
            yourList.shows()

            assertEquals(2, client.asked.size)
        }

    // So Continue watching reads it again while Home is open.
    @Test
    fun forgettingTheListSaysSo() =
        runTest {
            val yourList = AniListYourList({ me }, FakeClient { AniListResult.Success(shows) }, now = { 1_000 })
            val heard = async { yourList.forgotten.first() }
            runCurrent()

            yourList.forget()
            runCurrent()

            assertTrue(heard.isCompleted)
        }
}
