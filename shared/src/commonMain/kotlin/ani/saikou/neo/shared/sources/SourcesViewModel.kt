package ani.saikou.neo.shared.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Why a link or code didn't add a source. */
public enum class AddError { NO_SUCH_CODE, NOT_SECURE, NOT_A_SOURCE, BROKEN_LIST, NOTHING_IN_LIST, ALL_INSTALLED }

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

/**
 * Settings > Sources: the installed sources, or null until they're read, and how adding one is going. [icons] holds
 * the PNG logos of the sources in a list being chosen from, by id, as they arrive (ADR 0028).
 */
public data class SourcesState(
    val installed: List<InstalledSource>? = null,
    val adding: Adding = Adding.Idle,
    val icons: Map<String, ByteArray> = emptyMap(),
)

/** Adds sources from a link or a short code, and removes them. */
public class SourcesViewModel(
    private val store: SourceStore,
    private val codes: ShortCodes,
    private val downloads: SourceDownloads,
    // Where a list is read, off the main thread.
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val adding = MutableStateFlow<Adding>(Adding.Idle)
    private val icons = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    private var check: Job? = null

    // Kept apart from the check, so adding the ticked sources doesn't stop the logos arriving.
    private var iconLoads: Job? = null

    public val state: StateFlow<SourcesState> =
        combine(store.installed, adding, icons, ::SourcesState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, SourcesState(store.installed.value, Adding.Idle))

    /** Adds the source that [text], a link or a short code, leads to. */
    public fun add(text: String) {
        check?.cancel()
        dropIcons()
        adding.value = Adding.Checking
        check =
            viewModelScope.launch {
                val result = addFrom(text)
                adding.value = result
                if (result is Adding.Choosing) loadIcons(result.choices)
            }
    }

    /** Fetches the logo of each source in a list that names one, all at once. One that fails is simply left out. */
    private fun loadIcons(choices: List<Choice>) {
        iconLoads =
            viewModelScope.launch {
                for (choice in choices) {
                    val link = choice.source.icon ?: continue
                    launch { downloads.fetchIcon(link)?.let { icon -> icons.update { it + (choice.source.id to icon) } } }
                }
            }
    }

    private fun dropIcons() {
        iconLoads?.cancel()
        icons.value = emptyMap()
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
        if (isSourceList(file)) return choose(withContext(work) { readSourceList(file) })
        val source = store.install(file) ?: return Adding.Failed(AddError.NOT_A_SOURCE)
        return if (source.problem == null) Adding.Added(source) else Adding.CantUse(source)
    }

    private fun choose(read: List<ListedSource>?): Adding {
        val listed = read ?: return Adding.Failed(AddError.BROKEN_LIST)
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
        if (choices.none { it.status.canPick }) return Adding.Failed(AddError.ALL_INSTALLED)
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
                    choices = choices.withStatus(choice.source.id, status)
                }
                adding.value = if (added.size == ticked.size) Adding.AddedAll(added) else Adding.Choosing(choices)
            }
    }

    /** Stops any check, and clears what the last one found, as when its panel closes. Sources being added still are. */
    public fun dismiss() {
        if ((adding.value as? Adding.Choosing)?.progress != null) return
        check?.cancel()
        dropIcons()
        adding.value = Adding.Idle
    }

    /** Uninstalls the source with [id]. */
    public fun remove(id: String) {
        viewModelScope.launch { store.remove(id) }
    }
}

/** [this] with the source [id] given [status]. A source that failed stays ticked, so Add tries it again. */
private fun List<Choice>.withStatus(
    id: String,
    status: ChoiceStatus,
) = map { if (it.source.id == id) Choice(it.source, status, ticked = status == ChoiceStatus.Failed) else it }

/**
 * Compares versions such as `1.10.0` and `1.2.0` part by part, as numbers, ignoring a leading `v`. A part that isn't a
 * number counts as 0. A pre-release, such as `2.0.0-beta.1`, comes before its release.
 */
private fun compareVersions(
    a: String,
    b: String,
): Int {
    val first = a.removePrefix("v").split('-', '+', limit = 2)
    val second = b.removePrefix("v").split('-', '+', limit = 2)
    val numbers = first[0].split('.')
    val others = second[0].split('.')
    for (i in 0 until maxOf(numbers.size, others.size)) {
        val difference = (numbers.getOrNull(i)?.toIntOrNull() ?: 0).compareTo(others.getOrNull(i)?.toIntOrNull() ?: 0)
        if (difference != 0) return difference
    }
    // Only one is a pre-release: that one is older.
    return (first.size == 1).compareTo(second.size == 1)
}
