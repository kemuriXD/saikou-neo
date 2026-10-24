package ani.saikou.neo.shared.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SynopsisTest {
    @Test
    fun lineBreaksBecomeNewlinesAndTagsGo() {
        assertEquals(
            "One day, Maomao senses an anomaly.\n\nShe investigates.",
            parseSynopsis("One day, <i>Maomao</i> senses an anomaly.\n<br><br>\nShe investigates.").story,
        )
    }

    @Test
    fun aSingleBreakIsOneNewline() {
        assertEquals("First line\nSecond line", parseSynopsis("First line<br/>Second line").story)
    }

    @Test
    fun entitiesAreDecoded() {
        assertEquals(
            "Tom & Jerry's \"chase\" <3 — again",
            parseSynopsis("Tom &amp; Jerry&#039;s &quot;chase&quot; &lt;3 &#8212; again").story,
        )
    }

    @Test
    fun longGapsShrinkToOneBlankLine() {
        assertEquals("A\n\nB", parseSynopsis("A<br><br><br><br>B").story)
    }

    @Test
    fun nothingToShowIsEmpty() {
        assertEquals(Synopsis(story = null, notes = emptyList(), source = null), parseSynopsis(null))
        assertEquals(Synopsis(story = null, notes = emptyList(), source = null), parseSynopsis(" <br> "))
    }

    // As AniList sends Reincarnated as a Sword Season 2 (159042).
    @Test
    fun anItalicNoteMovesOutOfTheStory() {
        val synopsis =
            parseSynopsis(
                "The second season of <i>Tensei Shitara Ken Deshita</i>.<br><br>\n" +
                    "<i>Note: Each episode was streamed one week in advance of the TV broadcast.</i>",
            )

        assertEquals("The second season of Tensei Shitara Ken Deshita.", synopsis.story)
        assertEquals(listOf("Each episode was streamed one week in advance of the TV broadcast."), synopsis.notes)
    }

    @Test
    fun notesCanBePluralBoldOrRightAfterTheStory() {
        val synopsis = parseSynopsis("A story.<br><b>Notes:</b> First note.<br><br>note: Second note.")

        assertEquals("A story.", synopsis.story)
        assertEquals(listOf("First note.", "Second note."), synopsis.notes)
    }

    @Test
    fun theSourceCreditMovesOutOfTheStory() {
        assertEquals(Synopsis("A story.", emptyList(), "Crunchyroll"), parseSynopsis("A story.<br><br>(Source: Crunchyroll)"))
        assertEquals(Synopsis("A story.", emptyList(), "Funimation, edited"), parseSynopsis("A story. (Source: Funimation, edited)"))
        assertEquals(Synopsis("A story.", emptyList(), "MAL Rewrite"), parseSynopsis("A story.<br><br>[Written by MAL Rewrite]"))
    }

    @Test
    fun spoilersAreLeftOut() {
        assertEquals("He survives.", parseSynopsis("He survives.~!But not for long.!~").story)
    }

    @Test
    fun aNoteInsideASentenceStaysInTheStory() {
        assertEquals("Take note: she is a pharmacist.", parseSynopsis("Take note: she is a pharmacist.").story)
    }

    @Test
    fun scoreIsOutOfTen() {
        assertEquals("8.5", scoreOutOfTen(85))
        assertEquals("9.0", scoreOutOfTen(90))
        assertEquals("10.0", scoreOutOfTen(100))
        assertNull(scoreOutOfTen(null))
    }
}
