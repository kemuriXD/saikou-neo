package ani.saikou.neo.extension.api

/** What the player needs to play one server's episode. */
public data class Stream(
    /** Every quality on its own, so the viewer can pick one. There's no "Auto". */
    val qualities: List<Quality>,
    /** Audio served apart from the video, if any. */
    val audioTracks: List<AudioTrack> = emptyList(),
    /** Subtitles served apart from the video, if any. */
    val subtitles: List<SubtitleTrack> = emptyList(),
    /**
     * Sent with every playlist, segment, key and subtitle request for this
     * stream, whichever host it goes to. Only put in what all of them may see.
     */
    val headers: Map<String, String> = emptyMap(),
    /** Set this to fetch the media yourself instead of the player's HTTP stack. */
    val mediaLoader: MediaLoader? = null,
) {
    init {
        require(qualities.isNotEmpty()) { "A stream needs at least one quality" }
    }

    /** Leaves out header values, which often hold cookies or tokens. */
    override fun toString(): String =
        "Stream(qualities=$qualities, audioTracks=$audioTracks, subtitles=$subtitles, " +
            "headers=${headers.keys}, mediaLoader=$mediaLoader)"
}

public data class Quality(
    /** The video height in pixels, such as 1080. */
    val height: Int,
    /** A playlist or file with just this quality. */
    val url: String,
    val format: StreamFormat,
    /** Bits per second, if known. */
    val bitrate: Int? = null,
) {
    init {
        require(height > 0) { "A quality needs a real height: $height" }
        requireWebLink(url)
    }
}

public enum class StreamFormat { Hls, Dash, Progressive }

public data class AudioTrack(
    val url: String,
    /** A BCP 47 tag such as "ja", if known. */
    val language: String?,
    /** Shown in the audio list. */
    val label: String,
) {
    init {
        requireWebLink(url)
    }
}

public data class SubtitleTrack(
    val url: String,
    /** A BCP 47 tag such as "en", if known. */
    val language: String?,
    /** Shown in the subtitle list. */
    val label: String,
    val format: SubtitleFormat,
) {
    init {
        requireWebLink(url)
    }
}

public enum class SubtitleFormat { Vtt, Srt, Ass }

/** Only web links, so a source can't point the player at the app's own files. */
private fun requireWebLink(url: String) {
    val scheme = url.substringBefore("://", missingDelimiterValue = "").lowercase()
    require(scheme == "http" || scheme == "https") { "Media links must use http or https" }
}
