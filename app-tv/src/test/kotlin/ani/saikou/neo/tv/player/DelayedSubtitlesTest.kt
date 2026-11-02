package ani.saikou.neo.tv.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.NoSampleRenderer
import androidx.media3.exoplayer.RendererCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Notes the positions it's given. */
@OptIn(UnstableApi::class)
private class FakeRenderer : NoSampleRenderer() {
    val rendered = mutableListOf<Long>()
    val reset = mutableListOf<Long>()
    var ended = false

    override fun getName() = "fake"

    override fun render(
        positionUs: Long,
        elapsedRealtimeUs: Long,
    ) {
        rendered += positionUs
    }

    override fun onPositionReset(
        positionUs: Long,
        joining: Boolean,
        sampleStreamIsResetToKeyFrame: Boolean,
    ) {
        reset += positionUs
    }

    override fun isReady() = true

    override fun isEnded() = ended

    override fun supportsFormat(format: Format) = RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
}

@OptIn(UnstableApi::class)
class DelayedSubtitlesTest {
    private val inner = FakeRenderer()
    private val delay = SubtitleDelay()
    private val renderer = DelayedSubtitles(inner, delay)

    @Test
    fun withNoDelayItsTheVideosPosition() {
        renderer.render(10_000_000, 0)

        assertEquals(listOf(10_000_000L), inner.rendered)
    }

    @Test
    fun laterSubtitlesSeeAnEarlierPosition() {
        delay.ms = 500
        renderer.render(10_000_000, 0)

        assertEquals(listOf(9_500_000L), inner.rendered)
    }

    @Test
    fun earlierSubtitlesSeeALaterPosition() {
        delay.ms = -500
        renderer.render(10_000_000, 0)

        assertEquals(listOf(10_500_000L), inner.rendered)
    }

    @Test
    fun aSeekKeepsTheDelay() {
        delay.ms = 500
        renderer.resetPosition(10_000_000, false)

        assertEquals(listOf(9_500_000L), inner.reset)
    }

    @Test
    fun laterSubtitlesDontHoldUpTheEndOfTheEpisode() {
        assertFalse(renderer.isEnded)

        delay.ms = 500

        assertTrue(renderer.isEnded)
    }
}
