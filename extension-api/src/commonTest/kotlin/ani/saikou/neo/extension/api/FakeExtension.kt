package ani.saikou.neo.extension.api

import kotlin.time.Duration.Companion.minutes

/** A source that answers from memory, the way a real one would from its site. */
class FakeExtension : SourceExtension {
    val asked = mutableListOf<Episode>()

    override suspend fun servers(episode: Episode): List<Server> {
        asked += episode
        return listOf(
            Server(id = "sub-1", name = "Main", edition = Edition.Sub, runtime = 24.minutes),
            Server(id = "dub-1", name = "Main", edition = Edition.Dub, runtime = 23.minutes),
            Server(id = "hard-1", name = "Backup", edition = Edition.HardSub, runtime = null),
        )
    }

    override suspend fun stream(server: Server): Stream =
        Stream(
            qualities =
                listOf(1080, 720, 480).map { height ->
                    Quality(height, "https://example.com/${server.id}/$height.m3u8", StreamFormat.Hls)
                },
            audioTracks =
                listOf(
                    AudioTrack(url = "https://example.com/${server.id}/ja.m3u8", language = "ja", label = "Japanese"),
                    AudioTrack(url = "https://example.com/${server.id}/en.m3u8", language = "en", label = "English"),
                ),
            subtitles =
                listOf(
                    SubtitleTrack(
                        url = "https://example.com/${server.id}/en.ass",
                        language = "en",
                        label = "English",
                        format = SubtitleFormat.Ass,
                    ),
                ),
            headers = mapOf("Referer" to "https://example.com/"),
        )
}
