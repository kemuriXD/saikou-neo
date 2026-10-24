package ani.saikou.neo.tv.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame times while focus moves along a row, with and without the baseline profile.
 * Frame times come from `dumpsys gfxinfo`, because the trace-based
 * FrameTimingMetric finds no frames on the Fire TV Stick (Fire OS 7).
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun withoutProfile() = scrollRow(CompilationMode.None())

    @Test
    fun withBaselineProfile() = scrollRow(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun scrollRow(compilation: CompilationMode) =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingGfxInfoMetric()),
            compilationMode = compilation,
            iterations = 5,
            setupBlock = { coldStartFromHome() },
        ) {
            scrollSampleRow()
        }
}
