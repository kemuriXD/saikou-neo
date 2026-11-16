package ani.saikou.neo.shared.pairing

import kotlin.test.Test
import kotlin.test.assertEquals

class TvNameTest {
    @Test
    fun theNameIsTheMakeAndModel() = assertEquals("Amazon AFTSSS", tvName("Amazon", "AFTSSS"))

    @Test
    fun aModelThatNamesItsMakeIsNotDoubled() = assertEquals("Google Chromecast", tvName("google", "Google Chromecast"))

    @Test
    fun aLowerCaseMakeIsCapitalised() = assertEquals("Xiaomi MiTV-AXSO0", tvName("xiaomi", "MiTV-AXSO0"))
}
