package ani.saikou.neo.shared.home

import ani.saikou.neo.shared.account.AniListSync
import ani.saikou.neo.shared.account.ListedProgress
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Where Details carries on with a show, from this TV's place and AniList's (ADR 0050). */
public sealed interface PickUp {
    /** Where to carry on while nothing's been picked. */
    public val place: WatchProgress

    /** Carry on at [place] without asking. */
    public data class At(
        override val place: WatchProgress,
    ) : PickUp

    /** The two disagree, so the viewer picks [aniList], the episode after its progress, or [thisTv]. */
    public data class Ask(
        val aniList: WatchProgress,
        val thisTv: WatchProgress,
    ) : PickUp {
        override val place: WatchProgress get() = thisTv
    }
}

/**
 * Where to pick up, from this TV's place [onTv] and [listed] AniList's. One side alone, or both at the same episode,
 * picks up there, with this TV's position. AniList further on picks up there unless this TV has a part-watched episode.
 * Every other disagreement asks.
 */
public fun pickUp(
    onTv: WatchProgress?,
    listed: ListedProgress?,
): PickUp? {
    val aniList =
        listed?.let {
            WatchProgress(
                it.progress + 1,
                Duration.ZERO,
                it.episodeMinutes?.minutes ?: USUAL_EPISODE,
                null,
                it.updatedAt,
            )
        }
    return when {
        onTv == null -> aniList?.let { PickUp.At(it) }
        aniList == null || onTv.picksUpAt == aniList.episode -> PickUp.At(onTv)
        aniList.episode > onTv.picksUpAt && onTv.resumeAtMs(onTv.episode) == null -> PickUp.At(aniList)
        else -> PickUp.Ask(aniList, onTv)
    }
}

/** What answering "where to pick up" changes on each side. */
public interface PickUpSides {
    /** AniList's [place] was picked, so this TV carries on there. */
    public fun saveHere(
        animeId: Int,
        place: WatchProgress,
    )

    /** This TV's place was picked, so AniList moves from [listed] to [watched] episodes. */
    public suspend fun moveAniList(
        animeId: Int,
        watched: Int,
        listed: ListedProgress,
    )
}

/**
 * Saves AniList's place in [history] as of [now], in seconds. Moves AniList with [sync], only for the user whose list
 * was read: forward by the usual rules (ADR 0047), or back to exactly the count. [yourList] keeps the new count once
 * it's sent or queued.
 */
public class SyncedSides(
    private val history: WatchHistory,
    private val sync: AniListSync?,
    private val yourList: YourList?,
    private val now: () -> Long = { Clock.System.now().epochSeconds },
) : PickUpSides {
    override fun saveHere(
        animeId: Int,
        place: WatchProgress,
    ): Unit = history.save(animeId, place.copy(savedAt = now()))

    override suspend fun moveAniList(
        animeId: Int,
        watched: Int,
        listed: ListedProgress,
    ) {
        val moved =
            if (watched > listed.progress) {
                sync?.watched(animeId, watched, listed.userId)
                true
            } else {
                sync?.movedBack(animeId, watched, listed.userId) == true
            }
        if (moved) yourList?.moved(animeId, watched)
    }
}
