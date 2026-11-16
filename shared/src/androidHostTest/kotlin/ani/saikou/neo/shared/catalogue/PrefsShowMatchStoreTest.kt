package ani.saikou.neo.shared.catalogue

import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class PrefsShowMatchStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val checkedAt = Instant.parse("2026-11-16T12:00:00Z")

    @Test
    fun matchesReadBackAfterARestart() {
        PrefsShowMatchStore(context).saveRoute(154587, SavedMatch("sousou-no-frieren", checkedAt))
        PrefsShowMatchStore(context).saveAniListId("sousou-no-frieren", SavedMatch(154587, checkedAt))

        assertEquals(SavedMatch("sousou-no-frieren", checkedAt), PrefsShowMatchStore(context).route(154587))
        assertEquals(SavedMatch(154587, checkedAt), PrefsShowMatchStore(context).aniListId("sousou-no-frieren"))
    }

    @Test
    fun noMatchKeepsWhenItWasAsked() {
        PrefsShowMatchStore(context).saveRoute(1, SavedMatch(null, checkedAt))
        PrefsShowMatchStore(context).saveAniListId("a", SavedMatch(null, checkedAt))

        assertEquals(SavedMatch(null, checkedAt), PrefsShowMatchStore(context).route(1))
        assertEquals(SavedMatch(null, checkedAt), PrefsShowMatchStore(context).aniListId("a"))
    }

    @Test
    fun anUnknownShowHasNothingSaved() {
        assertNull(PrefsShowMatchStore(context).route(2))
        assertNull(PrefsShowMatchStore(context).aniListId("b"))
    }

    @Test
    fun aDamagedEntryIsTreatedAsNeverAsked() {
        context
            .getSharedPreferences(PrefsShowMatchStore.FILE, Context.MODE_PRIVATE)
            .edit()
            .putString("anilist:3", "not json")
            .commit()

        assertNull(PrefsShowMatchStore(context).route(3))
    }
}
