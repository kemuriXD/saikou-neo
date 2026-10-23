package ani.saikou.neo.shared.catalogue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnimeSeasonTest {
    @Test
    fun followsAniListsQuarters() {
        val seasons = (1..12).map { AnimeSeason.forMonth(it) }

        assertEquals(
            List(3) { AnimeSeason.WINTER } + List(3) { AnimeSeason.SPRING } +
                List(3) { AnimeSeason.SUMMER } + List(3) { AnimeSeason.FALL },
            seasons,
        )
    }

    @Test
    fun rejectsMonthsOutsideTheYear() {
        assertFailsWith<IllegalArgumentException> { AnimeSeason.forMonth(0) }
        assertFailsWith<IllegalArgumentException> { AnimeSeason.forMonth(13) }
    }
}
