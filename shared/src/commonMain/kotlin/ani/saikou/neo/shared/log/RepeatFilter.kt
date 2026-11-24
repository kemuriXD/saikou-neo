package ani.saikou.neo.shared.log

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Lets the first failure of each kind through and drops repeats until [reset], so an error that keeps happening
 * writes a few lines instead of filling the log. At most [maxKinds] get through between resets.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class RepeatFilter(
    private val maxKinds: Int = 8,
) {
    private val seen = AtomicReference<Set<String>>(emptySet())

    fun firstTime(error: Throwable): Boolean {
        val message = runCatching { error.message }.getOrNull()?.take(KIND_LIMIT)
        val kind = "${error::class.simpleName}: $message"
        while (true) {
            val old = seen.load()
            if (kind in old || old.size >= maxKinds) return false
            if (seen.compareAndSet(old, old + kind)) return true
        }
    }

    fun reset() {
        seen.store(emptySet())
    }

    private companion object {
        const val KIND_LIMIT = 200
    }
}
