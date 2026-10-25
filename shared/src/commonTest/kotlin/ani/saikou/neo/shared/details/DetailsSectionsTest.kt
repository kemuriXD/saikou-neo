package ani.saikou.neo.shared.details

import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeTitle
import kotlin.test.Test
import kotlin.test.assertEquals

class DetailsSectionsTest {
    @Test
    fun overviewComesFirst() {
        assertEquals(DetailsSection.OVERVIEW, DetailsState.Loaded(details()).sections.first())
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
