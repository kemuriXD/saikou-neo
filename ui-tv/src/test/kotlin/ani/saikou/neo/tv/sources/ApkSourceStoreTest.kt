package ani.saikou.neo.tv.sources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ani.saikou.neo.extension.api.HostServices
import ani.saikou.neo.shared.log.AppLog
import ani.saikou.neo.shared.log.LogLevel
import ani.saikou.neo.shared.log.LogLine
import ani.saikou.neo.shared.log.LogSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApkSourceStoreTest {
    private val logged = mutableListOf<LogLine>()
    private val sink = LogSink { synchronized(logged) { logged += it } }

    @Before
    fun record() = AppLog.addSink(sink)

    @After
    fun stopRecording() = AppLog.removeSink(sink)

    @Test
    fun brokenSourceIsLogged() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Installing never calls the host, so a stand-in that does nothing is enough.
        val host =
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(HostServices::class.java)) { _, _, _ -> null } as HostServices
        val store = ApkSourceStore(context, CoroutineScope(Dispatchers.Unconfined), host)

        val installed = runBlocking { store.install("not an apk".encodeToByteArray(), id = null, list = null) }

        assertNull(installed)
        assertTrue(synchronized(logged) { logged.any { it.level == LogLevel.Warn && it.tag == "Sources" } })
    }
}
