package ani.saikou.neo.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerNameTest {
    @Test
    fun aNameThatRepeatsTheSourceIsLeftOut() {
        assertNull(shownServerName("Alpha", "Alpha"))
    }

    @Test
    fun caseAndSpacesDontCount() {
        assertNull(shownServerName("  alpha ", "Alpha"))
    }

    @Test
    fun aBlankNameIsLeftOut() {
        assertNull(shownServerName(" ", "Alpha"))
    }

    @Test
    fun aNameOfItsOwnIsShownAsIs() {
        assertEquals("Mirror 2", shownServerName("Mirror 2", "Alpha"))
    }
}
