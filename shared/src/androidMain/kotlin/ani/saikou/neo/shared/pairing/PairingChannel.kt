package ani.saikou.neo.shared.pairing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * An encrypted connection between a phone and a TV, after the handshake.
 *
 * How the protocol travels over TCP, so another companion can implement it. [PairingCipher] has the crypto.
 *
 * - The TV listens on a TCP port on the local network while pairing is open. It shares its address, port, public
 *   key and session ID with the phone, by QR code or a lookup by short code (later issues cover both).
 * - Every message is a frame: a 4-byte big-endian length, then that many bytes. The length must be between 1 and
 *   65,536, or the receiver drops the connection.
 * - Each attempt is a new TCP connection. The phone picks a new key pair and a 32-byte nonce for it.
 *
 * The handshake, in order:
 * 1. Phone sends one 98-byte frame: `0x01` (the protocol version), its 65-byte public key, then
 *    [pairingCommitment] of that key and its nonce. Any other size or version, and the TV drops the attempt.
 * 2. TV sends one frame: its own 32-byte nonce, new for this attempt.
 * 3. Phone sends its 32-byte nonce, then `seal("saikou-neo/hello")`, as two frames without waiting in between.
 * 4. TV checks the nonce against the commitment and opens the hello. If either fails, the attempt is dropped.
 *    A hello that decrypts means the phone used the TV's real key and session ID.
 * 5. TV sends `seal("saikou-neo/hello")`. The phone opens it, which proves it reached the TV's private key.
 *
 * Both sides then show [checkCode], and the user confirms they match before any account data is sent with
 * [send]. The TV gives each attempt [PairingListener]'s handshake timeout and 2 seconds for the first frame. It
 * handles up to 8 attempts at once. A third attempt from one address replaces the oldest from that address. It
 * drops any that fail, so a bad, slow or wrong
 * attempt can't end pairing or hold it up.
 *
 * One limit does end pairing: after 10 attempts that got the TV's nonce but never sent a valid hello, the TV
 * stops listening and the user has to start pairing again. Each of those attempts gives someone in the middle a
 * one in a million chance of matching the check codes, so this keeps their total chance near one in 100,000.
 *
 * [send] and [receive] block, so call them off the main thread.
 */
public class PairingChannel internal constructor(
    private val socket: Socket,
    private val cipher: PairingCipher,
) : Closeable {
    private val sendLock = Any()

    /** The 6-digit code both screens show. */
    public val checkCode: String get() = cipher.checkCode

    /** Encrypts and sends one message. */
    public fun send(message: ByteArray): Unit =
        wrapIo {
            // Sealing and writing together keeps the frames in counter order.
            synchronized(sendLock) { socket.writeFrame(cipher.seal(message)) }
        }

    /** Waits up to [timeoutMillis] for the peer's next message. Any failure closes the connection. */
    public fun receive(timeoutMillis: Int): ByteArray =
        try {
            wrapIo {
                socket.soTimeout = timeoutMillis
                cipher.open(socket.readFrame())
            }
        } catch (e: PairingException) {
            close()
            throw e
        }

    override fun close(): Unit = socket.close()
}

/**
 * The TV's side of pairing. It owns [server], a socket bound to the TV's local network address, and closes it
 * in [close]. Call [accept] while pairing is open.
 */
