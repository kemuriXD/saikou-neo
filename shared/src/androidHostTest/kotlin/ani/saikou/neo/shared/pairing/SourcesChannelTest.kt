package ani.saikou.neo.shared.pairing

import ani.saikou.neo.shared.sources.ListedSource
import ani.saikou.neo.shared.sources.PickedSources
import ani.saikou.neo.shared.sources.SourcesPhone
import ani.saikou.neo.shared.sources.SourcesTv
import ani.saikou.neo.shared.sources.TvSource
import ani.saikou.neo.shared.sources.TvSources
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun source(
    id: String,
    apk: String = "https://example.com/$id.apk",
) = ListedSource(id, "Source $id", "1.0.0", 1, apk)

/** Add from my phone over a real [PairingListener] (#405, ADR 0042). */
@RunWith(RobolectricTestRunner::class)
class SourcesChannelTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val tvKeys = PairingKeyPair.generate()
    private val session = newPairingSessionId()
    private val listener = PairingListener(tvKeys, session, ServerSocket(0, 50, loopback), handshakeTimeoutMillis = 2_000)
    private val tvSources = TvSources(listOf(TvSource("com.example.one", "1.2.0", hasList = true)), 1..4)
    private val fromList =
        PickedSources.FromList(
            "https://example.com/sources.json",
            listOf(source("com.example.one"), source("com.example.two")),
        )

    @AfterTest
    fun closeListener() = listener.close()

    private fun pairing(block: suspend CoroutineScope.() -> Unit) = runBlocking { withTimeout(10_000) { block() } }

    /** Connects a phone; returns the phone's side and the TV's. Both show the check code, and nothing has been sent. */
    private suspend fun CoroutineScope.connect(
        matchTimeoutMillis: Int = 2_000,
        pickTimeoutMillis: Int = 2_000,
    ): Pair<SourcesTv, SourcesPhone> {
        val tv = async { listener.accept() }
        val phone = connectPhoneForSources(loopback.hostAddress!!, listener.port, tvKeys.publicKey, session, 2_000)
        return phone to ChannelSourcesPhone(listener, tv.await(), matchTimeoutMillis, pickTimeoutMillis)
    }

    /** Both say the codes match, and the phone hears what the TV has. */
    private suspend fun confirm(
        phone: SourcesTv,
        tv: SourcesPhone,
    ) {
        phone.confirmCodes()
        tv.receiveMatch()
        tv.sendTvSources(tvSources)
        assertEquals(tvSources, phone.receiveTvSources())
    }

    /** Why the TV stopped, when [end] says it did, or null for a connection that dropped. */
    private fun stopReason(end: PairingException): PairingStop? = (end as? PairingStoppedException)?.reason

    /** Connects a phone to a bare TV channel, to see exactly what the phone sends. */
    private suspend fun CoroutineScope.connectRaw(): Pair<SourcesTv, PairingChannel> {
        val tv = async { listener.accept() }
        val phone = connectPhoneForSources(loopback.hostAddress!!, listener.port, tvKeys.publicKey, session, 2_000)
        return phone to tv.await()
    }

    /** Asserts nothing more arrives from the phone: the connection ends instead. */
    private suspend fun PairingChannel.assertNothingMore() {
        val heard = withContext(Dispatchers.IO) { runCatching { receive(1_000) }.getOrNull() }
        assertNull(heard?.decodeToString())
    }

    /** The phone's raw channel, to send what a well-behaved phone wouldn't. */
    private suspend fun SourcesTv.sendRaw(text: String) =
        withContext(Dispatchers.IO) { (this@sendRaw as ChannelSourcesTv).channel.send(text.encodeToByteArray()) }

    @Test
    fun theTvSaysWhatItHasAndGetsThePicks() =
        pairing {
            val (phone, tv) = connect()
            assertEquals(tv.checkCode, phone.checkCode)
            confirm(phone, tv)

            val sent = async { phone.send(fromList) }
            assertEquals(fromList, tv.receivePicked())
            tv.replyReceived()
            sent.await()
        }

    @Test
    fun oneApkGoesWithNoList() =
        pairing {
            val (phone, tv) = connect()
            confirm(phone, tv)
            val apk = PickedSources.OneApk("https://example.com/source.apk")

            val sent = async { phone.send(apk) }
            assertEquals(apk, tv.receivePicked())
            tv.replyReceived()
            sent.await()
        }

    @Test
    fun noTvStateBeforeThePhoneSaysTheCodesMatch() =
        pairing {
            val (phone, tv) = connect()

            assertFailsWith<PairingException> { tv.sendTvSources(tvSources) }
            assertFailsWith<PairingException> { tv.receivePicked() }
            assertFailsWith<PairingException> { phone.receiveTvSources() }
            assertFailsWith<PairingException> { phone.send(fromList) }
        }

    @Test
    fun picksBeforeTheCodesMatchAreRefused() =
        pairing {
            val (phone, tv) = connect()

            phone.sendRaw(String(SourcesMessages.picked(fromList)))
            assertFailsWith<PairingException> { tv.receiveMatch() }
        }

    @Test
    fun codesThatDoNotMatchOnThePhoneEndIt() =
        pairing {
            val (phone, tv) = connect()
            phone.stop(PairingStop.NoMatch)

            val stopped = assertFailsWith<PairingStoppedException> { tv.receiveMatch() }
            assertEquals(PairingStop.NoMatch, stopped.reason)
        }

    @Test
    fun codesThatDoNotMatchOnTheTvEndIt() =
        pairing {
            val (phone, tv) = connect()
            phone.confirmCodes()
            tv.receiveMatch()
            tv.stop(PairingStop.NoMatch)

            val stopped = assertFailsWith<PairingStoppedException> { phone.receiveTvSources() }
            assertEquals(PairingStop.NoMatch, stopped.reason)
        }

    @Test
    fun codesThatDoNotMatchOnTheTvReachThePhoneBeforeItConfirms() =
        pairing {
            val (phone, tv) = connect()
            tv.stop(PairingStop.NoMatch)

            assertEquals(PairingStop.NoMatch, stopReason(withTimeout(2_000) { phone.awaitEnd() }))
        }

    @Test
    fun cancelOnTheTvReachesThePhoneBeforeItConfirms() =
        pairing {
            val (phone, tv) = connect()
            tv.stop(PairingStop.Cancelled)

            assertEquals(PairingStop.Cancelled, stopReason(withTimeout(2_000) { phone.awaitEnd() }))
        }

    @Test
    fun aTvThatClosesWhileThePhonePicksIsHeardAtOnce() =
        pairing {
            val (phone, tv) = connect()
            confirm(phone, tv)
            tv.close()

            assertNull(stopReason(withTimeout(2_000) { phone.awaitEnd() }))
        }

    @Test
    fun aTvThatStopsWhileThePhonePicksIsHeardAtOnce() =
        pairing {
            val (phone, tv) = connect()
            confirm(phone, tv)
            tv.stop(PairingStop.Cancelled)

            assertEquals(PairingStop.Cancelled, stopReason(withTimeout(2_000) { phone.awaitEnd() }))
        }

    @Test
    fun nothingIsSentAfterTheTvStopsAtTheCheckCode() =
        pairing {
            val (phone, tv) = connectRaw()
            withContext(Dispatchers.IO) { tv.send(PairingMessages.stopped(PairingStop.Cancelled)) }
            phone.awaitEnd()

            val stopped = assertFailsWith<PairingStoppedException> { phone.confirmCodes() }
            assertEquals(PairingStop.Cancelled, stopped.reason)
            phone.stop(PairingStop.Cancelled)
            tv.assertNothingMore()
        }

    @Test
    fun nothingIsSentAfterTheTvStopsWhileThePhonePicks() =
        pairing {
            val (phone, tv) = connectRaw()
            phone.confirmCodes()
            withContext(Dispatchers.IO) {
                assertTrue(SourcesMessages.isCodesMatch(tv.receive(2_000)))
                tv.send(SourcesMessages.tvSources(tvSources))
            }
            assertEquals(tvSources, phone.receiveTvSources())
            withContext(Dispatchers.IO) { tv.send(PairingMessages.stopped(PairingStop.Cancelled)) }
            phone.awaitEnd()

            assertFailsWith<PairingStoppedException> { phone.send(fromList) }
            phone.stop(PairingStop.Cancelled)
            tv.assertNothingMore()
        }

    @Test
    fun whatTheTvHasIsRefusedBeforeThePhoneConfirms() =
        pairing {
            val (phone, tv) = connectRaw()
            withContext(Dispatchers.IO) { tv.send(SourcesMessages.tvSources(tvSources)) }

            val end = withTimeout(2_000) { phone.awaitEnd() }
            assertNull(stopReason(end))
            assertFailsWith<PairingException> { phone.confirmCodes() }
            tv.assertNothingMore()
        }

    @Test
    fun aTvThatDropsTheConnectionFailsOnThePhone() =
        pairing {
            val (phone, tv) = connect()
            phone.confirmCodes()
            tv.close()

            assertFailsWith<PairingException> { phone.receiveTvSources() }
        }

    @Test
    fun aTvThatDropsWhileThePhoneSendsFailsOnThePhone() =
        pairing {
            val (phone, tv) = connect()
            confirm(phone, tv)
            tv.close()

            assertFailsWith<PairingException> { phone.send(fromList) }
        }

    @Test
    fun aTvThatNeverTakesThePicksIsGivenUpOnSoon() =
        pairing {
            val accepted = async { listener.accept() }
            // What the TV has may take as long as a code lasts, but the reply to the picks comes straight away.
            val phone = connectPhoneForSources(loopback.hostAddress!!, listener.port, tvKeys.publicKey, session, 60_000, 300)
            val tv = ChannelSourcesPhone(listener, accepted.await(), 2_000, 2_000)
            confirm(phone, tv)

            assertFailsWith<PairingException> { phone.send(fromList) }
        }

    @Test
    fun aPhoneThatNeverConfirmsHearsTheTvStopWaiting() =
        pairing {
            val (phone, tv) = connect(matchTimeoutMillis = 200)

            val stopped = assertFailsWith<PairingStoppedException> { tv.receiveMatch() }
            assertEquals(PairingStop.TimedOut, stopped.reason)
            assertEquals(PairingStop.TimedOut, stopReason(phone.awaitEnd()))
        }

    @Test
    fun aPhoneThatTakesTooLongToPickHearsSo() =
        pairing {
            val (phone, tv) = connect(pickTimeoutMillis = 200)
            confirm(phone, tv)

            val stopped = assertFailsWith<PairingStoppedException> { tv.receivePicked() }
            assertEquals(PairingStop.TimedOut, stopped.reason)
            assertEquals(PairingStop.TimedOut, stopReason(phone.awaitEnd()))
        }

    @Test
    fun aMalformedMessageDropsThePhone() =
        pairing {
            val (phone, tv) = connect()
            confirm(phone, tv)

            phone.sendRaw("""{"v":1,"type":"sources","list":"https://example.com/a.json"}""")
            assertFailsWith<UnusableMessageException> { tv.receivePicked() }
            // The phone hears the TV couldn't use them, rather than a lost connection.
            assertEquals(PairingStop.Refused, stopReason(phone.awaitEnd()))
        }

    @Test
    fun thePhoneCancelling() =
        pairing {
            val (phone, tv) = connect()
            confirm(phone, tv)
            phone.stop(PairingStop.Cancelled)

            val stopped = assertFailsWith<PairingStoppedException> { tv.receivePicked() }
            assertEquals(PairingStop.Cancelled, stopped.reason)
        }

    @Test
    fun bothMessagesRoundTrip() {
        assertEquals(tvSources, SourcesMessages.readTvSources(SourcesMessages.tvSources(tvSources)))
        assertEquals(fromList, SourcesMessages.readPicked(SourcesMessages.picked(fromList)))
        val apk = PickedSources.OneApk("https://example.com/source.apk")
        assertEquals(apk, SourcesMessages.readPicked(SourcesMessages.picked(apk)))
    }

    @Test
    fun theTvChecksEveryEntryLikeAList() {
        val list = "https://example.com/sources.json"
        val refused =
            listOf(
                "not json",
                """{"v":2,"type":"sources","list":"$list","sources":[${entry("com.example.one")}]}""",
                """{"v":1,"type":"account","list":"$list","sources":[${entry("com.example.one")}]}""",
                // A list link the TV wouldn't take typed, or none.
                """{"v":1,"type":"sources","list":"http://example.com/a.json","sources":[${entry("com.example.one")}]}""",
                """{"v":1,"type":"sources","sources":[${entry("com.example.one")}]}""",
                // An entry with a bad id, an APK link the TV wouldn't take, or a field missing.
                """{"v":1,"type":"sources","list":"$list","sources":[${entry("not-a-package")}]}""",
                """{"v":1,"type":"sources","list":"$list","sources":[${entry("com.example.one", "http://example.com/a.apk")}]}""",
                """{"v":1,"type":"sources","list":"$list","sources":[{"id":"com.example.one","name":"One","api":1,"apk":"https://example.com/a.apk"}]}""",
                // The same source twice, none, or more than a list may hold.
                """{"v":1,"type":"sources","list":"$list","sources":[${entry("com.example.one")},${entry("com.example.one")}]}""",
                """{"v":1,"type":"sources","list":"$list","sources":[]}""",
                """{"v":1,"type":"sources","list":"$list","sources":[${(1..51).joinToString { entry("com.example.s$it") }}]}""",
                // One APK with a link the TV wouldn't take, or mixed with a list.
                """{"v":1,"type":"sources","apk":"http://example.com/a.apk"}""",
                """{"v":1,"type":"sources","apk":"https://example.com/a.apk","list":"$list"}""",
            )

        refused.forEach { assertNull(SourcesMessages.readPicked(it.encodeToByteArray()), it) }
    }

    @Test
    fun linksWithHiddenCharactersOrTooLongAreRefused() {
        val list = "https://example.com/sources.json"
        val refused =
            listOf(
                // A direction override, written as a JSON escape.
                """{"v":1,"type":"sources","apk":"https://example.com/a‮.apk"}""",
                """{"v":1,"type":"sources","list":"$list","sources":[${entry("com.example.one", "https://example.com/a‮.apk")}]}""",
                """{"v":1,"type":"sources","list":"https://example.com/${"a".repeat(2_000)}","sources":[${entry("com.example.one")}]}""",
            )

        refused.forEach { assertNull(SourcesMessages.readPicked(it.encodeToByteArray()), it) }
    }

    @Test
    fun deepNestingIsRefusedWithoutCrashing() {
        val deep = """{"v":1,"type":"codes-match","x":${"[".repeat(30_000)}${"]".repeat(30_000)}}"""

        assertFalse(SourcesMessages.isCodesMatch(deep.encodeToByteArray()))
        assertNull(PairingMessages.readAccount(deep.replace("codes-match", "account").encodeToByteArray()))
        // The parser also takes single-quoted strings and comments, so a quote hidden in either can't hide the nesting.
        val nested = "${"[".repeat(30_000)}${"]".repeat(30_000)}"
        val singleQuoted = """{"v":1,"type":"codes-match",'q':'"','x':$nested}"""
        val commented = """{"v":1,"type":"account", /* " */ "x":$nested}"""
        assertFalse(SourcesMessages.isCodesMatch(singleQuoted.encodeToByteArray()))
        assertNull(PairingMessages.readAccount(commented.encodeToByteArray()))
        // A few brackets in a string are fine.
        assertTrue(SourcesMessages.isCodesMatch("""{"v":1,"type":"codes-match","x":"${"[".repeat(100)}"}""".encodeToByteArray()))
    }

    @Test
    fun anOversizedMessageIsRefused() {
        val padding = "x".repeat(70_000)
        val big = """{"v":1,"type":"sources","list":"https://example.com/a.json","pad":"$padding","sources":[${entry(
            "com.example.one",
        )}]}"""

        assertNull(SourcesMessages.readPicked(big.encodeToByteArray()))
        val tooMany = fromList.copy(sources = (1..50).map { source("com.example.s$it", "https://example.com/" + "a".repeat(1_500)) })
        assertFailsWith<PairingException> { SourcesMessages.picked(tooMany) }
    }

    @Test
    fun aBrokenTvReportIsRefused() {
        val refused =
            listOf(
                """{"v":1,"type":"tv-sources","api":{"min":3,"max":1},"installed":[]}""",
                """{"v":1,"type":"tv-sources","api":{"min":1,"max":4},"installed":[{"id":"bad id","version":"1"}]}""",
                """{"v":1,"type":"tv-sources","installed":[]}""",
                // A version with a direction override, written as a JSON escape.
                """{"v":1,"type":"tv-sources","api":{"min":1,"max":4},"installed":[{"id":"com.example.one","version":"1‮0"}]}""",
            )

        refused.forEach { assertNull(SourcesMessages.readTvSources(it.encodeToByteArray()), it) }
    }

    private fun entry(
        id: String,
        apk: String = "https://example.com/$id.apk",
    ) = """{"id":"$id","name":"Source $id","version":"1.0.0","api":1,"apk":"$apk"}"""
}
