package ani.saikou.neo.shared.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.account.AccountStore
import ani.saikou.neo.shared.account.SignedInAccount
import ani.saikou.neo.shared.catalogue.AniListError
import ani.saikou.neo.shared.catalogue.AniListResult
import ani.saikou.neo.shared.catalogue.AnimeScheduleError
import ani.saikou.neo.shared.catalogue.AnimeScheduleResult
import ani.saikou.neo.shared.catalogue.LoadProblem
import ani.saikou.neo.shared.catalogue.ShowLookup
import ani.saikou.neo.shared.catalogue.problem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant

/** Which shows Schedule lists when you're signed in. */
public enum class ScheduleFilter { YOUR_LIST, ALL_SHOWS }

/** Remembers the Schedule toggle. Calls block, so make them off the main thread. */
public interface ScheduleFilterStore {
    /** The last choice, or null when there's never been one. */
    public fun readFilter(): ScheduleFilter?

    public fun saveFilter(filter: ScheduleFilter)
}

/** What Schedule shows. */
public sealed interface ScheduleState {
    public data object Loading : ScheduleState

    /** This build has no AnimeSchedule.net token, so Schedule isn't available. */
    public data object NoToken : ScheduleState

    public data class Failed(
        val problem: LoadProblem,
    ) : ScheduleState

    public data class Ready(
        /** Monday to Sunday. Each day's rows, and so its count, follow [filter]. */
        val days: List<ScheduleDay>,
        val today: Int,
        val selectedDay: Int,
        /** Null when signed out, so there's no toggle. */
        val filter: ScheduleFilter?,
    ) : ScheduleState
}

/** What selecting a row leads to. */
public sealed interface ScheduleEvent {
    public data class OpenDetails(
        val aniListId: Int,
    ) : ScheduleEvent

    /** The show has no AniList match. With [tryAgain], the lookup failed and may work later. */
    public data class CantOpen(
        val tryAgain: Boolean,
    ) : ScheduleEvent
}

/**
 * This week's episodes for Schedule (ADR 0041): every show when signed out, and your AniList list or every show when
 * signed in.
 */
public class ScheduleViewModel(
    private val source: ScheduleSource,
    private val accounts: AccountStore,
    private val filters: ScheduleFilterStore,
    private val zone: ScheduleZone,
    private val now: () -> Instant = { Clock.System.now() },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ScheduleState>(ScheduleState.Loading)
    public val state: StateFlow<ScheduleState> = mutableState.asStateFlow()

    private val mutableEvents = Channel<ScheduleEvent>(Channel.BUFFERED)
    public val events: Flow<ScheduleEvent> = mutableEvents.receiveAsFlow()

    private var rows: List<ScheduleRow> = emptyList()

    /** Routes of the airing shows on your list, with their AniList ids. Null when signed out. */
    private var yourList: Map<String, Int>? = null
    private var filter: ScheduleFilter? = null
    private var loading: Job? = null

    init {
        load()
    }

    /** Switches the toggle and remembers it. */
    public fun showFilter(filter: ScheduleFilter) {
        if (this.filter == null || this.filter == filter) return
        this.filter = filter
        publish()
        viewModelScope.launch { withContext(io) { filters.saveFilter(filter) } }
    }

    public fun selectDay(index: Int) {
        mutableState.update { if (it is ScheduleState.Ready) it.copy(selectedDay = index.coerceIn(it.days.indices)) else it }
    }

    /** Opens [row]'s Details through [events], or says it can't be opened. */
    public fun open(row: ScheduleRow) {
        viewModelScope.launch {
            val event =
                yourList?.get(row.route)?.let { ScheduleEvent.OpenDetails(it) }
                    ?: when (val lookup = source.aniListIdFor(row.route)) {
                        is ShowLookup.Found -> ScheduleEvent.OpenDetails(lookup.value)
                        ShowLookup.NoMatch -> ScheduleEvent.CantOpen(tryAgain = false)
                        is ShowLookup.Failure -> ScheduleEvent.CantOpen(tryAgain = true)
                    }
            mutableEvents.send(event)
        }
    }

    /** Loads the week again after it failed. */
    public fun retry() {
        if (state.value is ScheduleState.Failed) load()
    }

    private fun load() {
        loading?.cancel()
        mutableState.value = ScheduleState.Loading
        loading =
            viewModelScope.launch {
                val account = withContext(io) { accounts.readAccount() }
                when (val week = source.week(zone)) {
                    is AnimeScheduleResult.Success -> rows = scheduleRows(week.episodes)
                    is AnimeScheduleResult.Failure -> return@launch fail(week.error)
                }
                yourList =
                    when (val list = account?.let { yourList(it) }) {
                        null, ListResult.Refused -> null
                        is ListResult.Failed -> return@launch fail(list.problem)
                        is ListResult.Matched -> list.routes
                    }
                filter = if (yourList == null) null else withContext(io) { filters.readFilter() } ?: ScheduleFilter.YOUR_LIST
                publish()
            }
    }

    private sealed interface ListResult {
        data class Matched(
            val routes: Map<String, Int>,
        ) : ListResult

        /** AniList refused the token, so Schedule acts as if signed out. */
        data object Refused : ListResult

        data class Failed(
            val problem: LoadProblem,
        ) : ListResult
    }

    /** Your list's airing shows by route, looked up one at a time. A show whose lookup fails is left out. */
    private suspend fun yourList(account: SignedInAccount): ListResult =
        when (val list = source.watchList(account)) {
            is AniListResult.Failure -> {
                if (list.error == AniListError.SignedOut) ListResult.Refused else ListResult.Failed(list.error.problem)
            }

            is AniListResult.Success -> {
                val routes = mutableMapOf<String, Int>()
                for (show in list.value.filter { it.airing }) {
                    (source.routeFor(show.id) as? ShowLookup.Found)?.let { routes[it.value] = show.id }
                }
                ListResult.Matched(routes)
            }
        }

    private fun fail(error: AnimeScheduleError) {
        mutableState.value = if (error == AnimeScheduleError.NoToken) ScheduleState.NoToken else ScheduleState.Failed(error.problem)
    }

    private fun fail(problem: LoadProblem) {
        mutableState.value = ScheduleState.Failed(problem)
    }

    private fun publish() {
        val list = yourList
        val shown = if (filter == ScheduleFilter.YOUR_LIST && list != null) rows.filter { it.route in list } else rows
        val week = scheduleWeek(shown, zone, now())
        val selected = (state.value as? ScheduleState.Ready)?.selectedDay ?: week.today
        mutableState.value = ScheduleState.Ready(week.days, week.today, selected, filter)
    }
}

private val AnimeScheduleError.problem: LoadProblem
    get() =
        when (this) {
            is AnimeScheduleError.Network -> LoadProblem.NO_NETWORK
            is AnimeScheduleError.RateLimited -> LoadProblem.RATE_LIMITED
            else -> LoadProblem.OTHER
        }
