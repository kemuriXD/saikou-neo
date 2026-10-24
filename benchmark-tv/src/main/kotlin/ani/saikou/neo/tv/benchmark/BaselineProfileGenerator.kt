package ani.saikou.neo.tv.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code that starts the app and scrolls a row. Run it with
 * `./gradlew :app-tv:generateBaselineProfile` on an Android 13+ emulator or a
 * rooted device; the profile lands in app-tv/src/main/generated/baselineProfiles.
 * On Windows the copy step can find nothing. Then copy the
 * `*-baseline-prof.txt` file from benchmark-tv/build/outputs/connected_android_test_additional_output
 * to that folder as baseline-prof.txt.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndRowScrolling() =
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            waitForSampleRow()
            scrollSampleRow()
        }
}
