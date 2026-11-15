package ani.saikou.neo.phone

import android.os.Looper
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class SplashFadeTest {
    @Test
    fun theSplashFadesOutBeforeItIsRemoved() {
        val view = View(RuntimeEnvironment.getApplication())
        var removed = false

        fadeOutSplash(view) { removed = true }

        assertEquals(SPLASH_FADE_OUT_MS, view.animate().duration)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SPLASH_FADE_OUT_MS))
        assertEquals(0f, view.alpha)
        assertTrue(removed)
    }
}
