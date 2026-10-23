package ani.saikou.neo.tv

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ChannelTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun usesTheStableApplicationId() {
        assertEquals("ani.saikou.neo", app.packageName)
    }

    @Test
    fun isLabelledSaikouNeo() {
        assertEquals("Saikou Neo", app.getString(R.string.app_name))
    }
}
