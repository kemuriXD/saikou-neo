package ani.saikou.neo.extension.api

/**
 * The episode the app's catalogue picked. Finding it on the source's own site
 * is up to the extension.
 */
public data class Episode(
    val anime: Anime,
    /** As AniList numbers it. 0 is a prologue. */
    val number: Int,
) {
    init {
        require(number >= 0) { "Episode number can't be negative: $number" }
    }
}

/** What the catalogue knows about the anime, from AniList and AniZip. */
public data class Anime(
    val ids: AnimeIds,
    val titles: AnimeTitles,
    val season: Season?,
    val year: Int?,
)

/** The anime's ID on each site that tracks it. Only AniList's is always known. */
public data class AnimeIds(
    val anilist: Int,
    val mal: Int?,
    val anidb: Int?,
    val kitsu: Int?,
)

public data class AnimeTitles(
    val romaji: String,
    val english: String?,
    val native: String?,
    val synonyms: List<String>,
)

/** The season the anime started airing in. */
public enum class Season { Winter, Spring, Summer, Fall }
