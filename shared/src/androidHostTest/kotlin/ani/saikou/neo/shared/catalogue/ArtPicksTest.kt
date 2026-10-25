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

    private fun backdrop(
        name: String,
        language: String? = null,
        width: Int = 1920,
        height: Int = 1080,
    ) = ArtImage("https://art.test/$name.jpg", width, height, language)

    @Test
    fun aBackdropIsTheMostLikedFullHdOne() {
        assertEquals(backdrop("hd"), pickBackdrop(listOf(backdrop("hd"), backdrop("4k", width = 3840, height = 2160))))
    }

    @Test
    fun aBackdropUnderFullHdIsNotUsed() {
        val small = listOf(backdrop("720", width = 1280, height = 720), backdrop("short", height = 1079), backdrop("narrow", width = 1919))

        assertNull(pickBackdrop(small))
        assertEquals(backdrop("hd"), pickBackdrop(small + backdrop("hd")))
    }

    // TMDB files backdrops with the title written on them under a language.
    @Test
    fun aBackdropWithWordsIsNotUsed() {
        assertEquals(backdrop("plain"), pickBackdrop(listOf(backdrop("titled", "en"), backdrop("plain"))))
        assertNull(pickBackdrop(listOf(backdrop("titled", "ja"))))
    }

    // Every one of TheTVDB's newer backgrounds was 1920 × 1080 in a survey of 209 shows. Its old fan art is often 1280 × 720.
    @Test
    fun onlyTheTvdbBackgroundsCountAsFullHdFanArt() {
        assertEquals(
            "https://artworks.thetvdb.com/banners/v4/series/81797/backgrounds/616009a8bd688.jpg",
            fullHdFanArt("https://artworks.thetvdb.com/banners/v4/series/81797/backgrounds/616009a8bd688.jpg"),
        )
        assertEquals(
            "https://artworks.thetvdb.com/banners/series/81797/backgrounds/5f1c1fd2e7a8b.jpg",
            fullHdFanArt("https://artworks.thetvdb.com/banners/series/81797/backgrounds/5f1c1fd2e7a8b.jpg"),
        )
        assertNull(fullHdFanArt("https://artworks.thetvdb.com/banners/fanart/original/74796-1.jpg"))
        assertNull(fullHdFanArt(null))
        // Android 7 and 8 still allow plain http, so only https art is used.
        assertNull(fullHdFanArt("http://artworks.thetvdb.com/banners/v4/series/81797/backgrounds/616009a8bd688.jpg"))
    }
}
