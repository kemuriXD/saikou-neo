package ani.saikou.neo.tv.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@UnstableApi
@RunWith(RobolectricTestRunner::class)
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
}
