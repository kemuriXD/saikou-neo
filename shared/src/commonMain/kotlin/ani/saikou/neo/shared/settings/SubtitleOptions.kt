package ani.saikou.neo.shared.settings

// Strengths from faint to solid, as the box and band offer them.
private val Strengths = listOf(Opacity.P25, Opacity.P50, Opacity.P75, Opacity.P100)

/** No box, then each box colour at each strength. */
public val BOX_CHOICES: List<Box?> = listOf(null) + BoxColour.entries.flatMap { colour -> Strengths.map { Box(colour, it) } }

/** No band, then each band colour at each strength. */
public val BAND_CHOICES: List<Band?> = listOf(null) + BandColour.entries.flatMap { colour -> Strengths.map { Band(colour, it) } }

/** The subtitle settings that apply to every look, each changed by stepping Left or Right. */
public enum class SubtitleOption { SIZE, START_FROM_TV, POSITION, FINE_TUNE, STYLED, MY_SIZE_ON_STYLED, WHILE_WATCHING, HDR }

/** The parts of a look the editor changes. */
public enum class LookPart { TEXT_COLOUR, OPACITY, FONT, BOLD, EDGE, THICKNESS, EDGE_COLOUR, BOX, BAND }

/** The choice [by] steps after [current] in this list, stopping at the ends. */
private fun <T> List<T>.step(
    current: T,
    by: Int,
): T = this[(indexOf(current) + by).coerceIn(indices)]

/** [option] stepped [by] places. A switch flips whichever way it steps. */
public fun SubtitleSettings.stepped(
    option: SubtitleOption,
    by: Int,
): SubtitleSettings =
    when (option) {
        SubtitleOption.SIZE -> copy(size = size.stepped(by))

        SubtitleOption.START_FROM_TV -> copy(startFromTvTextSize = !startFromTvTextSize)

        SubtitleOption.POSITION -> copy(position = position.stepped(by))

        SubtitleOption.FINE_TUNE -> withFineTune(fineTunePercent + by)

        SubtitleOption.STYLED -> copy(styledSubtitles = StyledSubtitles.entries.step(styledSubtitles, by))

        SubtitleOption.MY_SIZE_ON_STYLED -> copy(mySizeOnStyled = !mySizeOnStyled)

        SubtitleOption.WHILE_WATCHING -> copy(changesWhileWatching = WhileWatching.entries.step(changesWhileWatching, by))

        // Brightest last, so Right makes it brighter.
        SubtitleOption.HDR -> copy(hdrBrightness = HdrBrightness.entries.reversed().step(hdrBrightness, by))
    }

/** An edge's thickness and colour only mean something when there's an edge. */
public fun LookStyle.canChange(part: LookPart): Boolean = edge != Edge.NONE || (part != LookPart.THICKNESS && part != LookPart.EDGE_COLOUR)

/** [part] stepped [by] places, or this look as it is when the part [can't change][canChange]. Bold flips. */
public fun LookStyle.stepped(
    part: LookPart,
    by: Int,
): LookStyle {
    if (!canChange(part)) return this
    return when (part) {
        LookPart.TEXT_COLOUR -> copy(textColour = TextColour.entries.step(textColour, by))
        LookPart.OPACITY -> copy(textOpacity = Opacity.entries.step(textOpacity, by))
        LookPart.FONT -> copy(font = SubtitleFont.entries.step(font, by))
        LookPart.BOLD -> copy(bold = !bold)
        LookPart.EDGE -> copy(edge = Edge.entries.step(edge, by))
        LookPart.THICKNESS -> copy(edgeThickness = EdgeThickness.entries.step(edgeThickness, by))
        LookPart.EDGE_COLOUR -> copy(edgeColour = EdgeColour.entries.step(edgeColour, by))
        LookPart.BOX -> copy(box = BOX_CHOICES.step(box, by))
        LookPart.BAND -> copy(band = BAND_CHOICES.step(band, by))
    }
}

// AniList ids start at 1, so no show has this one.
private const val NO_SHOW = 0

/** How subtitles draw for every show without a look of its own, in [look] when one is given, for previews. */
public fun SubtitleSettings.drawing(look: LookId? = null): SubtitleDrawing = copy(shows = emptyMap()).drawingFor(NO_SHOW, look)
