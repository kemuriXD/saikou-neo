package ani.saikou.neo.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowTitleTest {
    // Some of AniZip's logos are pictures of a character, which would crowd out the title. They are no wider than tall.
    @Test
    fun aLogoIsAtLeastAFifthWiderThanTall() {
        assertTrue(looksLikeALogo(800, 310, opaqueShare = 0.6f))
        // Demon Slayer's logo.
        assertTrue(looksLikeALogo(951, 774, opaqueShare = 0.67f))
        assertFalse(looksLikeALogo(680, 680, opaqueShare = 0.6f))
        // Jujutsu Kaisen's vertical logo is too narrow to read in the logo's space.
        assertFalse(looksLikeALogo(210, 1013, opaqueShare = 0.74f))
        assertFalse(looksLikeALogo(0, 0, opaqueShare = 0f))
    }

    // Key art filed as a logo fills its whole rectangle. Real logos are mostly see-through.
    @Test
    fun anAlmostOpaqueImageIsNotALogoWhateverItsShape() {
        assertFalse(looksLikeALogo(1000, 100, opaqueShare = 1f))
        assertFalse(looksLikeALogo(1000, 100, opaqueShare = 0.96f))
        // Fullmetal Alchemist's logo, the most solid one among the most popular shows.
        assertTrue(looksLikeALogo(539, 299, opaqueShare = 0.91f))
    }

    @Test
    fun theOpaqueShareCountsVisiblePixels() {
        val clear = 0x00FFFFFF
        val faint = 0x0FFFFFFF
        val solid = 0xFF000000.toInt()
        assertEquals(0f, opaqueShare(intArrayOf(clear, clear, faint, faint)))
        assertEquals(0.5f, opaqueShare(intArrayOf(clear, solid, faint, solid)))
        assertEquals(1f, opaqueShare(intArrayOf(solid, solid)))
        // An empty image has nothing to show, so it can't pass for a logo.
        assertEquals(1f, opaqueShare(intArrayOf()))
    }
}
