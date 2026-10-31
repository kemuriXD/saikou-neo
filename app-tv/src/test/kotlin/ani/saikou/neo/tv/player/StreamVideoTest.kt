package ani.saikou.neo.tv.player

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.playback.FoundStream
import ani.saikou.neo.shared.playback.PlayerPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamVideoTest {
    private val owner =
        object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
        }

    private val calls = mutableListOf<String>()
    private var playing = true

    private fun watch() =
        owner.lifecycle.addObserver(
            PauseInBackground(playing = { playing }, pause = { calls += "pause" }, play = { calls += "play" }),
        )

    @Test
    fun goingToTheBackgroundPausesAndComingBackPlays() {
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        watch()

        owner.lifecycle.currentState = Lifecycle.State.CREATED
        owner.lifecycle.currentState = Lifecycle.State.RESUMED

        assertEquals(listOf("pause", "play"), calls)
    }

    @Test
    fun videoThatWasntPlayingStaysPausedOnComingBack() {
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        playing = false
        watch()

        owner.lifecycle.currentState = Lifecycle.State.CREATED
        owner.lifecycle.currentState = Lifecycle.State.RESUMED

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun theScreenStaysOnOnlyWhileTheVideoStartsOrPlays() {
        val answered = FoundStream("stream", 1080)

        assertTrue(PlayerPhase.Starting<String>(null).keepsScreenOn())
        assertTrue(PlayerPhase.Playing(answered).keepsScreenOn())
        assertFalse(PlayerPhase.Stopped(FailReason.SourceDown, null, null, null).keepsScreenOn())
    }
}
