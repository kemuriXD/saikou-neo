package ani.saikou.neo.tv.player

import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SubtitleLayerTest {
    @Test
    fun detachingLetsTheViewDropThePlayer() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val player = ExoPlayer.Builder(context).build()
        val view = PlayerView(context).apply { this.player = player }
        val layer = SubtitleLayer(view).also { it.attach(player) }

        layer.detach(player)

        // A view Android keeps after the screen goes (API 28 holds the last one it sorted for accessibility) must not
        // keep the released player and its buffers with it.
        assertNull(view.player)
        player.release()
    }

    @Test
    fun linesKeepAboveTheControlsWithAGap() {
        val view = PlayerView(ApplicationProvider.getApplicationContext())
        val layer = SubtitleLayer(view)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1920, 1080)

        layer.keepAbove(760f)

        // The controls start 320 px up; 16 dp is 32 px on this TV.
        assertEquals(352f, ours(view).clearBottom, 0.5f)
        layer.keepAbove(null)
        assertEquals(0f, ours(view).clearBottom, 0f)
    }

    private fun ours(view: View): LookSubtitles =
        if (view is LookSubtitles) {
            view
        } else {
            (0 until (view as ViewGroup).childCount).firstNotNullOf { runCatching { ours(view.getChildAt(it)) }.getOrNull() }
        }
}
