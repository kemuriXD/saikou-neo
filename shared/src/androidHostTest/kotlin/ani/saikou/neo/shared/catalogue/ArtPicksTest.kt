package ani.saikou.neo.shared.catalogue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtPicksTest {
    private fun logo(
        name: String,
        language: String? = "en",
        width: Int = 800,
        height: Int = 310,
        vector: Boolean = false,
    ) = ArtImage("https://art.test/$name.png", width, height, language, vector)

    @Test
    fun anEnglishLogoComesFirst() {
        assertEquals(logo("en"), pickLogo(listOf(logo("ja", "ja"), logo("none", null), logo("en"))))
    }

    // fanart.tv marks a logo with no words in any one language "00", and TMDB leaves it out or says "xx".
    @Test
    fun withoutEnglishALogoWithNoLanguageComesNext() {
        assertEquals(logo("none", null), pickLogo(listOf(logo("ja", "ja"), logo("none", null))))
        assertEquals(logo("zero", "00"), pickLogo(listOf(logo("ja", "ja"), logo("zero", "00"))))
        assertEquals(logo("xx", "xx"), pickLogo(listOf(logo("ja", "ja"), logo("xx", "xx"))))
    }

    @Test
    fun otherwiseTheMostLikedLogo() {
        assertEquals(logo("ja", "ja"), pickLogo(listOf(logo("ja", "ja"), logo("ko", "ko"))))
    }

    // fanart.tv's old logos are 400 wide, and some of TMDB's are smaller still.
    @Test
    fun aLogoUnder600WideIsTooBlurry() {
        assertEquals(logo("sharp", "ja"), pickLogo(listOf(logo("small", width = 599, height = 200), logo("sharp", "ja"))))
    }

    @Test
    fun anSvgLogoIsSharpAtAnyWidth() {
        val svg = logo("svg", width = 255, height = 93, vector = true)

        assertEquals(svg, pickLogo(listOf(svg)))
    }

    // Tensei Kizoku's logo is 0.8:1; nothing real is narrower than 0.75:1 or wider than 8:1.
    @Test
    fun aLogoFrom3To4UpTo8To1Wide() {
        assertEquals(logo("tall", width = 600, height = 800), pickLogo(listOf(logo("tall", width = 600, height = 800))))
        assertEquals(logo("wide", width = 2400, height = 300), pickLogo(listOf(logo("wide", width = 2400, height = 300))))
        assertNull(pickLogo(listOf(logo("narrow", width = 600, height = 801), logo("long", width = 2401, height = 300))))
    }

    @Test
    fun aLogoWithoutASizeIsNotUsed() {
        assertNull(pickLogo(listOf(logo("unknown", width = 0, height = 0))))
    }

    @Test
    fun noLogosMeansNone() {
        assertNull(pickLogo(emptyList()))
    }
}
