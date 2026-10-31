package ani.saikou.neo.shared.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.sources.InstalledSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/** A stream a source gave, and the height of its best quality, as the player shows it. */
public data class FoundStream<S>(
    val stream: S,
    val height: Int,
)

/**
 * Asks an installed source for a server's stream. The TV app loads the source and calls it. [S] is the source's own
 * stream, which only the player reads. Both throw [SourceFailure] when the source gives a reason.
 */
public interface StreamFinder<S> {
    public suspend fun stream(
        source: InstalledSource,
        server: FoundServer,
    ): FoundStream<S>

    /** A fresh stream for [server] after [expired] stopped working. */
    public suspend fun refresh(
        source: InstalledSource,
        server: FoundServer,
        expired: S,
    ): FoundStream<S>
}

/** A server to play, from a [source] with [sourceServers] servers for the episode. */
public data class PlayServer(
    val source: InstalledSource,
    val server: FoundServer,
    val sourceServers: Int,
)

/** What the player opens with: the episode, and the [servers] the streams page listed, in its order, with the one [picked]. */
public data class PlayRequest(
    val show: AnimeDetails,
    val episode: Int,
    val episodeTitle: String?,
    val servers: List<PlayServer>,
    val picked: Int,
)

/** The servers shown on the page in its order, with [server] from [source] picked. */
public fun ServersState.playRequest(
    source: InstalledSource,
    server: FoundServer,
    show: AnimeDetails,
    episode: Int,
    episodeTitle: String?,
): PlayRequest {
    val listed =
        shown.flatMap { entry ->
            val servers = (entry.answer as? SourceAnswer.Found)?.servers.orEmpty()
            servers.map { PlayServer(entry.source, it, servers.size) }
        }
    val picked = listed.indexOfFirst { it.source == source && it.server == server }
    // One no longer listed is still played, ahead of the rest.
    if (picked < 0) return PlayRequest(show, episode, episodeTitle, listOf(PlayServer(source, server, 1)) + listed, 0)
    return PlayRequest(show, episode, episodeTitle, listed, picked)
}

/** Why the player stopped, as it tells the ViewModel. */
public sealed interface PlayerError {
    /** A link stopped working, such as a 403 or 410 part way through. */
    public data object Expired : PlayerError

    public data class Failed(
        val reason: FailReason,
        val httpCode: Int? = null,
    ) : PlayerError
}

public sealed interface PlayerPhase<out S> {
    /**
     * Waiting for the server's stream, then for the video to start once it [answered]. A [quiet] start follows a fresh
     * link, so nothing shows over the video.
     */
    public data class Starting<S>(
        val answered: FoundStream<S>?,
        val quiet: Boolean = false,
    ) : PlayerPhase<S>

    public data class Playing<S>(
        val answered: FoundStream<S>,
    ) : PlayerPhase<S>

    /** The server stopped answering, at [atMs] if the video had got going, with the [next] server to try if there is one. */
    public data class Stopped(
        val reason: FailReason,
        val httpCode: Int?,
        val atMs: Long?,
        val next: PlayServer?,
    ) : PlayerPhase<Nothing>
}

public data class PlayerState<out S>(
    val server: PlayServer,
    val phase: PlayerPhase<S>,
    /** Where the player starts each stream it's given. */
    val startAtMs: Long = 0,
    /** Goes up each time there's a new stream for the player to load. */
    val load: Int = 0,
    /** Whether the next server is tried if this one fails before the video starts. */
    val willTryNext: Boolean = false,
)

/**
 * Plays the server [request] picked. A server gets [answerWithin] to give its stream. If one fails before the video
 * starts, the next server listed is tried, once per request. An expired link is refreshed once, carrying on from the
 * same spot, before it counts as stopped.
 */
