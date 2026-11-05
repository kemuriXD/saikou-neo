package ani.saikou.neo.shared.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SourceChecksViewModelTest {
    private val main: TestDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun useTestMain() = Dispatchers.setMain(main)

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private val sources = (1..5).map { InstalledSource(id = "s$it", name = "Source $it", version = "1.0.0") }

    private fun viewModel(
        loads: MutableList<String> = mutableListOf(),
        source: () -> FakeSource = { FakeSource() },
    ) = SourceChecksViewModel(
        SourceChecker(
            load = {
                loads += it.id
                source()
            },
            host = FakeHost(),
            timeSource = main.scheduler.timeSource,
        ),
    )

    @Test
    fun aCheckShowsEachStepAndThenIsDone() =
        runTest(main) {
            val viewModel = viewModel()

            viewModel.check(sources[0])
            assertEquals(CheckStatus.WAITING, viewModel.checks.value["s1"]?.status)
            advanceUntilIdle()

            val check = viewModel.checks.value.getValue("s1")
            assertEquals(CheckStatus.DONE, check.status)
            assertEquals(CheckStep.entries, check.steps.map { it.step })
            assertTrue(check.passed)
        }

    @Test
    fun checkingAllRunsAFewAtATime() =
        runTest(main) {
            var running = 0
            var most = 0
            val viewModel =
                viewModel {
                    FakeSource(servers = {
                        most = maxOf(most, ++running)
                        delay(5.seconds)
                        running--
                        emptyList()
                    })
                }

            viewModel.checkAll(sources)
            runCurrent()

            assertEquals(
                CHECKS_AT_ONCE,
                viewModel.checks.value.values
                    .count { it.status == CheckStatus.RUNNING },
            )
            advanceUntilIdle()
            assertEquals(CHECKS_AT_ONCE, most)
            assertTrue(
                viewModel.checks.value.values
                    .all { it.status == CheckStatus.DONE && !it.passed },
            )
        }

    @Test
    fun stopStopsEveryCheckAndKeepsTheStepsSoFar() =
        runTest(main) {
            val viewModel = viewModel { FakeSource(servers = { awaitCancellation() }) }
            viewModel.checkAll(sources)
            runCurrent()

            viewModel.stop()
            advanceUntilIdle()

            val checks = viewModel.checks.value.values
            assertTrue(checks.all { it.status == CheckStatus.STOPPED })
            assertEquals(CHECKS_AT_ONCE, checks.count { it.steps.map { step -> step.step } == listOf(CheckStep.START, CheckStep.SITE) })
            assertEquals(sources.size - CHECKS_AT_ONCE, checks.count { it.steps.isEmpty() })
        }

    @Test
    fun aSourceAlreadyBeingCheckedIsNotCheckedTwice() =
        runTest(main) {
            val loads = mutableListOf<String>()
            val viewModel = viewModel(loads)

            viewModel.check(sources[0])
            viewModel.check(sources[0])
            advanceUntilIdle()

            assertEquals(listOf("s1"), loads)
        }
}
