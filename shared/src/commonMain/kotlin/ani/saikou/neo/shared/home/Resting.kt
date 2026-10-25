package ani.saikou.neo.shared.home

import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.catalogue.ArtLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

// Long enough to pass over cards moving along a row, short enough to be ready before Details opens.
internal val REST = 600.milliseconds

/**
 * Finds the art of the card focus rests on, so its Details opens with the logo ready. Cards focus only passes over
 * are never asked about. [onLogo] gets the rested card's logo, for the screen to start loading, or null.
 */
internal class Resting(
    private val scope: CoroutineScope,
    private val library: ArtLibrary,
    private val onLogo: (Logo.Image?) -> Unit,
) {
    private var waiting: Job? = null

    fun on(card: AnimeCard?) {
        waiting?.cancel()
        onLogo(null)
        if (card == null) return
        waiting =
            scope.launch {
                delay(REST)
                onLogo(library.art(card).logo as? Logo.Image)
            }
    }
}
