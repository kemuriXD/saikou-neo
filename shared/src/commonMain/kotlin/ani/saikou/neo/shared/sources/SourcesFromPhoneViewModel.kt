package ani.saikou.neo.shared.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.pairing.NoNetworkException
import ani.saikou.neo.shared.pairing.PairingStop
import ani.saikou.neo.shared.pairing.PairingStoppedException
import ani.saikou.neo.shared.pairing.TV_CODE_SECONDS
import ani.saikou.neo.shared.pairing.TvPairingNote
import ani.saikou.neo.shared.pairing.newTvCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What happened to one source the phone sent, once Install was pressed. */
public sealed interface InstallOutcome {
    public data class Installed(
        val source: InstalledSource,
    ) : InstallOutcome

    /** It's built for another version of the app, so it wasn't installed. */
    public data class CantUse(
        val problem: SourceProblem,
    ) : InstallOutcome

    /** The same or a newer version is installed, so it was left alone. */
    public data object AlreadyInstalled : InstallOutcome

    /** Its APK couldn't be downloaded. */
    public data object NotDownloaded : InstallOutcome

    /** The file isn't a source, or isn't the one its entry names. */
    public data object NotTheSource : InstallOutcome
}

/** Where Add from my phone on the TV is (ADR 0042). */
public sealed interface FromPhoneState {
    /** Opening the board. */
    public data object Starting : FromPhoneState

    /** The QR code and [code], with [secondsLeft] until they run out. [note] says why the last phone left. */
    public data class Waiting(
        val qrText: String,
        val code: String,
        val secondsLeft: Int,
        val note: TvPairingNote? = null,
    ) : FromPhoneState

    /** A phone is making the encrypted link. */
    public data object Connecting : FromPhoneState

    /**
     * A phone is connected. Both screens show [checkCode], and nothing is sent either way until both people say it
     * matches. [confirmed] once It matches is chosen on the TV, while the phone hasn't said so yet.
     */
    public data class CheckCode(
        val checkCode: String,
        val confirmed: Boolean = false,
    ) : FromPhoneState

    /** The codes matched, the phone has heard what this TV has, and it's picking sources. */
    public data class Picking(
        val checkCode: String,
    ) : FromPhoneState

    /**
     * What the phone sent. The board has closed, and nothing installs until Install. [statuses] has what each source is
     * on this TV as the picks arrived, in order, as Add a source would show it. A link to one APK has null, since it's
     * only checked as it installs.
     */
    public data class Confirm(
        val picked: PickedSources,
        val statuses: List<ChoiceStatus?>,
    ) : FromPhoneState {
        /** How many Install would install: not those already installed or built for another version of the app. */
        val toInstall: Int get() = statuses.count { it == null || it.canPick }
    }

    /** Installing one at a time. [outcomes] has one per source, in order, null until it's been tried. */
    public data class Installing(
        val picked: PickedSources,
        val outcomes: List<InstallOutcome?>,
    ) : FromPhoneState {
        /** Whether every source has been tried. */
        val done: Boolean get() = outcomes.none { it == null }
    }

    /** [code] ran out before a phone sent anything. */
    public data class Expired(
        val code: String,
    ) : FromPhoneState

    /** The board couldn't go on, or was cancelled. Nothing was installed. */
    public data object Stopped : FromPhoneState

    /** This TV isn't on a network, so no phone could reach it. */
    public data object NoNetwork : FromPhoneState
}

/**
 * Add from my phone (#534, ADR 0042). The board opens a session under a new code with [open] and waits for a phone.
 * Both screens show the check code, and only once it matches on the phone and on the TV ([confirmCodes]) does the phone
 * hear what [store] has and the API versions in [api]. It then sends its picks, which arrive already checked as a typed
 * list is. A phone that leaves sends the board back to the same code, saying why; codes that don't match on the TV
 * ([codesDontMatch]) stop the board. Picks wait on the confirm screen, each with what it is on this TV: nothing
 * installs until [install], which runs the TV's own checks again and installs one at a time, saving the list link with
 * each source (ADR 0046).
 */
