package ani.saikou.neo.tv.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code that starts the app and browses Home, See more and Details. Run it with
 * `./gradlew :app-tv:generateBaselineProfile` on an Android 13+ TV emulator with the internet; the profile lands in
 * app-tv/src/main/generated/baselineProfiles/baseline-prof.txt. On Windows the copy step finds nothing and the file is
 * gone from the emulator once Gradle uninstalls the test. Then install both nonMinifiedRelease APKs, run this test with
 * `adb shell am instrument -w -e androidx.benchmark.enabledRules BaselineProfile ani.saikou.neo.tv.benchmark/androidx.test.runner.AndroidJUnitRunner`,
 * and pull `*-baseline-prof.txt` from /sdcard/Android/media/ani.saikou.neo.tv.benchmark to that file.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndBrowsing() =
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            waitForHome()
            enterTrending()
            scrollRow()
            openTrendingSeeMore()
            scrollGrid()
            focusPopular()
            browseDetails()
        }
}
