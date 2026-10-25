package ani.saikou.neo.shared.details

import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.catalogue.RelatedAnime
import ani.saikou.neo.shared.catalogue.Relation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetailsSectionsTest {
    @Test
    fun overviewComesFirst() {
        assertEquals(DetailsSection.OVERVIEW, DetailsState.Loaded(details()).sections.first())
    }

    @Test
    fun relatedHasATabOnlyWhenThereIsRelatedAnime() {
        val sequel = RelatedAnime(details().card.copy(id = 2), Relation.SEQUEL)

        assertTrue(DetailsSection.RELATED in DetailsState.Loaded(details().copy(related = listOf(sequel))).sections)
        assertFalse(DetailsSection.RELATED in DetailsState.Loaded(details()).sections)
    }

    // A watch order belongs to a franchise, so a show on its own has no tab for one.
    @Test
    fun watchOrderHasATabOnlyForAFranchise() {
        val sequel = RelatedAnime(details().card.copy(id = 2), Relation.SEQUEL)

        assertTrue(DetailsSection.WATCH_ORDER in DetailsState.Loaded(details().copy(related = listOf(sequel))).sections)
        assertFalse(DetailsSection.WATCH_ORDER in DetailsState.Loaded(details()).sections)
    }

    private fun details() =
        AnimeDetails(
            card = AnimeCard(1, AnimeTitle("Show", null, null), null, null, null, "TV", 12, null, null, null),
            description = null,
            status = null,
            episodeMinutes = null,
            genres = emptyList(),
            studios = emptyList(),
            nextEpisode = null,
        )
}
