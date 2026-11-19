package ani.saikou.neo.shared.pairing

import ani.saikou.neo.shared.account.AniListToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The phone's side of #249 against a real [PairingListener], standing in for the TV until #116. */
@RunWith(RobolectricTestRunner::class)
class ChannelTvLinkTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val tvKeys = PairingKeyPair.generate()
    private val session = newPairingSessionId()
    private val listener = PairingListener(tvKeys, session, ServerSocket(0, 50, loopback), handshakeTimeoutMillis = 2_000)
    private val token = AniListToken("the-token", 9_000)

    @AfterTest
    fun closeListener() = listener.close()

    private fun pairing(block: suspend CoroutineScope.() -> Unit) = runBlocking { withTimeout(10_000) { block() } }

    /** Connects the phone; returns the phone's link and the TV's channel. */
    private suspend fun CoroutineScope.connect(answerTimeoutMillis: Int = 2_000): Pair<TvLink, PairingChannel> {
        val tv = async { listener.accept() }
        val phone = connectPhoneToTv(loopback.hostAddress!!, listener.port, tvKeys.publicKey, session, answerTimeoutMillis)
        return phone to tv.await()
    }

    /** The TV's side of [channel], as the TV app uses it. */
    private fun tvSide(
        channel: PairingChannel,
        accountTimeoutMillis: Int = 2_000,
    ): PhoneLink = ChannelPhoneLink(listener, channel, accountTimeoutMillis)

    @Test
    fun theTvGetsTheAccountAndThePhoneHearsItIsSignedIn() =
        pairing {
            val (phone, tv) = connect()
            assertEquals(tv.checkCode, phone.checkCode)

            val answer = async { phone.answer() }
            phone.sendAccount(token)
            val received = tvSide(tv).receiveAccount()
            assertEquals("the-token", received.accessToken)
            assertEquals(9_000L, received.expiresAtEpochSeconds)

            tvSide(tv).replySignedIn(PairedTvReply("tv-1", "Living room TV"))
            assertEquals(TvAnswer.SignedIn(PairedTvReply("tv-1", "Living room TV")), answer.await())
            phone.close()
            tv.close()
        }

    @Test
    fun closingWithoutConfirmingSendsTheTvNothing() =
        pairing {
            val (phone, tv) = connect()
            phone.close()
            assertFailsWith<PairingException> { withContext(Dispatchers.IO) { tv.receive(2_000) } }
        }

    @Test
    fun aTvThatNeverAnswersFails() =
        pairing {
            val (phone, tv) = connect(answerTimeoutMillis = 200)
            assertFailsWith<PairingException> { phone.answer() }
            tv.close()
        }

    @Test
    fun anyOtherReplyFails() =
        pairing {
            val (phone, tv) = connect()
            val reply = async { runCatching { phone.answer() } }
            withContext(Dispatchers.IO) { tv.send("""{"v":1,"type":"failed"}""".encodeToByteArray()) }
            assertEquals(PairingException::class, reply.await().exceptionOrNull()?.let { it::class })
            tv.close()
        }

    @Test
    fun itDoesntMatchOnThePhoneReachesTheTvWithItsReason() =
        pairing {
            val (phone, tv) = connect()
            phone.stop(PairingStop.NoMatch)
            val stopped = assertFailsWith<PairingStoppedException> { tvSide(tv).receiveAccount() }
            assertEquals(PairingStop.NoMatch, stopped.reason)
        }

    @Test
    fun aPhoneThatJustClosesIsNotAStop() =
        pairing {
            val (phone, tv) = connect()
            phone.close()
            val failed = assertFailsWith<PairingException> { tvSide(tv).receiveAccount() }
            assertEquals(PairingException::class, failed::class)
        }

    @Test
    fun cancelOnTheTvReachesThePhoneBeforeItMatches() =
        pairing {
            val (phone, tv) = connect()
            tvSide(tv).stop(PairingStop.Cancelled)
            assertEquals(TvAnswer.Stopped(PairingStop.Cancelled), phone.answer())
        }

    @Test
    fun aTvThatStopsWaitingTellsThePhone() =
        pairing {
            val (phone, tv) = connect()
            val stopped = assertFailsWith<PairingStoppedException> { tvSide(tv, accountTimeoutMillis = 200).receiveAccount() }
            assertEquals(PairingStop.TimedOut, stopped.reason)
            assertEquals(TvAnswer.Stopped(PairingStop.TimedOut), phone.answer())
        }

    @Test
    fun stopMessagesReadBack() {
        PairingStop.entries.forEach { assertEquals(it, PairingMessages.readStopped(PairingMessages.stopped(it))) }
        assertNull(PairingMessages.readStopped("""{"v":1,"type":"stopped","reason":"bored"}""".encodeToByteArray()))
        assertNull(PairingMessages.readStopped("""{"v":1,"type":"stopped"}""".encodeToByteArray()))
        assertNull(PairingMessages.readStopped("""{"v":1,"type":"signed-in","reason":"no-match"}""".encodeToByteArray()))
        assertNull(PairingMessages.readStopped("""{"v":2,"type":"stopped","reason":"no-match"}""".encodeToByteArray()))
    }

    @Test
    fun repliesAreChecked() {
        fun read(json: String) = PairingMessages.readSignedIn(json.encodeToByteArray())

        assertEquals(PairedTvReply("tv-1", "Living room TV"), read("""{"v":1,"type":"signed-in","id":"tv-1","name":" Living room TV "}"""))
        assertNull(read("""{"v":2,"type":"signed-in","id":"tv-1","name":"TV"}"""))
        assertNull(read("""{"v":1,"type":"signed-in","id":"","name":"TV"}"""))
        assertNull(read("""{"v":1,"type":"signed-in","id":"tv-1","name":"   "}"""))
        assertNull(read("""{"v":1,"type":"signed-in","id":"tv-1","name":"${"x".repeat(65)}"}"""))
        assertNull(read("""{"v":1,"type":"signed-in","id":"${"x".repeat(65)}","name":"TV"}"""))
        assertNull(read("not json"))

        // Control and direction characters from the TV are dropped, so its name can't break or reorder the screen.
        fun named(name: String) = PairingMessages.readSignedIn(PairingMessages.signedIn(PairedTvReply("tv-1", name)))
        assertEquals(PairedTvReply("tv-1", "Living room TV"), named("Living\nroom‮ TV\u0007"))
        assertNull(named("‮‏"))
    }

    @Test
    fun anAccountMessageReadsBack() {
        val read = PairingMessages.readAccount(PairingMessages.account(token))
        assertEquals("the-token", read?.accessToken)
        assertNull(PairingMessages.readAccount("""{"v":1,"type":"account","token":"","expires":1}""".encodeToByteArray()))
    }
}
