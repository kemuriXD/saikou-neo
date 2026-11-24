package ani.saikou.neo.shared.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
            ) + ORDINARY
        inputs.forEach {
            val once = redact(it)
            assertEquals(once, redact(once), it)
        }
    }

    private companion object {
        const val JWT =
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

        val ORDINARY =
            listOf(
                "at 10:15:30 episode 12 of 24",
                "java.net.SocketTimeoutException: timeout",
                "HTTP 503 from server 2",
                "version 0.1.0 (Android 9)",
                "PlayerViewModel stopped: SourceDown",
            )
    }
}
