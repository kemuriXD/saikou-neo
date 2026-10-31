package ani.saikou.neo.shared.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.catalogue.AnimeDetails
import ani.saikou.neo.shared.settings.SubOrDub
import ani.saikou.neo.shared.sources.InstalledSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What one source has said about the episode so far. */
public sealed interface SourceAnswer {
    public data object Looking : SourceAnswer

    /** The servers the setting shows. Empty when the source has none, or none of the edition chosen. */
    public data class Found(
        val servers: List<FoundServer>,
    ) : SourceAnswer

    public data object Failed : SourceAnswer
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

public data class ServersState(
    /** Each source, in the order they answered, then those still looking. */
    val sources: List<SourceServers>,
    val best: BestPick? = null,
    /** The server last picked for this show, which may not be offered this time. */
    val lastUsed: LastServer? = null,
    /** The id of the one source shown, or null for all of them. */
    val filter: String? = null,
    val subOrDub: SubOrDub = SubOrDub.BOTH,
) {
    /** The sources the filter shows. */
    val shown: List<SourceServers> get() = if (filter == null) sources else sources.filter { it.source.id == filter }

    /** How many sources have answered, with servers or not. */
    val answered: Int get() = sources.count { it.answer != SourceAnswer.Looking }
}

/**
 * The servers for [episode] of [show], asking every installed source that can be used at the same time. Each one is
 * shown as it answers, so those still looking don't hold up the rest. Only the servers [subOrDub] allows are kept.
 * The server picked last for the show, from [lastServers], is the best pick when it's offered.
 */
public class ServersViewModel(
    finder: ServerFinder,
    sources: List<InstalledSource>,
    private val show: AnimeDetails,
    episode: Int,
    private val subOrDub: SubOrDub,
    private val lastServers: LastServers,
) : ViewModel() {
    private val usable = sources.filter { it.problem == null }
    private val mutableState =
        MutableStateFlow(ServersState(usable.map { SourceServers(it, SourceAnswer.Looking) }, subOrDub = subOrDub))
    public val state: StateFlow<ServersState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val last = lastServers.get(show.card.id)
            mutableState.update { it.copy(lastUsed = last).withBest() }
        }
        for (source in usable) {
            viewModelScope.launch {
                val answer =
                    try {
                        SourceAnswer.Found(finder.servers(source, show, episode).filter { subOrDub.shows(it.edition) })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        SourceAnswer.Failed
                    }
                mutableState.update { it.answered(source, answer).withBest() }
            }
        }
    }

    /** Remembers [server] from [source] as the one last used for this show. */
    public fun pick(
        source: InstalledSource,
        server: FoundServer,
    ) {
        val last = LastServer(source.id, server.name)
        mutableState.update { it.copy(lastUsed = last) }
        viewModelScope.launch { lastServers.set(show.card.id, last) }
    }

    /** Shows only the source with [sourceId], or all of them for null. */
    public fun filter(sourceId: String?) {
        mutableState.update { it.copy(filter = sourceId) }
    }
}

/** [source] moved after the others that have answered, with its [answer]. */
private fun ServersState.answered(
    source: InstalledSource,
    answer: SourceAnswer,
): ServersState {
    val (done, looking) = sources.filter { it.source != source }.partition { it.answer != SourceAnswer.Looking }
    return copy(sources = done + SourceServers(source, answer) + looking)
}

// The best pick only changes as sources answer, not when the viewer picks, so it doesn't jump under them.
private fun ServersState.withBest() = copy(best = bestPick(sources, lastUsed))
