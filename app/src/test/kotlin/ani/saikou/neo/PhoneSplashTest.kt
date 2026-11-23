package ani.saikou.neo

import android.content.Context
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.VectorDrawable
import android.util.TypedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.roundToInt
import ani.saikou.neo.phone.R as BrandR

// The phone splash. The launch activity draws it before the phone screens open (ADR 0022, ADR 0038).
@RunWith(RobolectricTestRunner::class)
class PhoneSplashTest {
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

    // The TV's lockup and loading line at the phone's size: 185 dp wide, the line 16 dp under it. They're centred
    // together, so the lockup sits 18.4 dp above centre and the line 33.6 dp below.
    private fun assertLockupAndLoadingLine(mark: LayerDrawable) {
        assertEquals(2, mark.numberOfLayers)
        assertEquals(app.dp(185.0), mark.getLayerWidth(0))
        assertEquals(app.dp(105.0), mark.getLayerWidth(1))
        assertEquals(app.dpOffset(36.8), mark.getLayerInsetBottom(0))
        assertEquals(app.dpOffset(67.2), mark.getLayerInsetTop(1))
    }

    @Test
    @Config(sdk = [30])
    fun olderAndroidDrawsTheLockupAndLoadingLineOnInk() {
        val background = themeDrawable(android.R.attr.windowBackground) as LayerDrawable

        assertEquals(app.getColor(BrandR.color.ink), (background.getDrawable(0) as ColorDrawable).color)
        assertLockupAndLoadingLine(background.getDrawable(1) as LayerDrawable)
    }

    @Test
    @Config(sdk = [31])
    fun android12AndLaterShowTheSameStillLockupInTheSystemSplash() {
        val icon = themeDrawable(android.R.attr.windowSplashScreenAnimatedIcon)

        // Still, so a fast start can't cut a fade-in short.
        assertFalse(icon is Animatable)
        // A single vector: the system scales other drawables up and crops them.
        assertTrue(icon is VectorDrawable)
        // The system clips its 288 dp icon to a 192 dp circle; the pair fits inside it.
        assertEquals(app.dp(288.0), icon.intrinsicWidth)
        val value = TypedValue()
        assertTrue(theme.resolveAttribute(android.R.attr.windowSplashScreenBackground, value, true))
        assertEquals(app.getColor(BrandR.color.ink), value.data)
    }

    @Test
    fun theLockupDrawsTheSharedRoundMark() {
        // The round mark comes first, with the same paths and colours the TV rail and the phone draw.
        val mark = paths(File("../brand/res/drawable/saikou_neo_mark.xml"))
        val lockup = paths(File("../brand/res/drawable/splash_lockup.xml"))

        assertEquals(mark, lockup.take(mark.size))
    }

    @Test
    fun neoTakesTheIconPinkSoAlphaBuildsTintIt() {
        // "Saikou" stays paper, "Neo" is the icon pink that alpha builds turn green along with the mark.
        val text = paths(File("../brand/res/drawable/splash_lockup.xml")).takeLast(2).map { it.first }

        assertEquals(listOf("@color/paper", "@color/saikou_neo_icon_pink"), text)
    }

    @Test
    fun theSystemSplashIconDrawsTheSameLockupAndLoadingLineAsTheTv() {
        // A vector can't include another drawable, so the icon carries copies of the shared paths and colours.
        val icon = paths(File("src/main/res/drawable-v31/splash_icon.xml"))
        val shared = paths(File("../brand/res/drawable/splash_lockup.xml")) + paths(File("../brand/res/drawable/splash_loading.xml"))

        assertEquals(shared, icon)
    }

    // Each path's fill colour and data, in drawing order.
    private fun paths(file: File) =
        Regex("""<path\s+android:fillColor="([^"]+)"[^>]*?android:pathData="([^"]+)"""")
            .findAll(file.readText())
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()
}
