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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** An installed source and what the last check found for it. [failed] when its last update didn't install. */
public data class SourceUpdate(
    val source: InstalledSource,
    val status: UpdateStatus,
    val failed: Boolean = false,
)

/**
 * Updates for the installed sources (ADR 0046): each one, in the store's order, with what the last check found.
 * [checking] while lists are read, [progress] while updates install, and [lastChecked] is when the last check finished,
 * in seconds since 1970, or null before the first.
 */
public data class UpdatesState(
    val sources: List<SourceUpdate> = emptyList(),
    val checking: Boolean = false,
    val progress: Progress? = null,
    val lastChecked: Long? = null,
) {
    /** How many sources have an update to offer. */
    val updates: Int get() = sources.count { it.status is UpdateStatus.Available }
}

/**
 * Checks the lists sources came from for newer versions, and installs them when asked. Nothing installs without a
 * press, because a new version can break a source that works (ADR 0046).
 */
public class SourceUpdatesViewModel(
    private val store: SourceStore,
    private val downloads: SourceDownloads,
    // Where a list is read, off the main thread.
    work: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = { Clock.System.now().epochSeconds },
) : ViewModel() {
    private val reader = SourceUpdates(downloads, work)

    // What each list named when it was last read, by link.
    private val lists = MutableStateFlow<Map<String, List<ListedSource>>>(emptyMap())
    private val run = MutableStateFlow(Run())
    private var checks: Job? = null
    private var installs: Job? = null

    public val state: StateFlow<UpdatesState> =
        combine(store.installed, lists, run, ::stateOf)
            .stateIn(viewModelScope, SharingStarted.Eagerly, stateOf(store.installed.value, lists.value, run.value))

    /** Reads the list of every source that has one, each once. Does nothing while a check is running. */
    public fun check() {
        if (checks?.isActive == true) return
        run.update { it.copy(checking = true) }
        checks =
            viewModelScope.launch {
                val installed = store.installed.filterNotNull().first()
                val read = reader.lists(installed.mapNotNullTo(mutableSetOf()) { it.list })
                // A list that couldn't be read this time keeps what it said before.
                lists.update { it + read }
                run.update { it.copy(checking = false, lastChecked = now()) }
            }
    }

    /** Installs the update for the source with [id], if it has one. */
    public fun update(id: String) {
        install(setOf(id))
    }

    /** Installs every update, one at a time. One that fails doesn't stop the rest. */
    public fun updateAll() {
        install(null)
    }

    /** Installs the updates of the sources with [ids], or of all of them when null. Does nothing while updates install. */
    private fun install(ids: Set<String>?) {
        if (installs?.isActive == true) return
        val found = lists.value
        val updates =
            store.installed.value.orEmpty().filter {
                (ids == null || it.id in ids) && updateStatus(it, found, store::problemFor) is UpdateStatus.Available
            }
        if (updates.isEmpty()) return
        run.update { it.copy(failed = it.failed - updates.map { source -> source.id }.toSet()) }
        installs =
            viewModelScope.launch {
                updates.forEachIndexed { done, source ->
                    run.update { it.copy(progress = Progress(done, updates.size)) }
                    if (!install(source, found)) run.update { it.copy(failed = it.failed + source.id) }
                }
                run.update { it.copy(progress = null) }
            }
    }

    /**
     * Installs the version of [source] its list names in [found], through the normal install, keeping the list's link.
     * False when it couldn't be fetched or isn't the source its entry names, which keeps the old version.
     */
    private suspend fun install(
        source: InstalledSource,
        found: Map<String, List<ListedSource>>,
    ): Boolean {
        val link = source.list ?: return false
        val listed = found[link]?.firstOrNull { it.id == source.id } ?: return false
        val apk = downloads.fetch(listed.apk) ?: return false
        val installed = store.install(apk, source.id, link) ?: return false
        return installed.problem == null
    }

    private fun stateOf(
        installed: List<InstalledSource>?,
        lists: Map<String, List<ListedSource>>,
        run: Run,
    ) = UpdatesState(
        sources = installed.orEmpty().map { SourceUpdate(it, updateStatus(it, lists, store::problemFor), it.id in run.failed) },
        checking = run.checking,
        progress = run.progress,
        lastChecked = run.lastChecked,
    )

    /** How checks and updates are going, and which sources' last update failed. */
    private data class Run(
        val checking: Boolean = false,
        val progress: Progress? = null,
        val lastChecked: Long? = null,
        val failed: Set<String> = emptySet(),
    )
}
