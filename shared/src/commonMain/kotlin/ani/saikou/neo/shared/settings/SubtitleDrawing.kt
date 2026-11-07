package ani.saikou.neo.shared.settings

// The share of the video's height that text takes at 100%, and the gap below the lines: both as Media3 had them.
private const val TEXT_FRACTION = 0.0533f
private const val BOTTOM_PERCENT = 8
private const val RAISED_PERCENT = 20
private const val TOP_PERCENT = 8
private const val MAX_PERCENT = 90
private const val PERCENT = 100f

/** Where lines sit: [fraction] of the video's height from its top edge or from its bottom one. */
public data class Placement(
    val fromTop: Boolean,
    val fraction: Float,
)

/** Everything the player needs to draw subtitles for one show. */
public data class SubtitleDrawing(
    val style: LookStyle,
    /** Draw from the TV's caption style when the TV shares one, and from [style] when it doesn't. */
    val followsTv: Boolean,
    val sizePercent: Int,
    val startFromTvTextSize: Boolean,
    val placement: Placement,
    val styled: StyledSubtitles,
    val mySizeOnStyled: Boolean,
    val hdrPercent: Int,
) {
    /** How tall text is, as a share of the video's height, with the TV's caption text size at [tvFontScale]. */
    public fun textFraction(tvFontScale: Float): Float =
        TEXT_FRACTION * sizePercent / PERCENT * (if (startFromTvTextSize) tvFontScale else 1f)
}

/**
 * How subtitles draw for the show with AniList id [showId], in [preview] while one is focused in the player. A look
 * that's gone draws as Classic, and a preview of one draws as the saved look.
 */
public fun SubtitleSettings.drawingFor(
    showId: Int,
    preview: LookId? = null,
): SubtitleDrawing {
    val saved = pickFor(showId)
    val pick = if (preview != null && style(preview) != null) saved.copy(look = preview) else saved
    val builtIn = (pick.look as? LookId.BuiltIn)?.look
    val base =
        when (pick.position) {
            SubtitlePosition.BOTTOM -> BOTTOM_PERCENT
            SubtitlePosition.RAISED -> RAISED_PERCENT
            SubtitlePosition.TOP -> TOP_PERCENT
        }
    val fromTop = pick.position == SubtitlePosition.TOP
    // Fine-tune moves lines up: further from the bottom, or nearer the top.
    val percent = (if (fromTop) base - fineTunePercent else base + fineTunePercent).coerceIn(0, MAX_PERCENT)
    return SubtitleDrawing(
        style = style(pick.look) ?: BuiltInLook.CLASSIC.let { changedLooks[it] ?: it.style },
        followsTv = builtIn?.followsTv == true,
        sizePercent = pick.size.percent,
        startFromTvTextSize = startFromTvTextSize,
        placement = Placement(fromTop, percent / PERCENT),
        styled = styledSubtitles,
        mySizeOnStyled = mySizeOnStyled,
        hdrPercent = hdrBrightness.percent,
    )
}
