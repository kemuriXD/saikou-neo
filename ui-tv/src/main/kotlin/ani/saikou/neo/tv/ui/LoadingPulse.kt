package ani.saikou.neo.tv.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.cos

/** How long a placeholder takes to fade down and back while it waits. */
internal const val PULSE_PERIOD_MILLIS = 1200

// At its lowest, a placeholder shows at half strength: two greys on the dark screen.
private const val PULSE_LOW = 0.5f

/**
 * How far into its fade a placeholder is at [frameMillis]: 0 at full strength, 1 at its lowest. Every placeholder
 * works it out from the frame's time, so all of them on screen pulse together.
 */
internal fun pulseAt(frameMillis: Long): Float {
    val phase = (frameMillis % PULSE_PERIOD_MILLIS).toFloat() / PULSE_PERIOD_MILLIS
    return (1f - cos(2 * PI * phase).toFloat()) / 2f
}

/** Set on a placeholder while it pulses, for tests. */
internal val Pulsing = SemanticsPropertyKey<Boolean>("Pulsing")
private var SemanticsPropertyReceiver.pulsing by Pulsing

/**
 * The pulse, updated every frame while [running], or null when not. Only drawing reads it, so it redraws without
 * recomposing. It's cheap enough to run in Slow TV mode too.
 */
@Composable
internal fun rememberPulse(running: Boolean = true): State<Float>? {
    val pulse = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) withInfiniteAnimationFrameMillis { pulse.floatValue = pulseAt(it) }
    }
    return if (running) pulse else null
}

/** A placeholder's fill in [color], fading with [pulse] while something loads, or still when [pulse] is null. */
internal fun Modifier.placeholder(
    color: Color,
    shape: Shape,
    pulse: State<Float>?,
): Modifier {
    if (pulse == null) return background(color, shape)
    return semantics { pulsing = true }.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind { drawOutline(outline, color.copy(alpha = color.alpha * lerp(1f, PULSE_LOW, pulse.value))) }
    }
}
