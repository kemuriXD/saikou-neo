package ani.saikou.neo.tv

import android.graphics.drawable.AdaptiveIconDrawable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LauncherArtTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun setsTheIconAndTheBanner() {
        assertNotEquals(0, app.applicationInfo.icon)
        assertNotEquals(0, app.applicationInfo.banner)
    }

    @Test
    fun iconHasNoMonochromeLayer() {
        // Android TV doesn't support themed icons.
        val icon = app.getDrawable(app.applicationInfo.icon)
        assertTrue(icon is AdaptiveIconDrawable)
        assertNull((icon as AdaptiveIconDrawable).monochrome)
    }

    @Test
    fun manifestHasNoRoundIcon() {
        // Android TV doesn't use android:roundIcon.
        File("src").walk().filter { it.name == "AndroidManifest.xml" }.forEach {
            assertFalse(it.path, it.readText().contains("roundIcon"))
        }
    }
}