public class PlayerViewModel<S>(
    private val finder: StreamFinder<S>,
    private val request: PlayRequest,
    private val answerWithin: Duration = ANSWER_WITHIN,
) : ViewModel() {
    private var index = request.picked
    private var triedNext = false
    private var refreshed = false
    private var asking: Job? = null

    private val mutableState = MutableStateFlow(PlayerState<S>(request.servers[index], PlayerPhase.Starting(null)))
    public val state: StateFlow<PlayerState<S>> = mutableState.asStateFlow()

    init {
        open(index, startAtMs = 0)
    }

    /** The video started playing. */
    public fun started() {
        val answered = (state.value.phase as? PlayerPhase.Starting)?.answered ?: return
        mutableState.update { it.copy(phase = PlayerPhase.Playing(answered)) }
    }

    /** The player stopped with [error] at [positionMs]. */
    public fun failed(
        error: PlayerError,
        positionMs: Long,
    ) {
        val current = state.value
        val answered =
            when (val phase = current.phase) {
                is PlayerPhase.Playing -> phase.answered
                is PlayerPhase.Starting -> phase.answered
                is PlayerPhase.Stopped -> null
            } ?: return
        val at = if (current.phase is PlayerPhase.Playing) positionMs else current.startAtMs
        when {
            error is PlayerError.Failed -> {
                stop(error.reason, error.httpCode, at)
            }

            refreshed -> {
                stop(FailReason.LinkExpired, null, at)
            }

            else -> {
                refreshed = true
                val server = current.server
                // Once the video has played, nothing shows over it while the link is fetched again.
                val quiet = current.phase is PlayerPhase.Playing
                mutableState.update { it.copy(phase = PlayerPhase.Starting(null, quiet), startAtMs = at) }
                ask(quiet) { finder.refresh(server.source, server.server, answered.stream) }
            }
        }
    }

    /** Plays the next server listed from where this one stopped. */
    public fun tryNext() {
        val stopped = state.value.phase as? PlayerPhase.Stopped ?: return
        if (stopped.next != null) open(index + 1, stopped.atMs ?: state.value.startAtMs)
    }

    /** Asks the same server again, from where it stopped. */
    public fun tryAgain() {
        val stopped = state.value.phase as? PlayerPhase.Stopped ?: return
        open(index, stopped.atMs ?: state.value.startAtMs)
    }

    private fun open(
        at: Int,
        startAtMs: Long,
    ) {
        index = at
        refreshed = false
        val server = request.servers[at]
        val willTryNext = !triedNext && request.servers.getOrNull(at + 1) != null
        mutableState.update {
            it.copy(server = server, phase = PlayerPhase.Starting(null), startAtMs = startAtMs, willTryNext = willTryNext)
        }
        ask(quiet = false) { finder.stream(server.source, server.server) }
    }

    /** Waits for [call]'s stream, which the player is then given, or stops when it fails. */
    private fun ask(
        quiet: Boolean,
        call: suspend () -> FoundStream<S>,
    ) {
        asking?.cancel()
        asking =
            viewModelScope.launch {
                val found =
                    try {
                        // Asked apart from the wait, so a source that won't stop can't hold up the time limit.
                        val answer = viewModelScope.async { call() }
                        withTimeoutOrNull(answerWithin) { answer.await() }.also { if (it == null) answer.cancel() }
                            ?: return@launch stop(FailReason.NoAnswer)
                    } catch (_: CancellationException) {
                        // Leaving the player cancels the call. A source's own timeout means it's down.
                        ensureActive()
                        return@launch stop(FailReason.SourceDown)
                    } catch (e: SourceFailure) {
                        return@launch stop(e.reason)
                    } catch (_: Exception) {
                        return@launch stop(FailReason.SourceDown)
                    }
                mutableState.update { it.copy(phase = PlayerPhase.Starting(found, quiet), load = it.load + 1) }
            }
    }

    /** Tries the next server if the video hadn't started and that hasn't been done yet, or else stops. */
    private fun stop(
        reason: FailReason,
        httpCode: Int? = null,
        atMs: Long? = state.value.startAtMs.takeIf { it > 0 },
    ) {
        val current = state.value
        val next = request.servers.getOrNull(index + 1)
        val starting = (current.phase as? PlayerPhase.Starting<*>)?.quiet == false
        if (starting && current.willTryNext) {
            triedNext = true
            open(index + 1, current.startAtMs)
            return
        }
        mutableState.update { it.copy(phase = PlayerPhase.Stopped(reason, httpCode, atMs, next)) }
    }
}
