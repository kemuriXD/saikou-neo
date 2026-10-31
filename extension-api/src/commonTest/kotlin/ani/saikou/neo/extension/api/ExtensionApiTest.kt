package ani.saikou.neo.extension.api

import kotlin.test.Test
import kotlin.test.assertEquals

class ExtensionApiTest {
    @Test
    fun aSourceBuiltForThisVersionIsCompatible() {
        assertEquals(Compatibility.Compatible, ExtensionApi.compatibility(ExtensionApi.VERSION))
    }

    @Test
    fun aSourceBuiltForAnOlderVersionNeedsUpdatingItself() {
        assertEquals(Compatibility.BuiltForOlderApp, ExtensionApi.compatibility(ExtensionApi.VERSION - 1))
    }

    @Test
    fun aSourceBuiltForANewerVersionNeedsTheAppUpdated() {
        assertEquals(Compatibility.BuiltForNewerApp, ExtensionApi.compatibility(ExtensionApi.VERSION + 1))
    }

    @Test
    fun theFirstVersionIsOne() {
        assertEquals(1, ExtensionApi.VERSION)
    }
}
