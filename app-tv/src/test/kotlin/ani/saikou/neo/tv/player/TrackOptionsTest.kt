package ani.saikou.neo.tv.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private fun audio(
    label: String? = null,
    language: String? = null,
) = Format
    .Builder()
    .setSampleMimeType(MimeTypes.AUDIO_AAC)
    .setLabel(label)
    .setLanguage(language)
    .build()

private fun text(label: String) =
    Format
        .Builder()
        .setSampleMimeType(MimeTypes.TEXT_VTT)
        .setLabel(label)
        .build()

@OptIn(UnstableApi::class)
private fun group(
    format: Format,
    supported: Boolean = true,
    selected: Boolean = false,
) = Tracks.Group(
    TrackGroup(format),
    false,
    intArrayOf(if (supported) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_SUBTYPE),
    booleanArrayOf(selected),
)

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class TrackOptionsTest {
    @Test
    fun listsTheTracksOfOneTypeWithWhereTheyAre() {
        val tracks = Tracks(listOf(group(text("English")), group(audio("Japanese"), selected = true), group(audio("English"))))

        assertEquals(
            listOf(TrackOption(1, 0, "Japanese", selected = true), TrackOption(2, 0, "English", selected = false)),
            tracks.options(C.TRACK_TYPE_AUDIO),
        )
        assertEquals(listOf(TrackOption(0, 0, "English", selected = false)), tracks.options(C.TRACK_TYPE_TEXT))
    }

    @Test
    fun leavesOutWhatTheDeviceCantPlay() {
        val tracks = Tracks(listOf(group(audio("Japanese"), supported = false), group(audio("English"))))

        assertEquals(listOf("English"), tracks.options(C.TRACK_TYPE_AUDIO).map { it.label })
    }

    @Test
    fun leavesOutCaptionsTheStreamOnlyMightCarry() {
        val guess = Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA608).build()
        val named = guess.buildUpon().setLabel("English CC").build()
        val tracks = Tracks(listOf(group(guess), group(named), group(text("English"))))

        assertEquals(listOf("English CC", "English"), tracks.options(C.TRACK_TYPE_TEXT).map { it.label })
    }

    @Test
    fun namesATrackByLanguageWhenItHasNoLabel() {
        val tracks = Tracks(listOf(group(audio(language = "ja")), group(audio(language = "und")), group(audio())))

        assertEquals(listOf("Japanese", null, null), tracks.options(C.TRACK_TYPE_AUDIO).map { it.label })
    }

    @Test
    fun theSameNameTwiceIsOneChoiceKeepingTheOnePlaying() {
        val tracks = Tracks(listOf(group(audio("English")), group(audio("English"), selected = true), group(audio())))

        assertEquals(
            listOf(TrackOption(1, 0, "English", selected = true), TrackOption(2, 0, null, selected = false)),
            tracks.options(C.TRACK_TYPE_AUDIO),
        )
    }

    @Test
    fun aPickedTrackIsFoundAgainByNameAfterAReload() {
        val options = listOf(TrackOption(0, 0, "English", selected = true), TrackOption(1, 0, "French", selected = false))

        assertEquals(options[1], options.toRestore("French"))
        assertEquals(null, options.toRestore("English"))
        assertEquals(null, options.toRestore("German"))
        assertEquals(null, options.toRestore(null))
    }

    @Test
    fun pickingASubtitleTurnsSubtitlesBackOn() {
        val track = TrackGroup(text("English"))
        val off = TrackSelectionParameters.DEFAULT.withoutSubtitles()
        assertTrue(C.TRACK_TYPE_TEXT in off.disabledTrackTypes)

        val on = off.picking(track, 0)

        assertFalse(C.TRACK_TYPE_TEXT in on.disabledTrackTypes)
        assertEquals(0, on.overrides[track]?.trackIndices?.single())
    }

    @Test
    fun turningSubtitlesOffForgetsThePick() {
        val track = TrackGroup(text("English"))

        assertTrue(
            TrackSelectionParameters.DEFAULT
                .picking(track, 0)
                .withoutSubtitles()
                .overrides
                .isEmpty(),
        )
    }
}
