package ani.saikou.neo.shared.settings

/** How much of a colour shows through. */
public enum class Opacity(
    public val percent: Int,
) {
    P100(100),
    P75(75),
    P50(50),
    P25(25),
}

/** The colour of subtitle text, as [rgb]. */
public enum class TextColour(
    public val rgb: Int,
) {
    WHITE(0xFFFFFF),
    LIGHT_GREY(0xC9C9C9),
    YELLOW(0xFFE14D),
    CYAN(0x7FE7FF),
    GREEN(0x9BF29B),
    PINK(0xFF9EC4),
    RED(0xFF6B6B),
    BLACK(0x000000),
}

/** The typeface of subtitle text. Atkinson Hyperlegible is the app's own. */
public enum class SubtitleFont { ATKINSON_HYPERLEGIBLE, SANS_SERIF, SERIF, MONOSPACE, CASUAL }

/** What sets the edge of each letter apart from the video. */
public enum class Edge { NONE, OUTLINE, SHADOW, RAISED, SUNKEN }

public enum class EdgeThickness { THIN, MEDIUM, THICK }

public enum class EdgeColour(
    public val rgb: Int,
) {
    BLACK(0x000000),
    DARK_GREY(0x404040),
    WHITE(0xFFFFFF),
}

public enum class BoxColour(
    public val rgb: Int,
) {
    BLACK(0x000000),
    GREY(0x808080),
    WHITE(0xFFFFFF),
}

public enum class BandColour(
    public val rgb: Int,
) {
    BLACK(0x000000),
    GREY(0x808080),
}

/** A box drawn behind the text of each line. */
public data class Box(
    val colour: BoxColour,
    val opacity: Opacity,
)

/** A band drawn across the whole width of the screen behind the lines. */
public data class Band(
    val colour: BandColour,
    val opacity: Opacity,
)

/** Everything about how subtitle text is drawn. Size and position aren't here: they apply to every look. */
public data class LookStyle(
    val textColour: TextColour = TextColour.WHITE,
    val textOpacity: Opacity = Opacity.P100,
    val font: SubtitleFont = SubtitleFont.ATKINSON_HYPERLEGIBLE,
    val bold: Boolean = true,
    val edge: Edge = Edge.OUTLINE,
    val edgeThickness: EdgeThickness = EdgeThickness.MEDIUM,
    val edgeColour: EdgeColour = EdgeColour.BLACK,
    /** No box when null. */
    val box: Box? = null,
    /** No band when null. */
    val band: Band? = null,
)

/** The looks every install has. They can be changed and reset, but not deleted. */
public enum class BuiltInLook(
    /** The look before any change. */
    public val style: LookStyle,
    /** Drawn from the TV's own caption style when the TV shares one, and from [style] when it doesn't. */
    public val followsTv: Boolean = false,
) {
    CLASSIC(LookStyle(edge = Edge.OUTLINE)),
    SOFT_SHADOW(LookStyle(edge = Edge.SHADOW)),
    BOXED(LookStyle(edge = Edge.NONE, box = Box(BoxColour.BLACK, Opacity.P75))),
    YELLOW(LookStyle(textColour = TextColour.YELLOW, edge = Edge.OUTLINE)),
    HIGH_CONTRAST(LookStyle(textColour = TextColour.YELLOW, edge = Edge.NONE, box = Box(BoxColour.BLACK, Opacity.P100))),
    MATCH_TV(LookStyle(edge = Edge.NONE, box = Box(BoxColour.BLACK, Opacity.P50)), followsTv = true),
}

/** Which look: a built-in one, or one of your own by its [Mine.number], which the screens show as "My look N". */
public sealed interface LookId {
    public data class BuiltIn(
        val look: BuiltInLook,
    ) : LookId

    public data class Mine(
        val number: Int,
    ) : LookId
}