public class SourcesFromPhoneViewModel(
    private val open: suspend (code: String) -> TvSourcesSession,
    private val store: SourceStore,
    private val downloads: SourceDownloads,
    private val api: IntRange,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val makeCode: () -> String = { newTvCode() },
) : ViewModel() {
    private val mutableState = MutableStateFlow<FromPhoneState>(FromPhoneState.Starting)
    public val state: StateFlow<FromPhoneState> = mutableState.asStateFlow()

    private var job: Job? = null
    private var session: TvSourcesSession? = null
    private var phone: SourcesPhone? = null
    private var tvMatch: CompletableDeferred<Unit>? = null

    init {
        newCode()
    }

    /** Opens the board with a new code, dropping any phone on the old one. Ignored while sources install. */
    public fun newCode() {
        if (installing()) return
        stop()
        mutableState.value = FromPhoneState.Starting
        job = viewModelScope.launch { listen() }
    }

    /**
     * It matches on the TV, for the check code [shown] on screen. Ignored when the board's check code is another one,
     * such as a phone that took the board as It matches was pressed.
     */
    public fun confirmCodes(shown: String) {
        val checking = mutableState.value as? FromPhoneState.CheckCode ?: return
        if (checking.checkCode != shown) return
        mutableState.value = checking.copy(confirmed = true)
        tvMatch?.complete(Unit)
    }

    /** It doesn't match on the TV: the phone hears so, and the board stops, since someone else may be listening. */
    public fun codesDontMatch() {
        if (mutableState.value is FromPhoneState.CheckCode) end(PairingStop.NoMatch)
    }

    /**
     * Leaving the board, or Cancel on the confirm screen: closes the connection, and a phone that's connected hears it
     * was cancelled. What arrived is dropped and nothing is installed. Ignored while sources install.
     */
    public fun cancel() {
        if (!installing()) end(PairingStop.Cancelled)
    }

    private fun end(reason: PairingStop) {
        val leaving = phone
        phone = null
        stop()
        // Not cancellable, so the phone hears it even if this screen goes away straight after.
        leaving?.let { viewModelScope.launch(io + NonCancellable) { it.stop(reason) } }
        mutableState.value = FromPhoneState.Stopped
    }

    /** Install on the confirm screen: installs what the phone sent, one at a time. */
    public fun install() {
        val picked = (mutableState.value as? FromPhoneState.Confirm)?.picked ?: return
        job =
            viewModelScope.launch {
                val outcomes = MutableList<InstallOutcome?>(picked.size) { null }
                for (i in outcomes.indices) {
                    mutableState.value = FromPhoneState.Installing(picked, outcomes.toList())
                    outcomes[i] =
                        when (picked) {
                            is PickedSources.FromList -> installEntry(picked.sources[i], picked.list)
                            is PickedSources.OneApk -> installApk(picked.apk, null, null)
                        }
                }
                mutableState.value = FromPhoneState.Installing(picked, outcomes.toList())
            }
    }

    private fun installing(): Boolean = (mutableState.value as? FromPhoneState.Installing)?.done == false

    /** What [entry] is on this TV now, as Add a source would show it. */
    private fun statusOf(entry: ListedSource): ChoiceStatus {
        val installed =
            store.installed.value
                .orEmpty()
                .firstOrNull { it.id == entry.id }
        return choiceStatus(entry, installed?.version, installed?.list != null, store::problemFor)
    }

    /** What each of [picked] is on this TV now. A link to one APK can't be checked before it downloads. */
    private fun statusesOf(picked: PickedSources): List<ChoiceStatus?> =
        when (picked) {
            is PickedSources.FromList -> picked.sources.map(::statusOf)
            is PickedSources.OneApk -> listOf(null)
        }

    /** Checks [entry] again as the TV checks a list entry before installing it, since what's installed may have changed. */
    private suspend fun installEntry(
        entry: ListedSource,
        list: String,
    ): InstallOutcome =
        when (val status = statusOf(entry)) {
            is ChoiceStatus.CantUse -> InstallOutcome.CantUse(status.problem)
            ChoiceStatus.Installed -> InstallOutcome.AlreadyInstalled
            else -> installApk(entry.apk, entry.id, list)
        }

    /** The APK must be the source with [id] when there is one, so a list can't install something else. */
    private suspend fun installApk(
        apk: String,
        id: String?,
        list: String?,
    ): InstallOutcome {
        val file = downloads.fetch(apk) ?: return InstallOutcome.NotDownloaded
        val source = store.install(file, id, list) ?: return InstallOutcome.NotTheSource
        return source.problem?.let { InstallOutcome.CantUse(it) } ?: InstallOutcome.Installed(source)
    }

    private suspend fun listen() {
        val code = makeCode()
        val opened =
            try {
                // Opening isn't interrupted, so a board opened just as it's cancelled is closed here.
                val caller = currentCoroutineContext()
                withContext(io + NonCancellable) { open(code).also { if (!caller.isActive) it.close() } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = if (e is NoNetworkException) FromPhoneState.NoNetwork else FromPhoneState.Stopped
                return
            }
        session = opened
        var waiting = FromPhoneState.Waiting(opened.invite.toQrText(), code, TV_CODE_SECONDS)
        mutableState.value = waiting
        val countdown =
            viewModelScope.launch {
                for (left in TV_CODE_SECONDS downTo 1) {
                    waiting = waiting.copy(secondsLeft = left)
                    mutableState.update { if (it is FromPhoneState.Waiting) waiting else it }
                    delay(SECOND_MILLIS)
                }
            }
        try {
            while (true) {
                val link = nextPhone(opened) { waiting } ?: return
                phone = link
                waiting = waiting.copy(note = null)
                val matched = CompletableDeferred<Unit>().also { tvMatch = it }
                mutableState.value = FromPhoneState.CheckCode(link.checkCode)
                val picked =
                    try {
                        link.receiveMatch()
                        // The phone said they match. The TV's own It matches gets as long as the phone had.
                        withTimeoutOrNull(MATCH_MILLIS) { matched.await() } ?: run {
                            link.stop(PairingStop.TimedOut)
                            throw PairingStoppedException(PairingStop.TimedOut)
                        }
                        link.sendTvSources(
                            store.installed.value
                                .orEmpty()
                                .toTvSources(api),
                        )
                        mutableState.value = FromPhoneState.Picking(link.checkCode)
                        link.receivePicked()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        currentCoroutineContext().ensureActive()
                        link.close()
                        if (phone === link) phone = null
                        if (countdown.isCompleted) {
                            mutableState.value = FromPhoneState.Expired(code)
                            return
                        }
                        waiting = waiting.copy(note = noteFor(e))
                        mutableState.value = waiting
                        continue
                    }
                // The board closes once picks arrive, so no other phone can connect while they wait to install.
                countdown.cancel()
                opened.close()
                try {
                    link.replyReceived()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The picks are here. The phone not hearing so only means it shows a lost connection.
                }
                link.close()
                if (phone === link) phone = null
                mutableState.value = FromPhoneState.Confirm(picked, statusesOf(picked))
                return
            }
        } finally {
            countdown.cancel()
            opened.close()
            if (session === opened) session = null
        }
    }

    /** Waits for a phone until the code runs out. Null, with the board set, when there won't be one. */
    private suspend fun nextPhone(
        opened: TvSourcesSession,
        waiting: () -> FromPhoneState.Waiting,
    ): SourcesPhone? {
        val link =
            try {
                withTimeoutOrNull(waiting().secondsLeft * SECOND_MILLIS) {
                    // Called on another thread, so each change only applies to the board it expects.
                    opened.accept { inProgress ->
                        mutableState.update {
                            when {
                                inProgress && it is FromPhoneState.Waiting -> FromPhoneState.Connecting
                                !inProgress && it == FromPhoneState.Connecting -> waiting()
                                else -> it
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.value = FromPhoneState.Stopped
                return null
            }
        if (link == null) mutableState.value = FromPhoneState.Expired(waiting().code)
        return link
    }

    private fun noteFor(e: Exception): TvPairingNote =
        when ((e as? PairingStoppedException)?.reason) {
            PairingStop.NoMatch -> TvPairingNote.StoppedOnPhone
            PairingStop.Cancelled -> TvPairingNote.CancelledOnPhone
            PairingStop.TimedOut -> TvPairingNote.TookTooLong
            else -> TvPairingNote.LostConnection
        }

    private fun stop() {
        job?.cancel()
        job = null
        session?.close()
        session = null
        phone?.close()
        phone = null
    }

    override fun onCleared() {
        stop()
    }

    private companion object {
        const val SECOND_MILLIS = 1_000L

        // As long as the phone has to choose It matches.
        const val MATCH_MILLIS = 90_000L
    }
}
