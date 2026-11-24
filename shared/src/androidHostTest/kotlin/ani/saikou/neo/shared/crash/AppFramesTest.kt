package ani.saikou.neo.shared.crash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppFramesTest {
    private fun frame(className: String) = StackTraceElement(className, "run", "File.kt", 1)

    @Test
    fun theAppsFramesAndThePlatformsStay() {
        val kept =
            listOf(
                "ani.saikou.neo.shared.playback.PlayerViewModel",
                "ani.saikou.neo.tv.MainActivity\$onCreate\$1",
                "ani.saikou.neo.phone.MainActivity",
                "ani.saikou.neo.EntryActivity",
                "ani.saikou.neo.extension.api.Extension",
                "kotlinx.coroutines.DispatchedTask",
                "java.io.IOException",
                "android.os.Handler",
                "okhttp3.internal.connection.RealCall",
            ).map(::frame)

        assertEquals(kept, appFrames(kept.toTypedArray())?.toList())
    }

    @Test
    fun anExtensionsFramesAreDropped() {
        // An extension's package can name its source, and can sit under the app's own name.
        val frames =
            arrayOf(
                frame("ani.gotaku.Source"),
                frame("ani.saikou.neo.example.ExampleSource"),
                frame("com.example.demo.Main"),
                frame("ani.saikou.neo.shared.playback.PlayerViewModel"),
            )

        assertEquals(listOf(frame("ani.saikou.neo.shared.playback.PlayerViewModel")), appFrames(frames)?.toList())
    }

    @Test
    fun withNoFramesLeftThereAreNone() {
        assertNull(appFrames(arrayOf(frame("ani.gotaku.Source"))))
        assertNull(appFrames(null))
    }
}
