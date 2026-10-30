package ani.saikou.neo.shared.details

/** The sections of Details, in the order their tabs come. */
public enum class DetailsSection {
    OVERVIEW,
    EPISODES,
    RELATED,
    WATCH_ORDER,
    RECOMMENDATIONS,
}

/** The sections this show has something to show in. Overview is always first. */
public val DetailsState.Loaded.sections: List<DetailsSection>
    get() =
        listOfNotNull(
            DetailsSection.OVERVIEW,
            DetailsSection.EPISODES.takeIf { (episodes?.episodes?.size ?: episodeCount(details, null)) > 0 },
            DetailsSection.RELATED.takeIf { details.related.isNotEmpty() },
            // A watch order belongs to a franchise: the show and the anime AniList relates to it.
            DetailsSection.WATCH_ORDER.takeIf { details.related.isNotEmpty() },
            DetailsSection.RECOMMENDATIONS.takeIf { details.recommendations.isNotEmpty() },
        )
