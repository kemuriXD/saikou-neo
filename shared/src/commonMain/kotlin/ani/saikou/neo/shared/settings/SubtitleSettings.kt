package ani.saikou.neo.shared.settings

/** How big subtitles are, as a [percent] of the usual size. */
public enum class SubtitleSize(
    public val percent: Int,
) {
    P60(60),
    P75(75),
    P90(90),
    P100(100),
    P115(115),
    P130(130),
    P150(150),
    P175(175),
    P200(200),
}

public enum class SubtitlePosition { BOTTOM, RAISED, TOP }

/** What happens to subtitles that carry their own styling, signs included. */
public enum class StyledSubtitles { KEEP_THEIR_LOOK, USE_MY_LOOK }

/** Where a change made in the player goes. */
public enum class WhileWatching { EVERY_SHOW, THIS_SHOW_ONLY }

/** How bright white text is on HDR video, as a [percent] of full. */
public enum class HdrBrightness(
    public val percent: Int,
) {
    P100(100),
    P80(80),
    P60(60),
}

/** The choices the player offers while watching. */
public data class SubtitlePick(
    val look: LookId,
    val size: SubtitleSize,
    val position: SubtitlePosition,
)

/** A show with its own look, size and position. The [title] is kept so the list can name it. */
public data class ShowSubtitles(
    val title: String,
    val pick: SubtitlePick,
)

/** Every subtitle setting. A new install starts with these defaults. */
public data class SubtitleSettings(
    val look: LookId = CLASSIC,
    val size: SubtitleSize = SubtitleSize.P100,
    /** Size scales from the TV's caption text size rather than from ours. */
    val startFromTvTextSize: Boolean = false,
    val position: SubtitlePosition = SubtitlePosition.BOTTOM,
    /** Moves lines up (or down, below zero) by this percent of the screen height. */
    val fineTunePercent: Int = 0,
    val styledSubtitles: StyledSubtitles = StyledSubtitles.KEEP_THEIR_LOOK,
    /** Our size applies to styled subtitles too. */
    val mySizeOnStyled: Boolean = true,
    val changesWhileWatching: WhileWatching = WhileWatching.EVERY_SHOW,
    val hdrBrightness: HdrBrightness = HdrBrightness.P80,
    /** Built-in looks that were changed. One that isn't here looks as it was made. */
    val changedLooks: Map<BuiltInLook, LookStyle> = emptyMap(),
    /** Your own looks, by number, 1 to [MAX_MY_LOOKS]. */
    val myLooks: Map<Int, LookStyle> = emptyMap(),
    /** Shows with their own look, by AniList id. */
    val shows: Map<Int, ShowSubtitles> = emptyMap(),
) {
    /** Every look there is, built-in ones first. */
    val looks: List<LookId>
        get() = BuiltInLook.entries.map { LookId.BuiltIn(it) } + myLooks.keys.sorted().map { LookId.Mine(it) }

    val canAddLook: Boolean get() = myLooks.size < MAX_MY_LOOKS

    /** How [id] looks, or null for one of your own that was deleted. */
    public fun style(id: LookId): LookStyle? =
        when (id) {
            is LookId.BuiltIn -> changedLooks[id.look] ?: id.look.style
            is LookId.Mine -> myLooks[id.number]
        }

    public fun withLook(id: LookId): SubtitleSettings = if (style(id) == null) this else copy(look = id)

    public fun withFineTune(percent: Int): SubtitleSettings = copy(fineTunePercent = percent.coerceIn(FINE_TUNE_RANGE))

    /** Changes how [id] looks. A built-in look changed back to how it was made counts as not changed. */
    public fun editLook(
        id: LookId,
        style: LookStyle,
    ): SubtitleSettings =
        when (id) {
            is LookId.BuiltIn -> {
                copy(changedLooks = if (style == id.look.style) changedLooks - id.look else changedLooks + (id.look to style))
            }

            is LookId.Mine -> {
                if (id.number in myLooks) copy(myLooks = myLooks + (id.number to style)) else this
            }
        }

    public fun resetLook(look: BuiltInLook): SubtitleSettings = copy(changedLooks = changedLooks - look)

    /** Adds a look of your own, copied from the one in use, and uses it. Does nothing once there are [MAX_MY_LOOKS]. */
    public fun addLook(): SubtitleSettings {
        if (!canAddLook) return this
        val number = (1..MAX_MY_LOOKS).first { it !in myLooks }
        val copied = style(look) ?: CLASSIC.look.style
        return copy(look = LookId.Mine(number), myLooks = myLooks + (number to copied))
    }

    /** Deletes a look of your own. Wherever it was in use, Classic takes its place. */
    public fun deleteLook(number: Int): SubtitleSettings {
        val gone = LookId.Mine(number)
        return copy(
            look = if (look == gone) CLASSIC else look,
            myLooks = myLooks - number,
            shows = shows.mapValues { (_, show) -> if (show.pick.look == gone) show.copy(pick = show.pick.copy(look = CLASSIC)) else show },
        )
    }

    /** The look, size and position for the show with AniList id [showId]. */
    public fun pickFor(showId: Int): SubtitlePick = shows[showId]?.pick ?: SubtitlePick(look, size, position)

    /**
     * Saves a [change] made in the player while watching [showId]. With [WhileWatching.THIS_SHOW_ONLY] it stays with
     * that show. Otherwise it goes to every show, and the show's own look is cleared so it can't hide the change.
     */
    public fun changeWhileWatching(
        showId: Int,
        title: String,
        change: (SubtitlePick) -> SubtitlePick,
    ): SubtitleSettings {
        val changed = change(pickFor(showId))
        return when (changesWhileWatching) {
            WhileWatching.THIS_SHOW_ONLY -> {
                copy(shows = shows + (showId to ShowSubtitles(title, changed)))
            }

            WhileWatching.EVERY_SHOW -> {
                copy(look = changed.look, size = changed.size, position = changed.position, shows = shows - showId)
            }
        }
    }

    /** The show goes back to the look every show uses. */
    public fun clearShow(showId: Int): SubtitleSettings = copy(shows = shows - showId)

    public companion object {
        public const val MAX_MY_LOOKS: Int = 3
        public val FINE_TUNE_RANGE: IntRange = -5..20
        private val CLASSIC = LookId.BuiltIn(BuiltInLook.CLASSIC)
    }
}
