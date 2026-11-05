package ani.saikou.neo.shared.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.settings.SubOrDub
import ani.saikou.neo.shared.sources.InstalledSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How long a source gets to list its servers. Finding them can take a while (ADR 0003), so it's generous. */
public val ANSWER_WITHIN: Duration = 25.seconds

/** What one source has said about the episode so far. */
public sealed interface SourceAnswer {
    public data object Looking : SourceAnswer

    /** Every server the source has, in its own order. Empty when it has none. */
    public data class Found(
        val servers: List<FoundServer>,
    ) : SourceAnswer

    /** Why the source failed. For a rate limit, [waitSeconds] counts down the seconds left before it can be asked again. */
    public data class Failed(
        val reason: FailReason,
        val waitSeconds: Long? = null,
    ) : SourceAnswer
}

public data class SourceServers(
    val source: InstalledSource,
    val answer: SourceAnswer,
)

/** Why the page has nothing to pick, once it knows. */
public sealed interface StreamsOutcome {
    /** No source is installed that can be used. */
    public data object NoSources : StreamsOutcome

    /** Every source answered, and the only servers were of the edition the page hides: [have] rather than [missing]. */
    public data class MissingEdition(
        val missing: ServerEdition,
        val have: ServerEdition,
    ) : StreamsOutcome

    /** Every source answered, and each failed or had no servers. */
    public data object NothingPlays : StreamsOutcome
}

public data class ServersState(
    /** Each source, in the order they're installed. */
    val sources: List<SourceServers>,
    /** The id of the one source shown, or null for all of them. */
    val filter: String? = null,
    /** The editions the page shows. It starts from the setting and can be changed for the page alone. */
    val subOrDub: SubOrDub = SubOrDub.BOTH,
    /** Whether the installed sources have been read, so [sources] being empty means there are none. */
    val ready: Boolean = false,
) {
    /** The sources the filter shows. */
    val shown: List<SourceServers> get() = if (filter == null) sources else sources.filter { it.source.id == filter }

    /** How many sources have answered, with servers or not. */
    val answered: Int get() = sources.count { it.answer != SourceAnswer.Looking }

    /** [entry]'s servers that the page shows, by edition. Both are empty unless it found some. */
    public fun groups(entry: SourceServers): ServerGroups = groupServers((entry.answer as? SourceAnswer.Found)?.servers.orEmpty(), subOrDub)

    /** Why there's nothing to pick, or null while there may still be something, or is. */
    val outcome: StreamsOutcome? get() {
        if (!ready) return null
        if (sources.isEmpty()) return StreamsOutcome.NoSources
        if (sources.any { it.answer == SourceAnswer.Looking || groups(it).all.isNotEmpty() }) return null
        val hidden = sources.any { (it.answer as? SourceAnswer.Found)?.servers?.isNotEmpty() == true }
        if (hidden && sources.none { it.answer is SourceAnswer.Failed }) {
            return when (subOrDub) {
                SubOrDub.DUB -> StreamsOutcome.MissingEdition(missing = ServerEdition.DUB, have = ServerEdition.SUB)
                else -> StreamsOutcome.MissingEdition(missing = ServerEdition.SUB, have = ServerEdition.DUB)
            }
        }
        return StreamsOutcome.NothingPlays
    }
}

/**
 * The servers for [episode] of [show], asking every installed source that can be used at the same time. Each one is
 * shown as it answers, in the order they're installed, so those still looking don't hold up the rest. The page shows
 * the editions [subOrDub] allows until it's changed. The sources asked are those [installed] holds once they've been
 * read, or the first it holds after there were none. A source that hasn't answered within [answerWithin] has no answer.
 */
