package ani.saikou.neo.shared.catalogue

/** An anime's names. AniList fills in whichever it knows. */
public data class AnimeTitle(
    val romaji: String?,
    val english: String?,
    val native: String?,
)

/** What a catalogue row needs to show one anime. */
public data class AnimeCard(
    val id: Int,
    val title: AnimeTitle,
    val coverImageUrl: String?,
    /** The cover's main colour as `#rrggbb`, for placeholders. */
    val coverColor: String?,
    /** Wide key art, about 1900 × 400. Many anime have none. */
    val bannerImageUrl: String?,
    /** AniList's format name, such as `TV`, `MOVIE` or `ONA`. */
    val format: String?,
    val episodes: Int?,
    /** Average score out of 100. */
    val averageScore: Int?,
    val season: AnimeSeason?,
    val seasonYear: Int?,
)

/** The banner, or the cover where there's none. */
public val AnimeCard.art: String? get() = bannerImageUrl ?: coverImageUrl

/** One page of results, and whether another page follows. */
public data class AnimePage(
    val items: List<AnimeCard>,
    val hasNextPage: Boolean,
)

/** Everything the details screen shows beyond the card. */
public data class AnimeDetails(
    val card: AnimeCard,
    /** AniList's synopsis. It can still contain `<br>` and `<i>` tags. */
    val description: String?,
    /** AniList's status name, such as `RELEASING` or `FINISHED`. */
    val status: String?,
    val episodeMinutes: Int?,
    val genres: List<String>,
    val studios: List<String>,
    val nextEpisode: NextEpisode?,
    /** Other anime in the show's franchise, in [Relation]'s order. */
    val related: List<RelatedAnime> = emptyList(),
)

/** How an anime is related to the one on Details. */
public enum class Relation {
    /** The series it's part of, such as a film's TV series. */
    PARENT,
    PREQUEL,
    SEQUEL,
    SIDE_STORY,
    SPIN_OFF,
}

public data class RelatedAnime(
    val card: AnimeCard,
    val relation: Relation,
)

public data class NextEpisode(
    val number: Int,
    val airingAtEpochSeconds: Long,
)

/** AniList's seasons: winter is January to March, and so on by quarter. */
public enum class AnimeSeason {
    WINTER,
    SPRING,
    SUMMER,
    FALL,
    ;

    public companion object {
        /** The season a month (1 to 12) falls in. */
        public fun forMonth(month: Int): AnimeSeason {
            require(month in 1..12) { "Month must be 1 to 12, was $month" }
            return entries[(month - 1) / 3]
        }
    }
}
