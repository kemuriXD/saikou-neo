package ani.saikou.neo.shared.settings

import ani.saikou.neo.shared.catalogue.ShowFormat

/** The releases Schedule can list besides Sub, which always shows. */
public enum class ScheduleRelease { RAW, DUB }

/** A setting that's a list of things to tick, any number of them, with its [items] in the order they're offered. */
public enum class TickList(
    public val items: List<Any>,
) {
    RELEASES(ScheduleRelease.entries),
    FORMATS(ShowFormat.entries),
}

/** The items of [list] that are ticked now. */
public fun Settings.ticked(list: TickList): Set<Any> =
    when (list) {
        TickList.RELEASES -> {
            buildSet {
                if (schedule.rawEpisodes) add(ScheduleRelease.RAW)
                if (schedule.dubs) add(ScheduleRelease.DUB)
            }
        }

        TickList.FORMATS -> {
            schedule.formats
        }
    }

/** Whether [item] is the one item of [list] left ticked and has to stay so. Only formats keep one ticked. */
public fun Settings.isLastTick(
    list: TickList,
    item: Any,
): Boolean = list == TickList.FORMATS && ticked(list) == setOf(item)

/** These settings with [item] of [list] ticked when [on], else unticked. Unticking the last format changes nothing. */
internal fun Settings.withTick(
    list: TickList,
    item: Any,
    on: Boolean,
): Settings =
    copy(
        schedule =
            when (list) {
                TickList.RELEASES -> {
                    when (item as ScheduleRelease) {
                        ScheduleRelease.RAW -> schedule.withRawEpisodes(on)
                        ScheduleRelease.DUB -> schedule.copy(dubs = on)
                    }
                }

                TickList.FORMATS -> {
                    schedule.withFormat(item as ShowFormat, on)
                }
            },
    )

/** How a tick list's row sums up what's ticked. */
public sealed interface TickSummary {
    /** Everything is ticked. */
    public data object All : TickSummary

    /** Nothing is ticked. */
    public data object None : TickSummary

    /** One or two are ticked, these, in the list's order. */
    public data class Names(
        val items: List<Any>,
    ) : TickSummary

    /** Some are ticked, [ticked] of the list's [of]. */
    public data class Count(
        val ticked: Int,
        val of: Int,
    ) : TickSummary
}

private const val MOST_NAMES = 2

/** Sums up [ticked], some of this list's items. */
public fun TickList.summary(ticked: Set<Any>): TickSummary {
    val names = items.filter { it in ticked }
    return when {
        names.isEmpty() -> TickSummary.None
        names.size == items.size -> TickSummary.All
        names.size <= MOST_NAMES -> TickSummary.Names(names)
        else -> TickSummary.Count(names.size, items.size)
    }
}
