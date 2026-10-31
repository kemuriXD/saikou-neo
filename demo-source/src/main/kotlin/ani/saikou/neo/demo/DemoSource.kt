package ani.saikou.neo.demo

import ani.saikou.neo.extension.api.AudioTrack
import ani.saikou.neo.extension.api.Edition
import ani.saikou.neo.extension.api.Episode
import ani.saikou.neo.extension.api.Quality
import ani.saikou.neo.extension.api.Server
import ani.saikou.neo.extension.api.SourceException
import ani.saikou.neo.extension.api.SourceExtension
import ani.saikou.neo.extension.api.Stream
import ani.saikou.neo.extension.api.StreamFormat
import ani.saikou.neo.extension.api.SubtitleFormat
import ani.saikou.neo.extension.api.SubtitleTrack
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Plays the same freely licensed test streams for every episode, so playback can be tested without any other source.
 * Each "Fails" server throws one of the typed errors instead, for testing how the app shows them. Extensions get no
 * Android context, so server names are plain text rather than string resources.
 */
class DemoSource : SourceExtension {
    override suspend fun servers(episode: Episode): List<Server> =
        listOf(
            Server(id = SUB, name = "Demo", edition = Edition.Sub, runtime = 734.seconds),
            Server(id = DUB, name = "Demo", edition = Edition.Dub, runtime = 634_567.milliseconds),
        ) + failures.map { Server(id = it.id, name = it.name, edition = Edition.Sub, runtime = null) }

    override suspend fun stream(server: Server): Stream =
        when (server.id) {
            SUB -> sub
            DUB -> dub
            else -> throw failures.find { it.id == server.id }?.error?.invoke() ?: SourceException.NotFound()
        }

    /** The expired link comes back working, as a real source's would after fetching a fresh one. */
    override suspend fun refresh(
        server: Server,
        expired: Stream,
    ): Stream = if (server.id == LINK_EXPIRED) sub else stream(server)

    private companion object {
        const val SUB = "sub"
        const val DUB = "dub"
        const val LINK_EXPIRED = "fails-link-expired"
        const val FAILED = "The demo source was set to fail"

        val failures =
            listOf(
                Failure("fails-blocked", "Fails: blocked") { SourceException.Blocked(FAILED) },
                Failure("fails-rate-limited", "Fails: rate limited") { SourceException.RateLimited(30.seconds, FAILED) },
                Failure("fails-source-down", "Fails: source down") { SourceException.SourceDown(FAILED) },
                Failure(LINK_EXPIRED, "Fails: link expired") { SourceException.LinkExpired(FAILED) },
                Failure("fails-not-found", "Fails: not found") { SourceException.NotFound(FAILED) },
            )

        // Tears of Steel (CC BY 3.0, Blender Foundation) from Unified Streaming's public demo, with the video and both
        // audio languages in separate playlists. The subtitle is the film's own, from Blender.
        const val TEARS = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel-multi-lang.ism"

        val sub =
            Stream(
                qualities =
                    listOf(750 to 2_200_000, 350 to 1_001_000, 200 to 751_000, 100 to 401_000).map { (height, bitrate) ->
                        Quality(height, "$TEARS/tears-of-steel-multi-lang-video_eng=$bitrate.m3u8", StreamFormat.Hls, bitrate)
                    },
                audioTracks =
                    listOf(
                        AudioTrack("$TEARS/tears-of-steel-multi-lang-audio_eng=64008.m3u8", language = "en", label = "English"),
                        AudioTrack("$TEARS/tears-of-steel-multi-lang-audio_ita=64008.m3u8", language = "it", label = "Italian"),
                    ),
                subtitles =
                    listOf(
                        SubtitleTrack(
                            url = "https://download.blender.org/demo/movies/ToS/subtitles/TOS-en.srt",
                            language = "en",
                            label = "English",
                            format = SubtitleFormat.Srt,
                        ),
                    ),
            )

        // Big Buck Bunny (CC BY 3.0, Blender Foundation) from Mux's public test streams, with the audio in the video.
        const val BUNNY = "https://test-streams.mux.dev/x36xhzz"

        val dub =
            Stream(
                qualities =
                    listOf(
                        Triple(1080, "url_8/193039199_mp4_h264_aac_fhd_7.m3u8", 6_221_600),
                        Triple(720, "url_0/193039199_mp4_h264_aac_hd_7.m3u8", 2_149_280),
                        Triple(480, "url_6/193039199_mp4_h264_aac_hq_7.m3u8", 836_280),
                        Triple(288, "url_4/193039199_mp4_h264_aac_7.m3u8", 460_560),
                        Triple(184, "url_2/193039199_mp4_h264_aac_ld_7.m3u8", 246_440),
                    ).map { (height, path, bitrate) -> Quality(height, "$BUNNY/$path", StreamFormat.Hls, bitrate) },
            )
    }
}

/** A server that always fails with [error]. */
private class Failure(
    val id: String,
    val name: String,
    val error: () -> SourceException,
)
