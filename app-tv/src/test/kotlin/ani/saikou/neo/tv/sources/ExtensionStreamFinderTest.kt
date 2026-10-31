package ani.saikou.neo.tv.sources

import ani.saikou.neo.extension.api.Edition
import ani.saikou.neo.extension.api.Episode
import ani.saikou.neo.extension.api.Quality
import ani.saikou.neo.extension.api.Server
import ani.saikou.neo.extension.api.SourceException
import ani.saikou.neo.extension.api.SourceExtension
import ani.saikou.neo.extension.api.Stream
import ani.saikou.neo.extension.api.StreamFormat
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.playback.FoundServer
import ani.saikou.neo.shared.playback.FoundStream
import ani.saikou.neo.shared.playback.ServerEdition
import ani.saikou.neo.shared.playback.SourceFailure
import ani.saikou.neo.shared.sources.InstalledSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

private val demo = InstalledSource("com.example.demo", "Demo source", "1.0.0")
private val dub = FoundServer("dub", "Server 2", ServerEdition.DUB, 24)

private fun stream(vararg heights: Int) = Stream(heights.map { Quality(it, "https://example.com/$it.m3u8", StreamFormat.Hls) })

/** Gives [given] for any server, remembering what it was asked, or throws [failure]. */
private class StreamSource(
    private val given: Stream = stream(720),
    private val failure: Throwable? = null,
) : SourceExtension {
    var asked: Server? = null
    var expired: Stream? = null

    override suspend fun servers(episode: Episode): List<Server> = emptyList()

    override suspend fun stream(server: Server): Stream {
        asked = server
        failure?.let { throw it }
        return given
    }

    override suspend fun refresh(
        server: Server,
        expired: Stream,
    ): Stream {
        this.expired = expired
        return stream(server)
    }
}

class ExtensionStreamFinderTest {
    private fun finder(source: SourceExtension?) = ExtensionStreamFinder { if (it == demo) source else null }

    private fun failure(thrown: Throwable): FailReason =
        assertThrows(SourceFailure::class.java) {
            runBlocking { finder(StreamSource(failure = thrown)).stream(demo, dub) }
        }.reason

    @Test
    fun theStreamComesWithItsBestHeight() {
        val given = stream(480, 1080, 720)

        val found = runBlocking { finder(StreamSource(given)).stream(demo, dub) }

        assertEquals(FoundStream(given, 1080), found)
    }

    @Test
    fun theSourceGetsBackTheServerItListed() {
        val source = StreamSource()

        runBlocking { finder(source).stream(demo, dub) }

        assertEquals(Server("dub", "Server 2", Edition.Dub, null), source.asked)
    }

    @Test
    fun aRefreshHandsTheSourceTheExpiredStream() {
        val source = StreamSource(stream(1080))
        val expired = stream(720)

        val found = runBlocking { finder(source).refresh(demo, dub, expired) }

        assertEquals(expired, source.expired)
        assertEquals(1080, found.height)
    }

    @Test
    fun eachTypedErrorKeepsItsReason() {
        assertEquals(FailReason.NotFound, failure(SourceException.NotFound()))
        assertEquals(FailReason.Blocked, failure(SourceException.Blocked()))
        assertEquals(FailReason.SourceDown, failure(SourceException.SourceDown()))
        assertEquals(FailReason.LinkExpired, failure(SourceException.LinkExpired()))
        assertEquals(FailReason.RateLimited(30), failure(SourceException.RateLimited(30.seconds)))
    }

    @Test
    fun anyOtherExceptionMeansTheSiteIsDown() {
        assertEquals(FailReason.SourceDown, failure(IllegalStateException("Broken")))
    }

    @Test
    fun anErrorMeansTheSourceWontStart() {
        assertEquals(FailReason.WontStart, failure(NoSuchMethodError("Built for another app")))
    }

    @Test
    fun aSourceThatWontLoadWontStart() {
        val thrown = assertThrows(SourceFailure::class.java) { runBlocking { finder(null).stream(demo, dub) } }

        assertEquals(FailReason.WontStart, thrown.reason)
    }
}
