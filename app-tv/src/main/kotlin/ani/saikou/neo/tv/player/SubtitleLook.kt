package ani.saikou.neo.tv.player

import android.graphics.Color
import android.view.accessibility.CaptioningManager
import androidx.annotation.OptIn
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import ani.saikou.neo.shared.settings.SubtitleColour
import ani.saikou.neo.shared.settings.SubtitleEdge
import ani.saikou.neo.shared.settings.SubtitleStyle

// Black at 75%, so the video shows a little through it.
private const val BACKGROUND = 0xBF000000.toInt()

/**
 * Puts [style] on the subtitles, at once. Where it says TV, it keeps the TV's caption settings. Styled subtitles keep
 * their own colours, edges and places, and take only the size.
 */
@OptIn(UnstableApi::class)
internal fun SubtitleView.applyStyle(style: SubtitleStyle) {
    val captions = context.getSystemService(CaptioningManager::class.java)?.takeIf { it.isEnabled }
    val tv = captions?.let { CaptionStyleCompat.createFromCaptionStyle(it.userStyle) } ?: CaptionStyleCompat.DEFAULT
    setStyle(tv.with(style.colour, style.edge))
    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * (style.size.scale ?: captions?.fontScale ?: 1f))
    setBottomPaddingFraction(style.position.bottomFraction)
    setApplyEmbeddedFontSizes(false)
}

/**
 * Hands cues on to [output], with lines that WebVTT puts on the last line by default left without a place, so the
 * bottom padding from [applyStyle] moves them. Lines with a place of their own keep it.
 */
@OptIn(UnstableApi::class)
internal class MovableLines(
    private val output: (CueGroup) -> Unit,
) : TextOutput {
    override fun onCues(cueGroup: CueGroup) = output(CueGroup(cueGroup.cues.map { it.movable() }, cueGroup.presentationTimeUs))
}

@OptIn(UnstableApi::class)
private fun Cue.movable() =
    if (lineType == Cue.LINE_TYPE_NUMBER && line == -1f) buildUpon().setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET).build() else this

/** This caption style with [colour] text and [edge], keeping what says TV. */
@OptIn(UnstableApi::class)
internal fun CaptionStyleCompat.with(
    colour: SubtitleColour,
    edge: SubtitleEdge,
): CaptionStyleCompat {
    val text = colour.argb ?: foregroundColor
    return when (edge) {
        SubtitleEdge.TV -> {
            CaptionStyleCompat(text, backgroundColor, windowColor, edgeType, edgeColor, typeface)
        }

        SubtitleEdge.OUTLINE -> {
            CaptionStyleCompat(text, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, typeface)
        }

        SubtitleEdge.SHADOW -> {
            CaptionStyleCompat(text, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, Color.BLACK, typeface)
        }

        SubtitleEdge.BACKGROUND -> {
            CaptionStyleCompat(text, BACKGROUND, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE, Color.TRANSPARENT, typeface)
        }
    }
}
