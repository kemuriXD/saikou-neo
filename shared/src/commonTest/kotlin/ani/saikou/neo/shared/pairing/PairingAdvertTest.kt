package ani.saikou.neo.shared.pairing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PairingAdvertTest {
    private val key = ByteArray(65) { if (it == 0) 0x04 else it.toByte() }
    private val session = ByteArray(32) { (it * 7).toByte() }
    private val invite = PairingInvite(host = "192.168.1.20", port = 40123, publicKey = key, sessionId = session)

    private fun advert(
        name: String = "K74Q9M",
        host: String = invite.host,
        attributes: Map<String, String> = invite.advertAttributes(),
    ) = AdvertisedTv(name, host, invite.port, attributes)

    @Test
    fun theServiceTypeIsTheAgreedOne() {
        assertEquals("_saikouneo._tcp", PAIRING_SERVICE_TYPE)
    }

    @Test
    fun theAdvertCarriesTheSameKeyAndSessionAsTheQrCode() {
        val attributes = invite.advertAttributes()
        assertEquals(setOf("v", "key", "session"), attributes.keys)
        assertEquals("1", attributes["v"])
        assertTrue(invite.toQrText().contains("key=${attributes["key"]}&session=${attributes["session"]}"))
    }

    @Test
    fun anAdvertSurvivesTheTrip() {
        assertEquals(invite, advert().toInvite())
    }

    @Test
    fun anAdvertOffTheLocalNetworkIsRefused() {
        for (host in listOf("8.8.8.8", "127.0.0.1", "tv.example.com", "fe80::1")) {
            assertNull(advert(host = host).toInvite(), host)
        }
    }

    @Test
    fun aDamagedOrOtherVersionAdvertIsRefused() {
        val good = invite.advertAttributes()
        assertNull(advert(attributes = good + ("v" to "2")).toInvite())
        assertNull(advert(attributes = good - "key").toInvite())
        assertNull(advert(attributes = good + ("session" to "short")).toInvite())
        assertNull(advert(attributes = good + ("extra" to "x")).toInvite())
    }

    @Test
    fun theTvShowingTheCodeIsFound() =
        runTest {
            val found = findAdvertisedTv("K74Q9M", flowOf(advert(name = "ABC234"), advert()))
            assertEquals(invite, found)
        }

    @Test
    fun anImpostorOffTheNetworkIsSkippedForTheRealTv() =
        runTest {
            val found = findAdvertisedTv("K74Q9M", flowOf(advert(host = "8.8.8.8"), advert()))
            assertEquals(invite, found)
        }

    @Test
    fun aCodeNoTvIsShowingGivesUpAfterEightSeconds() =
        runTest {
            val search = async { findAdvertisedTv("K74Q9M", flow { awaitCancellation() }) }
            advanceTimeBy(7_999)
            runCurrent()
            assertFalse(search.isCompleted)
            advanceTimeBy(2)
            runCurrent()
            assertNull(search.await())
        }

    @Test
    fun aSearchThatEndsWithoutTheCodeFindsNothing() =
        runTest {
            assertNull(findAdvertisedTv("K74Q9M", flowOf(advert(name = "ABC234"))))
        }
}
