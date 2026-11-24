package ani.saikou.neo.phone

import ani.saikou.neo.shared.account.AccountState
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.shared.account.SignInProblem
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneScreenNameTest {
    private val signedIn = AccountState.SignedIn(AniListProfile(5412345, "Kemuri", null))

    @Test
    fun signedOutTheAccountStateNamesTheScreen() {
        assertEquals("loading", reportName(AccountState.Loading, PhoneScreen.Home))
        assertEquals("welcome", reportName(AccountState.SignedOut(), PhoneScreen.Home))
        assertEquals("sign_in_problem", reportName(AccountState.SignedOut(SignInProblem.EXPIRED), PhoneScreen.Home))
        assertEquals("checking", reportName(AccountState.Verifying, PhoneScreen.Home))
        assertEquals("signed_in", reportName(signedIn.copy(justSignedIn = true), PhoneScreen.Account))
    }

    @Test
    fun signedInTheTopScreenNamesIt() {
        assertEquals("home", reportName(signedIn, PhoneScreen.Home))
        assertEquals("account", reportName(signedIn, PhoneScreen.Account))
        assertEquals("send_scan", reportName(signedIn, PhoneScreen.SendScan))
        assertEquals(
            PhoneScreen.entries.size,
            PhoneScreen.entries
                .map { reportName(signedIn, it) }
                .toSet()
                .size,
        )
    }
}
