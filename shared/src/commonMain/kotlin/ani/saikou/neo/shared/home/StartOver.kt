package ani.saikou.neo.shared.home

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** How long the app can be away before it opens on Home again, rather than where it was left. */
public val StartOverAfter: Duration = 30.minutes

/**
 * Whether the app, back after going to the background at [backgroundedAtMillis], starts over on Home. A short break keeps
 * the screen and focus as they were. After longer than [StartOverAfter], or if the clock went back, it starts over.
 */
public fun startsOver(
    backgroundedAtMillis: Long,
    nowMillis: Long,
): Boolean = nowMillis - backgroundedAtMillis !in 0..StartOverAfter.inWholeMilliseconds
