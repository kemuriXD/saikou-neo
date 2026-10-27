package ani.saikou.neo.shared.settings

/** What the viewer chose in Settings. A new install starts with these defaults. */
public data class Settings(
    /** Rows and search show anime before their first episode is out. */
    val showUnairedAnime: Boolean = false,
    /** Episode lists include the episodes that haven't aired yet. */
    val showUpcomingEpisodes: Boolean = true,
    /** A lighter look with fewer effects, for TVs too slow for the full one. */
    val slowTv: Boolean = false,
)
