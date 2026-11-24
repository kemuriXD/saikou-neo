package ani.saikou.neo.shared.crash

import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.shared.account.AniListToken
import ani.saikou.neo.shared.account.SignedInAccount
import ani.saikou.neo.shared.log.AppLog
import ani.saikou.neo.shared.settings.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CrashReportsTest {
    private class Recording : CrashReporter {
        val calls = mutableListOf<String>()
        val keys = mutableMapOf<String, String>()
        val lines = mutableListOf<String>()

        @Synchronized
        override fun setEnabled(enabled: Boolean) {
            calls += "enabled $enabled"
        }

        @Synchronized
        override fun setUser(
            id: String?,
            name: String?,
        ) {
            calls += "user $id $name"
        }

        @Synchronized
        override fun setKey(
            key: String,
            value: String,
        ) {
            keys[key] = value
        }

        @Synchronized
        override fun log(line: String) {
            lines += line
        }

        @Synchronized
        override fun recordNonFatal(
            message: String,
            error: Throwable?,
        ) {
            calls += "non-fatal $message"
        }
    }

    private val reporter = Recording()
    private val facts = mapOf("channel" to "alpha", "ui" to "tv", "network" to "wifi")

    private fun account(
        id: Int,
        name: String,
    ) = SignedInAccount(AniListProfile(id, name, null), AniListToken("token", 0))

    @AfterTest
    fun cleanUp() {
        AppLog.reset()
        CrashContext.reset()
    }

    @Test
    fun everyFactBecomesAKey() =
        runTest(UnconfinedTestDispatcher()) {
            CrashReports(reporter, backgroundScope).start({ facts }, emptyFlow(), emptyFlow())

            assertEquals(facts, reporter.keys)
        }

    @Test
    fun aNewNetworkReplacesTheKey() =
        runTest(UnconfinedTestDispatcher()) {
            val network = MutableSharedFlow<String>()
            CrashReports(reporter, backgroundScope).start({ facts }, emptyFlow(), emptyFlow(), network)

            network.emit("ethernet")

            assertEquals("ethernet", reporter.keys["network"])
        }

    @Test
    fun theSettingTurnsSendingOffAndOnAgain() =
        runTest(UnconfinedTestDispatcher()) {
            val settings = MutableSharedFlow<Settings>()
            CrashReports(reporter, backgroundScope).start({ facts }, settings, emptyFlow())

            settings.emit(Settings(crashReports = false))
            settings.emit(Settings(crashReports = true))
            settings.emit(Settings(crashReports = false))

            assertEquals(listOf("enabled false", "enabled true", "enabled false"), reporter.calls)
        }

    @Test
    fun otherSettingChangesLeaveSendingAlone() =
        runTest(UnconfinedTestDispatcher()) {
            val settings = MutableSharedFlow<Settings>()
            CrashReports(reporter, backgroundScope).start({ facts }, settings, emptyFlow())

            settings.emit(Settings(crashReports = false))
            settings.emit(Settings(crashReports = false, slowTv = true))
            settings.emit(Settings(crashReports = true))

            assertEquals(listOf("enabled false", "enabled true"), reporter.calls)
        }

    @Test
    fun theUserFollowsSignInAndSignOut() =
        runTest(UnconfinedTestDispatcher()) {
            val account = MutableStateFlow<SignedInAccount?>(null)
            CrashReports(reporter, backgroundScope).start({ facts }, emptyFlow(), account)

            account.value = account(5412345, "Kemuri")
            account.value = null

            assertEquals(listOf("user null null", "user 5412345 Kemuri", "user null null"), reporter.calls)
        }

    @Test
    fun logLinesReachTheReporterScrubbed() =
        runTest(UnconfinedTestDispatcher()) {
            val reports = CrashReports(reporter, backgroundScope)
            reports.start({ facts }, emptyFlow(), emptyFlow())
            reports.addSourceTerms(setOf("gotaku"))

            AppLog.i("Sources", "Found source gotaku 3")
            AppLog.i("Player", "Playing show 21 episode 3 on gotaku")

            assertEquals(
                listOf("I Sources: Found source [source] 3", "I Player: Playing show [id] episode [n] on [server]"),
                reporter.lines,
            )
        }

    @Test
    fun aSourceLineFromBeforeTheSourcesAreKnownIsHeldThenScrubbed() =
        runTest(UnconfinedTestDispatcher()) {
            val reports = CrashReports(reporter, backgroundScope)
            reports.start({ facts }, emptyFlow(), emptyFlow())

            AppLog.i("Sources", "Found source gotaku 3")
            AppLog.i("App", "Other lines go on")
            assertEquals(listOf("I App: Other lines go on"), reporter.lines)

            reports.addSourceTerms(setOf("gotaku"))

            assertEquals(listOf("I App: Other lines go on", "I Sources: Found source [source] 3"), reporter.lines)
            assertFalse(reporter.lines.any { "gotaku" in it })
        }

    @Test
    fun heldLinesAreCappedAndTheRestCounted() =
        runTest(UnconfinedTestDispatcher()) {
            val reports = CrashReports(reporter, backgroundScope)
            reports.start({ facts }, emptyFlow(), emptyFlow())

            repeat(60) { AppLog.i("Sources", "Found source gotaku $it") }
            reports.addSourceTerms(setOf("gotaku"))

            assertEquals(51, reporter.lines.size)
            assertEquals("I Sources: 10 earlier lines dropped", reporter.lines.last())
            assertFalse(reporter.lines.any { "gotaku" in it })
        }

    @Test
    fun laterTermsAddToEarlierOnes() =
        runTest(UnconfinedTestDispatcher()) {
            val reports = CrashReports(reporter, backgroundScope)
            reports.start({ facts }, emptyFlow(), emptyFlow())
            reports.addSourceTerms(setOf("gotaku"))
            // A source removed later can still be named in a line, so its name stays a term.
            reports.addSourceTerms(setOf("kaido"))

            AppLog.w("Player", "gotaku and kaido gave no servers")

            assertEquals(listOf("W Player: [source] and [source] gave no servers"), reporter.lines)
            assertTrue(reports.scrubber.sourceTerms.containsAll(setOf("gotaku", "kaido")))
        }

    @Test
    fun aSourceIdNamedLaterIsNeverSent() =
        runTest(UnconfinedTestDispatcher()) {
            val reports = CrashReports(reporter, backgroundScope)
            reports.start({ facts }, emptyFlow(), emptyFlow())
            reports.addSourceTerms(setOf("gotaku"))

            AppLog.w("Sources", "Install rejected: newsrc is broken")
            AppLog.w("Sources", "Install rejected: newsrc isn't the source being updated")
            AppLog.w("Sources", "Couldn't draw the icon of newsrc")
            AppLog.e("Sources", "Couldn't load ani.newsrc.Main")
            AppLog.i("Sources", "Found source other.newsrc 2")
            AppLog.w("Sources", "Install rejected: the file isn't a source")

            assertFalse(reporter.lines.any { "newsrc" in it }, reporter.lines.toString())
            assertTrue("W Sources: Install rejected: the file isn't a source" in reporter.lines, reporter.lines.toString())
        }

    @Test
    fun recoveredErrorsGoToTheReporterOnlyWhileReportsAreOn() =
        runTest(UnconfinedTestDispatcher()) {
            val settings = MutableSharedFlow<Settings>()
            val reports = CrashReports(reporter, backgroundScope)
            reports.start({ facts }, settings, emptyFlow())
            reports.addSourceTerms(setOf("gotaku"))

            CrashContext.recovered("Player", "gotaku stopped")
            settings.emit(Settings(crashReports = false))
            CrashContext.recovered("Player", "Lost while off")
            settings.emit(Settings(crashReports = true))
            CrashContext.screen("player")

            assertEquals(listOf("non-fatal Player: [source] stopped", "enabled false", "enabled true"), reporter.calls)
            assertEquals("player", reporter.keys["screen"])
        }

    @Test
    fun aReporterThatThrowsDoesNotStopTheRest() =
        runTest(UnconfinedTestDispatcher()) {
            val switches = mutableListOf<Boolean>()
            var users = 0
            val throwing =
                object : CrashReporter {
                    override fun setEnabled(enabled: Boolean) {
                        switches += enabled
                        error("broken")
                    }

                    override fun setUser(
                        id: String?,
                        name: String?,
                    ) {
                        users++
                        error("broken")
                    }

                    override fun setKey(
                        key: String,
                        value: String,
                    ): Unit = error("broken")

                    override fun log(line: String): Unit = error("broken")

                    override fun recordNonFatal(
                        message: String,
                        error: Throwable?,
                    ): Unit = error("broken")
                }
            val settings = MutableSharedFlow<Settings>()
            val account = MutableStateFlow<SignedInAccount?>(null)
            CrashReports(throwing, backgroundScope).start({ facts }, settings, account, MutableSharedFlow())

            settings.emit(Settings(crashReports = false))
            settings.emit(Settings(crashReports = true))
            account.value = account(1, "a")

            assertEquals(listOf(false, true), switches)
            assertEquals(2, users)
        }
}
