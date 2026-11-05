package ani.saikou.neo.shared.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Where a source's check has got to. */
public enum class CheckStatus { WAITING, RUNNING, DONE, STOPPED }

/** A source's check and the [steps] it has run so far. */
public data class SourceCheck(
    val status: CheckStatus,
    val steps: List<StepResult> = emptyList(),
) {
    /** Whether the check finished with no step failing. */
    val passed: Boolean get() = status == CheckStatus.DONE && steps.none { it.outcome is StepOutcome.Failed }
}

// Checks hit sites and share the source threads, so only a few run at once.
internal const val CHECKS_AT_ONCE = 3

/** Checks sources for Settings > Sources: one at a time, or all of them a few at a time, until stopped. */
public class SourceChecksViewModel(
    private val checker: SourceChecker,
) : ViewModel() {
    private val state = MutableStateFlow<Map<String, SourceCheck>>(emptyMap())
    private val slots = Semaphore(CHECKS_AT_ONCE)
    private val jobs = mutableMapOf<String, Job>()

    /** Each checked source's check, by source id. */
    public val checks: StateFlow<Map<String, SourceCheck>> = state.asStateFlow()

    /** Checks [source], unless it's already being checked. */
    public fun check(source: InstalledSource) {
        val id = source.id
        if (jobs[id]?.isActive == true) return
        state.update { it + (id to SourceCheck(CheckStatus.WAITING)) }
        jobs[id] =
            viewModelScope.launch {
                try {
                    slots.withPermit {
                        set(id) { SourceCheck(CheckStatus.RUNNING) }
                        val steps = checker.check(source) { step -> set(id) { it.copy(steps = it.steps + step) } }
                        set(id) { SourceCheck(CheckStatus.DONE, steps) }
                    }
                } catch (e: CancellationException) {
                    set(id) { it.copy(status = CheckStatus.STOPPED) }
                    throw e
                }
            }
    }

    /** Checks every one of [sources], a few at a time. */
    public fun checkAll(sources: List<InstalledSource>) {
        sources.forEach(::check)
    }

    /** Stops every check, keeping the steps each has run. */
    public fun stop() {
        jobs.values.forEach { it.cancel() }
    }

    private fun set(
        id: String,
        change: (SourceCheck) -> SourceCheck,
    ) {
        state.update { checks -> checks[id]?.let { checks + (id to change(it)) } ?: checks }
    }
}
