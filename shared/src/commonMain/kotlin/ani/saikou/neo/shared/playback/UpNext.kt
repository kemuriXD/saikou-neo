package ani.saikou.neo.shared.playback

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How long before the end Up next shows when no ending is marked (ADR 0054). */
public val UP_NEXT_BEFORE_END: Duration = 30.seconds

/** How long Up next counts down before the next episode plays. */
public const val UP_NEXT_SECONDS: Int = 10

/** The episode after the one playing, with what Details knows of it. */
public data class UpNextEpisode(
    val number: Int,
    val title: String?,
    val synopsis: String?,
    val still: String?,
)

/**
 * Up next is showing, counting down [secondsLeft], or waiting while it's null: on an answer, or for the next server. After
 * a run of episodes with no button pressed it asks if you're still watching instead, saying how many [stillWatching]
 * played in a row (ADR 0055).
 */
public data class UpNext(
    val secondsLeft: Int?,
    val stillWatching: Int? = null,
)

/** Where moving on goes: the next episode's [request], or the servers page for [episode]. */
public sealed interface NextPlay {
    public data class Play(
        val request: PlayRequest,
    ) : NextPlay

    public data class Choose(
        val episode: Int,
    ) : NextPlay
}

/**
 * The episode after this one, or null after the last or the newest out. Details' list decides when there is one. Without
 * it, the show's total does, or the episode airing next.
 */
public fun PlayRequest.nextEpisode(): UpNextEpisode? {
    val number = episode + 1
    val list = episodes
    if (list != null) {
        if (list.film) return null
        val at = list.episodes.indexOfFirst { it.number == number }
        // The episodes out come first, in order.
        if (at < 0 || at >= list.aired) return null
        val next = list.episodes[at]
        return UpNextEpisode(number, next.title, next.synopsis, next.thumbnail)
    }
    val last = show.nextEpisode?.let { it.number - 1 } ?: show.card.episodes
    if (last != null && number > last) return null
    return UpNextEpisode(number, null, null, null)
}

/**
 * Where Up next shows in an episode [durationMs] long: where the ending starts, or else a little before the end. An ending
 * in the first half is taken as wrong, and a video too short to have an end to wait for has no Up next until it ends.
 */
public fun upNextAtMs(
    segments: List<Segment>,
    durationMs: Long,
): Long? {
    if (durationMs <= UP_NEXT_BEFORE_END.inWholeMilliseconds) return null
    val ending = segments.firstOrNull { it.kind == SegmentKind.Ending }?.startMs
    if (ending != null && ending in durationMs / 2 until durationMs) return ending
    return durationMs - UP_NEXT_BEFORE_END.inWholeMilliseconds
}

/**
 * The next episode on [playing]'s source, from a server with the same name and edition, with [audio] to pick there. With
 * no such server, or no answer, the servers page opens for it instead.
 */
internal suspend fun PlayRequest.next(
    next: UpNextEpisode,
    playing: PlayServer,
    audio: String?,
    finder: ServerFinder,
): NextPlay {
    val servers =
        try {
            finder.servers(playing.source, show, next.number)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            return NextPlay.Choose(next.number)
        }
    val same = servers.firstOrNull { it.name == playing.server.name && it.edition == playing.server.edition }
    same ?: return NextPlay.Choose(next.number)
    // Only that server: the player never moves to another on its own.
    return NextPlay.Play(
        copy(
            episode = next.number,
            episodeTitle = next.title,
            servers = listOf(PlayServer(playing.source, same, 1)),
            picked = 0,
            audio = audio,
        ),
    )
}
