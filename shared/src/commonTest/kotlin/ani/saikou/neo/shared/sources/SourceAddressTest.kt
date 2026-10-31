package ani.saikou.neo.shared.sources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceAddressTest {
    @Test
    fun aLinkIsUsedAsTyped() {
        assertEquals(SourceAddress.Link("https://example.com/source.apk"), SourceAddress.parse("  https://example.com/source.apk "))
        assertEquals(SourceAddress.Link("HTTP://10.0.2.2:8000/a.apk"), SourceAddress.parse("HTTP://10.0.2.2:8000/a.apk"))
    }

    @Test
    fun anythingElseIsAShortCode() {
        assertEquals(SourceAddress.Code("Ex_ample-1"), SourceAddress.parse(" Ex_ample-1 "))
    }

    @Test
    fun textThatIsNeitherIsRefused() {
        assertNull(SourceAddress.parse(""))
        assertNull(SourceAddress.parse("   "))
        assertNull(SourceAddress.parse("two words"))
        assertNull(SourceAddress.parse("ftp://example.com/a.apk"))
        assertNull(SourceAddress.parse("a".repeat(41)))
    }

    @Test
    fun httpsLinksAreAllowedAnywhere() {
        assertTrue(isAllowedSourceLink("https://example.com/source.apk"))
        assertTrue(isAllowedSourceLink("HTTPS://EXAMPLE.COM"))
    }

    @Test
    fun plainHttpIsAllowedOnlyAtHome() {
        listOf(
            "http://localhost/a.apk",
            "http://127.0.0.1:8000/a.apk",
            "http://10.0.2.2:8000/a.apk",
            "http://172.16.0.4/a.apk",
            "http://172.31.255.1/a.apk",
            "http://192.168.1.20:8080",
        ).forEach { assertTrue(isAllowedSourceLink(it), it) }
        listOf(
            "http://example.com/a.apk",
            "http://8.8.8.8/a.apk",
            "http://172.32.0.1/a.apk",
            "http://192.169.0.1/a.apk",
            "http://10.0.2.2.example.com/a.apk",
            "http://10.0.0.1@example.com/a.apk",
            "http://localhost.example.com/a.apk",
            "http://10.0.0.256/a.apk",
        ).forEach { assertFalse(isAllowedSourceLink(it), it) }
    }

    @Test
    fun otherSchemesAreRefused() {
        assertFalse(isAllowedSourceLink("file:///data/a.apk"))
        assertFalse(isAllowedSourceLink("content://a/b"))
        assertFalse(isAllowedSourceLink("https://"))
    }
}
