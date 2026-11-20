package ani.saikou.neo.shared.playback

import ani.saikou.neo.shared.catalogue.AniZipEpisode
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.AnimeTitle
import ani.saikou.neo.shared.catalogue.NextEpisode
import ani.saikou.neo.shared.details.EpisodeList
import ani.saikou.neo.shared.sources.InstalledSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

private val source = InstalledSource("com.example.demo", "Demo source", "1.0.0")
private val server = FoundServer("1", "Server 1", ServerEdition.SUB, 24)

private fun show(
    episodes: Int? = 12,
    airing: Int? = null,
) = AnimeDetails(
    card = AnimeCard(21, AnimeTitle("A show", null, null), null, null, null, "TV", episodes, null, null, null),
    description = null,
    status = null,
    episodeMinutes = 24,
    genres = emptyList(),
    studios = emptyList(),
    nextEpisode = airing?.let { NextEpisode(it, 0) },
)

private fun episode(number: Int) = AniZipEpisode(number, "Title $number", "What happens in $number", "still-$number", null, 24)

private fun list(
    count: Int,
    aired: Int = count,
    film: Boolean = false,
) = EpisodeList(count, countSoFar = false, film = film, episodes = (1..count).map(::episode), extras = emptyList(), aired = aired)

private fun request(
    episode: Int,
    episodes: EpisodeList? = null,
    show: AnimeDetails = show(),
) = PlayRequest(show, episode, null, listOf(PlayServer(source, server, 1)), 0, episodes = episodes)

class UpNextTest {
    @Test
    fun theNextEpisodeComesFromDetailsList() {
        assertEquals(UpNextEpisode(8, "Title 8", "What happens in 8", "still-8"), request(7, list(12)).nextEpisode())
    }

    @Test
    fun theLastEpisodeHasNoNext() {
        assertNull(request(12, list(12)).nextEpisode())
    }

    @Test
    fun theNewestAiredEpisodeHasNoNext() {
        assertNull(request(7, list(12, aired = 7)).nextEpisode())
        assertEquals(7, request(6, list(12, aired = 7)).nextEpisode()?.number)
    }

    @Test
    fun aFilmHasNoNext() {
        assertNull(request(1, list(1, film = true)).nextEpisode())
    }

    @Test
    fun withoutTheListTheTotalOrNextAiringDecides() {
        assertEquals(UpNextEpisode(8, null, null, null), request(7).nextEpisode())
        assertNull(request(12).nextEpisode())
        assertNull(request(7, show = show(episodes = 24, airing = 8)).nextEpisode())
        assertEquals(7, request(6, show = show(episodes = null, airing = 8)).nextEpisode()?.number)
        assertEquals(41, request(40, show = show(episodes = null)).nextEpisode()?.number)
    }

    @Test
    fun upNextShowsAtTheEndingOrThirtySecondsBeforeTheEnd() {
        val length = 24.minutes.inWholeMilliseconds
        assertEquals(length - 30_000, upNextAtMs(emptyList(), length))
        val ending = Segment(SegmentKind.Ending, 21.minutes.inWholeMilliseconds, 22.minutes.inWholeMilliseconds)
        assertEquals(ending.startMs, upNextAtMs(listOf(Segment(SegmentKind.Opening, 0, 90_000), ending), length))
        assertNull(upNextAtMs(emptyList(), 0))
        assertNull(upNextAtMs(emptyList(), 30_000))
        val early = Segment(SegmentKind.Ending, 60_000, 90_000)
        assertEquals(length - 30_000, upNextAtMs(listOf(early), length))
    }
}
