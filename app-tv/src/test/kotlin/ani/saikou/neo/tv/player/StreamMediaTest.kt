package ani.saikou.neo.tv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import ani.saikou.neo.extension.api.AudioTrack
import ani.saikou.neo.extension.api.Quality
import ani.saikou.neo.extension.api.SourceException
import ani.saikou.neo.extension.api.Stream
import ani.saikou.neo.extension.api.StreamFormat
import ani.saikou.neo.extension.api.SubtitleFormat
import ani.saikou.neo.extension.api.SubtitleTrack
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.playback.PlayerError
import ani.saikou.neo.shared.playback.ServerEdition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

private val qualities =
    listOf(
        Quality(480, "https://example.com/480.m3u8", StreamFormat.Hls),
        Quality(1080, "https://example.com/1080.m3u8", StreamFormat.Hls),
        Quality(720, "https://example.com/720.m3u8", StreamFormat.Hls),
    )

private val subtitles =
    listOf(
        SubtitleTrack("https://example.com/en.srt", "en", "English", SubtitleFormat.Srt),
        SubtitleTrack("https://example.com/es.vtt", "es", "Spanish", SubtitleFormat.Vtt),
        SubtitleTrack("https://example.com/fr.ass", "fr", "French", SubtitleFormat.Ass),
    )

private val audio =
    listOf(
        AudioTrack("https://example.com/audio-ja", "ja", "Japanese"),
        AudioTrack("https://example.com/audio-en", "en", "English"),
    )

private fun httpError(code: Int) =
    PlaybackException(
        "Bad status",
        HttpDataSource.InvalidResponseCodeException(
            code,
            null,
            null,
            emptyMap(),
            DataSpec(Uri.parse("https://example.com/a.ts")),
            ByteArray(0),
        ),
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    )

private fun sourceError(error: Throwable) =
    PlaybackException("The source failed", IOException("The source failed", error), PlaybackException.ERROR_CODE_IO_UNSPECIFIED)

@RunWith(RobolectricTestRunner::class)
class StreamMediaTest {
    @Test
    fun theHighestQualityPlays() {
        val video = Stream(qualities).parts(ServerEdition.SUB).video

        assertEquals("https://example.com/1080.m3u8", video.localConfiguration!!.uri.toString())
        assertEquals(MimeTypes.APPLICATION_M3U8, video.localConfiguration!!.mimeType)
    }

    @Test
    fun eachFormatHasItsType() {
        fun type(format: StreamFormat) =
            Stream(listOf(Quality(720, "https://example.com/v", format)))
                .parts(ServerEdition.SUB)
                .video.localConfiguration!!
                .mimeType

        assertEquals(MimeTypes.APPLICATION_MPD, type(StreamFormat.Dash))
        assertNull(type(StreamFormat.Progressive))
    }

    @Test
    fun subtitlesComeWithTheirTypes() {
        val configs =
            Stream(qualities, subtitles = subtitles)
                .parts(ServerEdition.DUB)
                .video.localConfiguration!!
                .subtitleConfigurations

        assertEquals(listOf(MimeTypes.APPLICATION_SUBRIP, MimeTypes.TEXT_VTT, MimeTypes.TEXT_SSA), configs.map { it.mimeType })
        assertEquals(listOf("en", "es", "fr"), configs.map { it.language })
        assertEquals(listOf("English", "Spanish", "French"), configs.map { it.label })
    }

    @Test
    fun aSubServerTurnsTheFirstSubtitleOn() {
        val configs =
            Stream(qualities, subtitles = subtitles)
                .parts(ServerEdition.SUB)
                .video.localConfiguration!!
                .subtitleConfigurations

        assertEquals(listOf(C.SELECTION_FLAG_DEFAULT, 0, 0), configs.map { it.selectionFlags })
    }

    @Test
    fun aDubServerLeavesSubtitlesOff() {
        val configs =
            Stream(qualities, subtitles = subtitles)
                .parts(ServerEdition.DUB)
                .video.localConfiguration!!
                .subtitleConfigurations

        assertEquals(listOf(0, 0, 0), configs.map { it.selectionFlags })
    }

    @Test
    fun theFirstSeparateAudioTrackPlaysWithTheVideosFormat() {
        val parts = Stream(qualities, audioTracks = audio).parts(ServerEdition.SUB)

        assertEquals(
            "https://example.com/audio-ja",
            parts.audio!!
                .localConfiguration!!
                .uri
                .toString(),
        )
        assertEquals(MimeTypes.APPLICATION_M3U8, parts.audio.localConfiguration!!.mimeType)
    }

    @Test
    fun audioInsideTheVideoNeedsNoTrack() {
        assertNull(Stream(qualities).parts(ServerEdition.DUB).audio)
    }

    @Test
    fun forbiddenOrGoneMeansTheLinkExpired() {
        assertEquals(PlayerError.Expired, httpError(403).toPlayerError())
        assertEquals(PlayerError.Expired, httpError(410).toPlayerError())
    }

    @Test
    fun tooManyRequestsIsARateLimit() {
        assertEquals(PlayerError.Failed(FailReason.RateLimited(null), 429), httpError(429).toPlayerError())
    }

    @Test
    fun anotherStatusMeansTheSiteIsDownWithItsCode() {
        assertEquals(PlayerError.Failed(FailReason.SourceDown, 503), httpError(503).toPlayerError())
    }

    @Test
    fun aTimeoutIsNoAnswer() {
        val timeout = PlaybackException("Timed out", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)

        assertEquals(PlayerError.Failed(FailReason.NoAnswer), timeout.toPlayerError())
    }

    @Test
    fun theSourcesOwnErrorsKeepTheirReason() {
        assertEquals(PlayerError.Expired, sourceError(SourceException.LinkExpired()).toPlayerError())
        assertEquals(PlayerError.Failed(FailReason.Blocked), sourceError(SourceException.Blocked()).toPlayerError())
    }

    @Test
    fun anythingElseMeansTheSiteIsDown() {
        val broken = PlaybackException("Broken file", null, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)

        assertEquals(PlayerError.Failed(FailReason.SourceDown), broken.toPlayerError())
    }
}
