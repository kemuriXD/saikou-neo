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
public enum class AddError { NO_SUCH_CODE, NOT_SECURE, NOT_A_SOURCE }

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
        val source = downloads.fetch(url)?.let { store.install(it) } ?: return Adding.Failed(AddError.NOT_A_SOURCE)
        return if (source.problem == null) Adding.Added(source) else Adding.CantUse(source)
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
