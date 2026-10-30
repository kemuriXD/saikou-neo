package ani.saikou.neo.shared.details

import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeSeason
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.catalogue.RelatedAnime
import ani.saikou.neo.shared.catalogue.Relation
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchOrderTest {
    @Test
    fun theShowAndItsRelationsComeInReleaseOrder() {
        val details =
            details(
                card(1, 2025, AnimeSeason.WINTER),
                RelatedAnime(card(2, 2026, AnimeSeason.SPRING), Relation.SEQUEL),
                RelatedAnime(card(3, 2025, AnimeSeason.SUMMER), Relation.SIDE_STORY),
                RelatedAnime(card(4, 2023, null), Relation.PREQUEL),
            )

        assertEquals(listOf(4, 1, 3, 2), details.watchOrder().map { it.card.id })
    }

    @Test
    fun oneNotYetDatedComesLast() {
        val details =
            details(
                card(1, 2025, AnimeSeason.WINTER),
                RelatedAnime(card(2, null, null), Relation.SEQUEL),
                RelatedAnime(card(3, 2025, AnimeSeason.FALL), Relation.SEQUEL),
            )

        assertEquals(listOf(1, 3, 2), details.watchOrder().map { it.card.id })
    }

    @Test
    fun eachStepSaysHowItIsRelatedToThisShow() {
        val details =
            details(
                card(1, 2025, AnimeSeason.WINTER),
                RelatedAnime(card(2, 2026, AnimeSeason.SPRING), Relation.SEQUEL),
                RelatedAnime(card(3, 2023, null), Relation.PREQUEL),
            )

        assertEquals(listOf(Relation.PREQUEL, null, Relation.SEQUEL), details.watchOrder().map { it.relation })
    }

    private fun details(
        card: AnimeCard,
        vararg related: RelatedAnime,
    ) = AnimeDetails(
        card = card,
        description = null,
        status = null,
        episodeMinutes = null,
        genres = emptyList(),
        studios = emptyList(),
        nextEpisode = null,
        related = related.toList(),
    )

    private fun card(
        id: Int,
        year: Int?,
        season: AnimeSeason?,
    ) = AnimeCard(id, AnimeTitle("Show $id", null, null), null, null, null, "TV", 12, null, season, year)
}
