package ani.saikou.neo.demo

import ani.saikou.neo.extension.api.Anime
import ani.saikou.neo.extension.api.AnimeIds
import ani.saikou.neo.extension.api.AnimeTitles
import ani.saikou.neo.extension.api.Edition
import ani.saikou.neo.extension.api.Episode
import ani.saikou.neo.extension.api.Server
import ani.saikou.neo.extension.api.SourceException
import ani.saikou.neo.extension.api.Stream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DemoSourceTest {
    private val source = DemoSource()

    private fun episode(number: Int) =
        Episode(
            anime =
                Anime(
                    ids = AnimeIds(anilist = 1, mal = null, anidb = null, kitsu = null),
                    titles = AnimeTitles(romaji = "Any anime", english = null, native = null, synonyms = emptyList()),
                    season = null,
                    year = null,
                ),
            number = number,
        )

    private suspend fun playable(): List<Pair<Server, Stream>> =
        source.servers(episode(1)).mapNotNull { server ->
            try {
                server to source.stream(server)
            } catch (_: SourceException) {
                null
            }
        }

    @Test
    fun everyEpisodeHasASubAndADubServer() =
        runTest {
            listOf(0, 1, 12, 500).forEach { number ->
                val editions = source.servers(episode(number)).map { it.edition }
                assertTrue("Sub for episode $number", Edition.Sub in editions)
                assertTrue("Dub for episode $number", Edition.Dub in editions)
            }
        }

    @Test
    fun theSubAndDubServersPlayInSeveralQualities() =
        runTest {
            val streams = playable()
            assertEquals(setOf(Edition.Sub, Edition.Dub), streams.map { it.first.edition }.toSet())
            streams.forEach { (server, stream) ->
                val heights = stream.qualities.map { it.height }
                assertTrue("${server.name} has several qualities", heights.size >= 3)
                assertEquals("${server.name} lists each height once, highest first", heights.distinct().sortedDescending(), heights)
                assertNotNull("${server.name} has a runtime", server.runtime)
            }
        }

    @Test
    fun oneServerHasASubtitleAndASecondAudioTrack() =
        runTest {
            val withTracks = playable().map { it.second }.filter { it.subtitles.isNotEmpty() && it.audioTracks.size >= 2 }
            assertEquals(1, withTracks.size)
        }

    @Test
    fun everyMediaLinkUsesHttps() =
        runTest {
            playable().forEach { (_, stream) ->
                val links = stream.qualities.map { it.url } + stream.audioTracks.map { it.url } + stream.subtitles.map { it.url }
                links.forEach { assertTrue(it, it.startsWith("https://")) }
            }
        }

    @Test
    fun aServerFailsWithEachTypedError() =
        runTest {
            val failures =
                source.servers(episode(1)).mapNotNull { server ->
                    try {
                        source.stream(server)
                        null
                    } catch (error: SourceException) {
                        error::class
                    }
                }
            assertEquals(
                setOf(
                    SourceException.Blocked::class,
                    SourceException.RateLimited::class,
                    SourceException.SourceDown::class,
                    SourceException.LinkExpired::class,
                    SourceException.NotFound::class,
                ),
                failures.toSet(),
            )
            assertEquals("one server per error", 5, failures.size)
        }

    @Test
    fun rateLimitedSaysWhenToTryAgain() =
        runTest {
            val server = source.servers(episode(1)).single { it.id == "fails-rate-limited" }
            try {
                source.stream(server)
                fail("expected RateLimited")
            } catch (error: SourceException.RateLimited) {
                assertNotNull(error.retryAfter)
            }
        }

    @Test
    fun anExpiredLinkComesBackWhenRefreshed() =
        runTest {
            val server = source.servers(episode(1)).single { it.id == "fails-link-expired" }
            val sub = playable().first { it.first.edition == Edition.Sub }.second
            assertEquals(sub, source.refresh(server, expired = sub))
        }

    @Test
    fun anUnknownServerIsNotFound() =
        runTest {
            try {
                source.stream(Server(id = "gone", name = "Gone", edition = Edition.Sub, runtime = null))
                fail("expected NotFound")
            } catch (_: SourceException.NotFound) {
            }
        }
}