public class PairingListener(
    private val keyPair: PairingKeyPair,
    private val sessionId: ByteArray,
    private val server: ServerSocket,
    private val handshakeTimeoutMillis: Long = 5_000,
) : Closeable {
    private val unfinished = AtomicInteger()
    private val perAddress = HashMap<InetAddress, ArrayDeque<Socket>>()

    init {
        server.soTimeout = ACCEPT_POLL_MILLIS
    }

    /** The port to share with the phone. */
    public val port: Int get() = server.localPort

    /**
     * Waits for the first phone that completes the handshake, then drops the other attempts in progress. Failed
     * attempts are dropped and it keeps waiting, until it's cancelled or [close] is called (then it throws
     * [PairingException]). Call it again to take another attempt, for example when the user says the check codes
     * didn't match. After too many unfinished attempts it throws [PairingException] for good; open a new
     * listener with a new key and session ID to try again.
     */
    public suspend fun accept(): PairingChannel =
        coroutineScope {
            if (unfinished.get() >= MAX_UNFINISHED) throw PairingException(TOO_MANY_ATTEMPTS)
            val winner = CompletableDeferred<PairingChannel>()
            val running = AtomicInteger()
            val acceptor =
                launch(Dispatchers.IO) {
                    while (true) {
                        ensureActive()
                        val socket = nextSocket() ?: continue
                        if (running.incrementAndGet() > MAX_ATTEMPTS) {
                            running.decrementAndGet()
                            socket.close()
                            continue
                        }
                        track(socket)
                        launch {
                            val gotNonce = AtomicBoolean()
                            val channel =
                                try {
                                    attempt(socket, gotNonce)
                                } finally {
                                    running.decrementAndGet()
                                    untrack(socket)
                                }
                            when {
                                channel != null -> {
                                    if (!winner.complete(channel)) channel.close()
                                }

                                gotNonce.get() && unfinished.incrementAndGet() >= MAX_UNFINISHED -> {
                                    winner.completeExceptionally(PairingException(TOO_MANY_ATTEMPTS))
                                }
                            }
                        }
                    }
                }
            try {
                winner.await()
            } finally {
                acceptor.cancel()
            }
        }

    private suspend fun nextSocket(): Socket? =
        try {
            server.accept()
        } catch (_: SocketTimeoutException) {
            null
        } catch (e: IOException) {
            if (server.isClosed) throw PairingException("Pairing was closed", e)
            // A passing error, such as running out of file handles. Back off and keep listening.
            delay(ACCEPT_POLL_MILLIS.toLong())
            null
        }

    /** Keeps attempts per address. A third from one address closes the oldest, so one device can't fill the slots. */
    private fun track(socket: Socket) {
        val oldest =
            synchronized(perAddress) {
                val sockets = perAddress.getOrPut(socket.inetAddress) { ArrayDeque() }
                sockets.addLast(socket)
                if (sockets.size > MAX_PER_ADDRESS) sockets.removeFirst() else null
            }
        oldest?.close()
    }

    private fun untrack(socket: Socket) {
        synchronized(perAddress) {
            val sockets = perAddress[socket.inetAddress] ?: return
            sockets.remove(socket)
            if (sockets.isEmpty()) perAddress.remove(socket.inetAddress)
        }
    }

    /** One attempt: its channel, or null after closing the socket if anything went wrong. */
    private suspend fun attempt(
        socket: Socket,
        gotNonce: AtomicBoolean,
    ): PairingChannel? =
        try {
            socket.within(handshakeTimeoutMillis) { handshake(socket, gotNonce) }
        } catch (e: CancellationException) {
            socket.close()
            throw e
        } catch (_: Exception) {
            socket.close()
            null
        }

    private fun handshake(
        socket: Socket,
        gotNonce: AtomicBoolean,
    ): PairingChannel {
        socket.soTimeout = FIRST_FRAME_MILLIS
        val hello = socket.readFrame()
        socket.soTimeout = 0
        if (hello.size != HELLO_FRAME_SIZE || hello[0] != PROTOCOL_VERSION) throw PairingException("Unknown pairing hello")
        val phoneKey = hello.copyOfRange(1, 1 + PUBLIC_KEY_SIZE)
        val commitment = hello.copyOfRange(1 + PUBLIC_KEY_SIZE, hello.size)
        val tvNonce = newPairingNonce()
        gotNonce.set(true)
        socket.writeFrame(tvNonce)
        val phoneNonce = socket.readFrame()
        if (!MessageDigest.isEqual(pairingCommitment(phoneKey, phoneNonce), commitment)) {
            throw PairingException("The phone's nonce doesn't match its commitment")
        }
        val cipher = PairingCipher.create(PairingRole.TV, keyPair, phoneKey, sessionId, tvNonce, phoneNonce)
        if (!cipher.open(socket.readFrame()).contentEquals(HELLO)) throw PairingException("Unexpected pairing hello")
        socket.writeFrame(cipher.seal(HELLO))
        return PairingChannel(socket, cipher)
    }

    override fun close(): Unit = server.close()
}

