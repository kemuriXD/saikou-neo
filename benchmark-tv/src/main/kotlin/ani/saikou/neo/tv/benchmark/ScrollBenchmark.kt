package ani.saikou.neo.tv.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame times while focus moves along a Home row and down the See more grid, with and without the baseline profile.
 * Frame times come from `dumpsys gfxinfo`, because the trace-based FrameTimingMetric finds no frames on the Fire TV
 * Stick (Fire OS 7).
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun homeRowWithoutProfile() = measure(CompilationMode.None(), { enterTrending() }) { scrollRow() }

    @Test
    fun homeRowWithBaselineProfile() = measure(CompilationMode.Partial(BaselineProfileMode.Require), { enterTrending() }) { scrollRow() }

    @Test
    fun seeMoreWithoutProfile() = measure(CompilationMode.None(), { openSeeMore() }) { scrollGrid() }

    @Test
    fun seeMoreWithBaselineProfile() = measure(CompilationMode.Partial(BaselineProfileMode.Require), { openSeeMore() }) { scrollGrid() }

    private fun MacrobenchmarkScope.openSeeMore() {
        enterTrending()
        openTrendingSeeMore()
    }

    private fun measure(
        compilation: CompilationMode,
        setup: MacrobenchmarkScope.() -> Unit,
        moves: MacrobenchmarkScope.() -> Unit,
    ) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingGfxInfoMetric()),
        compilationMode = compilation,
        iterations = 5,
        setupBlock = {
            coldStartFromHome()
            setup()
        },
        measureBlock = moves,
    )
}
