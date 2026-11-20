package ani.saikou.neo.shared.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A notice on screen, or what's left of it once Undo [failed]. */
public data class ShownNotice(
    val notice: ListNotice,
    val failed: Boolean = false,
)

/**
 * The AniList notice with Undo (ADR 0056), for as long as the app runs, so it outlives the player that made it. One shows
 * at a time, for [showFor] or while Undo has focus. One from watching waits while the player is full screen. [undo] puts a
 * change back, and says whether it did.
 */
public class ListNotices(
    scope: CoroutineScope,
    notices: Flow<ListNotice>,
    private val showFor: Duration = SHOW_FOR,
    private val undo: suspend (ListNotice) -> Boolean,
) {
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val showing = MutableStateFlow<ShownNotice?>(null)

    public val shown: StateFlow<ShownNotice?> = showing.asStateFlow()

    init {
        scope.launch { notices.collect { events.send(Event.Arrived(it)) } }
        scope.launch { run(this) }
    }

    /** The player is on screen, full screen or not. Notices from watching wait while it is. */
    public fun playing(fullScreen: Boolean) {
        events.trySend(Event.Playing(fullScreen))
    }

    /** Undo has focus, so the notice stays. */
    public fun undoFocused(focused: Boolean) {
        events.trySend(Event.Focused(focused))
    }

    /** Undo was pressed. It runs once per notice. */
    public fun undo() {
        events.trySend(Event.Undo)
    }

    // Every change goes through one loop, so the timer, focus and Undo can't race.
    private suspend fun run(scope: CoroutineScope) {
        var held: ListNotice? = null
        var fullScreen = false
        var focused = false
        var undoing = false
        var timer: Job? = null
        var shownAt = 0

        fun show(notice: ShownNotice?) {
            shownAt++
            showing.value = notice
            focused = false
            undoing = false
            timer?.cancel()
            val at = shownAt
            timer = notice?.let { scope.launch { expireAfter(at) } }
        }

        for (event in events) {
            when (event) {
                is Event.Arrived -> {
                    if (event.notice.fromWatching && fullScreen) held = event.notice else show(ShownNotice(event.notice))
                }

                is Event.Playing -> {
                    fullScreen = event.fullScreen
                    if (!fullScreen) held?.let { show(ShownNotice(it)) }
                    if (!fullScreen) held = null
                }

                is Event.Focused -> {
                    if (showing.value == null) continue
                    focused = event.focused
                    timer?.cancel()
                    val at = shownAt
                    timer = if (focused) null else scope.launch { expireAfter(at) }
                }

                Event.Undo -> {
                    val now = showing.value
                    if (now == null || now.failed || undoing) continue
                    undoing = true
                    val at = shownAt
                    scope.launch { events.send(Event.Undone(at, putBack(now.notice))) }
                }

                is Event.Undone -> {
                    val now = showing.value
                    if (event.at == shownAt && now != null) show(if (event.putBack) null else now.copy(failed = true))
                }

                is Event.Expired -> {
                    if (event.at == shownAt && !focused) show(null)
                }
            }
        }
    }

    private suspend fun expireAfter(at: Int) {
        delay(showFor)
        events.send(Event.Expired(at))
    }

    private suspend fun putBack(notice: ListNotice): Boolean =
        try {
            undo(notice)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }

    private sealed interface Event {
        data class Arrived(
            val notice: ListNotice,
        ) : Event

        data class Playing(
            val fullScreen: Boolean,
        ) : Event

        data class Focused(
            val focused: Boolean,
        ) : Event

        data object Undo : Event

        data class Undone(
            val at: Int,
            val putBack: Boolean,
        ) : Event

        data class Expired(
            val at: Int,
        ) : Event
    }
}

private val SHOW_FOR = 8.seconds
