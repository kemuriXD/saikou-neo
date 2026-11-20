package ani.saikou.neo.shared.account

import ani.saikou.neo.shared.catalogue.AniListResult

/** Where a show sits on the user's AniList list. */
public enum class ListStatus {
    WATCHING,
    PLANNING,
    COMPLETED,
    DROPPED,
    PAUSED,
    REWATCHING,
}

/** A calendar date where the viewer is, as AniList keeps start dates. */
public data class ListDate(
    val year: Int,
    val month: Int,
    val day: Int,
)

/** A show's entry on the user's list: its [status], how many episodes they've watched, and whether it has a start date. */
public data class ListEntry(
    val status: ListStatus,
    val progress: Int,
    val hasStartDate: Boolean,
)

/** A show's episode count, if AniList knows it, and the user's [entry] for it, if it's on their list. */
public data class ShowEntry(
    val episodes: Int?,
    val entry: ListEntry?,
)

/** What to save on AniList: the new [progress], and the [status] and [startedAt] to set, when they change. */
public data class ProgressUpdate(
    val progress: Int,
    val status: ListStatus?,
    val startedAt: ListDate?,
)

// A show in one of these moves to Watching once an episode of it is watched.
private val MOVES_TO_WATCHING = setOf(ListStatus.PLANNING, ListStatus.PAUSED, ListStatus.DROPPED)

/**
 * What watching [episode] of [show] changes on AniList, or null when nothing does (ADR 0047). Progress only goes up and
 * can't pass the show's total. A show that isn't on the list, or is Planning, Paused or Dropped, becomes Watching, with
 * [today] as its start date if it has none. Completed and Rewatching shows are left alone.
 */
public fun progressUpdate(
    show: ShowEntry,
    episode: Int,
    today: ListDate,
): ProgressUpdate? {
    if (episode < 1 || (show.episodes != null && episode > show.episodes)) return null
    val entry = show.entry
    if (entry != null && (entry.progress >= episode || entry.status !in MOVES_TO_WATCHING + ListStatus.WATCHING)) return null
    if (entry?.status == ListStatus.WATCHING) return ProgressUpdate(episode, null, null)
    return ProgressUpdate(episode, ListStatus.WATCHING, today.takeUnless { entry?.hasStartDate == true })
}

/** The AniList calls watching makes with the signed-in user's token. */
public interface ProgressClient {
    /** [mediaId]'s episode count and the user's entry for it. */
    public suspend fun showEntry(
        token: AniListToken,
        mediaId: Int,
    ): AniListResult<ShowEntry>

    /** Saves [update] to the user's entry for [mediaId], adding the show to their list if it isn't there. */
    public suspend fun saveProgress(
        token: AniListToken,
        mediaId: Int,
        update: ProgressUpdate,
    ): AniListResult<Unit>
}
