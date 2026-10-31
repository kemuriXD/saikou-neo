package ani.saikou.neo.extension.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtensionApiTest {
    @Test
    fun aSourceBuiltForThisVersionIsCompatible() {
        assertEquals(Compatibility.Compatible, ExtensionApi.compatibility(ExtensionApi.VERSION))
    }

    @Test
    fun aSourceBuiltBeforeTheLastBreakingChangeNeedsUpdatingItself() {
        assertEquals(Compatibility.BuiltForOlderApp, ExtensionApi.compatibility(ExtensionApi.MIN_VERSION - 1))
    }

    @Test
    fun aSourceBuiltForANewerVersionNeedsTheAppUpdated() {
        assertEquals(Compatibility.BuiltForNewerApp, ExtensionApi.compatibility(ExtensionApi.VERSION + 1))
    }

    // Say the API is at 3 and last broke sources at 2: sources built for 2 and 3 still work.
    @Test
    fun anOlderSourceKeepsWorkingUntilABreakingChange() {
        assertEquals(Compatibility.Compatible, compatibility(builtFor = 2, version = 3, minVersion = 2))
        assertEquals(Compatibility.Compatible, compatibility(builtFor = 3, version = 3, minVersion = 2))
        assertEquals(Compatibility.BuiltForOlderApp, compatibility(builtFor = 1, version = 3, minVersion = 2))
        assertEquals(Compatibility.BuiltForNewerApp, compatibility(builtFor = 4, version = 3, minVersion = 2))
    }

    @Test
    fun theFirstVersionIsOneAndNothingHasBrokenYet() {
        assertEquals(1, ExtensionApi.VERSION)
        assertEquals(1, ExtensionApi.MIN_VERSION)
        assertTrue(ExtensionApi.MIN_VERSION <= ExtensionApi.VERSION)
    }
}
