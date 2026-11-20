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

/**
 * A show's entry on the user's list: its [status], how many episodes they've watched, whether it has a start date, how
 * many times they've rewatched it, whether it has a finish date, and the entry's own AniList [id].
 */
public data class ListEntry(
    val status: ListStatus,
    val progress: Int,
    val hasStartDate: Boolean,
    val repeat: Int = 0,
    val hasFinishDate: Boolean = false,
    val id: Int? = null,
)

/**
 * A show's episode count, if AniList knows it, the user's [entry] for it, if it's on their list, and whether it has
 * [finished] airing.
 */
public data class ShowEntry(
    val episodes: Int?,
    val entry: ListEntry?,
    val finished: Boolean = false,
)

/**
 * What to save on AniList: the new [progress], and the [status], [startedAt], [completedAt] and rewatch count
 * ([repeat]) to set, when they change.
 */
public data class ProgressUpdate(
    val progress: Int,
    val status: ListStatus?,
    val startedAt: ListDate?,
    val completedAt: ListDate? = null,
    val repeat: Int? = null,
)

/**
 * What watching [episode] of [show] changes on AniList, or null when nothing does (ADRs 0047 and 0048). Progress only
 * goes up and can't pass the show's total. A show that isn't on the list, or is Planning, Paused or Dropped, becomes
 * Watching, with [today] as its start date if it has none. The last episode of a finished show completes it today.
 * Episode 1 of a Completed show starts a rewatch, and finishing the rewatch completes it again with one more rewatch.
 */
public fun progressUpdate(
    show: ShowEntry,
    episode: Int,
    today: ListDate,
): ProgressUpdate? {
    if (episode < 1 || (show.episodes != null && episode > show.episodes)) return null
    val entry = show.entry
    val last = show.finished && episode == show.episodes
    if (entry?.status == ListStatus.COMPLETED) {
        return when {
            episode != 1 -> null

            // A film watched again is a whole rewatch at once.
            last -> ProgressUpdate(episode, null, null, repeat = entry.repeat + 1)

            else -> ProgressUpdate(episode, ListStatus.REWATCHING, null)
        }
    }
    if (entry != null && entry.progress >= episode) return null
    val completed = ListStatus.COMPLETED.takeIf { last }
    return when (entry?.status) {
        ListStatus.REWATCHING -> {
            ProgressUpdate(episode, completed, null, repeat = (entry.repeat + 1).takeIf { last })
        }

        ListStatus.WATCHING -> {
            ProgressUpdate(episode, completed, null, completedAt = today.takeIf { last })
        }

        else -> {
            ProgressUpdate(
                episode,
                if (last) ListStatus.COMPLETED else ListStatus.WATCHING,
                today.takeUnless { entry?.hasStartDate == true },
                completedAt = today.takeIf { last },
            )
        }
    }
}

/**
 * What picking [to] for [show] from the list button changes on AniList (ADR 0051). The status, and nothing else, except
 * that Completed watches every episode AniList knows of, fills in empty start and finish dates with [today], and counts
 * a rewatch it finishes. A show that isn't on the list is added at no progress.
 */
public fun statusUpdate(
    show: ShowEntry,
    to: ListStatus,
    today: ListDate,
): ProgressUpdate {
    val entry = show.entry
    val progress = entry?.progress ?: 0
    if (to != ListStatus.COMPLETED) return ProgressUpdate(progress, to, null)
    return ProgressUpdate(
        show.episodes ?: progress,
        to,
        today.takeUnless { entry?.hasStartDate == true },
        completedAt = today.takeUnless { entry?.hasFinishDate == true },
        repeat = entry?.repeat?.plus(1)?.takeIf { entry.status == ListStatus.REWATCHING },
    )
}

/** The AniList calls watching and the list button make with the signed-in user's token. */
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

    /** Takes the entry with AniList ID [entryId] off the user's list. */
    public suspend fun removeEntry(
        token: AniListToken,
        entryId: Int,
    ): AniListResult<Unit>
}
