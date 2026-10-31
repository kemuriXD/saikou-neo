package ani.saikou.neo.shared.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Why a link or code didn't add a source. */
public enum class AddError { NO_SUCH_CODE, NOT_SECURE, NOT_A_SOURCE, BROKEN_LIST, NOTHING_IN_LIST }

/** What a source in a list is to this app. */
public sealed interface ChoiceStatus {
    public data object New : ChoiceStatus

    /** An older version, [from], is installed. */
    public data class Update(
        val from: String,
    ) : ChoiceStatus

    /** The same or a newer version is installed. */
    public data object Installed : ChoiceStatus

    public data class CantUse(
        val problem: SourceProblem,
    ) : ChoiceStatus

    /** It was picked, but couldn't be fetched, or its APK isn't the source the list names. */
    public data object Failed : ChoiceStatus
}

/** Whether a source with this status can be ticked. */
public val ChoiceStatus.canPick: Boolean
    get() = this is ChoiceStatus.New || this is ChoiceStatus.Update || this is ChoiceStatus.Failed

/** A source in a list, and whether it's ticked. */
public data class Choice(
    val source: ListedSource,
    val status: ChoiceStatus,
    val ticked: Boolean = false,
)

/** How many of the [total] ticked sources are [done]. */
public data class Progress(
    val done: Int,
    val total: Int,
)

/** Where adding a source has got to. */
public sealed interface Adding {
    public data object Idle : Adding

    public data object Checking : Adding

    public data class Failed(
        val error: AddError,
    ) : Adding

    public data class Added(
        val source: InstalledSource,
    ) : Adding

    /** The source was built for another version of the app, so it wasn't installed. */
    public data class CantUse(
        val source: InstalledSource,
    ) : Adding

    /** The link led to a list. Sources you can pick come first, then installed ones, then ones you can't use. */
    public data class Choosing(
        val choices: List<Choice>,
        val progress: Progress? = null,
    ) : Adding

    /** Every ticked source in a list was added. */
    public data class AddedAll(
        val sources: List<InstalledSource>,
    ) : Adding
}

/** Settings > Sources: the installed sources, or null until they're read, and how adding one is going. */
public data class SourcesState(
    val installed: List<InstalledSource>? = null,
    val adding: Adding = Adding.Idle,
)

/** Adds sources from a link or a short code, and removes them. */
public class SourcesViewModel(
    private val store: SourceStore,
    private val codes: ShortCodes,
    private val downloads: SourceDownloads,
) : ViewModel() {
    private val adding = MutableStateFlow<Adding>(Adding.Idle)
    private var check: Job? = null

    public val state: StateFlow<SourcesState> =
        combine(store.installed, adding, ::SourcesState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, SourcesState(store.installed.value, Adding.Idle))

    /** Adds the source that [text], a link or a short code, leads to. */
    public fun add(text: String) {
        check?.cancel()
        adding.value = Adding.Checking
        check = viewModelScope.launch { adding.value = addFrom(text) }
    }

    private suspend fun addFrom(text: String): Adding {
        val url =
            when (val address = SourceAddress.parse(text)) {
                is SourceAddress.Link -> address.url
                is SourceAddress.Code -> codes.link(address.code)
                null -> null
            } ?: return Adding.Failed(AddError.NO_SUCH_CODE)
        if (!isAllowedSourceLink(url)) return Adding.Failed(AddError.NOT_SECURE)
        val file = downloads.fetch(url) ?: return Adding.Failed(AddError.NOT_A_SOURCE)
        if (isSourceList(file)) return choose(file)
        val source = store.install(file) ?: return Adding.Failed(AddError.NOT_A_SOURCE)
        return if (source.problem == null) Adding.Added(source) else Adding.CantUse(source)
    }

    private fun choose(file: ByteArray): Adding {
        val listed = readSourceList(file) ?: return Adding.Failed(AddError.BROKEN_LIST)
        val installed =
            store.installed.value
                .orEmpty()
                .associateBy { it.id }
        val choices =
            listed
                .map { Choice(it, status(it, installed[it.id])) }
                .sortedBy {
                    when {
                        it.status.canPick -> 0
                        it.status == ChoiceStatus.Installed -> 1
                        else -> 2
                    }
                }
        if (choices.all { it.status is ChoiceStatus.CantUse }) return Adding.Failed(AddError.NOTHING_IN_LIST)
        return Adding.Choosing(choices)
    }

    private fun status(
        source: ListedSource,
        installed: InstalledSource?,
    ): ChoiceStatus {
        store.problemFor(source.api)?.let { return ChoiceStatus.CantUse(it) }
        return when {
            installed == null -> ChoiceStatus.New
            compareVersions(source.version, installed.version) > 0 -> ChoiceStatus.Update(installed.version)
            else -> ChoiceStatus.Installed
        }
    }

    /** Ticks or unticks the source with [id] in the list, unless it can't be picked or sources are being added. */
    public fun toggle(id: String) {
        val choosing = adding.value as? Adding.Choosing ?: return
        if (choosing.progress != null) return
        adding.value =
            choosing.copy(
                choices = choosing.choices.map { if (it.source.id == id && it.status.canPick) it.copy(ticked = !it.ticked) else it },
            )
    }

    /** Adds the ticked sources one at a time. A source that fails stays ticked, with why; the rest are installed. */
    public fun addTicked() {
        val choosing = adding.value as? Adding.Choosing ?: return
        val ticked = choosing.choices.filter { it.ticked }
        if (choosing.progress != null || ticked.isEmpty()) return
        check?.cancel()
        check =
            viewModelScope.launch {
                var choices = choosing.choices
                val added = mutableListOf<InstalledSource>()
                ticked.forEachIndexed { done, choice ->
                    adding.value = Adding.Choosing(choices, Progress(done, ticked.size))
                    // The APK must be the source its entry names, so a list can't install something else.
                    val source = downloads.fetch(choice.source.apk)?.let { store.install(it, choice.source.id) }
                    val status =
                        when {
                            source == null -> ChoiceStatus.Failed
                            source.problem != null -> ChoiceStatus.CantUse(source.problem)
                            else -> ChoiceStatus.Installed.also { added += source }
                        }
                    choices =
                        choices.map {
                            if (it.source.id ==
                                choice.source.id
                            ) {
                                Choice(it.source, status, status == ChoiceStatus.Failed)
                            } else {
                                it
                            }
                        }
                }
                adding.value = if (added.size == ticked.size) Adding.AddedAll(added) else Adding.Choosing(choices)
            }
    }

    /** Stops any check, and clears what the last one found, as when its panel closes. */
    public fun dismiss() {
        check?.cancel()
        adding.value = Adding.Idle
    }

    /** Uninstalls the source with [id]. */
    public fun remove(id: String) {
        viewModelScope.launch { store.remove(id) }
    }
}

/** Compares versions such as `1.10.0` and `1.2.0` part by part, as numbers. A part that isn't one counts as 0. */
private fun compareVersions(
    a: String,
    b: String,
): Int {
    val first = a.split('.', '-')
    val second = b.split('.', '-')
    for (i in 0 until maxOf(first.size, second.size)) {
        val difference = (first.getOrNull(i)?.toIntOrNull() ?: 0).compareTo(second.getOrNull(i)?.toIntOrNull() ?: 0)
        if (difference != 0) return difference
    }
    return 0
}
