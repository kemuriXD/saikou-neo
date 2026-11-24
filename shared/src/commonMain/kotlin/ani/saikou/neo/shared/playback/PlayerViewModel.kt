package ani.saikou.neo.shared.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.account.WatchingUpdates
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.catalogue.art
import ani.saikou.neo.shared.crash.CrashContext
import ani.saikou.neo.shared.details.EpisodeList
import ani.saikou.neo.shared.home.WatchHistory
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.shared.log.AppLog
import ani.saikou.neo.shared.sources.InstalledSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A stream a source gave, and the height of its best quality, as the player shows it, with every height it offers and
 * the names of any audio tracks served apart from the video.
 */
public data class FoundStream<S>(
    val stream: S,
    val height: Int,
    val heights: List<Int> = listOf(height),
    val audio: List<String> = emptyList(),
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

/** The tag the player's lines carry in the app log. */
internal const val PLAYER_LOG_TAG = "Player"

/**
 * What the player opens with: the episode, and the [servers] the streams page listed, in its order, with the one [picked].
 * [backdrop] is the artwork Details showed, if it had any. The [episodes] Details listed, and the [progress] it showed
 * them with, fill the player's episodes drawer. Going on from the episode before, [audio] is the separate audio track it
 * played, which this one plays too.
 *
 * From Details' main button (#499), [findServer] says the one server listed is the show's last, standing in until its
 * source names the server of the same name and edition for this episode. [resume] says Resume was already pressed, so
 * the player carries on from [progress] without asking.
 */
public data class PlayRequest(
    val show: AnimeDetails,
    val episode: Int,
    val episodeTitle: String?,
    val servers: List<PlayServer>,
    val picked: Int,
    val backdrop: String? = null,
    val episodes: EpisodeList? = null,
    val progress: WatchProgress? = null,
    val audio: String? = null,
    val findServer: Boolean = false,
    val resume: Boolean = false,
) {
    /** The artwork behind the player while it starts: the one Details showed, or else the show's banner or cover. */
    val art: String? get() = backdrop ?: show.card.art
}

/** The servers shown on the page in its order, with [server] from [source] picked. */
public fun ServersState.playRequest(
    source: InstalledSource,
    server: FoundServer,
    show: AnimeDetails,
    episode: Int,
    episodeTitle: String?,
    backdrop: String? = null,
): PlayRequest {
    val listed =
        sources.flatMap { entry ->
            val servers = groups(entry).all
            servers.map { PlayServer(entry.source, it, servers.size) }
        }
    val picked = listed.indexOfFirst { it.source == source && it.server == server }
    // One no longer listed is still played, ahead of the rest.
    if (picked < 0) return PlayRequest(show, episode, episodeTitle, listOf(PlayServer(source, server, 1)) + listed, 0, backdrop)
    return PlayRequest(show, episode, episodeTitle, listed, picked, backdrop)
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
    /** The episode was part watched on this TV, so the viewer picks whether to carry on from [atMs] or start over. */
    public data class Resume(
        val atMs: Long,
    ) : PlayerPhase<Nothing>

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
    /** The recap, opening, ending and preview, when their times are known. Nothing gives them yet (#76). */
    val segments: List<Segment> = emptyList(),
    /** The height the viewer picked for this server, or null for the best. */
    val pickedHeight: Int? = null,
    /** Which of the stream's separate audio tracks plays. */
    val pickedAudio: Int = 0,
    /** The episode waiting on "Update AniList as you watch?", asked as the viewer leaves (ADR 0053). */
    val askAniList: Int? = null,
    /** An episode is waiting on that answer, so leaving asks first. */
    val holdsForAniList: Boolean = false,
    /** The episode after this one, or null when there's none to go on to (ADR 0054). */
    val next: UpNextEpisode? = null,
    /** Up next, while it shows. */
    val upNext: UpNext? = null,
    /** Where to go on to, once it's known. */
    val going: NextPlay? = null,
) {
    /** The height playing out of [answered]. */
    public fun height(answered: FoundStream<*>): Int = pickedHeight ?: answered.height
}

/**
 * Plays the server [request] picked. A server gets [answerWithin] to give its stream. If one fails before the video
 * starts, the next server listed is tried, once per request. An expired link is refreshed once, carrying on from the
 * same spot, before it counts as stopped. A part-watched episode first asks whether to resume. Where the video got to is
 * saved to [history] while it plays and when it stops, with the time [now] gives in seconds. Once the episode counts as
 * watched, or the viewer moves on to the next one, [updates] is told, once, unless it asks first. Then the episode is
 * held, and the question is asked as the viewer leaves, or over Up next. Going on to the next episode asks [servers] for
 * it on the same source (ADR 0054). After a [run] of episodes with no button pressed, Up next asks instead of counting
 * down (ADR 0055). The server and audio that start playing are kept in [lastServers], for Details' main button to play
 * on next time (#499).
 */
public class PlayerViewModel<S>(
    private val finder: StreamFinder<S>,
    private val request: PlayRequest,
    private val answerWithin: Duration = ANSWER_WITHIN,
    private val history: WatchHistory? = null,
    private val now: () -> Long = { Clock.System.now().epochSeconds },
    private val updates: WatchingUpdates? = null,
    private val servers: ServerFinder? = null,
    private val run: WatchingRun? = null,
    private val lastServers: LastServers? = null,
) : ViewModel() {
    // The servers to play, which a request to find the show's last server replaces once its source names it.
    private var listed = request.servers

    private var index = request.picked
    private var triedNext = false
    private var refreshed = false
    private var asking: Job? = null
    private var counted = false
    private var countdown: Job? = null
    private var goingOn: Job? = null
    private var showing: Job? = null

    // How many episodes played in a row once Up next has asked if you're still watching. It asks again at the end.
    private var stillWatching: Int? = null

    // Play was pressed, so another press waits for it.
    private var startingNext: Job? = null

    // Watch credits was pressed, so Up next waits for the end.
    private var credits = false

    // Going on waits for the answer to "Update AniList as you watch?".
    private var playsOnAfterAnswer = false

    // The audio the request wants is still to be picked from the first stream.
    private var wantsAudio = request.audio

    // The user to ask, once an episode is held for an answer.
    private var held: Int? = null

    // Whether watching this show asks first, and who, read as the player opens.
    private var askFor: Int? = null
    private val askRead = updates?.let { updates -> viewModelScope.launch { askFor = updates.asksFirst(request.show.card.id) } }

    // What made the stream call fail, for the report if playback then stops.
    private var lastError: Throwable? = null

    // The episode's length, once the player knows it.
    private var lengthMs = 0L

    private val resumeAt = request.progress?.resumeAtMs(request.episode)

    private val mutableState =
        MutableStateFlow(
            PlayerState<S>(
                request.servers[index],
                resumeAt?.takeIf { !request.resume }?.let { PlayerPhase.Resume(it) } ?: PlayerPhase.Starting(null),
                next = request.nextEpisode(),
            ),
        )
    public val state: StateFlow<PlayerState<S>> = mutableState.asStateFlow()

    init {
        CrashContext.playing(this, request.show.card.id, request.episode, request.servers[index].source)
        when {
            request.findServer -> findServer()
            resumeAt == null || request.resume -> open(index, startAtMs = resumeAt ?: 0)
        }
    }

    // Asks the last server's source for this episode, as Up next does, and plays the same server, or else the
    // episode's servers page opens (#499).
    private fun findServer() {
        val wanted = request.servers[index]
        val finder = servers ?: return mutableState.update { it.copy(going = NextPlay.Choose(request.episode)) }
        viewModelScope.launch {
            val found =
                withTimeoutOrNull(answerWithin) {
                    try {
                        finder.servers(wanted.source, request.show, request.episode)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLog.w(PLAYER_LOG_TAG, "${wanted.source.name} gave no servers", e)
                        null
                    }
                }
            val same = found?.firstOrNull { it.name == wanted.server.name && it.edition == wanted.server.edition }
            if (same == null) return@launch mutableState.update { it.copy(going = NextPlay.Choose(request.episode)) }
            listed = listOf(PlayServer(wanted.source, same, found.size))
            open(0, startAtMs = resumeAt ?: 0)
        }
    }

    override fun onCleared() {
        CrashContext.stoppedPlaying(this)
    }

    /** Carries on from where this TV stopped. */
    public fun resume() {
        val resume = state.value.phase as? PlayerPhase.Resume ?: return
        open(index, resume.atMs)
    }

    /** Plays the episode from the beginning. */
    public fun startOver() {
        if (state.value.phase is PlayerPhase.Resume) open(index, startAtMs = 0)
    }

    /** The video is at [positionMs] of [durationMs], which is saved while it plays. A duration of 0 isn't known yet. */
    public fun played(
        positionMs: Long,
        durationMs: Long,
    ) {
        if (durationMs > 0) lengthMs = durationMs
        if (state.value.phase is PlayerPhase.Playing) save(positionMs)
    }

    // Only a spot the video reached is saved, so leaving before it starts keeps the one saved before.
    private fun save(positionMs: Long) {
        if (history == null || lengthMs <= 0 || positionMs <= 0) return
        val still =
            request.episodes
                ?.episodes
                ?.firstOrNull { it.number == request.episode }
                ?.thumbnail
        val progress = WatchProgress(request.episode, positionMs.milliseconds, lengthMs.milliseconds, still, now())
        history.save(request.show.card.id, progress)
        if (progress.watchedToEnd) count()
    }

    /** The viewer picked [episode] to play next. Moving on to the one after this counts this one as watched. */
    public fun movingOn(episode: Int) {
        if (episode == request.episode + 1) count()
    }

    private fun count() {
        if (counted) return
        counted = true
        val updates = updates ?: return
        val read = askRead ?: return
        // Read when the player opened, so moving on straight after this knows whether to ask.
        if (read.isCompleted) {
            decide(updates)
        } else {
            viewModelScope.launch {
                read.join()
                decide(updates)
            }
        }
    }

    private fun decide(updates: WatchingUpdates) {
        val ask = askFor
        if (ask == null) {
            updates.watching(request.show.card.id, request.episode, request.show.card.title)
        } else {
            held = ask
            mutableState.update { it.copy(holdsForAniList = true) }
        }
    }

    /** The viewer is leaving. True when "Update AniList as you watch?" asks first, so the player stays until it's answered. */
    public fun leave(): Boolean {
        if (held == null) return false
        mutableState.update { it.copy(askAniList = request.episode) }
        return true
    }

    /** Answers the question: AniList is updated for this show from now on, or never, and the held episode goes with yes. */
    public fun answer(update: Boolean) {
        val user = held ?: return
        updates?.answer(request.show.card.id, user, update, request.episode, request.show.card.title)
        noAnswer()
    }

    /** Back on the question: nothing is sent, and it's asked again next time. */
    public fun noAnswer() {
        held = null
        mutableState.update { it.copy(askAniList = null, holdsForAniList = false) }
        if (playsOnAfterAnswer) {
            playsOnAfterAnswer = false
            goOn()
        } else if (state.value.upNext != null && stillWatching == null) {
            countDown()
        }
    }

    /** The video reached where Up next shows. It shows once, unless Watch credits waits for the end. */
    public fun reachedUpNext() {
        if (!credits && state.value.upNext == null) showUpNext()
    }

    /** The video ended. Up next shows again, or with no next episode, true, as the player leaves (ADR 0054). */
    public fun ended(): Boolean {
        if (state.value.next == null) return true
        credits = false
        if (state.value.upNext == null) showUpNext()
        return false
    }

    /** Hides Up next and keeps the video full screen until it ends. */
    public fun watchCredits() {
        if (state.value.going != null) return
        countdown?.cancel()
        showing?.cancel()
        startingNext?.cancel()
        goingOn?.cancel()
        credits = true
        mutableState.update { it.copy(upNext = null) }
    }

    private fun showUpNext() {
        if (state.value.next == null || state.value.going != null) return
        mutableState.update { it.copy(upNext = UpNext(secondsLeft = null, stillWatching)) }
        showing =
            viewModelScope.launch {
                if (stillWatching == null) stillWatching = run?.asks()
                mutableState.update { it.copy(upNext = UpNext(secondsLeft = null, stillWatching)) }
                if (held != null) {
                    mutableState.update { it.copy(askAniList = request.episode) }
                } else if (stillWatching == null) {
                    countDown()
                }
            }
    }

    private fun countDown() {
        countdown?.cancel()
        countdown =
            viewModelScope.launch {
                for (left in UP_NEXT_SECONDS downTo 1) {
                    mutableState.update { it.copy(upNext = UpNext(left)) }
                    delay(1000)
                }
                run?.playedOn()
                playNext()
            }
    }

    /** Goes on to the next episode, which counts this one, asking first when an episode waits on an answer. */
    public fun playNext() {
        if (state.value.next == null || state.value.going != null || goingOn?.isActive == true) return
        if (startingNext?.isActive == true || playsOnAfterAnswer) return
        countdown?.cancel()
        startingNext =
            viewModelScope.launch {
                askRead?.join()
                count()
                if (held == null) {
                    goOn()
                } else {
                    playsOnAfterAnswer = true
                    mutableState.update { it.copy(upNext = UpNext(secondsLeft = null, stillWatching), askAniList = request.episode) }
                }
            }
    }

    private fun goOn() {
        val next = state.value.next ?: return
        val finder = servers ?: return
        val playing = state.value.server
        val answered = (state.value.phase as? PlayerPhase.Playing)?.answered
        val audio = answered?.audio?.getOrNull(state.value.pickedAudio)
        mutableState.update { it.copy(upNext = UpNext(secondsLeft = null, stillWatching)) }
        // Kept, so a second press waits for this one and Watch credits stops it.
        goingOn =
            viewModelScope.launch {
                val going = withTimeoutOrNull(answerWithin) { request.next(next, playing, audio, finder) } ?: NextPlay.Choose(next.number)
                mutableState.update { it.copy(going = going) }
            }
    }

    /** The video started playing. */
    public fun started() {
        val answered = (state.value.phase as? PlayerPhase.Starting)?.answered ?: return
        mutableState.update { it.copy(phase = PlayerPhase.Playing(answered)) }
        val server = state.value.server
        val audio = answered.audio.getOrNull(state.value.pickedAudio)
        AppLog.i(PLAYER_LOG_TAG, "Playing show ${request.show.card.id} episode ${request.episode} on ${server.server.name}")
        lastServers?.saveServer(request.show.card.id, LastServer(server.source.id, server.server.name, server.server.edition, audio))
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
                is PlayerPhase.Stopped, is PlayerPhase.Resume -> null
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

    /** Plays [height] of the same stream from [positionMs], with nothing shown over the video. */
    public fun changeQuality(
        height: Int,
        positionMs: Long,
    ) {
        val answered = (state.value.phase as? PlayerPhase.Playing)?.answered ?: return
        if (height == state.value.height(answered)) return
        reload(answered, positionMs) { it.copy(pickedHeight = height) }
    }

    /** Plays the stream's separate audio track at [audio] from [positionMs], with nothing shown over the video. */
    public fun changeAudio(
        audio: Int,
        positionMs: Long,
    ) {
        val answered = (state.value.phase as? PlayerPhase.Playing)?.answered ?: return
        if (audio == state.value.pickedAudio) return
        reload(answered, positionMs) { it.copy(pickedAudio = audio) }
    }

    /** Plays [server] from [positionMs]. */
    public fun changeServer(
        server: PlayServer,
        positionMs: Long,
    ) {
        if (state.value.phase !is PlayerPhase.Playing || server == state.value.server) return
        val at = listed.indexOf(server)
        if (at >= 0) open(at, positionMs)
    }

    private fun reload(
        answered: FoundStream<S>,
        positionMs: Long,
        pick: (PlayerState<S>) -> PlayerState<S>,
    ) {
        mutableState.update {
            pick(it).copy(phase = PlayerPhase.Starting(answered, quiet = true), startAtMs = positionMs, load = it.load + 1)
        }
    }

    private fun open(
        at: Int,
        startAtMs: Long,
    ) {
        index = at
        refreshed = false
        lastError = null
        val server = listed[at]
        CrashContext.playing(this, request.show.card.id, request.episode, server.source)
        val willTryNext = !triedNext && listed.getOrNull(at + 1) != null
        mutableState.update {
            it.copy(
                server = server,
                phase = PlayerPhase.Starting(null),
                startAtMs = startAtMs,
                willTryNext = willTryNext,
                pickedHeight = null,
                pickedAudio = 0,
            )
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
                    } catch (e: Exception) {
                        AppLog.w(PLAYER_LOG_TAG, "The stream could not be loaded", e)
                        lastError = e
                        return@launch stop(FailReason.SourceDown)
                    }
                val wanted = wantsAudio
                wantsAudio = null
                val audio = wanted?.let { found.audio.indexOf(it) }
                // The episode before played audio this stream doesn't have, so the viewer picks a server.
                if (audio == -1) return@launch mutableState.update { it.copy(going = NextPlay.Choose(request.episode)) }
                mutableState.update {
                    it.copy(
                        phase = PlayerPhase.Starting(found, quiet),
                        load = it.load + 1,
                        pickedAudio = audio ?: it.pickedAudio,
                    )
                }
            }
    }

    /** Tries the next server if the video hadn't started and that hasn't been done yet, or else stops. */
    private fun stop(
        reason: FailReason,
        httpCode: Int? = null,
        atMs: Long? = state.value.startAtMs.takeIf { it > 0 },
    ) {
        val detail = "$reason${httpCode?.let { ", HTTP $it" }.orEmpty()}"
        val current = state.value
        val next = listed.getOrNull(index + 1)
        val starting = (current.phase as? PlayerPhase.Starting<*>)?.quiet == false
        if (starting && current.willTryNext) {
            AppLog.w(PLAYER_LOG_TAG, "Server failed: $detail. Trying the next one")
            triedNext = true
            open(index + 1, current.startAtMs)
            return
        }
        AppLog.w(PLAYER_LOG_TAG, "Playback stopped: $detail")
        CrashContext.recovered(PLAYER_LOG_TAG, "Playback stopped: $detail", lastError)
        if (current.phase is PlayerPhase.Playing && atMs != null) save(atMs)
        mutableState.update { it.copy(phase = PlayerPhase.Stopped(reason, httpCode, atMs, next)) }
    }
}
