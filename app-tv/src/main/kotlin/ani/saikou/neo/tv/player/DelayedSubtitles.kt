package ani.saikou.neo.tv.player

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput

private const val US_PER_MS = 1_000

/** How far subtitles run behind the video, or ahead when negative. The screen sets it and the player thread reads it. */
internal class SubtitleDelay {
    @Volatile
    var ms = 0
}

/**
 * Subtitles shown earlier or later than their times, by telling [renderer] the video is somewhere else. Media3 has no
 * setting for this. After moving later, a line that already passed doesn't show again, and the next one is on time.
 */
@OptIn(UnstableApi::class)
internal class DelayedSubtitles(
    renderer: Renderer,
    private val delay: SubtitleDelay,
) : ForwardingRenderer(renderer) {
    private fun shifted(positionUs: Long) = positionUs - delay.ms.toLong() * US_PER_MS

    override fun render(
        positionUs: Long,
        elapsedRealtimeUs: Long,
    ) = super.render(shifted(positionUs), elapsedRealtimeUs)

    override fun resetPosition(
        positionUs: Long,
        sampleStreamIsResetToKeyFrame: Boolean,
    ) = super.resetPosition(shifted(positionUs), sampleStreamIsResetToKeyFrame)

    // Subtitles running behind would otherwise keep the episode from ending until they catch up.
    override fun isEnded() = delay.ms > 0 || super.isEnded()
}

/** The usual renderers, with subtitles following [delay]. */
@OptIn(UnstableApi::class)
internal class DelayedSubtitlesFactory(
    context: Context,
    private val delay: SubtitleDelay,
) : DefaultRenderersFactory(context) {
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val text = ArrayList<Renderer>()
        super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, text)
        text.mapTo(out) { DelayedSubtitles(it, delay) }
    }
}
