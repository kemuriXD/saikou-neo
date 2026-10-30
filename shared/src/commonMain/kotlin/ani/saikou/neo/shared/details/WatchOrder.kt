package ani.saikou.neo.shared.details

import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.Relation

/** One anime in a watch order, and how it's related to the show on Details, or null for that show. */
public data class WatchOrderStep(
    val card: AnimeCard,
    val relation: Relation?,
)

/**
 * The show and the anime AniList relates to it, in the order they came out. One without a date isn't out yet, so it
 * comes last.
 */
public fun AnimeDetails.watchOrder(): List<WatchOrderStep> =
    (listOf(WatchOrderStep(card, null)) + related.map { WatchOrderStep(it.card, it.relation) })
        .distinctBy { it.card.id }
        .sortedWith(compareBy(nullsLast()) { step: WatchOrderStep -> step.card.seasonYear }.thenBy(nullsLast()) { it.card.season })
