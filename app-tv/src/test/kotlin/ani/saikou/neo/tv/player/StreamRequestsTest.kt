package ani.saikou.neo.tv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import ani.saikou.neo.extension.api.MediaLoader
import ani.saikou.neo.extension.api.MediaRequest
import ani.saikou.neo.extension.api.MediaResponse
import ani.saikou.neo.extension.api.Quality
import ani.saikou.neo.extension.api.Stream
import ani.saikou.neo.extension.api.StreamFormat
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

private val headers = mapOf("Referer" to "https://example.com/", "Cookie" to "session=1")

private fun stream(loader: MediaLoader? = null) =
    Stream(listOf(Quality(1080, "https://example.com/1080.m3u8", StreamFormat.Hls)), headers = headers, mediaLoader = loader)

/** Serves [bytes] from memory, remembering what it was asked for. */
private class FakeResponse(
    private val bytes: ByteArray,
) : MediaResponse {
    var read = 0
    var closed = false

    override val length: Long = bytes.size.toLong()

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (read == bytes.size) return MediaResponse.END
        val count = minOf(length, bytes.size - read)
        bytes.copyInto(buffer, offset, read, read + count)
        read += count
        return count
    }

    override fun close() {
        closed = true
    }
}

@RunWith(RobolectricTestRunner::class)
class StreamRequestsTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val received = CopyOnWriteArrayList<Map<String, String?>>()

    private fun serve() {
        server.createContext("/") { exchange ->
            received += headers.keys.associateWith { exchange.requestHeaders.getFirst(it) }
            val body = "#EXTM3U".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun open(
        stream: Stream,
        url: String,
        position: Long = 0,
        length: Long = C.LENGTH_UNSET.toLong(),
    ) = stream.dataSourceFactory().createDataSource().also {
        it.open(
            DataSpec
                .Builder()
                .setUri(Uri.parse(url))
                .setPosition(position)
                .setLength(length)
                .build(),
        )
    }

    @Test
    fun theHeadersGoWithEveryRequest() {
        serve()
        val base = "http://127.0.0.1:${server.address.port}"

        listOf("$base/playlist.m3u8", "$base/subtitles.srt").forEach { open(stream(), it).close() }

        assertEquals(listOf(headers, headers), received.toList())
    }

    @Test
    fun theLoaderGetsTheUrlHeadersAndRange() {
        val asked = mutableListOf<MediaRequest>()
        val source =
            open(
                stream({
                    asked += it
                    FakeResponse(ByteArray(4))
                }),
                "https://example.com/a.ts",
                position = 100,
                length = 50,
            )

        source.close()

        assertEquals(listOf(MediaRequest("https://example.com/a.ts", headers, 100, 50)), asked)
    }

    @Test
    fun theLoaderReadsToTheEnd() {
        val response = FakeResponse(byteArrayOf(1, 2, 3))
        val source = open(stream({ response }), "https://example.com/a.ts")
        val buffer = ByteArray(8)

        assertEquals(3, source.read(buffer, 0, 8))
        assertEquals(C.RESULT_END_OF_INPUT, source.read(buffer, 0, 8))
    }

    @Test
    fun anErrorFromTheLoaderBecomesAnIoException() {
        val thrown = StackOverflowError("A broken source")
        val source = stream({ throw thrown }).dataSourceFactory().createDataSource()

        val error = assertThrows(IOException::class.java) { source.open(DataSpec(Uri.parse("https://example.com/a.ts"))) }

        assertSame(thrown, error.cause)
    }

    @Test
    fun anErrorWhileReadingBecomesAnIoException() {
        val response =
            object : MediaResponse {
                override val length: Long? = null

                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int = throw IllegalStateException("Broken")

                override fun close() = Unit
            }
        val source = open(stream({ response }), "https://example.com/a.ts")

        assertThrows(IOException::class.java) { source.read(ByteArray(8), 0, 8) }
    }

    @Test
    fun anInterruptedLoaderStaysInterrupted() {
        val source = stream({ throw InterruptedException("Stopped") }).dataSourceFactory().createDataSource()

        try {
            assertThrows(InterruptedIOException::class.java) { source.open(DataSpec(Uri.parse("https://example.com/a.ts"))) }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun closingClosesTheResponse() {
        val response = FakeResponse(ByteArray(4))
        val source = open(stream({ response }), "https://example.com/a.ts")

        source.close()

        assertTrue(response.closed)
    }
}
