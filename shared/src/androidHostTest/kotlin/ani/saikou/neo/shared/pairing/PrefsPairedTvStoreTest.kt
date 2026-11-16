package ani.saikou.neo.shared.pairing

import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PrefsPairedTvStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun yourTvsReadBackAfterARestart() {
        val tvs = listOf(PairedTv("tv-1", "Living room TV", 1_761_868_800), PairedTv("tv-2", "Bedroom Fire TV", 1_762_041_600))
        PrefsPairedTvStore(context).saveTvs(tvs)

        assertEquals(tvs, PrefsPairedTvStore(context).readTvs())
    }

    @Test
    fun nothingSavedIsAnEmptyList() {
        assertTrue(PrefsPairedTvStore(context).readTvs().isEmpty())
    }

    @Test
    fun aDamagedListIsForgotten() {
        context.getSharedPreferences(PrefsPairedTvStore.FILE, Context.MODE_PRIVATE).edit().putString("tvs", "{oops").commit()

        assertTrue(PrefsPairedTvStore(context).readTvs().isEmpty())
    }
}
