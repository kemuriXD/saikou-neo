package ani.saikou.neo.shared.sources

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClearerWebClientTest {
    private val failed = AtomicBoolean(false)
    private val client = ClearerWebClient(failed, AtomicBoolean(false))

    // The framework makes the real ones; its constructor is only deprecated for apps.
    @Suppress("DEPRECATION")
    private val crashed =
        object : RenderProcessGoneDetail() {
            override fun didCrash() = true

            override fun rendererPriorityAtExit() = 0
        }

    @Test
    fun aDeadRendererLeavesTheAppRunning() {
        assertTrue(client.onRenderProcessGone(WebView(RuntimeEnvironment.getApplication()), crashed))
    }

    @Test
    fun aDeadRendererFailsTheCheck() {
        assertFalse(failed.get())

        client.onRenderProcessGone(WebView(RuntimeEnvironment.getApplication()), crashed)

        assertTrue(failed.get())
    }
}
