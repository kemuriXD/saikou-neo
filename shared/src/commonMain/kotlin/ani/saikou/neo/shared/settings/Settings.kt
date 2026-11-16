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
)

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
    /** These settings with [format] turned [on] or off. Turning off the last format left on changes nothing. */
    public fun withFormat(
        format: ShowFormat,
        on: Boolean,
    ): ScheduleSettings {
        val changed = if (on) formats + format else formats - format
        return if (changed.isEmpty()) this else copy(formats = changed)
    }

    public companion object {
        /** An episode shorter than this is a short episode. */
        public const val SHORT_EPISODE_MINUTES: Int = 15
    }
}

/** Which servers to show for an episode, by whether they're subtitled or dubbed. HardSub servers count as Sub. */
public enum class SubOrDub { SUB, DUB, BOTH }
