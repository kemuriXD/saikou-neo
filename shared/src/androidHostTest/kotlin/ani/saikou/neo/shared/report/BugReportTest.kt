package ani.saikou.neo.shared.report

import ani.saikou.neo.shared.crash.CrashLogScrubber
import java.util.zip.GZIPInputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BugReportTest {
    private val scrubber = CrashLogScrubber().apply { sourceTerms = setOf("Gotaku") }

    private fun build(
        log: String = "",
        screenshot: ByteArray? = null,
        category: String = "playback",
        description: String = "",
        device: Map<String, String> = emptyMap(),
        context: Map<String, String> = emptyMap(),
        account: ReportAccount? = null,
    ) = buildBugReport(category, description, device, context, account, log, screenshot, scrubber)

    private fun ByteArray.unzipped(): String = GZIPInputStream(inputStream()).bufferedReader().readText()

    @Test
    fun holdsWhatItWasGiven() {
        val shot = byteArrayOf(1, 2, 3)
        val report =
            build(
                log = "2026-11-24T10:00:00Z I App: Started",
                screenshot = shot,
                description = "Stuck on loading",
                device = mapOf("model" to "AFTMM", "channel" to "alpha"),
                context = mapOf("screen" to "player", "episode" to "3"),
                account = ReportAccount("123", "kemuri"),
            )

        assertEquals("playback", report.category)
        assertEquals("Stuck on loading", report.description)
        assertEquals(mapOf("model" to "AFTMM", "channel" to "alpha"), report.device)
        assertEquals(mapOf("screen" to "player", "episode" to "3"), report.context)
        assertEquals(ReportAccount("123", "kemuri"), report.account)
        assertEquals("2026-11-24T10:00:00Z I App: Started\n", report.log.unzipped())
        assertTrue(shot.contentEquals(report.screenshot))
    }

    @Test
    fun theLogIsScrubbed() {
        val log =
            """
            2026-11-24T10:00:00Z W Player: Gotaku failed for show 21 episode 3 at https://example.com/watch?token=abc
            2026-11-24T10:00:01Z I Sources: Found source com.example.gotaku
            """.trimIndent()

        val text = build(log = log).log.unzipped()

        assertFalse("Gotaku" in text, text)
        assertFalse("example" in text, text)
        assertFalse("21" in text, text)
        assertFalse("abc" in text, text)
        assertTrue(text.startsWith("2026-11-24T10:00:00Z W Player: "), text)
    }

    @Test
    fun aHugeLogKeepsItsNewestLinesUnderTheCap() {
        val random = Random(7)
        val lines = (1..40_000).map { "2026-11-24T10:00:00Z I App: line $it ${random.words()}" }

        val report = build(log = lines.joinToString("\n"))

        assertTrue(report.log.size <= BugReportLimits.LOG_BYTES, "${report.log.size}")
        val text = report.log.unzipped()
        assertTrue(text.startsWith(EARLIER_LINES_DROPPED), text.take(80))
        assertTrue(text.trimEnd().endsWith(lines.last()), text.takeLast(80))
    }

    @Test
    fun aScreenshotOverTheCapIsLeftOut() {
        assertNull(build(screenshot = ByteArray(BugReportLimits.SCREENSHOT_BYTES + 1)).screenshot)
        assertEquals(
            BugReportLimits.SCREENSHOT_BYTES,
            build(screenshot = ByteArray(BugReportLimits.SCREENSHOT_BYTES)).screenshot?.size,
        )
    }

    @Test
    fun textIsCutToItsLimits() {
        val long = "x".repeat(5_000)

        val report =
            build(
                description = long,
                device = mapOf("model" to long),
                context = mapOf("screen" to long),
                account = ReportAccount(long, long),
            )

        assertEquals(BugReportLimits.DESCRIPTION_CHARS, report.description.length)
        assertEquals(BugReportLimits.VALUE_CHARS, report.device.getValue("model").length)
        assertEquals(BugReportLimits.VALUE_CHARS, report.context.getValue("screen").length)
        assertEquals(BugReportLimits.VALUE_CHARS, report.account?.id?.length)
        assertEquals(BugReportLimits.VALUE_CHARS, report.account?.name?.length)
    }

    @Test
    fun unknownDetailsAreLeftOut() {
        val report = build(device = mapOf("model" to "a", "serial" to "b"), context = mapOf("screen" to "c", "url" to "d"))

        assertEquals(setOf("model"), report.device.keys)
        assertEquals(setOf("screen"), report.context.keys)
    }

    @Test
    fun theWholeReportFitsInOneDocument() {
        val random = Random(3)
        val log = (1..40_000).joinToString("\n") { "2026-11-24T10:00:00Z I App: ${random.words()}" }
        val long = "x".repeat(5_000)
        val report =
            build(
                log = log,
                screenshot = ByteArray(BugReportLimits.SCREENSHOT_BYTES),
                description = long,
                device = BugReportLimits.DEVICE_KEYS.associateWith { long },
                context = BugReportLimits.CONTEXT_KEYS.associateWith { long },
                account = ReportAccount(long, long),
            )

        assertTrue(report.sizeBytes() <= BugReportLimits.REPORT_BYTES, "${report.sizeBytes()}")
    }

    @Test
    fun aCategoryMustBeAPlainWord() {
        assertFailsWith<IllegalArgumentException> { build(category = "Playback!") }
        assertFailsWith<IllegalArgumentException> { build(category = "") }
    }

    @Test
    fun codesAreShortAndEasyToRead() {
        val random = Random(1)
        repeat(1_000) {
            val code = newReportCode(random)
            assertTrue(code.matches(Regex("SN-[2-9A-HJKMNP-TV-Z]{4}")), code)
        }
    }

    // Lower-case words, which neither compress well nor look like secrets or IDs.
    private fun Random.words(): String = (1..16).joinToString(" ") { (1..5).map { 'a' + nextInt(26) }.joinToString("") }
}
