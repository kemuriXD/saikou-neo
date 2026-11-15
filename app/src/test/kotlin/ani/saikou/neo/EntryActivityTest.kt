package ani.saikou.neo

import android.content.Intent
import ani.saikou.neo.tv.NeoApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import ani.saikou.neo.phone.MainActivity as PhoneActivity
import ani.saikou.neo.tv.MainActivity as TvActivity

@RunWith(RobolectricTestRunner::class)
class EntryActivityTest {
    private fun launch(intent: Intent = Intent()): Pair<EntryActivity, String?> {
        val controller =
            Robolectric.buildActivity(
                EntryActivity::class.java,
                intent.setClass(RuntimeEnvironment.getApplication(), EntryActivity::class.java),
            )
        val activity = controller.setup().get()
        return activity to shadowOf(activity).nextStartedActivity?.component?.className
    }

    @Test
    fun aPhoneOpensThePhoneScreensAndCloses() {
        val (activity, opened) = launch()

        assertEquals(PhoneActivity::class.java.name, opened)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun aTvOpensTheTvScreensAndCloses() {
        RuntimeEnvironment.getApplication().actAsTelevision()
        val (activity, opened) = launch()

        assertEquals(TvActivity::class.java.name, opened)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun aDebugBuildCanForceTheTvScreensOnAPhone() {
        val (_, opened) = launch(Intent().putExtra(EntryActivity.EXTRA_UI, "tv"))

        assertEquals(TvActivity::class.java.name, opened)
    }

    @Test
    fun aDebugBuildCanForceThePhoneScreensOnATv() {
        RuntimeEnvironment.getApplication().actAsTelevision()
        val (_, opened) = launch(Intent().putExtra(EntryActivity.EXTRA_UI, "phone"))

        assertEquals(PhoneActivity::class.java.name, opened)
    }

    @Test
    fun aReleaseBuildIgnoresTheOverride() {
        assertNull(forcedUi("tv", isDebug = false))
        assertEquals(Ui.Tv, forcedUi("tv", isDebug = true))
    }

    @Test
    fun aPhoneNeverMakesTheTvsStores() {
        // Extensions, the player's servers and the catalogue's stores all hang off NeoApp, and are only made when
        // something asks for them. Opening the phone screens must not.
        launch()
        Robolectric.buildActivity(PhoneActivity::class.java).setup()

        val app = RuntimeEnvironment.getApplication() as NeoApp
        val stores = NeoApp::class.java.declaredFields.filter { Lazy::class.java.isAssignableFrom(it.type) }
        assertTrue(stores.size >= 8)
        stores.forEach { field ->
            field.isAccessible = true
            assertFalse(field.name, (field.get(app) as Lazy<*>).isInitialized())
        }
    }
}
