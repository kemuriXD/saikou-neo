package ani.saikou.neo.tv.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowTitleTest {
    // Some of AniZip's logos are square key art, which would crowd out the title.
    @Test
    fun onlyAWideImageCountsAsALogo() {
        assertTrue(looksLikeALogo(800, 310))
        assertTrue(looksLikeALogo(800, 450))
        assertFalse(looksLikeALogo(680, 680))
        assertFalse(looksLikeALogo(0, 0))
    }
}
