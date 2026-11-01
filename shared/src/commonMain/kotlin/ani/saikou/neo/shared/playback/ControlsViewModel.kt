package ani.saikou.neo.shared.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/** How far a press of Left or Right seeks. */
public const val SEEK_STEP_MS: Long = 10_000

/** A jump being picked by scrubbing, from where the video was to the [targetMs] OK goes to. */
public data class Scrub(
    val fromMs: Long,
    val targetMs: Long,
) {
    val jumpMs: Long get() = targetMs - fromMs
}

/**
 * What sits over the playing video: the controls when [shown], how far quick presses of Left or Right have seeked in
 * all ([nudgeMs]), and a [scrub] while one is being picked.
 */
public data class ControlsState(
    val shown: Boolean = false,
    val nudgeMs: Long? = null,
    val scrub: Scrub? = null,
)

/**
 * The player's controls. They hide [hideAfter] the last key while the video plays, and stay up while it's paused or a
 * scrub is being picked. Holding Left or Right scrubs faster the longer it's held. The TV app does the seeking.
 */
public class ControlsViewModel(
    private val time: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    private val hideAfter: Duration = HIDE_AFTER,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ControlsState())
    public val state: StateFlow<ControlsState> = mutableState.asStateFlow()

    private var hiding: Job? = null
    private var nudgeEnds: Job? = null
    private var holdStarted: ComparableTimeMark? = null
    private var holdLast: ComparableTimeMark? = null
    private var holdForward = true

    /** Shows the controls. */
    public fun show(playing: Boolean) {
        mutableState.update { it.copy(shown = true) }
        touched(playing)
    }

    /** A key was pressed: the controls stay up another [hideAfter] while [playing]. */
    public fun touched(playing: Boolean) {
        hiding?.cancel()
        if (!playing || !state.value.shown) return
        hiding =
            viewModelScope.launch {
                delay(hideAfter)
                if (state.value.scrub == null) hide()
            }
    }

    /** Hides the controls, dropping any scrub. */
    public fun hide() {
        hiding?.cancel()
        endHold()
        mutableState.update { it.copy(shown = false, scrub = null) }
    }

    /** A press of Left or Right. Gives how far to seek; quick presses the same way add up on screen. */
    public fun nudge(forward: Boolean): Long {
        val step = if (forward) SEEK_STEP_MS else -SEEK_STEP_MS
        mutableState.update {
            val sum = it.nudgeMs?.takeIf { before -> before > 0 == forward }
            it.copy(nudgeMs = (sum ?: 0) + step)
        }
        nudgeEnds?.cancel()
        nudgeEnds =
            viewModelScope.launch {
                delay(ADD_UP_WITHIN)
                mutableState.update { it.copy(nudgeMs = null) }
            }
        return step
    }

    /**
     * Moves the scrub target one step, starting a scrub from [positionMs] if there isn't one. A press moves it 10 s. While
     * Left or Right is [held], it moves by how long since the last step, faster the longer it's held.
     */
    public fun scrub(
        forward: Boolean,
        held: Boolean,
        positionMs: Long,
        durationMs: Long,
    ) {
        hiding?.cancel()
        val now = time.markNow()
        val last = holdLast
        val started = holdStarted
        val step =
            if (held && last != null && started != null && forward == holdForward) {
                val speed = scrubSpeed(now - started)
                (now - last).toDouble(DurationUnit.MILLISECONDS).times(speed).toLong()
            } else {
                SEEK_STEP_MS
            }
        if (held) {
            if (last == null || forward != holdForward) holdStarted = now
            holdLast = now
            holdForward = forward
        } else {
            endHold()
        }
        mutableState.update {
            val scrub = it.scrub ?: Scrub(positionMs, positionMs)
            val target = (scrub.targetMs + if (forward) step else -step).coerceIn(0, durationMs.coerceAtLeast(0))
            it.copy(shown = true, scrub = scrub.copy(targetMs = target))
        }
    }

    /** Left or Right was let go, so the next hold starts slow again. */
    public fun released(): Unit = endHold()

    /** OK while scrubbing: gives where to seek, and ends the scrub. */
    public fun jump(): Long? {
        val target = state.value.scrub?.targetMs ?: return null
        stay()
        return target
    }

    /** Back while scrubbing: the video stays where it is. */
    public fun stay() {
        endHold()
        mutableState.update { it.copy(scrub = null) }
    }

    private fun endHold() {
        holdStarted = null
        holdLast = null
    }

    private companion object {
        val HIDE_AFTER = 5.seconds
        val ADD_UP_WITHIN = 1500.milliseconds

        /** Seconds of video a second of holding moves: 30 at first, 60 after a second, 120 after three. */
        fun scrubSpeed(held: Duration) =
            when {
                held < 1.seconds -> 30.0
                held < 3.seconds -> 60.0
                else -> 120.0
            }
    }
}
