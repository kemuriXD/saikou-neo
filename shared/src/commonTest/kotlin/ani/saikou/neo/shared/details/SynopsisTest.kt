package ani.saikou.neo.shared.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SynopsisTest {
    @Test
    fun lineBreaksBecomeNewlinesAndTagsGo() {
        assertEquals(
            "One day, Maomao senses an anomaly.\n\n(Source: Crunchyroll)",
            plainSynopsis("One day, <i>Maomao</i> senses an anomaly.\n<br><br>\n(Source: Crunchyroll)"),
        )
    }

    @Test
    fun aSingleBreakIsOneNewline() {
        assertEquals("First line\nSecond line", plainSynopsis("First line<br/>Second line"))
    }

    @Test
    fun entitiesAreDecoded() {
        assertEquals("Tom & Jerry's \"chase\" <3 — again", plainSynopsis("Tom &amp; Jerry&#039;s &quot;chase&quot; &lt;3 &#8212; again"))
    }

    @Test
    fun longGapsShrinkToOneBlankLine() {
        assertEquals("A\n\nB", plainSynopsis("A<br><br><br><br>B"))
    }

    @Test
    fun nothingToShowIsNull() {
        assertNull(plainSynopsis(null))
        assertNull(plainSynopsis(" <br> "))
    }

    @Test
    fun scoreIsOutOfTen() {
        assertEquals("8.5", scoreOutOfTen(85))
        assertEquals("9.0", scoreOutOfTen(90))
        assertEquals("10.0", scoreOutOfTen(100))
        assertNull(scoreOutOfTen(null))
    }
}
