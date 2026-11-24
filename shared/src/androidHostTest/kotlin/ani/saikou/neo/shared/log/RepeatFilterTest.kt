package ani.saikou.neo.shared.log

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatFilterTest {
    @Test
    fun repeatsAreDroppedUntilReset() {
        val filter = RepeatFilter()
        val passed = (1..100).count { filter.firstTime(IOException("Too many open files")) }
        assertEquals(1, passed)
        filter.reset()
        assertEquals(true, filter.firstTime(IOException("Too many open files")))
    }

    @Test
    fun aNewKindGetsThrough() {
        val filter = RepeatFilter()
        assertEquals(true, filter.firstTime(IOException("a")))
        assertEquals(true, filter.firstTime(IOException("b")))
        assertEquals(false, filter.firstTime(IOException("a")))
    }

    @Test
    fun kindsAreCapped() {
        val filter = RepeatFilter(maxKinds = 3)
        val passed = (1..100).count { filter.firstTime(IOException("error $it")) }
        assertEquals(3, passed)
    }
}
