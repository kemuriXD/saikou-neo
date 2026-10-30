package ani.saikou.neo.shared.details

import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AniZipEpisode
import ani.saikou.neo.shared.catalogue.AniZipEpisodes
import ani.saikou.neo.shared.catalogue.AniZipExtra
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.catalogue.NextEpisode
import ani.saikou.neo.shared.home.WatchProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class EpisodeListTest {
    private val rebirth = AniZipEpisode(1, "Rebirth", "A notebook falls.", "https://img.test/1.jpg", AirDate(2006, 10, 4), 23)
    private val aniZip = AniZipEpisodes(listOf(rebirth), listOf(AniZipExtra("Special", 25)))
    private val today = AirDate(2026, 10, 26)

    @Test
    fun theCountComesFromAniListAndAniZipFillsInWhatItKnows() {
        val list = episodeList(details(episodes = 37), aniZip, today)

        assertEquals(37, list.count)
        assertFalse(list.countSoFar)
        assertEquals(rebirth, list.episodes.first())
        assertEquals(37, list.episodes.last().number)
        assertNull(list.episodes.last().title)
        assertFalse(list.detailsMissing)
    }

    @Test
    fun aShowStillAiringCountsTheEpisodesOutSoFar() {
        val list = episodeList(details(episodes = null, next = 5), aniZip, today)

        assertEquals(4, list.count)
        assertTrue(list.countSoFar)
    }

    @Test
    fun withoutAnyCountFromAniListAniZipsEpisodesCount() {
        assertEquals(1, episodeList(details(episodes = null), aniZip, today).count)
    }

    @Test
    fun withoutAniZipEpisodesAreJustNumbers() {
        val list = episodeList(details(episodes = 201), null, today)

        assertEquals(201, list.episodes.size)
        assertTrue(list.episodes.all { it.title == null })
        assertTrue(list.detailsMissing)
    }

    @Test
    fun episodesWithNoTitlesOrDatesAreMissingTheirDetails() {
        val blank = AniZipEpisodes(listOf(AniZipEpisode(1, null, null, null, null, 24)), emptyList())

        assertTrue(episodeList(details(episodes = 12), blank, today).detailsMissing)
    }

    @Test
    fun aFilmShowsItsExtras() {
        val list = episodeList(details(episodes = 1, format = "MOVIE"), aniZip, today)

        assertTrue(list.film)
        assertEquals(listOf(AniZipExtra("Special", 25)), list.extras)
    }

    // A series' specials are mostly interviews and recaps, so only films list them.
    @Test
    fun aSeriesLeavesItsSpecialsOut() {
        assertEquals(emptyList(), episodeList(details(episodes = 37), aniZip, today).extras)
    }

    @Test
    fun episodesAfterTodayHaveNotAired() {
        val list = episodeList(details(episodes = 12), airing(AirDate(2026, 10, 12), AirDate(2026, 10, 19), AirDate(2026, 11, 2)), today)

        assertEquals(12, list.count)
        assertEquals(11, list.aired)
    }

    @Test
    fun anEpisodeOutTodayHasAired() {
        assertEquals(2, episodeList(details(episodes = 2), airing(AirDate(2026, 10, 19), today), today).aired)
    }

    @Test
    fun episodesWithoutAnAirDateCountAsAired() {
        assertEquals(37, episodeList(details(episodes = 37), aniZip, today).aired)
        assertEquals(201, episodeList(details(episodes = 201), null, today).aired)
    }

    // AniZip may only know the first few episodes, so AniList's next episode marks where the unaired ones start.
    @Test
    fun aniListsNextEpisodeHasNotAiredNorHaveTheOnesAfterIt() {
        val list = episodeList(details(episodes = 12, next = 5), airing(AirDate(2026, 10, 5)), today)

        assertEquals(12, list.count)
        assertEquals(4, list.aired)
    }

    // AniZip dated episode 5 a day after AniList said it was out.
    @Test
    fun aniListsNextEpisodeOutweighsAniZipsDates() {
        val list = episodeList(details(episodes = null, next = 6), airing(*Array(5) { AirDate(2026, 10, 27) }), today)

        assertEquals(5, list.count)
        assertEquals(5, list.aired)
    }

    @Test
    fun aShowWithNothingLeftToAirHasAiredEveryEpisode() {
        val list = episodeList(details(episodes = 2), airing(AirDate(2006, 10, 4), AirDate(2006, 10, 11)), today)

        assertEquals(list.count, list.aired)
    }

    @Test
    fun upcomingEpisodesCanBeLeftOut() {
        val list = episodeList(details(episodes = 12, next = 5), airing(AirDate(2026, 10, 5)), today, showUpcoming = false)

        assertEquals(listOf(1, 2, 3, 4), list.episodes.map { it.number })
        // The line over the list still says how many there are and how many are out.
        assertEquals(12, list.count)
        assertEquals(4, list.aired)
    }

    @Test
    fun aShowWithNothingOutHasNoEpisodesTabWhenUpcomingOnesAreLeftOut() {
        val show = details(episodes = 12, next = 1)
        val list = episodeList(show, null, today, showUpcoming = false)

        assertEquals(emptyList(), list.episodes)
        assertFalse(DetailsSection.EPISODES in DetailsState.Loaded(show, episodes = list).sections)
    }

    @Test
    fun aShowOfUpTo100EpisodesSplitsIntoRangesOfTwelve() {
        assertEquals(emptyList(), episodeRanges(12))
        assertEquals(listOf(1..12, 13..22), episodeRanges(22))
        // A few left over join the last range.
        assertEquals(listOf(1..12, 13..26), episodeRanges(26))
        assertEquals(85..100, episodeRanges(100).last())
    }

    @Test
    fun longSeriesSplitIntoRangesOfFifty() {
        assertEquals(listOf(1..50, 51..100, 101..150, 151..201), episodeRanges(201))
        // Enough left over for a range of their own.
        assertEquals(listOf(1..50, 51..101), episodeRanges(101))
        assertEquals(listOf(1..50, 51..100, 101..125), episodeRanges(125))
    }

    @Test
    fun episodesBeforeTheOneInProgressAreWatched() {
        val progress = WatchProgress(7, 12.minutes, 24.minutes, null)

        assertEquals(EpisodeMark.Watched, progress.markFor(6))
        assertEquals(EpisodeMark.InProgress(progress), progress.markFor(7))
        assertNull(progress.markFor(8))
    }

    @Test
    fun theNextToWatchIsTheOneInProgressOrElseTheFirst() {
        val list = episodeList(details(episodes = 12), null, today)

        assertEquals(7, list.nextToWatch(WatchProgress(7, 12.minutes, 24.minutes, null)))
        assertEquals(1, list.nextToWatch(null))
        // One that isn't listed, such as an upcoming one left out, starts at the first.
        assertEquals(1, list.nextToWatch(WatchProgress(20, 12.minutes, 24.minutes, null)))
    }

    @Test
    fun episodesHaveATabWhenThereAreAny() {
        assertTrue(DetailsSection.EPISODES in DetailsState.Loaded(details(episodes = 12)).sections)
        assertFalse(DetailsSection.EPISODES in DetailsState.Loaded(details(episodes = null)).sections)
    }

    private fun airing(vararg dates: AirDate) =
        AniZipEpisodes(dates.mapIndexed { index, date -> AniZipEpisode(index + 1, null, null, null, date, 24) }, emptyList())

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
