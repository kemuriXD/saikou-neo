package ani.saikou.neo.shared.details

import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AniZipEpisode
import ani.saikou.neo.shared.catalogue.AniZipEpisodes
import ani.saikou.neo.shared.catalogue.AniZipExtra
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.catalogue.NextEpisode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpisodeListTest {
    private val rebirth = AniZipEpisode(1, "Rebirth", "A notebook falls.", "https://img.test/1.jpg", AirDate(2006, 10, 4), 23)
    private val aniZip = AniZipEpisodes(listOf(rebirth), listOf(AniZipExtra("Special", 25)))

    @Test
    fun theCountComesFromAniListAndAniZipFillsInWhatItKnows() {
        val list = episodeList(details(episodes = 37), aniZip)

        assertEquals(37, list.count)
        assertFalse(list.countSoFar)
        assertEquals(rebirth, list.episodes.first())
        assertEquals(37, list.episodes.last().number)
        assertNull(list.episodes.last().title)
        assertFalse(list.detailsMissing)
    }

    @Test
    fun aShowStillAiringCountsTheEpisodesOutSoFar() {
        val list = episodeList(details(episodes = null, next = 5), aniZip)

        assertEquals(4, list.count)
        assertTrue(list.countSoFar)
    }

    @Test
    fun withoutAnyCountFromAniListAniZipsEpisodesCount() {
        assertEquals(1, episodeList(details(episodes = null), aniZip).count)
    }

    @Test
    fun withoutAniZipEpisodesAreJustNumbers() {
        val list = episodeList(details(episodes = 201), null)

        assertEquals(201, list.episodes.size)
        assertTrue(list.episodes.all { it.title == null })
        assertTrue(list.detailsMissing)
    }

    @Test
    fun episodesWithNoTitlesOrDatesAreMissingTheirDetails() {
        val blank = AniZipEpisodes(listOf(AniZipEpisode(1, null, null, null, null, 24)), emptyList())

        assertTrue(episodeList(details(episodes = 12), blank).detailsMissing)
    }

    @Test
    fun aFilmShowsItsExtras() {
        val list = episodeList(details(episodes = 1, format = "MOVIE"), aniZip)

        assertTrue(list.film)
        assertEquals(listOf(AniZipExtra("Special", 25)), list.extras)
    }

    // A series' specials are mostly interviews and recaps, so only films list them.
    @Test
    fun aSeriesLeavesItsSpecialsOut() {
        assertEquals(emptyList(), episodeList(details(episodes = 37), aniZip).extras)
    }

    @Test
    fun longSeriesSplitIntoRangesOfFifty() {
        assertEquals(listOf(1..50, 51..100, 101..150, 151..201), episodeRanges(201))
        assertEquals(emptyList(), episodeRanges(50))
        // Enough left over for a range of their own.
        assertEquals(listOf(1..50, 51..75), episodeRanges(75))
    }

    @Test
    fun episodesHaveATabWhenThereAreAny() {
        assertTrue(DetailsSection.EPISODES in DetailsState.Loaded(details(episodes = 12)).sections)
        assertFalse(DetailsSection.EPISODES in DetailsState.Loaded(details(episodes = null)).sections)
    }

    private fun details(
        episodes: Int?,
        next: Int? = null,
        format: String = "TV",
    ) = AnimeDetails(
        card = AnimeCard(1535, AnimeTitle("Death Note", null, null), null, null, null, format, episodes, null, null, null),
        description = null,
        status = null,
        episodeMinutes = null,
        genres = emptyList(),
        studios = emptyList(),
        nextEpisode = next?.let { NextEpisode(it, 0) },
    )
}
