package ani.saikou.neo.shared.sources

import ani.saikou.neo.shared.pairing.PairingException
import ani.saikou.neo.shared.pairing.PairingInvite
import ani.saikou.neo.shared.pairing.PairingStop

/** The TV's Add from my phone board, listening for a phone (ADR 0042). */
public interface TvSourcesSession {
    public val invite: PairingInvite

    /** Waits for a phone to finish the handshake, as [ani.saikou.neo.shared.pairing.TvPairingSession.accept] does. */
    public suspend fun accept(onAttempt: (inProgress: Boolean) -> Unit): SourcesPhone

    /** Stops listening and stops advertising the code. */
    public fun close()
}

/**
 * A phone connected to the TV's board, after the handshake. Both screens show [checkCode], and nothing else goes
 * either way until both people confirm it matches: [receiveMatch] hears the phone's, and the TV's own comes from its
 * screen. Only then does [sendTvSources] work, and only after that [receivePicked].
 */
public interface SourcesPhone {
    /** The 6-digit code both screens show. */
    public val checkCode: String

    /**
     * Waits for It matches on the phone. Throws [ani.saikou.neo.shared.pairing.PairingStoppedException] when the phone
     * stops, for example because the codes don't match, or when the TV stops waiting (after telling the phone), and
     * [ani.saikou.neo.shared.pairing.PairingException] if the connection drops or the phone sends anything else, such
     * as its picks. Either way the phone counts as turned away.
     */
    public suspend fun receiveMatch()

    /** Tells the phone what this TV has. Only once the phone said the codes match, and the TV did too. */
    public suspend fun sendTvSources(tv: TvSources)

    /** Waits for the phone's picks, checked as a typed list is. Only after [sendTvSources]. Fails as [receiveMatch] does. */
    public suspend fun receivePicked(): PickedSources

    /** Tells the phone the TV has its picks. */
    public suspend fun replyReceived()

    /** Tells the phone why the TV stopped, then drops it. It counts as turned away. */
    public suspend fun stop(reason: PairingStop)

    public fun close()
}

/**
 * The TV's board as the phone sees it, after the handshake. Both screens show [checkCode]. Nothing goes either way
 * until [confirmCodes], when the user says it matches; if it doesn't, [stop] with
 * [ani.saikou.neo.shared.pairing.PairingStop.NoMatch].
 */
public interface SourcesTv {
    /** The 6-digit code both screens show. */
    public val checkCode: String

    /** It matches on the phone: tells the TV. */
    public suspend fun confirmCodes()

    /**
     * Waits for what the TV has, which it sends once It matches is chosen on the TV too. Throws
     * [ani.saikou.neo.shared.pairing.PairingStoppedException] when the TV stopped, and
     * [ani.saikou.neo.shared.pairing.PairingException] if it drops or says anything else. Only after [confirmCodes].
     */
    public suspend fun receiveTvSources(): TvSources

    /**
     * Sends [picked], then waits for the TV to say it has them. Throws as [receiveTvSources] does, and
     * [ani.saikou.neo.shared.pairing.PairingException] if [picked] is too big to send. Only after [receiveTvSources].
     */
    public suspend fun send(picked: PickedSources)

    /**
     * Waits for the TV to end the session on its own, from the check code on, and says how: a
     * [ani.saikou.neo.shared.pairing.PairingStoppedException] when it stopped and said why, or a
     * [ani.saikou.neo.shared.pairing.PairingException] when the connection dropped or the TV sent something out of turn.
     * It never returns once this phone has stopped or closed the connection, or the TV has taken the picks.
     */
    public suspend fun awaitEnd(): PairingException

    /** Tells the TV why the phone stopped, then drops the connection. Says nothing to a TV that has already gone. */
    public suspend fun stop(reason: PairingStop)

    public fun close()
}