/**
 * The phone's side of pairing: connects to the TV at [host]:[port] and runs the handshake within [timeoutMillis].
 * Throws [PairingException] if the TV can't be reached or isn't the one holding [tvPublicKey].
 */
public suspend fun connectToTv(
    host: String,
    port: Int,
    tvPublicKey: ByteArray,
    sessionId: ByteArray,
    timeoutMillis: Long = 10_000,
): PairingChannel {
    val socket = Socket()
    return try {
        socket.within(timeoutMillis) {
            socket.connect(InetSocketAddress(host, port), timeoutMillis.toInt())
            val own = PairingKeyPair.generate()
            val phoneNonce = newPairingNonce()
            socket.writeFrame(byteArrayOf(PROTOCOL_VERSION) + own.publicKey + pairingCommitment(own.publicKey, phoneNonce))
            val tvNonce = socket.readFrame()
            val cipher = PairingCipher.create(PairingRole.PHONE, own, tvPublicKey, sessionId, tvNonce, phoneNonce)
            socket.writeFrame(phoneNonce)
            socket.writeFrame(cipher.seal(HELLO))
            if (!cipher.open(socket.readFrame()).contentEquals(HELLO)) throw PairingException("Unexpected pairing hello")
            PairingChannel(socket, cipher)
        }
    } catch (e: Throwable) {
        socket.close()
        throw e
    }
}

private const val PROTOCOL_VERSION: Byte = 1
private const val PUBLIC_KEY_SIZE = 65
private const val HELLO_FRAME_SIZE = 1 + PUBLIC_KEY_SIZE + 32
private const val MAX_FRAME = 65_536
private const val MAX_ATTEMPTS = 8
private const val MAX_PER_ADDRESS = 2
private const val MAX_UNFINISHED = 10
private const val FIRST_FRAME_MILLIS = 2_000
private const val TOO_MANY_ATTEMPTS = "Too many unfinished pairing attempts. Start pairing again."
private const val ACCEPT_POLL_MILLIS = 250
private val HELLO = "saikou-neo/hello".encodeToByteArray()

/**
 * Runs blocking [block] on an IO thread, closing this socket if it takes longer than [millis] or the caller is
 * cancelled. Closing is what unblocks a read, so a peer that trickles bytes can't hold the attempt open. If the
 * deadline closed the socket, this throws even when [block] finished just in time.
 */
private suspend fun <T> Socket.within(
    millis: Long,
    block: () -> T,
): T =
    coroutineScope {
        val deadline = launch { delay(millis).also { close() } }
        val result =
            try {
                async(Dispatchers.IO) { wrapIo(block) }.await()
            } catch (e: CancellationException) {
                close()
                throw e
            }
        deadline.cancelAndJoin()
        if (isClosed) throw PairingException("The pairing attempt timed out")
        result
    }

private inline fun <T> wrapIo(block: () -> T): T =
    try {
        block()
    } catch (e: IOException) {
        throw PairingException("The pairing connection failed", e)
    }

internal fun Socket.writeFrame(bytes: ByteArray) {
    DataOutputStream(getOutputStream()).apply {
        writeInt(bytes.size)
        write(bytes)
        flush()
    }
}

internal fun Socket.readFrame(): ByteArray {
    val input = DataInputStream(getInputStream())
    val size = input.readInt()
    if (size !in 1..MAX_FRAME) throw PairingException("A pairing frame was the wrong size")
    return ByteArray(size).also { input.readFully(it) }
}
