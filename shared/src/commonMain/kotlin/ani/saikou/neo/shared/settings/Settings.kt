package ani.saikou.neo.shared.settings

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
)

/** Which servers to show for an episode, by whether they're subtitled or dubbed. HardSub servers count as Sub. */
public enum class SubOrDub { SUB, DUB, BOTH }
