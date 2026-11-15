package ani.saikou.neo

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.roundToInt
import ani.saikou.neo.tv.R as BrandR

// The TV splash, which TVs and Fire TVs pick with the television qualifier.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "television")
class TvSplashTest {
    private val app = RuntimeEnvironment.getApplication()
    private val theme = app.resources.newTheme().apply { applyStyle(R.style.Theme_SaikouNeo, true) }

    private fun Context.dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private fun themeDrawable(attr: Int): Drawable {
        val value = TypedValue()
        assertTrue(theme.resolveAttribute(attr, value, true))
        return app.resources.getDrawable(value.resourceId, theme)
    }

    private fun assertLockupOnInk() {
        val background = themeDrawable(android.R.attr.windowBackground) as LayerDrawable

        assertEquals(app.getColor(BrandR.color.ink), (background.getDrawable(0) as ColorDrawable).color)
        // The board's lockup is 360 dp wide.
        assertEquals(app.dp(360), background.getDrawable(1).intrinsicWidth)
        // A still loading line sits under the lockup, so a slow start doesn't look frozen.
        assertEquals(3, background.numberOfLayers)
        assertEquals(app.dp(128), background.getLayerInsetTop(2))
    }

    @Test
    @Config(sdk = [30])
    fun olderAndroidDrawsTheLockupOnInkFromTheWindowBackground() {
        assertLockupOnInk()
    }

    @Test
    @Config(sdk = [31])
    fun android12AndLaterDrawTheSameLockupFromTheWindowBackground() {
        // Google TV shows no system splash, so the window background is what shows there too.
        assertLockupOnInk()
    }

    @Test
    @Config(sdk = [31])
    fun android12AndLaterGiveTheSystemSplashASmallerLockup() {
        // Where a TV does show the system splash, it clips the 288 dp icon to a circle two thirds its size.
        assertEquals(app.dp(288), themeDrawable(android.R.attr.windowSplashScreenAnimatedIcon).intrinsicWidth)
        val value = TypedValue()
        assertTrue(theme.resolveAttribute(android.R.attr.windowSplashScreenBackground, value, true))
        assertEquals(app.getColor(BrandR.color.ink), value.data)
    }
}
