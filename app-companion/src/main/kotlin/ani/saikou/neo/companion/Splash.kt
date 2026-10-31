package ani.saikou.neo.companion

import android.view.View

/** How long the system splash takes to fade out once the first screen draws (ADR 0022). */
const val SPLASH_FADE_OUT_MS = 200L

/**
 * Fades the splash out, then removes it. The animator follows the system's animation scale, so with animations off
 * it ends at once and the splash cuts.
 */
fun fadeOutSplash(
    splash: View,
    remove: () -> Unit,
) {
    splash
        .animate()
        .alpha(0f)
        .setDuration(SPLASH_FADE_OUT_MS)
        .withEndAction(remove)
        .start()
}
