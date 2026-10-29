package ani.saikou.neo.shared.home

import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.art
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** How far into an episode this TV got. [still] is a frame from the episode, where one is known. */
public data class WatchProgress(
    val episode: Int,
    val watched: Duration,
    val length: Duration,
    val still: String?,
) {
    /** Whole minutes left, rounded up, so an episode that isn't over never shows 0. */
    val minutesLeft: Int get() = ceil((length - watched).inWholeSeconds / 60.0).toInt().coerceAtLeast(1)

    /** How much was watched, from 0 to 1. */
    val fraction: Float get() = (watched / length).toFloat().coerceIn(0f, 1f)
}

private const val SAMPLE_SHOWS = 5
private val SampleLength = 24.minutes

// Episode and how much of it was watched, for each sample.
private val samples = listOf(7 to 0.58, 6 to 0.52, 9 to 0.25, 3 to 0.4, 2 to 0.15)

/**
 * Made-up progress on the first of [cards], until the player saves real progress (#115). Only alpha builds show it, so
 * the Continue watching row can be checked on a TV. Each show's art stands in for its episode still.
 */
internal fun sampleProgress(cards: List<AnimeCard>): Map<Int, WatchProgress> =
    cards
        .take(SAMPLE_SHOWS)
        .zip(samples) { card, (episode, watched) ->
            card.id to WatchProgress(episode, SampleLength * watched, SampleLength, card.art)
        }.toMap()
