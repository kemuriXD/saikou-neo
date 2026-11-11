package ani.saikou.neo.shared.sources

import ani.saikou.neo.extension.api.SourceException
import ani.saikou.neo.extension.api.SourceExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class SourceWarmUpTest {
    private val one = InstalledSource(id = "one", name = "One", version = "1.0.0")
    private val two = InstalledSource(id = "two", name = "Two", version = "1.0.0")

    private fun TestScope.warmUp(
        host: FakeHost,
        sources: List<InstalledSource>?,
        extensions: Map<String, SourceExtension>,
    ) = SourceWarmUp(
        installed = MutableStateFlow(sources),
        extension = { extensions[it.id] },
        host = host,
        scope = backgroundScope,
        timeSource = testScheduler.timeSource,
    )

    @Test
    fun eachInstalledSourcesSiteIsVisited() =
        runTest {
            val host = FakeHost()
            val sites = mapOf("one" to FakeSource(site = "https://one.example/"), "two" to FakeSource(site = "https://two.example/"))

            warmUp(host, listOf(one, two), sites).warm()
            runCurrent()

            assertEquals(setOf("https://one.example/", "https://two.example/"), host.asked.toSet())
        }

    @Test
    fun aSourceThatCantBeUsedOrHasNoSiteIsSkipped() =
        runTest {
            val host = FakeHost()
            val broken = two.copy(problem = SourceProblem.BROKEN)
            val sites = mapOf("one" to FakeSource(site = null), "two" to FakeSource(site = "https://two.example/"))

            warmUp(host, listOf(one, broken), sites).warm()
            runCurrent()

            assertEquals(emptyList(), host.asked)
        }

    @Test
    fun onlyAWebAddressIsVisited() =
        runTest {
            val host = FakeHost()

            warmUp(host, listOf(one), mapOf("one" to FakeSource(site = "file:///data/secret"))).warm()
            runCurrent()

            assertEquals(emptyList(), host.asked)
        }

    @Test
    fun aSiteVisitedRecentlyIsNotVisitedAgain() =
        runTest {
            val host = FakeHost()
            val warmUp = warmUp(host, listOf(one), mapOf("one" to FakeSource(site = "https://one.example/")))

            warmUp.warm()
            runCurrent()
            advanceTimeBy(9.minutes)
            warmUp.warm()
            runCurrent()
            assertEquals(1, host.asked.size)

            advanceTimeBy(2.minutes)
            warmUp.warm()
            runCurrent()
            assertEquals(2, host.asked.size)
        }

    @Test
    fun aSiteThatFailsDoesNotStopTheOthers() =
        runTest {
            val host = FakeHost(error = SourceException.Blocked("needs a person"))
            val sites = mapOf("one" to FakeSource(site = "https://one.example/"), "two" to FakeSource(site = "https://two.example/"))

            warmUp(host, listOf(one, two), sites).warm()
            runCurrent()

            assertEquals(2, host.asked.size)
        }
}
