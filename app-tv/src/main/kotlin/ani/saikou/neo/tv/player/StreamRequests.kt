package ani.saikou.neo.tv.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import ani.saikou.neo.extension.api.MediaLoader
import ani.saikou.neo.extension.api.MediaRequest
import ani.saikou.neo.extension.api.MediaResponse
import ani.saikou.neo.extension.api.Stream
import okhttp3.Call
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Where the player gets every playlist, segment, key and subtitle of this stream: over HTTP through the app's shared
 * client [callFactory], so the cookie jar, pinned User-Agent and Cloudflare clearance carry into playback, or from the
 * source's own [Stream.mediaLoader] when it has one.
 */
@OptIn(UnstableApi::class)
fun Stream.dataSourceFactory(callFactory: Call.Factory): DataSource.Factory {
    val loader = mediaLoader ?: return OkHttpDataSource.Factory(callFactory).setDefaultRequestProperties(headers)
    return DataSource.Factory { LoaderDataSource(loader, headers) }
}

/**
 * Reads media through a source's [loader], which runs on the player's loading thread. Anything the source throws
 * becomes an [IOException], which the player reports as an error, so a broken source can't take the app down.
 */
@OptIn(UnstableApi::class)
private class LoaderDataSource(
    private val loader: MediaLoader,
    private val headers: Map<String, String>,
) : BaseDataSource(true) {
    private var spec: DataSpec? = null
    private var response: MediaResponse? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        spec = dataSpec
        val length = dataSpec.length.takeUnless { it == C.LENGTH_UNSET.toLong() }
        val request = MediaRequest(dataSpec.uri.toString(), headers + dataSpec.httpRequestHeaders, dataSpec.position, length)
        val opened = fromSource { loader.open(request) }
        response = opened
        transferStarted(dataSpec)
        return length ?: opened.length ?: C.LENGTH_UNSET.toLong()
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        val opened = response ?: throw IOException("Not open")
        val count = fromSource { opened.read(buffer, offset, length) }
        if (count == MediaResponse.END) return C.RESULT_END_OF_INPUT
        if (count !in 1..length) throw IOException("The source read $count bytes when asked for up to $length")
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = spec?.uri

    override fun close() {
        val opened = response ?: return
        response = null
        try {
            fromSource { opened.close() }
        } finally {
            transferEnded()
        }
    }
}

private inline fun <T> fromSource(call: () -> T): T =
    try {
        call()
    } catch (e: IOException) {
        throw e
    } catch (e: InterruptedException) {
        // The player stops a load by interrupting its thread, so the interrupt has to survive.
        Thread.currentThread().interrupt()
        throw InterruptedIOException(e.message)
    } catch (e: Throwable) {
        throw IOException("The source failed", e)
    }
