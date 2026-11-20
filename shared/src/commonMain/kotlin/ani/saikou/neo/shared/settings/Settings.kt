package ani.saikou.neo.shared.settings

import ani.saikou.neo.shared.catalogue.ShowFormat

/** What the viewer chose in Settings. A new install starts with these defaults. */
public data class Settings(
    /** Rows and search show anime before their first episode is out. */
    val showUnairedAnime: Boolean = false,
    /** Episode lists include the episodes that haven't aired yet. */
    val showUpcomingEpisodes: Boolean = true,
    /** A lighter look with fewer effects, for TVs too slow for the full one. */
    val slowTv: Boolean = false,
    /** Which servers the server list shows: Sub ones, Dub ones or both. */
    val subOrDub: SubOrDub = SubOrDub.BOTH,
    /** Which shows Schedule leaves out. */
    val schedule: ScheduleSettings = ScheduleSettings(),
    /** What watching on this TV does to the signed-in user's AniList progress (ADR 0053). */
    val updateAniList: UpdateAniList = UpdateAniList.ALWAYS,
    /** Nothing goes to AniList from this TV while it's on, whatever [updateAniList] says. */
    val privateMode: Boolean = false,
    /** How many episodes play on with no button pressed before Up next asks if you're still watching (ADR 0055). */
    val stillWatching: StillWatching = StillWatching.AFTER_2,
)

/** Up next asks after this many [episodes] in a row with no button pressed, or never. */
public enum class StillWatching(
    public val episodes: Int?,
) {
    AFTER_2(2),
    AFTER_3(3),
    AFTER_5(5),
    NEVER(null),
}

/** Whether finishing an episode here moves AniList: always, once the show's been answered yes, or never. */
public enum class UpdateAniList { ALWAYS, ASK, OFF }

/** Which shows Schedule lists. Filtering uses the week already loaded, so a change asks for nothing new. */
public data class ScheduleSettings(
    /** Episodes with only a raw release this week. Sub episodes always show. */
    val rawEpisodes: Boolean = true,
    /** Dub rows, and the dub time on an episode's row. */
    val dubs: Boolean = true,
    /** The formats shown. At least one stays on, and a show with no format listed always shows. */
    val formats: Set<ShowFormat> = ShowFormat.entries.toSet(),
    /** Chinese animation. */
    val chineseAnimation: Boolean = false,
    /** Episodes under [SHORT_EPISODE_MINUTES] minutes. */
    val shortEpisodes: Boolean = true,
) {
    /**
     * These settings with [format] turned [on] or off. Turning off the last format left on changes nothing. TV shorts
     * are all under [SHORT_EPISODE_MINUTES], so ticking TV short turns short episodes on too.
     */
    public fun withFormat(
        format: ShowFormat,
        on: Boolean,
    ): ScheduleSettings {
        val changed = if (on) formats + format else formats - format
        if (changed.isEmpty()) return this
        return copy(formats = changed, shortEpisodes = shortEpisodes || (on && format == ShowFormat.TV_SHORT))
    }

    /** Donghua are nearly all raw only, so turning them on turns raw episodes on too. */
    public fun withChineseAnimation(on: Boolean): ScheduleSettings = copy(chineseAnimation = on, rawEpisodes = rawEpisodes || on)

    /** Without raw episodes hardly any donghua are left, so turning raw off turns donghua off too. */
    public fun withRawEpisodes(on: Boolean): ScheduleSettings = copy(rawEpisodes = on, chineseAnimation = chineseAnimation && on)

    /** Turning short episodes off unticks TV short, unless it's the only format ticked. */
    public fun withShortEpisodes(on: Boolean): ScheduleSettings =
        if (on) copy(shortEpisodes = true) else copy(shortEpisodes = false).withFormat(ShowFormat.TV_SHORT, false)

    public companion object {
        /** An episode shorter than this is a short episode. */
        public const val SHORT_EPISODE_MINUTES: Int = 15
    }
}

/** Which servers to show for an episode, by whether they're subtitled or dubbed. HardSub servers count as Sub. */
public enum class SubOrDub { SUB, DUB, BOTH }
