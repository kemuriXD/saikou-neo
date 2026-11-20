package ani.saikou.neo.shared.account

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ListButtonViewModelTest {
    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetTestMain() = Dispatchers.resetMain()

    private class FakeEntries(
        var read: OnYourList? = OnYourList(7, ShowEntry(12, ListEntry(ListStatus.WATCHING, 3, true))),
    ) : YourListEntries {
        var answer = CompletableDeferred<Boolean>(true)
        override val changes = MutableSharedFlow<ListChange>(extraBufferCapacity = 4)
        val sent = mutableListOf<Triple<String, ListStatus?, Int>>()

        override suspend fun entry(animeId: Int) = read

        override suspend fun changeStatus(
            animeId: Int,
            to: ListStatus,
            forUser: Int,
        ): Boolean {
            sent += Triple("status", to, forUser)
            return answer.await()
        }

        override suspend fun remove(
            animeId: Int,
            forUser: Int,
        ): Boolean {
            sent += Triple("remove", null, forUser)
            return answer.await()
        }
    }

    @Test
    fun theButtonShowsTheStatusOnTheList() =
        runTest {
            val model = ListButtonViewModel(101, FakeEntries())

            assertEquals(ListButtonState.Shown(ListStatus.WATCHING, episodes = 12), model.state.value)
        }

    @Test
    fun aShowNotOnTheListOffersToAddIt() =
        runTest {
            val model = ListButtonViewModel(101, FakeEntries(OnYourList(7, ShowEntry(null, null))))

            assertEquals(ListButtonState.Shown(null, episodes = null), model.state.value)
        }

    // Signed out, or AniList couldn't say.
    @Test
    fun withNoEntryThereIsNoButton() =
        runTest {
            assertEquals(ListButtonState.Hidden, ListButtonViewModel(101, FakeEntries(read = null)).state.value)
        }

    @Test
    fun aPickedStatusShowsStraightAwayAndIsSentForTheUserWhoseListWasRead() =
        runTest {
            val entries = FakeEntries().apply { answer = CompletableDeferred<Boolean>() }
            val model = ListButtonViewModel(101, entries)

            model.pick(ListStatus.COMPLETED)

            assertEquals(ListButtonState.Shown(ListStatus.COMPLETED, 12), model.state.value)
            assertEquals(listOf<Triple<String, ListStatus?, Int>>(Triple("status", ListStatus.COMPLETED, 7)), entries.sent)
        }

    @Test
    fun aStatusAniListDoesntTakeGoesBack() =
        runTest {
            val entries = FakeEntries().apply { answer = CompletableDeferred<Boolean>(false) }
            val model = ListButtonViewModel(101, entries)

            model.pick(ListStatus.PAUSED)

            assertEquals(ListButtonState.Shown(ListStatus.WATCHING, 12), model.state.value)
        }

    @Test
    fun removingOffersToAddItAgain() =
        runTest {
            val entries = FakeEntries()
            val model = ListButtonViewModel(101, entries)

            model.remove()

            assertEquals(ListButtonState.Shown(null, 12), model.state.value)
            assertEquals(listOf<Triple<String, ListStatus?, Int>>(Triple("remove", null, 7)), entries.sent)
        }

    @Test
    fun theListIsReadAgainOnlyAfterAChangeAniListTook() =
        runTest {
            val entries = FakeEntries()
            var changes = 0
            val model = ListButtonViewModel(101, entries) { changes++ }

            model.pick(ListStatus.PAUSED)
            entries.answer = CompletableDeferred<Boolean>(false)
            model.pick(ListStatus.DROPPED)

            assertEquals(1, changes)
        }

    @Test
    fun theSameStatusAgainSendsNothing() =
        runTest {
            val entries = FakeEntries()

            ListButtonViewModel(101, entries).pick(ListStatus.WATCHING)

            assertEquals(emptyList<Triple<String, ListStatus?, Int>>(), entries.sent)
        }

    // Home's button stays behind Details, so a change there shows when Details closes.
    @Test
    fun aChangeFromAnotherButtonForTheShowShows() =
        runTest {
            val entries = FakeEntries()
            val model = ListButtonViewModel(101, entries)

            entries.changes.emit(ListChange(202, ListStatus.DROPPED, userId = 7))
            entries.changes.emit(ListChange(101, ListStatus.PAUSED, userId = 8))
            assertEquals(ListButtonState.Shown(ListStatus.WATCHING, 12), model.state.value)

            entries.changes.emit(ListChange(101, null, userId = 7))
            assertEquals(ListButtonState.Shown(null, 12), model.state.value)
        }
}
