package ani.saikou.neo.shared.sources

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SiteChecksTest {
    @Test
    fun oneCheckRunsAtATimeAndTheNextWaitsForIt() =
        runTest {
            // Each check is a WebView running a site's script, which a TV with little memory can't run many of.
            val checks = SiteChecks()
            val first = CompletableDeferred<Unit>()
            val ran = mutableListOf<String>()
            launch {
                checks.run("one.example") {
                    ran += "one"
                    first.await()
                    "one"
                }
            }
            launch {
                checks.run("two.example") {
                    ran += "two"
                    "two"
                }
            }
            runCurrent()

            assertEquals(listOf("one"), ran)
            assertTrue(checks.checking.value)

            first.complete(Unit)
            runCurrent()
            assertEquals(listOf("one", "two"), ran)
            assertFalse(checks.checking.value)
        }

    @Test
    fun aCheckThatJustFailedIsNotRunAgainForAWhile() =
        runTest {
            val checks = SiteChecks(testScheduler.timeSource)
            var runs = 0
            assertNull(
                checks.run<String>("site.example") {
                    runs++
                    null
                },
            )

            // Those waiting behind a failed check get its answer, rather than each running a check of their own.
            assertNull(
                checks.run("site.example") {
                    runs++
                    "cleared"
                },
            )
            assertEquals(1, runs)

            delay(SITE_CHECK_RETRY + 1.seconds)
            assertEquals(
                "cleared",
                checks.run("site.example") {
                    runs++
                    "cleared"
                },
            )
            assertEquals(2, runs)
        }

    @Test
    fun anAnswerPastTheLimitCountsWhileASiteCheckRuns() =
        runTest {
            val checking = MutableStateFlow(true)
            val call =
                async {
                    delay(70.seconds)
                    "servers"
                }
            launch {
                delay(65.seconds)
                checking.value = false
            }

            assertEquals("servers", call.awaitAllowingSiteChecks(25.seconds, checking)?.value)
        }

    @Test
    fun withNoSiteCheckTheLimitStands() =
        runTest {
            val call =
                async {
                    delay(70.seconds)
                    "servers"
                }

            assertNull(call.awaitAllowingSiteChecks(25.seconds, MutableStateFlow(false)))
            call.cancel()
        }
}
