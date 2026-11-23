package ani.saikou.neo

import android.content.ComponentName
import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// The phone screens have no landscape layouts yet, so they stay upright. The TV screens are left free to run in
// landscape.
@RunWith(RobolectricTestRunner::class)
class ScreenOrientationTest {
    private val app = RuntimeEnvironment.getApplication()

    private fun orientationOf(className: String) = app.packageManager.getActivityInfo(ComponentName(app, className), 0).screenOrientation

    @Test
    fun thePhoneScreensStayInPortrait() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, orientationOf("ani.saikou.neo.phone.MainActivity"))
    }

    @Test
    fun theTvScreensAreNotLocked() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, orientationOf("ani.saikou.neo.tv.MainActivity"))
    }

    @Test
    fun theSplashTurnsWithTheLauncherBehindIt() {
        // TVs and phones both open the launcher entry, so it can't be locked. It takes the launcher's way up instead:
        // upright on a phone, so the splash doesn't open sideways and then turn, and landscape on a TV.
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_BEHIND, orientationOf(EntryActivity::class.java.name))
    }

    @Test
    fun theSignInReturnFollowsTheScreenBehindIt() {
        // It's translucent, and Android 8.0 won't open a translucent activity that asks for an orientation.
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            orientationOf("ani.saikou.neo.phone.AniListCallbackActivity"),
        )
    }
}
