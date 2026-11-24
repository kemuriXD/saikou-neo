package ani.saikou.neo.tv

import ani.saikou.neo.shared.home.HomeRow
import ani.saikou.neo.shared.search.SearchGenre
import ani.saikou.neo.tv.ui.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScreenReportNameTest {
    @Test
    fun eachScreenHasItsOwnName() {
        assertEquals("home", Screen.Home.reportName())
        assertEquals("see_more", Screen.SeeMore(HomeRow.entries.first()).reportName())
        assertEquals("settings", Screen.Section(Section.SETTINGS).reportName())
        assertEquals("search", Screen.Section(Section.SEARCH).reportName())
        assertEquals("settings_sources", Screen.Sources.reportName())
        assertEquals("genre", Screen.Genre(SearchGenre.entries.first()).reportName())
        assertEquals("details", Screen.Details(21).reportName())
        assertEquals("recommendations", Screen.Recommendations(21).reportName())
        assertEquals("sign_in", Screen.SignIn.reportName())
        assertEquals("signed_out_profile", Screen.SignedOutProfile.reportName())
    }

    @Test
    fun noNameCarriesAnId() {
        listOf(Screen.Details(5412345, visit = 3), Screen.Recommendations(5412345, visit = 3)).forEach {
            assertFalse(it.reportName().any(Char::isDigit))
        }
    }
}
