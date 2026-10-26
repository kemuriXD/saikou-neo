package ani.saikou.neo.shared.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RatingsTest {
    @Test
    fun theMeanIsTheAverageOfTheRatingsThereAre() {
        assertEquals(86, Ratings(aniList = 84, mal = 86, imdb = 89).mean)
        assertEquals(89, Ratings(aniList = 86, mal = 88, imdb = 86, rottenTomatoes = 96).mean)
    }

    @Test
    fun aRatingAShowLacksIsLeftOut() {
        assertEquals(73, Ratings(aniList = 74, mal = 72).mean)
        assertEquals(80, Ratings(aniList = null, imdb = 80).mean)
    }

    @Test
    fun halvesRoundUp() {
        assertEquals(85, Ratings(aniList = 84, mal = 85).mean)
    }

    @Test
    fun withoutAnyRatingThereIsNoMean() {
        assertNull(Ratings(aniList = null).mean)
    }
}
