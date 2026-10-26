package ani.saikou.neo.tv.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** The alpha build, which is the one the test TV has installed. */
const val TARGET_PACKAGE = "ani.saikou.neo.preview"

private const val WAIT_MS = 15_000L
private const val ROW_MOVES = 10
private const val GRID_MOVES = 6
private const val MAX_PRESSES = 60
private const val SETTLE_MS = 1_000L

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
    waitForHome()
}

/** Waits until Trending's first card is on screen. */
fun MacrobenchmarkScope.waitForHome() = waitFor("trending-card-0")

/** From the spotlight's Play button, down past its dots to Trending's first card. */
fun MacrobenchmarkScope.enterTrending() {
    repeat(2) { press { device.pressDPadDown() } }
    waitFor("trending-card-0", focused = true)
}

/** Moves focus along a row and back, as the remote would. */
fun MacrobenchmarkScope.scrollRow() {
    repeat(ROW_MOVES) { press { device.pressDPadRight() } }
    repeat(ROW_MOVES) { press { device.pressDPadLeft() } }
}

/** From Trending, right to its See more button and into the page. */
fun MacrobenchmarkScope.openTrendingSeeMore() {
    pressUntilFocused("trending-see-more") { device.pressDPadRight() }
    press { device.pressDPadCenter() }
    waitFor("poster-0", focused = true)
}

/** Moves focus down the See more grid and back up. */
fun MacrobenchmarkScope.scrollGrid() {
    repeat(GRID_MOVES) { press { device.pressDPadDown() } }
    repeat(GRID_MOVES) { press { device.pressDPadUp() } }
}

/** Back from See more to Home, then down to Popular's first show, which has every Details tab and stays put. */
fun MacrobenchmarkScope.focusPopular() {
    press { device.pressBack() }
    waitFor("trending-see-more", focused = true)
    repeat(2) { press { device.pressDPadDown() } }
    waitFor("popular-card-0", focused = true)
}

/** Opens the focused show, then moves through the Details tabs and down its episodes. */
fun MacrobenchmarkScope.browseDetails() {
    press { device.pressDPadCenter() }
    waitFor("details-main-button", focused = true)
    press { device.pressDPadDown() }
    waitFor("details-tab-overview", focused = true)
    // Shows have different tabs, so back left until Episodes, the second.
    repeat(4) { press { device.pressDPadRight() } }
    pressUntilFocused("details-tab-episodes") { device.pressDPadLeft() }
    repeat(GRID_MOVES) { press { device.pressDPadDown() } }
}

private fun MacrobenchmarkScope.press(key: () -> Unit) {
    key()
    device.waitForIdle()
}

/** Presses [key] until [tag] has focus, a row's length at most. */
private fun MacrobenchmarkScope.pressUntilFocused(
    tag: String,
    key: () -> Unit,
) {
    // Waits a moment after each press, as focus can settle after the app goes idle, and pressing again would pass it.
    repeat(MAX_PRESSES) {
        if (device.wait(Until.hasObject(By.res(tag).focused(true)), SETTLE_MS)) return
        press(key)
    }
    error("$tag never got focus")
}

private fun MacrobenchmarkScope.waitFor(
    tag: String,
    focused: Boolean = false,
) {
    val selector = if (focused) By.res(tag).focused(true) else By.res(tag)
    check(device.wait(Until.hasObject(selector), WAIT_MS)) { "$tag never appeared" }
}
