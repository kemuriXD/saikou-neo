package ani.saikou.neo

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class UiPickerTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun aTelevisionGetsTheTvScreens() {
        assertEquals(Ui.Tv, pickUi(isTelevision = true, hasLeanback = false, isFireTv = false))
    }

    @Test
    fun leanbackOrFireTvGetTheTvScreensEvenWithoutTelevisionMode() {
        assertEquals(Ui.Tv, pickUi(isTelevision = false, hasLeanback = true, isFireTv = false))
        assertEquals(Ui.Tv, pickUi(isTelevision = false, hasLeanback = false, isFireTv = true))
    }

    @Test
    fun anythingElseGetsThePhoneScreens() {
        // Phones and tablets alike.
        assertEquals(Ui.Phone, pickUi(isTelevision = false, hasLeanback = false, isFireTv = false))
    }

    @Test
    fun aForcedUiWinsOverTheDevice() {
        assertEquals(Ui.Phone, pickUi(isTelevision = true, hasLeanback = true, isFireTv = true, forced = Ui.Phone))
        assertEquals(Ui.Tv, pickUi(isTelevision = false, hasLeanback = false, isFireTv = false, forced = Ui.Tv))
    }

    @Test
    fun readsTheOverrideFromAnExtraAndIgnoresAnythingElse() {
        assertEquals(Ui.Tv, Ui.fromExtra("tv"))
        assertEquals(Ui.Phone, Ui.fromExtra("PHONE"))
        assertNull(Ui.fromExtra("tablet"))
        assertNull(Ui.fromExtra(null))
    }

    @Test
    fun aPhoneReadsAsAPhone() {
        assertEquals(Ui.Phone, app.deviceUi())
    }

    @Test
    fun televisionModeReadsAsATv() {
        app.actAsTelevision()
        assertEquals(Ui.Tv, app.deviceUi())
    }

    @Test
    fun theLeanbackFeatureReadsAsATv() {
        shadowOf(app.packageManager).setSystemFeature("android.software.leanback", true)
        assertEquals(Ui.Tv, app.deviceUi())
    }

    @Test
    fun fireTvsFeatureReadsAsATv() {
        shadowOf(app.packageManager).setSystemFeature("amazon.hardware.fire_tv", true)
        assertEquals(Ui.Tv, app.deviceUi())
    }
}

/** Makes [UiModeManager] report a television, as Android TV and Fire TV do. */
fun Context.actAsTelevision() {
    shadowOf(getSystemService(UiModeManager::class.java)).setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
}
