package ani.saikou.neo.shared.pairing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PairingChannelTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val tvKeys = PairingKeyPair.generate()
    private val session = newPairingSessionId()
    private val listener = PairingListener(tvKeys, session, ServerSocket(0, 50, loopback), handshakeTimeoutMillis = 300)

    @AfterTest
    fun closeListener() = listener.close()

    /** Real sockets, so a stuck test fails instead of hanging the build. */
    private fun pairing(block: suspend CoroutineScope.() -> Unit) = runBlocking { withTimeout(10_000) { block() } }

    private suspend fun phone(
        id: ByteArray = session,
        tvKey: ByteArray = tvKeys.publicKey,
    ) = connectToTv(loopback.hostAddress!!, listener.port, tvKey, id, timeoutMillis = 2_000)

    private fun rawSocket() = Socket(loopback, listener.port)

    @Test
    fun aPhoneAndTvPairAndTalk() =
        pairing {
            val tv = async { listener.accept() }
            val phone = phone()
            val tvSide = tv.await()

            assertEquals(tvSide.checkCode, phone.checkCode)
            withContext(Dispatchers.IO) {
                phone.send("account".encodeToByteArray())
                assertContentEquals("account".encodeToByteArray(), tvSide.receive(timeoutMillis = 1_000))
                tvSide.send(byteArrayOf(1))
                assertContentEquals(byteArrayOf(1), phone.receive(timeoutMillis = 1_000))
            }
            phone.close()
            tvSide.close()
        }

    @Test
    fun theTvHearsWhenAPhoneStartsAHandshakeAndWhenItFails() =
        pairing {
            val attempts = CopyOnWriteArrayList<Boolean>()
            val tv = async { listener.accept { attempts += it } }
            withContext(Dispatchers.IO) {
                // A phone that reads the TV's nonce and then hangs up.
                rawSocket().use { socket ->
                    val own = PairingKeyPair.generate()
                    socket.writeFrame(byteArrayOf(1) + own.publicKey + pairingCommitment(own.publicKey, newPairingNonce()))
                    socket.readFrame()
                }
            }
            while (attempts.size < 2) delay(10)
            assertEquals(listOf(true, false), attempts.toList())

            val phone = phone()
            tv.await().close()
            phone.close()
            assertEquals(listOf(true, false, true), attempts.toList())
        }

    @Test
    fun garbageDoesNotEndPairing() =
        pairing {
            val tv = async { listener.accept() }
            withContext(Dispatchers.IO) {
                rawSocket().use { DataOutputStream(it.getOutputStream()).apply { writeInt(Int.MAX_VALUE) }.flush() }
                rawSocket().use { it.getOutputStream().write(ByteArray(70) { 7 }) }
            }
            val phone = phone()

            assertEquals(tv.await().checkCode, phone.checkCode)
        }

    @Test
    fun anIdleAttemptIsDroppedAndPairingCarriesOn() =
        pairing {
            val tv = async { listener.accept() }
            val idle = withContext(Dispatchers.IO) { rawSocket() }
            val phone = phone()

            assertEquals(tv.await().checkCode, phone.checkCode)
            assertEquals(-1, withContext(Dispatchers.IO) { idle.getInputStream().read() })
        }

    @Test
    fun aPhoneWithTheWrongSessionFailsAndPairingCarriesOn() =
        pairing {
            val tv = async { listener.accept() }

            assertFailsWith<PairingException> { phone(id = newPairingSessionId()) }
            val phone = phone()

            assertEquals(tv.await().checkCode, phone.checkCode)
        }

    @Test
    fun aPhoneTalkingToAnImpostorFails() =
        pairing {
            val tv = async { listener.accept() }

            assertFailsWith<PairingException> { phone(tvKey = PairingKeyPair.generate().publicKey) }
            val phone = phone()

            assertEquals(tv.await().checkCode, phone.checkCode)
        }

    @Test
    fun aPhoneThatChangesItsCommittedNonceIsDroppedAndPairingCarriesOn() =
        pairing {
            val tv = async { listener.accept() }
            withContext(Dispatchers.IO) {
                rawSocket().use { socket ->
                    val own = PairingKeyPair.generate()
                    socket.writeFrame(byteArrayOf(1) + own.publicKey + pairingCommitment(own.publicKey, newPairingNonce()))
                    val tvNonce = socket.readFrame()
                    val otherNonce = newPairingNonce()
                    val cipher = PairingCipher.create(PairingRole.PHONE, own, tvKeys.publicKey, session, tvNonce, otherNonce)
                    socket.writeFrame(otherNonce)
                    socket.writeFrame(cipher.seal("saikou-neo/hello".encodeToByteArray()))

                    assertEquals(-1, socket.getInputStream().read())
                }
            }
            val phone = phone()

            assertEquals(tv.await().checkCode, phone.checkCode)
        }

    @Test
    fun aSlowAttemptDoesNotHoldUpARealPhone() =
        pairing {
            val patient = PairingListener(tvKeys, session, ServerSocket(0, 50, loopback), handshakeTimeoutMillis = 8_000)
            patient.use {
                val tv = async { patient.accept() }
                val idle = withContext(Dispatchers.IO) { Socket(loopback, patient.port) }
                val phone = connectToTv(loopback.hostAddress!!, patient.port, tvKeys.publicKey, session, timeoutMillis = 1_500)

                assertEquals(tv.await().checkCode, phone.checkCode)
                idle.close()
            }
        }

    @Test
    fun tooManyUnfinishedAttemptsEndPairing() =
        pairing {
            val tv = async { runCatching { listener.accept() } }
            withContext(Dispatchers.IO) {
                // Someone fishing for a matching check code: read the TV's nonce, then hang up.
                repeat(10) {
                    rawSocket().use { socket ->
                        val own = PairingKeyPair.generate()
                        socket.writeFrame(byteArrayOf(1) + own.publicKey + pairingCommitment(own.publicKey, newPairingNonce()))
                        socket.readFrame()
                    }
                }
            }

            assertTrue(tv.await().exceptionOrNull() is PairingException)
            assertFailsWith<PairingException> { listener.accept() }
        }

    @Test
    fun aThirdAttemptFromOneAddressReplacesTheOldest() =
        pairing {
            val patient = PairingListener(tvKeys, session, ServerSocket(0, 50, loopback), handshakeTimeoutMillis = 8_000)
            patient.use {
                val tv = async { patient.accept() }
                withContext(Dispatchers.IO) {
                    val sockets = List(2) { Socket(loopback, patient.port) }
                    Thread.sleep(300)
                    val third = Socket(loopback, patient.port)
                    // Well inside the 2-second first-frame limit, so only the replacement can close it.
                    sockets[0].soTimeout = 1_000

                    assertEquals(-1, sockets[0].getInputStream().read())
                    (sockets + third).forEach { it.close() }
                }
                tv.cancel()
                tv.join()
            }
        }

    @Test
    fun cancellingStopsListening() =
        pairing {
            val tv = async { listener.accept() }
            withContext(Dispatchers.IO) { Thread.sleep(100) }

            tv.cancel()
            tv.join()
            assertTrue(tv.isCancelled)
        }
}
