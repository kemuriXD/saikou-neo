package ani.saikou.neo.shared.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The AniList calls behind the list button, which [AniListSync] makes. */
public interface YourListEntries {
    /** Each change made from this TV, so every button for the show agrees. */
    public val changes: Flow<ListChange>

    public suspend fun entry(animeId: Int): OnYourList?

    public suspend fun changeStatus(
        animeId: Int,
        to: ListStatus,
        forUser: Int,
    ): Boolean

    public suspend fun remove(
        animeId: Int,
        forUser: Int,
    ): Boolean
}

public sealed interface ListButtonState {
    /** Signed out, or the entry couldn't be read: there's no button. */
    public data object Hidden : ListButtonState

    /** The show's [status] on the list, or null when it isn't on it, and how many [episodes] AniList knows of. */
    public data class Shown(
        val status: ListStatus?,
        val episodes: Int?,
    ) : ListButtonState
}

/**
 * The list button on Home's spotlight and Details, for one show (ADR 0051). It reads the signed-in user's entry, and a
 * status picked or a remove shows straight away, goes to AniList for that user, and goes back if AniList doesn't take
 * it. [changed] runs once AniList has, so the kept list is read again. A change from another button for the show shows
 * here too.
 */
public class ListButtonViewModel(
    private val animeId: Int,
    private val entries: YourListEntries,
    private val changed: suspend () -> Unit = {},
) : ViewModel() {
    private val mutableState = MutableStateFlow<ListButtonState>(ListButtonState.Hidden)
    public val state: StateFlow<ListButtonState> = mutableState.asStateFlow()

    // The user whose entry is shown. Changes are only sent for them.
    private var userId: Int? = null
    private val lock = Mutex()

    init {
        viewModelScope.launch {
            val read = entries.entry(animeId) ?: return@launch
            userId = read.userId
            mutableState.value = ListButtonState.Shown(read.show.entry?.status, read.show.episodes)
            entries.changes
                .filter { it.animeId == animeId && it.userId == read.userId }
                .collect { change -> mutableState.update { (it as? ListButtonState.Shown)?.copy(status = change.status) ?: it } }
        }
    }

    public fun pick(status: ListStatus): Unit = change(status) { entries.changeStatus(animeId, status, it) }

    public fun remove(): Unit = change(null) { entries.remove(animeId, it) }

    /** Picks [status], or removes the show with null, as the list panel says. */
    public fun change(status: ListStatus?): Unit = if (status == null) remove() else pick(status)

    private fun change(
        to: ListStatus?,
        send: suspend (userId: Int) -> Boolean,
    ) {
        val user = userId ?: return
        val before = mutableState.value as? ListButtonState.Shown ?: return
        if (before.status == to) return
        mutableState.value = before.copy(status = to)
        viewModelScope.launch {
            lock.withLock {
                if (send(user)) {
                    changed()
                } else {
                    // Unless another pick has shown since.
                    mutableState.update { if ((it as? ListButtonState.Shown)?.status == to) before else it }
                }
            }
        }
    }
}
