package ani.saikou.neo.shared.catalogue

import kotlin.test.Test
import kotlin.test.assertEquals

// The backdrop of last resort, when no artwork site has one full HD.
class AnimeCardArtTest {
    private val card =
        AnimeCard(
            1,
            AnimeTitle("Show", null, null),
            "https://anilist.test/cover.jpg",
            null,
            "https://anilist.test/banner.jpg",
            "TV",
            null,
            null,
            null,
            null,
        )

    @Test
    fun theArtIsTheBanner() {
        assertEquals("https://anilist.test/banner.jpg", card.art)
    }

    @Test
    fun withoutABannerTheArtIsTheCover() {
        assertEquals("https://anilist.test/cover.jpg", card.copy(bannerImageUrl = null).art)
    }
}
