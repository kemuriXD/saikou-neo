package ani.saikou.neo.shared.settings

import kotlin.math.roundToInt

// The old player drew every edge 2 dp wide. Thin keeps that.
private const val THIN_DP = 2f

// From the design's preview, where text is 26 px: a Medium outline is 6 px wide and a Thick one 9 px.
private const val MEDIUM_OUTLINE = 6f / 26
private const val THICK_OUTLINE = 9f / 26
private const val SHADOW_DROP = 0.08f
private const val SHADOW_RADIUS = 0.15f
private const val THICK_SHADOW = 1.5f
private const val MEDIUM_RELIEF = 1.5f / 26
private const val THICK_RELIEF = 2.25f / 26
private const val OPAQUE = 0xFF
private const val RGB = 0xFFFFFF
private const val ALPHA_SHIFT = 24
private const val BYTE = 0xFF
private const val PERCENT = 100

/** A blurred copy of the text, [radius] wide, moved by [dx] and [dy]. All in pixels. */
public data class Shadow(
    val radius: Float,
    val dx: Float,
    val dy: Float,
)

/**
 * A look turned into what drawing needs. Colours are ARGB with their opacity in. A null [font] means the TV's own
 * typeface; a null [boxArgb] or [bandArgb] means none.
 */
public data class SubtitlePaint(
    val textArgb: Int,
    val font: SubtitleFont?,
    val bold: Boolean,
    val edge: Edge,
    val edgeThickness: EdgeThickness,
    val edgeArgb: Int,
    val boxArgb: Int?,
    val bandArgb: Int?,
) {
    /** How wide the outline stroke is, half of it outside the letters, for text [textPx] tall at [density] px a dp. */
    public fun outlineWidth(
        textPx: Float,
        density: Float,
    ): Float = scaled(textPx, density, MEDIUM_OUTLINE, THICK_OUTLINE, THIN_DP)

    public fun shadow(
        textPx: Float,
        density: Float,
    ): Shadow {
        val thin = THIN_DP * density
        val grow =
            when (edgeThickness) {
                EdgeThickness.THIN -> return Shadow(thin, thin, thin)
                EdgeThickness.MEDIUM -> 1f
                EdgeThickness.THICK -> THICK_SHADOW
            }
        return Shadow(
            radius = (textPx * SHADOW_RADIUS * grow).coerceAtLeast(thin),
            dx = 0f,
            dy = (textPx * SHADOW_DROP * grow).coerceAtLeast(thin),
        )
    }

    /** How far the light and dark copies of Raised and Sunken text sit from the letters. */
    public fun reliefUnit(
        textPx: Float,
        density: Float,
    ): Float = scaled(textPx, density, MEDIUM_RELIEF, THICK_RELIEF, THIN_DP / 2)

    /** Light colours brought down to [percent] of full, for HDR video. Opacity stays. */
    public fun dimmed(percent: Int): SubtitlePaint =
        if (percent >= PERCENT) {
            this
        } else {
            copy(
                textArgb = textArgb.dim(percent),
                edgeArgb = edgeArgb.dim(percent),
                boxArgb = boxArgb?.dim(percent),
                bandArgb = bandArgb?.dim(percent),
            )
        }

    private fun scaled(
        textPx: Float,
        density: Float,
        medium: Float,
        thick: Float,
        thinDp: Float,
    ): Float {
        val thin = thinDp * density
        return when (edgeThickness) {
            EdgeThickness.THIN -> thin
            EdgeThickness.MEDIUM -> (textPx * medium).coerceAtLeast(thin)
            EdgeThickness.THICK -> (textPx * thick).coerceAtLeast(thin)
        }
    }
}

/** The TV's own caption style, as `CaptioningManager` gives it. A colour with no alpha is left out. */
public data class TvCaptions(
    val foreground: Int,
    val background: Int,
    val window: Int,
    val edge: Edge,
    val edgeArgb: Int,
)

public fun LookStyle.toPaint(): SubtitlePaint =
    SubtitlePaint(
        textArgb = argb(textColour.rgb, textOpacity),
        font = font,
        bold = bold,
        edge = edge,
        edgeThickness = edgeThickness,
        edgeArgb = argb(edgeColour.rgb, Opacity.P100),
        boxArgb = box?.let { argb(it.colour.rgb, it.opacity) },
        bandArgb = band?.let { argb(it.colour.rgb, it.opacity) },
    )

/** The TV's style draws thin, in the TV's typeface, with its background as a box and its window as a band. */
public fun TvCaptions.toPaint(): SubtitlePaint =
    SubtitlePaint(
        textArgb = foreground,
        font = null,
        bold = false,
        edge = edge,
        edgeThickness = EdgeThickness.THIN,
        edgeArgb = edgeArgb,
        boxArgb = background.takeIf { it.alpha > 0 },
        bandArgb = window.takeIf { it.alpha > 0 },
    )

private val Int.alpha get() = (this ushr ALPHA_SHIFT) and BYTE

private fun argb(
    rgb: Int,
    opacity: Opacity,
): Int = ((opacity.percent * OPAQUE / PERCENT.toFloat()).roundToInt() shl ALPHA_SHIFT) or (rgb and RGB)

private fun Int.dim(percent: Int): Int {
    fun channel(shift: Int) = (((this shr shift) and BYTE) * percent / PERCENT) shl shift
    return (this and (BYTE shl ALPHA_SHIFT)) or channel(16) or channel(8) or channel(0)
}