public class ServersViewModel(
    private val finder: ServerFinder,
    installed: Flow<List<InstalledSource>?>,
    private val show: AnimeDetails,
    private val episode: Int,
    subOrDub: SubOrDub,
    private val answerWithin: Duration = ANSWER_WITHIN,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ServersState(emptyList(), subOrDub = subOrDub))
    public val state: StateFlow<ServersState> = mutableState.asStateFlow()

    // The countdown of each rate-limited source's wait, by source id.
    private val waits = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            // With none, it waits, so one added in Settings is asked on coming back.
            installed
                .filterNotNull()
                .map { list -> list.filter { it.problem == null } }
                .onEach { if (it.isEmpty()) mutableState.update { state -> state.copy(ready = true) } }
                .first { it.isNotEmpty() }
                .forEach(::ask)
        }
    }

    /** Asks [source] for its servers, showing it as still looking until it answers. */
    private fun ask(source: InstalledSource) {
        waits.remove(source.id)?.cancel()
        mutableState.update { it.answered(source, SourceAnswer.Looking).copy(ready = true) }
        viewModelScope.launch {
            val answer =
                try {
                    // Asked apart from the wait, so a source that won't stop can't hold up the time limit.
                    val call = viewModelScope.async { finder.servers(source, show, episode) }
                    val all = withTimeoutOrNull(answerWithin) { call.await() }
                    if (all == null) {
                        call.cancel()
                        SourceAnswer.Failed(FailReason.NoAnswer)
                    } else {
                        SourceAnswer.Found(all)
                    }
                } catch (_: CancellationException) {
                    // Leaving the page cancels the search. A source's own timeout fails only that source.
                    ensureActive()
                    SourceAnswer.Failed(FailReason.SourceDown)
                } catch (e: SourceFailure) {
                    val wait = (e.reason as? FailReason.RateLimited)?.retryAfterSeconds?.takeIf { it > 0 }
                    SourceAnswer.Failed(e.reason, wait)
                } catch (_: Exception) {
                    SourceAnswer.Failed(FailReason.SourceDown)
                }
            mutableState.update { it.answered(source, answer) }
            if (answer is SourceAnswer.Failed && answer.waitSeconds != null) countDown(source)
        }
    }

    /** Takes a second off [source]'s wait each second, until none is left. */
    private fun countDown(source: InstalledSource) {
        waits[source.id] =
            viewModelScope.launch {
                do {
                    delay(1.seconds)
                    val failed = answer(source) as? SourceAnswer.Failed ?: return@launch
                    val left = failed.waitSeconds?.minus(1)?.takeIf { it > 0 }
                    mutableState.update { it.answered(source, failed.copy(waitSeconds = left)) }
                } while (left != null)
            }
    }

    private fun answer(source: InstalledSource) =
        state.value.sources
            .firstOrNull { it.source == source }
            ?.answer

    /** Asks again every source that failed or had nothing to show. Those still looking or with servers are left alone. */
    public fun retry() {
        state.value.sources
            .filter { it.answer.hasNothing() }
            .forEach { ask(it.source) }
    }

    /** Asks [source] again if it failed or had nothing to show, unless it's still waiting out a rate limit. */
    public fun retry(source: InstalledSource) {
        val answer = answer(source) ?: return
        if (!answer.hasNothing() || (answer as? SourceAnswer.Failed)?.waitSeconds != null) return
        ask(source)
    }

    /** Shows the editions [subOrDub] allows, on this page alone. */
    public fun showEdition(subOrDub: SubOrDub) {
        mutableState.update { it.copy(subOrDub = subOrDub) }
    }

    /** Shows only the source with [sourceId], or all of them for null. */
    public fun filter(sourceId: String?) {
        mutableState.update { it.copy(filter = sourceId) }
    }
}

private fun SourceAnswer.hasNothing() = this is SourceAnswer.Failed || (this as? SourceAnswer.Found)?.servers?.isEmpty() == true

/** [source] with its [answer], in its place, or at the end the first time it's asked. */
private fun ServersState.answered(
    source: InstalledSource,
    answer: SourceAnswer,
): ServersState {
    val entry = SourceServers(source, answer)
    val at = sources.indexOfFirst { it.source == source }
    return copy(sources = if (at < 0) sources + entry else sources.toMutableList().also { it[at] = entry })
}
