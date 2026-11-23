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

/**
 * An installed source and what the last check found for it. [failed] when its last update didn't install, [installing]
 * while its update installs, [waiting] while it waits its turn, and [updatedTo] the version an update brought it to.
 */
public data class SourceUpdate(
    val source: InstalledSource,
    val status: UpdateStatus,
    val failed: Boolean = false,
    val installing: Boolean = false,
    val waiting: Boolean = false,
    val updatedTo: String? = null,
)

/** Where the time of the last check is kept, so the app checks at most every 12 hours across restarts. */
public interface UpdateCheckTimes {
    /** When sources were last checked, in seconds since 1970, or null if they never were. */
    public suspend fun lastChecked(): Long?

    /** Keeps [at] as when sources were last checked. */
    public suspend fun checked(at: Long)
}

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
    private val times: UpdateCheckTimes? = null,
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

    /** Checks, as [check] does, unless the last check was less than 12 hours ago (ADR 0046). */
    public fun checkIfDue() {
        if (checks?.isActive == true) return
        viewModelScope.launch {
            val last = times?.lastChecked()
            if (last == null || now() - last >= CHECK_EVERY_SECONDS) check()
        }
    }

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
                val at = now()
                run.update { it.copy(checking = false, lastChecked = at) }
                times?.checked(at)
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
        val ids = updates.map { it.id }
        run.update { it.copy(failed = it.failed - ids.toSet(), queue = ids) }
        installs =
            viewModelScope.launch {
                updates.forEachIndexed { done, queued ->
                    run.update { it.copy(progress = Progress(done, updates.size), queue = ids.drop(done)) }
                    // Read again, so one removed or updated some other way while it waited is left as it is now.
                    val source =
                        store.installed.value
                            .orEmpty()
                            .firstOrNull { it.id == queued.id }
                    if (source == null || updateStatus(source, found, store::problemFor) !is UpdateStatus.Available) return@forEachIndexed
                    val version = install(source, found)
                    run.update {
                        if (version == null) {
                            it.copy(failed = it.failed + source.id)
                        } else {
                            it.copy(updated = it.updated + (source.id to version))
                        }
                    }
                }
                run.update { it.copy(progress = null, queue = emptyList()) }
            }
    }

    /**
     * Installs the version of [source] its list names in [found], through the normal install, keeping the list's link.
     * The version installed, or null when it couldn't be fetched or isn't the source its entry names, which keeps the
     * old version, or when what it installed is no newer than [source], so the update would be offered again forever.
     */
    private suspend fun install(
        source: InstalledSource,
        found: Map<String, List<ListedSource>>,
    ): String? {
        val link = source.list ?: return null
        val listed = found[link]?.firstOrNull { it.id == source.id } ?: return null
        val apk = downloads.fetch(listed.apk) ?: return null
        val installed = store.install(apk, source.id, link) ?: return null
        return installed.version.takeIf { installed.problem == null && compareVersions(it, source.version) > 0 }
    }

    private fun stateOf(
        installed: List<InstalledSource>?,
        lists: Map<String, List<ListedSource>>,
        run: Run,
    ) = UpdatesState(
        sources =
            installed.orEmpty().map {
                SourceUpdate(
                    it,
                    updateStatus(it, lists, store::problemFor),
                    failed = it.id in run.failed,
                    installing = run.queue.firstOrNull() == it.id,
                    waiting = it.id in run.queue.drop(1),
                    updatedTo = run.updated[it.id],
                )
            },
        checking = run.checking,
        progress = run.progress,
        lastChecked = run.lastChecked,
    )

    /**
     * How checks and updates are going, which sources' last update failed, the ids still to update, the first being
     * installed, and the version each updated source got.
     */
    private data class Run(
        val checking: Boolean = false,
        val progress: Progress? = null,
        val lastChecked: Long? = null,
        val failed: Set<String> = emptySet(),
        val queue: List<String> = emptyList(),
        val updated: Map<String, String> = emptyMap(),
    )
}

private const val CHECK_EVERY_SECONDS = 12 * 60 * 60L
