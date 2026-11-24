package ani.saikou.neo

import ani.saikou.neo.shared.crash.NoCrashReporter
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppClassTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test
    fun theAppRunsAsSaikouNeoApp() {
        assertTrue(app is SaikouNeoApp)
    }

    @Test
    fun withoutFirebaseTheAppHasNoCrashReporter() {
        // Tests never start Firebase, so even a build with google-services.json gets no reporter here.
        assertSame(NoCrashReporter, (app as SaikouNeoApp).crashReporter)
    }
}
