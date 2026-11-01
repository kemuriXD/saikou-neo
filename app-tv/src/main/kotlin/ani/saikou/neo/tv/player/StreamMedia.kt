package ani.saikou.neo.tv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import ani.saikou.neo.extension.api.SourceException
import ani.saikou.neo.extension.api.Stream
import ani.saikou.neo.extension.api.StreamFormat
import ani.saikou.neo.extension.api.SubtitleFormat
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.playback.PlayerError
import ani.saikou.neo.shared.playback.ServerEdition
import ani.saikou.neo.tv.sources.reason
import okhttp3.Call

/** What plays: the [video], with its subtitles, and the [audio] served apart from it, if any. */
class StreamParts(
    val video: MediaItem,
    val audio: MediaItem?,
)

/**
 * The best quality, with the first separate audio track and every subtitle. On a Sub server the first subtitle is on.
 * Choosing others is the player's side panel (#242).
 */
fun Stream.parts(edition: ServerEdition): StreamParts {
    val best = qualities.maxBy { it.height }
    val type = best.format.mimeType()
    val subtitles =
        subtitles.mapIndexed { index, track ->
            MediaItem.SubtitleConfiguration
                .Builder(track.url.toUri())
                .setMimeType(track.format.mimeType())
                .setLanguage(track.language)
                .setLabel(track.label)
                .setSelectionFlags(if (index == 0 && edition == ServerEdition.SUB) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
    val video =
        MediaItem
            .Builder()
            .setUri(best.url)
            .setMimeType(type)
            .setSubtitleConfigurations(subtitles)
            .build()
    // Audio playlists come in the video's format, whatever their links look like.
    val audio =
        audioTracks.firstOrNull()?.let {
            MediaItem
                .Builder()
                .setUri(it.url)
                .setMimeType(type)
                .build()
        }
    return StreamParts(video, audio)
}

/** The media for [parts] of this stream, with every request going through the shared client [callFactory]. */
@OptIn(UnstableApi::class)
fun Stream.mediaSource(
    context: Context,
    edition: ServerEdition,
    callFactory: Call.Factory,
): MediaSource {
    val factory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory(callFactory))
    val parts = parts(edition)
    val video = factory.createMediaSource(parts.video)
    val audio = parts.audio ?: return video
    return MergingMediaSource(video, factory.createMediaSource(audio))
}

/**
 * Why the player stopped. A link that's forbidden or gone has expired, and a source's own errors keep their reason. An
 * HTTP status the server sent comes along to show.
 */
@OptIn(UnstableApi::class)
fun PlaybackException.toPlayerError(): PlayerError {
    val causes = generateSequence<Throwable>(cause) { it.cause }.take(MAX_CAUSES).toList()
    causes.firstNotNullOfOrNull { it as? SourceException }?.let { error ->
        return if (error is SourceException.LinkExpired) PlayerError.Expired else PlayerError.Failed(error.reason())
    }
    val code = causes.firstNotNullOfOrNull { it as? HttpDataSource.InvalidResponseCodeException }?.responseCode
    return when {
        code == HTTP_FORBIDDEN || code == HTTP_GONE -> PlayerError.Expired
        code == HTTP_TOO_MANY -> PlayerError.Failed(FailReason.RateLimited(null), code)
        code != null -> PlayerError.Failed(FailReason.SourceDown, code)
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlayerError.Failed(FailReason.NoAnswer)
        else -> PlayerError.Failed(FailReason.SourceDown)
    }
}

// A cause chain can loop, so only the first few are looked at.
private const val MAX_CAUSES = 10
private const val HTTP_FORBIDDEN = 403
private const val HTTP_GONE = 410
private const val HTTP_TOO_MANY = 429

private fun StreamFormat.mimeType() =
    when (this) {
        StreamFormat.Hls -> MimeTypes.APPLICATION_M3U8
        StreamFormat.Dash -> MimeTypes.APPLICATION_MPD
        StreamFormat.Progressive -> null
    }

private fun SubtitleFormat.mimeType() =
    when (this) {
        SubtitleFormat.Vtt -> MimeTypes.TEXT_VTT
        SubtitleFormat.Srt -> MimeTypes.APPLICATION_SUBRIP
        SubtitleFormat.Ass -> MimeTypes.TEXT_SSA
    }
