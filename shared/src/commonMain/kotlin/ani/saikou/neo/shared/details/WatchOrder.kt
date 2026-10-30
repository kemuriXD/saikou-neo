package ani.saikou.neo.shared.details

import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails

/**
 * The show and the anime AniList relates to it, in the order they came out. One without a date isn't out yet, so it
 * comes last.
 */
public fun AnimeDetails.watchOrder(): List<AnimeCard> =
    (listOf(card) + related.map { it.card })
        .distinctBy { it.id }
        .sortedWith(compareBy(nullsLast()) { card: AnimeCard -> card.seasonYear }.thenBy(nullsLast()) { it.season })
