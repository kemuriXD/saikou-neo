package ani.saikou.neo.shared.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class LogRedactorTest {
    private fun redact(text: String) = LogRedactor.redact(text)

    @Test
    fun urlKeepsOnlySchemeAndHost() {
        assertEquals(
            "GET https://cdn.example.com/… failed",
            redact("GET https://cdn.example.com/hls/abc/master.m3u8?token=x failed"),
        )
        assertEquals("see https://anilist.co", redact("see https://anilist.co"))
        assertEquals("http://host.test:8080/…", redact("http://user:pw@host.test:8080/a"))
    }

    @Test
    fun urlWithIpHost() {
        assertEquals("http://[ip]:8799/…", redact("http://192.168.1.20:8799/pair"))
    }

    @Test
    fun bareQueryStringRemoved() {
        assertEquals("/api/v1/watch done", redact("/api/v1/watch?id=5&sig=abc done"))
    }

    @Test
    fun authHeadersRemoved() {
        assertEquals("Authorization: [redacted]", redact("Authorization: Bearer abc.def"))
        assertEquals("cookie: [redacted]", redact("cookie: cf_clearance=xyz; a=b"))
        assertEquals("Set-Cookie=[redacted]", redact("Set-Cookie=foo"))
    }

    @Test
    fun bearerAnywhere() {
        assertEquals("sent Bearer [redacted] to server", redact("sent Bearer abcdef123 to server"))
    }

    @Test
    fun secretPairs() {
        val pair = redact("access_token=abc123&x=1")
        assertTrue("access_token=[redacted]" in pair, pair)
        assertFalse("abc123" in pair, pair)
        assertEquals("{\"token\":\"[redacted]\"}", redact("{\"token\":\"s3cr3t\"}"))
        assertEquals("code: [redacted]", redact("code: 482913"))
    }

    @Test
    fun jwtRemoved() {
        assertEquals("got [token] here", redact("got $JWT here"))
    }

    @Test
    fun longOpaqueStringRemoved() {
        assertEquals("id [token]", redact("id 3f9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a"))
    }

    @Test
    fun ipv4Removed() {
        assertEquals("from [ip] and [ip]", redact("from 10.0.2.2 and 255.255.255.255"))
    }

    @Test
    fun ipv6BeforeFullStop() {
        assertEquals("from [ip].", redact("from fe80::1."))
        assertEquals("peer [ip].", redact("peer 2001:db8::8a2e:370:7334."))
    }

    @Test
    fun escapedJsonPairs() {
        val out = redact("""body={\"token\":\"s3cr3t\"}""")
        assertFalse("s3cr3t" in out, out)
        assertEquals("""body={\"token\":\"[redacted]\"}""", out)
    }

    @Test
    fun unclosedQuotedValue() {
        val out = redact("password=\"hunter2")
        assertFalse("hunter2" in out, out)
        assertEquals("password=\"[redacted]", out)
    }

    @Test
    fun longRunsRedactQuickly() {
        val input = "a-".repeat(100_000)
        val elapsed = measureTime { redact(input) }
        assertTrue(elapsed < 1.seconds, "took $elapsed")
    }

    @Test
    fun userinfoWithSlashDropped() {
        val out = redact("http://user:p/w@host/x")
        assertFalse("user" in out, out)
        assertFalse("p/w" in out, out)
        assertEquals("http://host/…", out)
    }

    @Test
    fun escapedAndProtocolRelativeUrls() {
        assertEquals("""url=https:\/\/host.test\/…""", redact("""url=https:\/\/host.test\/path\/abc"""))
        assertEquals("""{"u":"https:\/\/host.test\/…"}""", redact("""{"u":"https:\/\/host.test\/p\/abc"}"""))
        assertEquals("src //cdn.example.com/…", redact("src //cdn.example.com/path/abc"))
        assertEquals("// TODO fix this", redact("// TODO fix this"))
        assertEquals("a //comment here", redact("a //comment here"))
    }

    @Test
    fun otherAuthHeaders() {
        assertEquals("Proxy-Authorization: [redacted]", redact("Proxy-Authorization: Basic abc"))
        assertEquals("X-Api-Key: [redacted]", redact("X-Api-Key: abc123"))
        assertEquals("x-auth-token=[redacted]", redact("x-auth-token=abc123"))
    }

    @Test
    fun basicScheme() {
        assertEquals("used Basic [redacted] here", redact("used Basic dXNlcjpwYXNz here"))
    }

    @Test
    fun jsonPairWithSpace() {
        assertEquals("{\"key\": \"[redacted]\"}", redact("{\"key\": \"abc\"}"))
    }

    @Test
    fun urlWithIpv6Host() {
        assertEquals("http://[ip]:8080/…", redact("http://[fe80::1]:8080/a"))
    }

    @Test
    fun multiLineKeepsOtherLines() {
        assertEquals(
            "Authorization: [redacted]\nepisode 12 loaded",
            redact("Authorization: x\nepisode 12 loaded"),
        )
    }

    @Test
    fun longQuotedValuesDoNotOverflow() {
        val long = "a".repeat(50_000)
        assertEquals("token=\"[redacted]\"", redact("token=\"$long\""))
        assertEquals("token=\"[redacted]", redact("token=\"$long"))
        assertEquals("token=\"[redacted]", redact("token=\"" + "\\a".repeat(25_000)))
        assertEquals("""\"token\":\"[redacted]\"""", redact("""\"token\":\"$long\""""))
        val jwtLike = "eyJ" + "x1".repeat(10_000) + ".abc.def"
        assertEquals("\"access_token\":\"[redacted]\"", redact("\"access_token\":\"$jwtLike\""))
    }

    @Test
    fun longRelativeUrlCandidateDoesNotOverflow() {
        val input = " //" + "a.".repeat(25_000)
        assertEquals(input, redact(input))
    }

    @Test
    fun unquotedValueKeepsBackslashes() {
        assertEquals("password=[redacted] rest", redact("password=p\\ss rest"))
    }

    @Test
    fun ipv6Removed() {
        listOf(
            "fe80::1%wlan0",
            "2001:db8::8a2e:370:7334",
            "::1",
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
            "::ffff:10.0.0.1",
        ).forEach { assertEquals("[ip]", redact(it), it) }
    }

    @Test
    fun camelCaseAndPrefixedKeys() {
        val secrets = listOf("opaqueTok3n", "s3cr3tval", "rt123", "id1", "abc123def", "sess42", "xv1", "mv2")
        for (input in SECRET_KEYS) {
            val out = redact(input)
            secrets.forEach { assertFalse(it in out, out) }
        }
        assertEquals(
            "{\"accessToken\":\"[redacted]\",\"clientSecret\":\"[redacted]\"}",
            redact("{\"accessToken\":\"opaqueTok3n\",\"clientSecret\":\"s3cr3tval\"}"),
        )
    }

    @Test
    fun moreSecretKeys() {
        assertEquals("connect.sid=[redacted]", redact("connect.sid=s%3Aabc123.def456"))
        assertEquals("cf_clearance=[redacted]", redact("cf_clearance=AbC.dEf-short"))
        val md5 = redact("&md5=Kx7Lq4n1Z2yC3vB8pQ9rWa&expires=1700000000")
        assertFalse("Kx7Lq4n1Z2yC3vB8pQ9rWa" in md5, md5)
        for (key in listOf("sid", "sessionid", "session_id", "jwt", "hash", "md5", "hmac", "pass", "passwd", "pwd")) {
            assertEquals("$key=[redacted]", redact("$key=v4lue"), key)
        }
    }

    @Test
    fun urlEncodedSecrets() {
        for (input in ENCODED) {
            val out = redact(input)
            listOf("abc123", "zz9", "seg.ts").forEach { assertFalse(it in out, out) }
        }
    }

    @Test
    fun encodedAmpersandStaysInTheValue() {
        assertEquals("token=[redacted]", redact("token=abc%26def"))
    }

    @Test
    fun newKeyRulesRunQuickly() {
        listOf("aToken".repeat(50_000), "%3D".repeat(100_000), "x_".repeat(100_000)).forEach { input ->
            val elapsed = measureTime { redact(input) }
            assertTrue(elapsed < 1.seconds, "took $elapsed")
        }
    }

    @Test
    fun leavesOrdinaryTextAlone() {
        ORDINARY.forEach { assertEquals(it, redact(it)) }
    }

    @Test
    fun isIdempotent() {
        val inputs =
            listOf(
                "GET https://cdn.example.com/hls/abc/master.m3u8?token=x failed",
                "see https://anilist.co",
                "http://user:pw@host.test:8080/a",
                "http://192.168.1.20:8799/pair",
                "/api/v1/watch?id=5&sig=abc done",
                "Authorization: Bearer abc.def",
                "cookie: cf_clearance=xyz; a=b",
                "Set-Cookie=foo",
                "sent Bearer abcdef123 to server",
                "access_token=abc123&x=1",
                "{\"token\":\"s3cr3t\"}",
                "code: 482913",
                "got $JWT here",
                "id 3f9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a",
                "from 10.0.2.2 and 255.255.255.255",
                "fe80::1%wlan0",
                "2001:db8::8a2e:370:7334",
                "::1",
                "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
                "::ffff:10.0.0.1",
            ) + FIX_INPUTS + SECRET_KEYS + ENCODED + MORE_KEYS + ORDINARY
        inputs.forEach {
            val once = redact(it)
            assertEquals(once, redact(once), it)
        }
    }

    private companion object {
        const val JWT =
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

        val FIX_INPUTS =
            listOf(
                "password=p\\ss rest",
                "from fe80::1.",
                "peer 2001:db8::8a2e:370:7334.",
                """body={\"token\":\"s3cr3t\"}""",
                "password=\"hunter2",
                "http://user:p/w@host/x",
                """url=https:\/\/host.test\/path\/abc""",
                """{"u":"https:\/\/host.test\/p\/abc"}""",
                "src //cdn.example.com/path/abc",
                "// TODO fix this",
                "Proxy-Authorization: Basic abc",
                "X-Api-Key: abc123",
                "x-auth-token=abc123",
                "used Basic dXNlcjpwYXNz here",
                "{\"key\": \"abc\"}",
                "http://[fe80::1]:8080/a",
                "Authorization: x\nepisode 12 loaded",
            )

        val SECRET_KEYS =
            listOf(
                "{\"accessToken\":\"opaqueTok3n\",\"clientSecret\":\"s3cr3tval\",\"refreshToken\":\"rt123\",\"idToken\":\"id1\"}",
                "{\"authToken\": \"abc123def\", \"sessionId\": \"sess42\"}",
                "x_token=xv1 my-secret=mv2",
            )

        val ENCODED =
            listOf(
                "token%3Dabc123%26sig%3Dzz9",
                "https%3A%2F%2Fcdn.x.to%2Fseg.ts%3Ftoken%3Dabc123",
                "token=abc%26def",
            )

        val MORE_KEYS =
            listOf(
                "connect.sid=s%3Aabc123.def456",
                "cf_clearance=AbC.dEf-short",
                "&md5=Kx7Lq4n1Z2yC3vB8pQ9rWa&expires=1700000000",
            )

        val ORDINARY =
            listOf(
                "at 10:15:30 episode 12 of 24",
                "java.net.SocketTimeoutException: timeout",
                "HTTP 503 from server 2",
                "version 0.1.0 (Android 9)",
                "PlayerViewModel stopped: SourceDown",
                "Keyboard focus moved to monkey row",
                "hashCode of ani.saikou.neo.KeyStore differs",
                "primaryKey missing",
            )
    }
}
