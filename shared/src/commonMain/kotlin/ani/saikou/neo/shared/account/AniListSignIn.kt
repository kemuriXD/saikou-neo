package ani.saikou.neo.shared.account

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** An AniList access token. It never shows in text, so it can't end up in a log or a crash report. */
public class AniListToken(
    public val accessToken: String,
    public val expiresAtEpochSeconds: Long,
) {
    override fun toString(): String = "AniListToken([redacted])"
}

/** The AniList user the phone is signed in as. */
public data class AniListProfile(
    val id: Int,
    val name: String,
    /** Their profile page on anilist.co. */
    val siteUrl: String?,
)

/** A signed-in AniList user and their token. */
public class SignedInAccount(
    public val profile: AniListProfile,
    public val token: AniListToken,
) {
    override fun toString(): String = "SignedInAccount($profile, [redacted])"
}

/** A sign-in started in the browser. Only a callback carrying the same [state] is accepted. */
public data class SignInAttempt(
    val state: String,
    val startedAtEpochSeconds: Long,
)

/** What came back from the browser. */
public sealed interface CallbackResult {
    /** Not an answer to the sign-in we started, such as a stale link. Nothing changes. */
    public data object Unrelated : CallbackResult

    /** The answer to a sign-in started too long ago. */
    public data object Expired : CallbackResult

    /** The person chose not to let the app in. */
    public data object Declined : CallbackResult

    /** AniList answered with an error or a token we can't use. */
    public data object Invalid : CallbackResult

    /** A token to check with AniList before it's saved. */
    public class Token(
        public val token: AniListToken,
    ) : CallbackResult
}

/**
 * AniList's implicit grant: the browser signs in on anilist.co and comes back to the app's redirect address with the
 * token in the fragment. It needs only the client ID, so there's no secret in the app.
 */
public object AniListSignIn {
    private const val AUTHORIZE_URL = "https://anilist.co/api/v2/oauth/authorize"

    /** How long a sign-in started in the browser stays open. */
    private const val ATTEMPT_SECONDS = 600

    public fun authorizeUrl(
        clientId: String,
        state: String,
    ): String = "$AUTHORIZE_URL?client_id=${encode(clientId)}&response_type=token&state=${encode(state)}"

    /** A fresh random state for one sign-in. */
    @OptIn(ExperimentalUuidApi::class)
    public fun newState(): String = Uuid.random().toHexString() + Uuid.random().toHexString()

    /**
     * Reads the browser's answer at [uri]. Only an answer to [redirectUri] for the current [attempt] counts. A token is
     * only taken from the fragment, which browsers never send to a server.
     */
    public fun parseCallback(
        uri: String,
        redirectUri: String,
        attempt: SignInAttempt?,
        nowEpochSeconds: Long,
    ): CallbackResult {
        val beforeFragment = uri.substringBefore('#')
        val address = beforeFragment.substringBefore('?')
        if (address != redirectUri) return CallbackResult.Unrelated
        val query = parameters(beforeFragment.substringAfter('?', missingDelimiterValue = ""))
        val fragment = parameters(uri.substringAfter('#', missingDelimiterValue = ""))
        if (query == null || fragment == null) return CallbackResult.Invalid

        val error = fragment["error"] ?: query["error"]
        val state = fragment["state"] ?: query["state"]
        if (attempt == null || state != attempt.state) return CallbackResult.Unrelated
        val age = nowEpochSeconds - attempt.startedAtEpochSeconds
        if (age !in 0 until ATTEMPT_SECONDS) return CallbackResult.Expired
        if (error != null) return if (error == "access_denied") CallbackResult.Declined else CallbackResult.Invalid
        if ("access_token" in query) return CallbackResult.Invalid

        val token = fragment["access_token"]
        val lifetime = fragment["expires_in"]?.toLongOrNull()
        return when {
            token.isNullOrEmpty() || token.any { it.isWhitespace() || it.code < 32 } -> CallbackResult.Invalid
            !fragment["token_type"].equals("Bearer", ignoreCase = true) -> CallbackResult.Invalid
            lifetime == null || lifetime <= 0 -> CallbackResult.Invalid
            else -> CallbackResult.Token(AniListToken(token, nowEpochSeconds + lifetime))
        }
    }

    /** The decoded name=value pairs in [text], or null when a name repeats, so nothing can be smuggled in twice. */
    private fun parameters(text: String): Map<String, String>? {
        val pairs = text.split('&').filter { it.isNotEmpty() }.map { decode(it.substringBefore('=')) to decode(it.substringAfter('=', "")) }
        val map = pairs.toMap()
        return if (map.size == pairs.size) map else null
    }
}

private const val UNRESERVED = "-._~"

private fun encode(value: String): String =
    buildString {
        value.encodeToByteArray().forEach { byte ->
            val char = byte.toInt().toChar()
            if (byte >= 0 && (char.isLetterOrDigit() || char in UNRESERVED)) {
                append(char)
            } else {
                append('%').append((byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
            }
        }
    }

/** Percent-decodes [value]. A broken escape is kept as it is, which the checks then refuse. */
private fun decode(value: String): String {
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        val high = if (char == '%' && index + 2 <= value.lastIndex) value[index + 1].digitToIntOrNull(16) else null
        val low = if (high != null) value[index + 2].digitToIntOrNull(16) else null
        val escaped = if (high != null && low != null) high * 16 + low else null
        when {
            escaped != null -> {
                bytes += escaped.toByte()
                index += 3
            }

            char == '+' -> {
                bytes += ' '.code.toByte()
                index++
            }

            else -> {
                char.toString().encodeToByteArray().forEach { bytes += it }
                index++
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}
