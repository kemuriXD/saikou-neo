package ani.saikou.neo.shared.pairing

import ani.saikou.neo.shared.account.AccountStore
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.shared.account.AniListToken
import ani.saikou.neo.shared.account.SignInAttempt
import ani.saikou.neo.shared.account.SignedInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TvAccountViewModelTest {
    private val main = StandardTestDispatcher()

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private class OneAccount : AccountStore {
        var account: SignedInAccount? = null

        override fun readAccount() = account

        override fun saveAccount(account: SignedInAccount) {
            this.account = account
        }

        override fun clearAccount() {
            account = null
        }

        override fun readAttempt(): SignInAttempt? = null

        override fun saveAttempt(attempt: SignInAttempt) = Unit

        override fun clearAttempt() = Unit
    }

    @Test
    fun showsTheSavedAccountAndPicksUpANewOneOnRefresh() =
        runTest(main) {
            val store = OneAccount()
            val model = TvAccountViewModel(store, io = main)
            advanceUntilIdle()
            assertNull(model.profile.value)

            val hikari = AniListProfile(7, "Hikari", null)
            store.saveAccount(SignedInAccount(hikari, AniListToken("t", 9_000)))
            model.refresh()
            advanceUntilIdle()
            assertEquals(hikari, model.profile.value)
        }

    @Test
    fun signOutForgetsTheAccountOnThisTv() =
        runTest(main) {
            val store = OneAccount()
            store.saveAccount(SignedInAccount(AniListProfile(7, "Hikari", null), AniListToken("t", 9_000)))
            val model = TvAccountViewModel(store, io = main)
            advanceUntilIdle()

            model.signOut()
            advanceUntilIdle()

            assertNull(store.account)
            assertNull(model.profile.value)
        }
}
