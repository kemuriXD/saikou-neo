package ani.saikou.neo.tv.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** The alpha build, which is the one the test TV has installed. */
const val TARGET_PACKAGE = "ani.saikou.neo.preview"

private const val CARD_COUNT = 12
private const val WAIT_MS = 10_000L

/**
 * Starts the app from a killed process. Macrobenchmark's cold start also drops
 * the kernel page cache, which needs root on Android 9. Unrooted sticks skip
 * that with a warning, but the test stick has Magisk, so Macrobenchmark expects
 * root and fails. Killing the process gives the same start as an unrooted stick.
 */
fun MacrobenchmarkScope.coldStartFromHome() {
    pressHome()
    killProcess()
    startActivityAndWait()
    waitForSampleRow()
}

/** Waits until the sample row's first card is on screen. */
fun MacrobenchmarkScope.waitForSampleRow() {
    check(device.wait(Until.hasObject(By.res("sample-card-0")), WAIT_MS)) { "The sample row never appeared" }
}

/** Moves focus along the whole sample row and back, as the remote would. */
fun MacrobenchmarkScope.scrollSampleRow() {
    repeat(CARD_COUNT - 1) {
        device.pressDPadRight()
        device.waitForIdle()
    }
    check(device.wait(Until.hasObject(By.res("sample-card-${CARD_COUNT - 1}")), WAIT_MS)) {
        "The row didn't scroll to its last card"
    }
    repeat(CARD_COUNT - 1) {
        device.pressDPadLeft()
        device.waitForIdle()
    }
}
