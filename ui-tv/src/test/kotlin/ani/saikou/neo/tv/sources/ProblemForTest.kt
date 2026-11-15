package ani.saikou.neo.tv.sources

import ani.saikou.neo.extension.api.ExtensionApi
import ani.saikou.neo.shared.sources.SourceProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProblemForTest {
    @Test
    fun aSourceBuiltForThisApiVersionCanBeUsed() {
        assertNull(problemFor(ExtensionApi.VERSION))
    }

    @Test
    fun anOlderOrNewerOneSaysWhichNeedsUpdating() {
        assertEquals(SourceProblem.BUILT_FOR_OLDER_APP, problemFor(ExtensionApi.MIN_VERSION - 1))
        assertEquals(SourceProblem.BUILT_FOR_NEWER_APP, problemFor(ExtensionApi.VERSION + 1))
    }
}
