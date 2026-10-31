package ani.saikou.neo.extension.api

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes

class ServersTest {
    private val anime =
        Anime(
            ids = AnimeIds(anilist = 21, mal = 21, anidb = 69, kitsu = 12),
            titles = AnimeTitles(romaji = "Example Romaji", english = "Example", native = null, synonyms = listOf("Ex")),
            season = Season.Fall,
            year = 1999,
        )

    @Test
    fun anExtensionSeesTheAnimeAndEpisodeItWasAskedFor() =
        runTest {
            val extension = FakeExtension()

            extension.servers(Episode(anime, number = 3))

            val asked = extension.asked.single()
            assertEquals(21, asked.anime.ids.anilist)
            assertEquals("Example", asked.anime.titles.english)
            assertEquals(Season.Fall, asked.anime.season)
            assertEquals(1999, asked.anime.year)
            assertEquals(3, asked.number)
        }

    @Test
    fun eachServerHasItsOwnEditionAndRuntime() =
        runTest {
            val servers = FakeExtension().servers(Episode(anime, number = 1))

            assertEquals(listOf(Edition.Sub, Edition.Dub, Edition.HardSub), servers.map { it.edition })
            assertEquals(24.minutes, servers.first().runtime)
            assertEquals(null, servers.last().runtime)
        }

    @Test
    fun anEpisodeNumberCantBeNegative() {
        assertFailsWith<IllegalArgumentException> { Episode(anime, number = -1) }
    }

    @Test
    fun episodeZeroIsAllowedForPrologues() {
        assertEquals(0, Episode(anime, number = 0).number)
    }
}
