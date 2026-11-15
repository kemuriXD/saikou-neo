package ani.saikou.neo.tv.player

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import ani.saikou.neo.shared.settings.Edge
import ani.saikou.neo.shared.settings.StyledSubtitles
import ani.saikou.neo.shared.settings.TvCaptions

/**
 * Whether a subtitle track carries its own styling, signs included. Media3 reads subtitles as the media loads, and
 * keeps the type they came in as [Format.codecs].
 */
internal fun Format?.isStyledText() = this != null && (sampleMimeType == MimeTypes.TEXT_SSA || codecs == MimeTypes.TEXT_SSA)

/** Which lines we draw in the look, and whether Media3 draws the cues instead. Never both. */
internal data class CueRoute(
    val ours: List<CharSequence>,
    val media3: Boolean,
)

/** Pictures and styled lines that keep their look go to Media3. Everything else is ours, at our place and in our look. */
internal fun routeCues(
    cues: List<Cue>,
    styledTrack: Boolean,
    styled: StyledSubtitles,
): CueRoute =
    if (cues.any { it.bitmap != null } || (styledTrack && styled == StyledSubtitles.KEEP_THEIR_LOOK)) {
        CueRoute(emptyList(), media3 = true)
    } else {
        CueRoute(cues.mapNotNull { it.text?.plain() }, media3 = false)
    }

// Italics, bold, underlines and strike-throughs are part of what's said. Colours, sizes and fonts are the look's job.
private val kept = listOf(StyleSpan::class.java, UnderlineSpan::class.java, StrikethroughSpan::class.java)

private fun CharSequence.plain(): CharSequence {
    if (this !is Spanned) return this
    val text = SpannableStringBuilder(this)
    text.getSpans(0, text.length, Any::class.java).filterNot { span -> kept.any { it.isInstance(span) } }.forEach(text::removeSpan)
    return text
}

@OptIn(UnstableApi::class)
internal fun CaptionStyleCompat.toTvCaptions() =
    TvCaptions(
        foreground = foregroundColor,
        background = backgroundColor,
        window = windowColor,
        edge =
            when (edgeType) {
                CaptionStyleCompat.EDGE_TYPE_OUTLINE -> Edge.OUTLINE
                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW -> Edge.SHADOW
                CaptionStyleCompat.EDGE_TYPE_RAISED -> Edge.RAISED
                CaptionStyleCompat.EDGE_TYPE_DEPRESSED -> Edge.SUNKEN
                else -> Edge.NONE
            },
        edgeArgb = edgeColor,
    )
