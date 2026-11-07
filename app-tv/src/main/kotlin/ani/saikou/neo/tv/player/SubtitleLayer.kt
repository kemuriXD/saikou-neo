package ani.saikou.neo.tv.player

import android.view.ViewGroup
import android.view.accessibility.CaptioningManager
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import ani.saikou.neo.shared.settings.SubtitleDrawing
import ani.saikou.neo.shared.settings.toPaint

/**
 * Puts subtitles on [view]'s video: plain lines through [LookSubtitles] in the look, and styled lines or pictures
 * through Media3's own view, which keeps their look and takes only the size.
 */
@OptIn(UnstableApi::class)
internal class SubtitleLayer(
    private val view: PlayerView,
) : Player.Listener {
    private val ours = LookSubtitles(view.context)
    private val media3 = view.subtitleView
    private val captioning = view.context.getSystemService(CaptioningManager::class.java)
    private var drawing: SubtitleDrawing? = null
    private var cues: List<Cue> = emptyList()
    private var styledTrack = false
    private var hdr = false
    private var sample: Sample? = null

    init {
        // Beside Media3's view, inside the frame that fits the video, so sizes and places follow the picture.
        val frame = media3?.parent as? ViewGroup ?: view
        val at = media3?.let { frame.indexOfChild(it) + 1 } ?: frame.childCount
        frame.addView(ours, at, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun attach(player: Player) {
        player.addListener(this)
        onTracksChanged(player.currentTracks)
        onCues(player.currentCues)
    }

    fun detach(player: Player) = player.removeListener(this)

    fun draw(drawing: SubtitleDrawing) {
        this.drawing = drawing
        restyle()
    }

    /** Shows [sample] when no line is on, beside the side panel, while the looks are open. Null takes it away. */
    fun sample(sample: Sample?) {
        if (sample == this.sample) return
        this.sample = sample
        ours.aside = sample != null
        route()
    }

    override fun onCues(cueGroup: CueGroup) {
        cues = cueGroup.cues
        route()
    }

    override fun onTracksChanged(tracks: Tracks) {
        styledTrack = tracks.selected(C.TRACK_TYPE_TEXT).isStyledText()
        hdr = ColorInfo.isTransferHdr(tracks.selected(C.TRACK_TYPE_VIDEO)?.colorInfo)
        restyle()
    }

    private fun route() {
        val drawing = drawing ?: return
        val route = routeCues(cues, styledTrack, drawing.styled)
        val sample = sample?.takeIf { cues.isEmpty() && !route.media3 }
        if (sample != null) ours.show(listOf(sample.line), sample.label) else ours.show(route.ours)
        media3?.isVisible = route.media3
    }

    private fun restyle() {
        val drawing = drawing ?: return
        // As Media3 does, the TV's caption settings count only while its captions are on.
        val tv = captioning?.takeIf { it.isEnabled }
        val tvStyle = tv?.takeIf { drawing.followsTv }?.let { CaptionStyleCompat.createFromCaptionStyle(it.userStyle) }
        val textFraction = drawing.textFraction(tv?.fontScale ?: 1f)
        val paint = tvStyle?.toTvCaptions()?.toPaint() ?: drawing.style.toPaint()
        ours.look(if (hdr) paint.dimmed(drawing.hdrPercent) else paint, textFraction, drawing.placement, tvStyle?.typeface)
        media3?.setApplyEmbeddedFontSizes(!drawing.mySizeOnStyled)
        media3?.setFractionalTextSize(textFraction)
        route()
    }
}

/** A [line] drawn in the look to show it, under a [label] that says it's a sample. */
internal data class Sample(
    val line: String,
    val label: String,
)

internal fun Tracks.selected(type: Int): Format? {
    val group = groups.firstOrNull { it.type == type && it.isSelected } ?: return null
    return (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat)
}
