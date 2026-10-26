package ani.saikou.neo.tv.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class KeylineTest {
    // Home's rows: a 24 dp heading and 10 dp gap over a 110 dp card, 24 dp between rows, in a 510 dp list with 30 dp
    // under the last card. So row n's card starts at 34 + 168n, measured from the list's scrolled top.
    private fun scroll(
        cardTop: Float,
        cardHeight: Float = 110f,
    ) = windowScrollDistance(offset = cardTop, size = cardHeight, containerSize = 510f, leading = 34f, trailing = 30f)

    @Test
    fun aRowAlreadyOnScreenDoesNotScroll() {
        assertEquals(0f, scroll(cardTop = 34f))
        assertEquals(0f, scroll(cardTop = 202f))
        assertEquals(0f, scroll(cardTop = 370f))
    }

    @Test
    fun aFourthRowScrollsTheListByOneRow() {
        assertEquals(168f, scroll(cardTop = 538f))
    }

    @Test
    fun upToARowAboveScrollsByOneRowAndShowsItsHeading() {
        // Scrolled down one row, the first row's card is at 34 - 168.
        assertEquals(-168f, scroll(cardTop = -134f))
    }

    @Test
    fun aCardWhoseHeadingIsCutOffScrollsToShowTheHeading() {
        assertEquals(-14f, scroll(cardTop = 20f))
    }

    @Test
    fun anItemShorterThanACardStaysPutWhileItsRowIsOnScreen() {
        // A row's See more button, centred beside the cards.
        assertEquals(0f, scroll(cardTop = 370f + 33f, cardHeight = 44f))
    }
}
