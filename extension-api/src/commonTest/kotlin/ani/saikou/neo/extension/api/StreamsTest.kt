package ani.saikou.neo.extension.api

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamsTest {
    private val server = Server(id = "sub-1", name = "Main", edition = Edition.Sub, runtime = null)

    @Test
    fun aStreamListsEveryQualityWithItsOwnLink() =
        runTest {
            val stream = FakeExtension().stream(server)

            assertEquals(listOf(1080, 720, 480), stream.qualities.map { it.height })
            assertEquals(
                3,
                stream.qualities
                    .map { it.url }
                    .toSet()
                    .size,
            )
        }

    @Test
    fun audioAndSubtitlesAreSeparateTracks() =
        runTest {
            val stream = FakeExtension().stream(server)

            assertEquals(listOf("ja", "en"), stream.audioTracks.map { it.language })
            assertEquals(listOf(SubtitleFormat.Ass), stream.subtitles.map { it.format })
        }

    @Test
    fun aStreamCarriesTheHeadersEveryRequestNeeds() =
        runTest {
            val stream = FakeExtension().stream(server)

            assertEquals("https://example.com/", stream.headers["Referer"])
            assertNull(stream.mediaLoader)
        }

    @Test
    fun aStreamNeedsAtLeastOneQuality() {
        assertFailsWith<IllegalArgumentException> { Stream(qualities = emptyList()) }
    }

    @Test
    fun aQualityHasARealHeightSoThereIsNoAuto() {
        assertFailsWith<IllegalArgumentException> {
            Quality(height = 0, url = "https://example.com/auto.m3u8", format = StreamFormat.Hls)
        }
    }

    @Test
    fun mediaLinksMustBeWebLinksSoASourceCantReachLocalFiles() {
        val local = "file:///data/data/ani.saikou.neo/shared_prefs/tokens.xml"

        assertFailsWith<IllegalArgumentException> { Quality(720, local, StreamFormat.Hls) }
        assertFailsWith<IllegalArgumentException> { AudioTrack(local, "ja", "Japanese") }
        assertFailsWith<IllegalArgumentException> { SubtitleTrack(local, "en", "English", SubtitleFormat.Vtt) }
        assertFailsWith<IllegalArgumentException> { Quality(720, "content://media/1", StreamFormat.Hls) }
        Quality(720, "HTTP://example.com/720.m3u8", StreamFormat.Hls)
    }

    @Test
    fun printingAStreamDoesntShowHeaderValues() {
        val stream =
            Stream(
                qualities = listOf(Quality(720, "https://example.com/720.m3u8", StreamFormat.Hls)),
                headers = mapOf("Cookie" to "session=secret"),
            )
        val request = MediaRequest("https://example.com/1.ts", mapOf("Cookie" to "session=secret"), 0, null)

        assertFalse("secret" in stream.toString())
        assertFalse("secret" in request.toString())
        assertTrue("Cookie" in stream.toString())
    }

    @Test
    fun aSourceCanServeTheMediaItself() {
        val bytes = "segment-bytes".encodeToByteArray()
        val loader = MediaLoader { request -> MemoryResponse(bytes.copyOfRange(request.position.toInt(), bytes.size)) }

        val response = loader.open(MediaRequest(url = "https://example.com/1.ts", headers = emptyMap(), position = 8, length = null))
        val buffer = ByteArray(16)
        val read = response.read(buffer, 0, buffer.size)
        response.close()

        assertContentEquals("bytes".encodeToByteArray(), buffer.copyOf(read))
        assertEquals(MediaResponse.END, response.read(buffer, 0, buffer.size))
    }

    private class MemoryResponse(
        private val bytes: ByteArray,
    ) : MediaResponse {
        private var next = 0
        override val length: Long = bytes.size.toLong()

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            if (next == bytes.size) return MediaResponse.END
            val count = minOf(length, bytes.size - next)
            bytes.copyInto(buffer, offset, next, next + count)
            next += count
            return count
        }

        override fun close() {}
    }
}
