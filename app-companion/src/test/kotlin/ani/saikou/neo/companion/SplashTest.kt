package ani.saikou.neo.companion

import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Looper
import android.util.TypedValue
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
class SplashTest {
    private val app = RuntimeEnvironment.getApplication()
    private val theme = app.resources.newTheme().apply { applyStyle(R.style.Theme_SaikouNeo, true) }

    // Sizes round to whole pixels and insets truncate, as Android's getDimensionPixelSize and Offset do.
    private fun Context.dp(value: Double) = (value * resources.displayMetrics.density).roundToInt()

    private fun Context.dpOffset(value: Double) = (value * resources.displayMetrics.density).toInt()

    private fun themeDrawable(attr: Int): Drawable {
        val value = TypedValue()
        assertTrue(theme.resolveAttribute(attr, value, true))
        return app.resources.getDrawable(value.resourceId, theme)
    }

    @Test
    @Config(sdk = [30])
    fun olderAndroidDrawsTheLockupAndLoadingLineOnInk() {
        val background = themeDrawable(android.R.attr.windowBackground) as LayerDrawable

        assertEquals(app.getColor(R.color.ink), (background.getDrawable(0) as ColorDrawable).color)
        assertEquals(3, background.numberOfLayers)
        // The TV's lockup and loading line, at the phone's size: 185 dp wide, the line 16 dp under it.
        assertEquals(app.dp(185.0), background.getLayerWidth(1))
        assertEquals(app.dp(105.0), background.getLayerWidth(2))
        // The pair is centred together, so the lockup sits 18.4 dp above centre and the line 33.6 dp below.
        assertEquals(app.dpOffset(36.8), background.getLayerInsetBottom(1))
        assertEquals(app.dpOffset(67.2), background.getLayerInsetTop(2))
    }

    // Android 13 reads the icon's own length and deprecates the duration, but Android 12 still needs it.
    @Suppress("DEPRECATION")
    @Test
    @Config(sdk = [31])
    fun android12AndLaterAnimateTheSystemSplashIcon() {
        val icon = themeDrawable(android.R.attr.windowSplashScreenAnimatedIcon)

        assertTrue(icon is AnimatedVectorDrawable)
        assertEquals(app.dp(288.0), icon.intrinsicWidth)
        val value = TypedValue()
        assertTrue(theme.resolveAttribute(android.R.attr.windowSplashScreenAnimationDuration, value, true))
        assertEquals(400, value.data)
        assertTrue(theme.resolveAttribute(android.R.attr.windowSplashScreenBackground, value, true))
        assertEquals(app.getColor(R.color.ink), value.data)
    }

    @Test
    fun theSplashFadesOutBeforeItIsRemoved() {
        val view = View(app)
        var removed = false

        fadeOutSplash(view) { removed = true }

        assertEquals(SPLASH_FADE_OUT_MS, view.animate().duration)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SPLASH_FADE_OUT_MS))
        assertEquals(0f, view.alpha)
        assertTrue(removed)
    }

    @Test
    fun theAnimatedIconDrawsTheSameLockupAndLoadingLineAsTheTv() {
        // A vector can't include another drawable, so the animated icon carries copies of the shared paths.
        val icon = pathData(File("src/main/res/drawable/splash_icon.xml"))
        val shared =
            pathData(File("../brand/res/drawable/splash_lockup.xml")) +
                pathData(File("../brand/res/drawable/splash_loading.xml"))

        assertEquals(shared, icon)
    }

    private fun pathData(file: File) = Regex("""android:pathData="([^"]+)"""").findAll(file.readText()).map { it.groupValues[1] }.toList()
}
