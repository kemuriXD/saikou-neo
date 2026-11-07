package ani.saikou.neo.shared.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// Every subtitle key starts with this, so Reset subtitle settings can clear them all and nothing else.
private const val PREFIX = "subtitle_"
private const val CHANGED_PREFIX = "${PREFIX}builtin_"
private const val MINE_PREFIX = "${PREFIX}mine_"
private const val SHOW_PREFIX = "${PREFIX}show_"
private const val MINE_ID = "mine:"
private const val SEPARATOR = ";"
private const val PART = ":"
private const val NONE = "-"

private val Look = stringPreferencesKey("${PREFIX}look")
private val Size = stringPreferencesKey("${PREFIX}size")
private val TvTextSize = booleanPreferencesKey("${PREFIX}tv_text_size")
private val Position = stringPreferencesKey("${PREFIX}position")
private val FineTune = intPreferencesKey("${PREFIX}fine_tune")
private val Styled = stringPreferencesKey("${PREFIX}styled")
private val MySizeOnStyled = booleanPreferencesKey("${PREFIX}my_size_on_styled")
private val WhileWatchingKey = stringPreferencesKey("${PREFIX}while_watching")
private val Hdr = stringPreferencesKey("${PREFIX}hdr_brightness")

/** The subtitle settings saved here. A value that's missing or can't be read is its default. */
internal fun Preferences.toSubtitleSettings(): SubtitleSettings {
    val defaults = SubtitleSettings()
    val saved = asMap().mapKeys { it.key.name }
    val myLooks = saved.withPrefix(MINE_PREFIX).mapNotNull { (n, value) -> n.toIntOrNull()?.let { it to value.decodeStyle() } }.toMap()
    val known = defaults.copy(myLooks = myLooks)
    return known
        .copy(
            size = enumOr(this[Size], defaults.size),
            startFromTvTextSize = this[TvTextSize] ?: defaults.startFromTvTextSize,
            position = enumOr(this[Position], defaults.position),
            styledSubtitles = enumOr(this[Styled], defaults.styledSubtitles),
            mySizeOnStyled = this[MySizeOnStyled] ?: defaults.mySizeOnStyled,
            changesWhileWatching = enumOr(this[WhileWatchingKey], defaults.changesWhileWatching),
            hdrBrightness = enumOr(this[Hdr], defaults.hdrBrightness),
            changedLooks =
                saved
                    .withPrefix(CHANGED_PREFIX)
                    .mapNotNull { (name, value) -> BuiltInLook.entries.firstOrNull { it.name == name }?.let { it to value.decodeStyle() } }
                    .toMap(),
            shows =
                saved
                    .withPrefix(SHOW_PREFIX)
                    .mapNotNull { (id, value) -> id.toIntOrNull()?.let { show -> value.decodeShow(known)?.let { show to it } } }
                    .toMap(),
        ).withLook(this[Look]?.decodeLookId(known) ?: defaults.look)
        .withFineTune(this[FineTune] ?: defaults.fineTunePercent)
}

/** Replaces every saved subtitle value with [settings]. */
internal fun MutablePreferences.putSubtitleSettings(settings: SubtitleSettings) {
    asMap().keys.filter { it.name.startsWith(PREFIX) }.forEach { remove(it) }
    this[Look] = settings.look.encode()
    this[Size] = settings.size.name
    this[TvTextSize] = settings.startFromTvTextSize
    this[Position] = settings.position.name
    this[FineTune] = settings.fineTunePercent
    this[Styled] = settings.styledSubtitles.name
    this[MySizeOnStyled] = settings.mySizeOnStyled
    this[WhileWatchingKey] = settings.changesWhileWatching.name
    this[Hdr] = settings.hdrBrightness.name
    settings.changedLooks.forEach { (look, style) -> this[stringPreferencesKey("$CHANGED_PREFIX${look.name}")] = style.encode() }
    settings.myLooks.forEach { (number, style) -> this[stringPreferencesKey("$MINE_PREFIX$number")] = style.encode() }
    settings.shows.forEach { (id, show) ->
        // The title goes last, as it may hold the separator.
        this[stringPreferencesKey("$SHOW_PREFIX$id")] =
            listOf(show.pick.look.encode(), show.pick.size.name, show.pick.position.name, show.title).joinToString(SEPARATOR)
    }
}

private fun Map<String, Any>.withPrefix(prefix: String): Map<String, String> =
    filterKeys { it.startsWith(prefix) }
        .mapKeys { it.key.removePrefix(prefix) }
        .mapNotNull { (k, v) ->
            (v as? String)?.let { k to it }
        }.toMap()

private inline fun <reified T : Enum<T>> enumOr(
    name: String?,
    default: T,
): T = enumValues<T>().firstOrNull { it.name == name } ?: default

private fun LookId.encode(): String =
    when (this) {
        is LookId.BuiltIn -> look.name
        is LookId.Mine -> "$MINE_ID$number"
    }

// A look of your own that's gone reads as null.
private fun String.decodeLookId(settings: SubtitleSettings): LookId? {
    val id =
        removePrefix(MINE_ID).toIntOrNull()?.takeIf { startsWith(MINE_ID) }?.let { LookId.Mine(it) }
            ?: BuiltInLook.entries.firstOrNull { it.name == this }?.let { LookId.BuiltIn(it) }
    return id?.takeIf { settings.style(it) != null }
}

private fun String.decodeShow(settings: SubtitleSettings): ShowSubtitles? {
    val fields = split(SEPARATOR, limit = 4)
    if (fields.size != 4) return null
    val look = fields[0].decodeLookId(settings) ?: SubtitleSettings().look
    return ShowSubtitles(fields[3], SubtitlePick(look, enumOr(fields[1], SubtitleSize.P100), enumOr(fields[2], SubtitlePosition.BOTTOM)))
}

private fun LookStyle.encode(): String =
    listOf(
        textColour.name,
        textOpacity.name,
        font.name,
        bold.toString(),
        edge.name,
        edgeThickness.name,
        edgeColour.name,
        box?.let { "${it.colour.name}$PART${it.opacity.name}" } ?: NONE,
        band?.let { "${it.colour.name}$PART${it.opacity.name}" } ?: NONE,
    ).joinToString(SEPARATOR)

private fun String.decodeStyle(): LookStyle {
    val fields = split(SEPARATOR)
    val defaults = LookStyle()

    fun field(index: Int) = fields.getOrNull(index)

    fun backdrop(index: Int) = field(index)?.split(PART)?.takeIf { it.size == 2 }
    return LookStyle(
        textColour = enumOr(field(0), defaults.textColour),
        textOpacity = enumOr(field(1), defaults.textOpacity),
        font = enumOr(field(2), defaults.font),
        bold = field(3)?.toBooleanStrictOrNull() ?: defaults.bold,
        edge = enumOr(field(4), defaults.edge),
        edgeThickness = enumOr(field(5), defaults.edgeThickness),
        edgeColour = enumOr(field(6), defaults.edgeColour),
        box = backdrop(7)?.let { (colour, opacity) -> Box(enumOr(colour, BoxColour.BLACK), enumOr(opacity, Opacity.P75)) },
        band = backdrop(8)?.let { (colour, opacity) -> Band(enumOr(colour, BandColour.BLACK), enumOr(opacity, Opacity.P75)) },
    )
}
