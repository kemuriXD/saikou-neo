package ani.saikou.neo.shared.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AniListSignInTest {
    private val redirect = "ani.saikou.neo.preview://oauth/anilist"
    private val attempt = SignInAttempt(state = "abc123", startedAtEpochSeconds = 1_000)

    private fun parse(
        uri: String,
        attempt: SignInAttempt? = this.attempt,
        now: Long = 1_060,
    ) = AniListSignIn.parseCallback(uri, redirect, attempt, now)

    @Test
    fun authorizeUrlAsksForATokenWithTheClientIdAndState() {
        assertEquals(
            "https://anilist.co/api/v2/oauth/authorize?client_id=12345&response_type=token&state=abc123",
            AniListSignIn.authorizeUrl(clientId = "12345", state = "abc123"),
        )
    }

    @Test
    fun aMatchingCallbackGivesTheTokenAndWhenItEnds() {
        val result = parse("$redirect#access_token=tok.en-1&token_type=Bearer&expires_in=31536000&state=abc123")

        val token = assertIs<CallbackResult.Token>(result).token
        assertEquals("tok.en-1", token.accessToken)
        assertEquals(1_060 + 31_536_000L, token.expiresAtEpochSeconds)
    }

    @Test
    fun aCallbackForAnotherAttemptIsIgnored() {
        assertEquals(
            CallbackResult.Unrelated,
            parse("$redirect#access_token=t&token_type=Bearer&expires_in=60&state=someone-else"),
        )
        assertEquals(CallbackResult.Unrelated, parse("$redirect#access_token=t&token_type=Bearer&expires_in=60&state=abc123", attempt = null))
        assertEquals(CallbackResult.Unrelated, parse("$redirect#access_token=t&token_type=Bearer&expires_in=60"))
    }

    @Test
    fun aCallbackToAnotherAddressIsIgnored() {
        assertEquals(
            CallbackResult.Unrelated,
            parse("evil://oauth/anilist#access_token=t&token_type=Bearer&expires_in=60&state=abc123"),
        )
        assertEquals(
            CallbackResult.Unrelated,
            parse("$redirect/more#access_token=t&token_type=Bearer&expires_in=60&state=abc123"),
        )
    }

    @Test
    fun aTokenInTheQueryIsNeverAccepted() {
        assertEquals(
            CallbackResult.Invalid,
            parse("$redirect?access_token=t&token_type=Bearer&expires_in=60&state=abc123"),
        )
    }

    @Test
    fun anAttemptOlderThanTenMinutesHasExpired() {
        assertEquals(
            CallbackResult.Expired,
            parse("$redirect#access_token=t&token_type=Bearer&expires_in=60&state=abc123", now = 1_000 + 600),
        )
    }

    @Test
    fun sayingNoOnAniListIsDeclined() {
        assertEquals(CallbackResult.Declined, parse("$redirect?error=access_denied&state=abc123"))
        assertEquals(CallbackResult.Declined, parse("$redirect#error=access_denied&state=abc123"))
    }

    @Test
    fun anyOtherErrorOrABrokenTokenIsInvalid() {
        assertEquals(CallbackResult.Invalid, parse("$redirect#error=server_error&state=abc123"))
        assertEquals(CallbackResult.Invalid, parse("$redirect#token_type=Bearer&expires_in=60&state=abc123"))
        assertEquals(CallbackResult.Invalid, parse("$redirect#access_token=t&token_type=Mac&expires_in=60&state=abc123"))
        assertEquals(CallbackResult.Invalid, parse("$redirect#access_token=t&token_type=Bearer&expires_in=0&state=abc123"))
        assertEquals(CallbackResult.Invalid, parse("$redirect#access_token=a%20b&token_type=Bearer&expires_in=60&state=abc123"))
        assertEquals(
            CallbackResult.Invalid,
            parse("$redirect#access_token=a&access_token=b&token_type=Bearer&expires_in=60&state=abc123"),
        )
    }

    @Test
    fun percentEncodedValuesAreDecoded() {
        val result = parse("$redirect#access_token=a%2Bb&token_type=bearer&expires_in=60&state=abc123")

        assertEquals("a+b", assertIs<CallbackResult.Token>(result).token.accessToken)
    }

    @Test
    fun tokensNeverShowInText() {
        val token = AniListToken("secret-token", 99)
        val account = SignedInAccount(AniListProfile(1, "Kemuri", "https://anilist.co/user/Kemuri"), token)

        assertFalse("secret-token" in token.toString())
        assertFalse("secret-token" in account.toString())
        assertTrue("Kemuri" in account.profile.toString())
    }

    @Test
    fun newStatesAreLongAndDifferent() {
        val first = AniListSignIn.newState()
        val second = AniListSignIn.newState()

        assertTrue(first.length >= 32)
        assertTrue(first.all { it.isLetterOrDigit() })
        assertFalse(first == second)
    }
}
