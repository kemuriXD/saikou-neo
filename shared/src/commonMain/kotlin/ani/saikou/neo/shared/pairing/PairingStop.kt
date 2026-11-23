package ani.saikou.neo.shared.pairing

/** Why one side stopped pairing at the check code. It goes to the other side before the connection closes (#414). */
public enum class PairingStop {
    /** It doesn't match on the phone, or, when adding sources, on the TV. */
    NoMatch,

    /** Close or Cancel on the phone, or Cancel, Not now or Back on the TV. */
    Cancelled,

    /** AniList didn't accept the account the phone sent, or, when adding sources, the TV couldn't use the phone's picks. */
    Refused,

    /** The TV stopped waiting for It matches. */
    TimedOut,
}

/** The other side stopped pairing and said why. A connection that closes without saying is a plain [PairingException]. */
public class PairingStoppedException(
    public val reason: PairingStop,
) : PairingException("Pairing stopped: $reason")

/** The other side sent something that couldn't be read or wasn't its turn, so it was dropped. */
public class UnusableMessageException(
    message: String,
) : PairingException(message)

/** The TV has no local network address, so no phone could reach it. */
public class NoNetworkException : PairingException("This TV isn't on a local network")

/** What the TV says once the phone is at the check code. */
public sealed interface TvAnswer {
    /** The TV is signed in. */
    public data class SignedIn(
        val reply: PairedTvReply,
    ) : TvAnswer

    /** The TV stopped pairing, for [reason]. */
    public data class Stopped(
        val reason: PairingStop,
    ) : TvAnswer
}
