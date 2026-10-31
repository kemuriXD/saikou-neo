package ani.saikou.neo.shared.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.settings.SubOrDub
import ani.saikou.neo.shared.sources.InstalledSource
import kotlinx.coroutines.CancellationException
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

    /** The servers the setting shows, and how many it [hidden]. Empty when the source has none, or none of the edition chosen. */
    public data class Found(
        val servers: List<FoundServer>,
        val hidden: Int = 0,
    ) : SourceAnswer

    public data class Failed(
        val reason: FailReason,
    ) : SourceAnswer
}

public data class SourceServers(
    val source: InstalledSource,
    val answer: SourceAnswer,
)

/** The server suggested first, and why: it was [lastUsed], or its source [answeredFirst] of several. */
public data class BestPick(
    val source: InstalledSource,
    val server: FoundServer,
    val lastUsed: Boolean,
    val answeredFirst: Boolean,
)

/** Why the page has nothing to pick, once it knows. */
public sealed interface StreamsOutcome {
    /** No source is installed that can be used. */
    public data object NoSources : StreamsOutcome

    /** Every source answered, and the only servers were of the edition the setting hides: [have] rather than [missing]. */
    public data class MissingEdition(
        val missing: ServerEdition,
        val have: ServerEdition,
    ) : StreamsOutcome

    /** Every source answered, and each failed or had no servers. */
    public data object NothingPlays : StreamsOutcome
}

public data class ServersState(
    /** Each source, in the order they answered, then those still looking. */
    val sources: List<SourceServers>,
    val best: BestPick? = null,
    /** The server last picked for this show, which may not be offered this time. */
    val lastUsed: LastServer? = null,
    /** The id of the one source shown, or null for all of them. */
    val filter: String? = null,
    val subOrDub: SubOrDub = SubOrDub.BOTH,
    /** Whether the installed sources have been read, so [sources] being empty means there are none. */
    val ready: Boolean = false,
) {
    /** The sources the filter shows. */
    val shown: List<SourceServers> get() = if (filter == null) sources else sources.filter { it.source.id == filter }

    /** How many sources have answered, with servers or not. */
    val answered: Int get() = sources.count { it.answer != SourceAnswer.Looking }

    /** Why there's nothing to pick, or null while there may still be something, or is. */
    val outcome: StreamsOutcome? get() {
        if (!ready) return null
        if (sources.isEmpty()) return StreamsOutcome.NoSources
        val answers = sources.map { it.answer }
        if (answers.any { it == SourceAnswer.Looking || (it is SourceAnswer.Found && it.servers.isNotEmpty()) }) return null
        val hidden = answers.any { it is SourceAnswer.Found && it.hidden > 0 }
        if (hidden && answers.none { it is SourceAnswer.Failed }) {
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
 * shown as it answers, so those still looking don't hold up the rest. Only the servers [subOrDub] allows are kept.
 * The server picked last for the show, from [lastServers], is the best pick when it's offered. The sources asked are
 * those [installed] holds once they've been read, or the first it holds after there were none. A source that hasn't answered within [answerWithin] has no answer.
 */
public class ServersViewModel(
    private val finder: ServerFinder,
    installed: Flow<List<InstalledSource>?>,
    private val show: AnimeDetails,
    private val episode: Int,
    private val subOrDub: SubOrDub,
    private val lastServers: LastServers,
    private val answerWithin: Duration = ANSWER_WITHIN,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ServersState(emptyList(), subOrDub = subOrDub))
    public val state: StateFlow<ServersState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val last = lastServers.get(show.card.id)
            mutableState.update { it.copy(lastUsed = last).withBest() }
        }
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
        mutableState.update { it.answered(source, SourceAnswer.Looking).copy(ready = true).withBest() }
        viewModelScope.launch {
            val answer =
                try {
                    withTimeoutOrNull(answerWithin) { finder.servers(source, show, episode) }
                        ?.let { all ->
                            val shown = all.filter { subOrDub.shows(it.edition) }
                            SourceAnswer.Found(shown, hidden = all.size - shown.size)
                        } ?: SourceAnswer.Failed(FailReason.NoAnswer)
                } catch (_: CancellationException) {
                    // Leaving the page cancels the search. A source's own timeout fails only that source.
                    ensureActive()
                    SourceAnswer.Failed(FailReason.SourceDown)
                } catch (e: SourceFailure) {
                    SourceAnswer.Failed(e.reason)
                } catch (_: Exception) {
                    SourceAnswer.Failed(FailReason.SourceDown)
                }
            mutableState.update { it.answered(source, answer).withBest() }
        }
    }

    /** Asks again every source that failed or had nothing to show. Those still looking or with servers are left alone. */
    public fun retry() {
        state.value.sources
            .filter { it.answer is SourceAnswer.Failed || (it.answer as? SourceAnswer.Found)?.servers?.isEmpty() == true }
            .forEach { ask(it.source) }
    }

    /** Remembers [server] from [source] as the one last used for this show. */
    public fun pick(
        source: InstalledSource,
        server: FoundServer,
    ) {
        val last = LastServer(source.id, server.name, server.edition)
        mutableState.update { it.copy(lastUsed = last) }
        viewModelScope.launch { lastServers.set(show.card.id, last) }
    }

    /** Shows only the source with [sourceId], or all of them for null. */
    public fun filter(sourceId: String?) {
        mutableState.update { it.copy(filter = sourceId) }
    }
}

/** [source] moved after the others that have answered, with its [answer], or after the rest while it's looking again. */
private fun ServersState.answered(
    source: InstalledSource,
    answer: SourceAnswer,
): ServersState {
    val (done, looking) = sources.filter { it.source != source }.partition { it.answer != SourceAnswer.Looking }
    // One asked again goes to the end, after those still looking.
    val entry = SourceServers(source, answer)
    return copy(sources = if (answer == SourceAnswer.Looking) done + looking + entry else done + entry + looking)
}

// The best pick only changes as sources answer, not when the viewer picks, so it doesn't jump under them.
private fun ServersState.withBest() = copy(best = bestPick(sources, lastUsed))
