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
    get() = listOf(DetailsSection.OVERVIEW)
