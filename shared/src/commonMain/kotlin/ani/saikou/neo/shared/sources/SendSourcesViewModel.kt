package ani.saikou.neo.shared.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.pairing.PairingRequest
import ani.saikou.neo.shared.pairing.PairingStop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where sending sources to a TV has got to on the phone, screen by screen (4.2 to 4.10). */
public sealed interface SendSourcesState {
    /** Not sending. */
    public data object Idle : SendSourcesState

    /** 4.2 Connecting, until the handshake ends. */
    public data object Connecting : SendSourcesState

    /** 4.2b Check the codes match. [waiting] once It matches is tapped, until the TV has said It matches too. */
    public data class CheckCode(
        val code: String,
        val waiting: Boolean = false,
    ) : SendSourcesState

    /** 4.3 Paste a link or code, 4.4 while [checking], 4.8 with the [error] that stopped it. */
    public data class Link(
        val text: String = "",
        val checking: Boolean = false,
        val error: AddError? = null,
    ) : SendSourcesState

    /**
     * 4.5 to 4.6 and 4.9: the [choices] in the list at [from]. 4.5b: a link to one [apk], shown as one row. Nothing
     * starts ticked. [sending] once Send is tapped, until the TV says it has them.
     */
    public data class Pick(
        val from: String,
        val choices: List<Choice> = emptyList(),
        val apk: String? = null,
        val apkTicked: Boolean = false,
        val sending: Boolean = false,
    ) : SendSourcesState {
        /** Nothing in the list can be picked: each source is installed or can't be used. */
        val nothingFits: Boolean get() = apk == null && choices.whyNone() != null

        /** What Send sends, or null while nothing is ticked. */
        val picked: PickedSources?
            get() = if (apk != null) PickedSources.OneApk(apk).takeIf { apkTicked } else LinkForTv.Listed(from, choices).picked()
    }

    /** 4.7 The TV has the picks. */
    public data object Sent : SendSourcesState

    /** 4.10 The TV couldn't be reached, dropped the connection or stopped. */
    public data object Lost : SendSourcesState
}

/**
 * Sends sources to a TV's Add from my phone board (#406). [start] connects; both screens then show the check code, and
 * nothing more goes either way until the user confirms it here and on the TV (ADR 0058). The TV then says what it has,
 * a link or code is read with the TV's rules by [read] (on Android, [readLinkForTv]), and only the ticked entries go.
 */
public class SendSourcesViewModel(
    private val connect: suspend (PairingRequest) -> SourcesTv,
    private val read: suspend (text: String, tv: TvSources) -> LinkForTv,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow<SendSourcesState>(SendSourcesState.Idle)
    public val state: StateFlow<SendSourcesState> = mutableState.asStateFlow()

    private var tv: SourcesTv? = null
    private var tvSources: TvSources? = null
    private var linkText = ""
    private var job: Job? = null

    /** Connects to the TV in [request], replacing anything in progress. */
    public fun start(request: PairingRequest) {
        stop(SendSourcesState.Connecting, PairingStop.Cancelled)
        linkText = ""
        job =
            viewModelScope.launch {
                val connected = attempt { connect(request) } ?: return@launch
                tv = connected
                mutableState.value = SendSourcesState.CheckCode(connected.checkCode)
            }
    }

    /** It matches: tells the TV, then waits for it to say what it has once It matches is chosen there too. */
    public fun confirm() {
        val shown = state.value as? SendSourcesState.CheckCode ?: return
        val tv = tv ?: return
        if (shown.waiting) return
        mutableState.value = shown.copy(waiting = true)
        job =
            viewModelScope.launch {
                tvSources =
                    attempt {
                        tv.confirmCodes()
                        tv.receiveTvSources()
                    } ?: return@launch
                mutableState.value = SendSourcesState.Link(linkText)
            }
    }

    /** It doesn't match: tells the TV and sends nothing. */
    public fun reject() {
        val shown = state.value as? SendSourcesState.CheckCode ?: return
        if (!shown.waiting) stop(SendSourcesState.Idle, PairingStop.NoMatch)
    }

    /** The link or code as typed. A message about the last one goes. */
    public fun onLinkChange(text: String) {
        val shown = state.value as? SendSourcesState.Link ?: return
        if (shown.checking) return
        linkText = text
        mutableState.value = SendSourcesState.Link(text)
    }

    /** Check link: reads the link or code with the TV's rules. */
    public fun check() {
        val shown = state.value as? SendSourcesState.Link ?: return
        val tvSources = tvSources ?: return
        if (shown.checking || shown.text.isBlank()) return
        mutableState.value = shown.copy(checking = true, error = null)
        job =
            viewModelScope.launch {
                val link =
                    try {
                        read(shown.text.trim(), tvSources)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        LinkForTv.Failed(AddError.NOT_A_SOURCE)
                    }
                mutableState.value =
                    when (link) {
                        is LinkForTv.Failed -> SendSourcesState.Link(shown.text, error = link.error)
                        is LinkForTv.Listed -> SendSourcesState.Pick(link.list, link.choices)
                        is LinkForTv.OneApk -> SendSourcesState.Pick(link.apk, apk = link.apk)
                    }
            }
    }

    /** Ticks or unticks the source with [id], or the APK row when [id] is its link. Not while sending. */
    public fun toggle(id: String) {
        val shown = state.value as? SendSourcesState.Pick ?: return
        if (shown.sending) return
        mutableState.value =
            if (shown.apk != null) {
                shown.copy(apkTicked = shown.apkTicked != (id == shown.apk))
            } else {
                shown.copy(
                    choices =
                        shown.choices.map {
                            if (it.source.id == id &&
                                it.status.canPick
                            ) {
                                it.copy(ticked = !it.ticked)
                            } else {
                                it
                            }
                        },
                )
            }
    }

    /** Back from the picks to the link, as it was typed. */
    public fun backToLink() {
        val shown = state.value as? SendSourcesState.Pick ?: return
        if (!shown.sending) mutableState.value = SendSourcesState.Link(linkText)
    }

    /** Send: only the ticked entries go. Once the TV says it has them, the connection closes. */
    public fun send() {
        val shown = state.value as? SendSourcesState.Pick ?: return
        val picked = shown.picked ?: return
        val tv = tv ?: return
        if (shown.sending) return
        mutableState.value = shown.copy(sending = true)
        job =
            viewModelScope.launch {
                attempt { tv.send(picked) } ?: return@launch
                this@SendSourcesViewModel.tv = null
                mutableState.value = SendSourcesState.Sent
            }
    }

    /**
     * Cancel, Close or Back: stops whatever is happening and tells a connected TV it was cancelled. Once the picks are
     * on their way it waits for the TV's answer, so the phone never says nothing went when it did.
     */
    public fun cancel() {
        if ((state.value as? SendSourcesState.Pick)?.sending == true) return
        stop(SendSourcesState.Idle, PairingStop.Cancelled)
    }

    /** Runs [step] with the TV. If it fails, the TV is dropped and counts as lost. */
    private suspend fun <T> attempt(step: suspend () -> T): T? =
        try {
            step()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            tv?.close()
            tv = null
            mutableState.value = SendSourcesState.Lost
            null
        }

    /** Stops listening and drops the connection, telling the TV [reason] first. */
    private fun stop(
        next: SendSourcesState,
        reason: PairingStop,
    ) {
        job?.cancel()
        job = null
        val leaving = tv
        tv = null
        tvSources = null
        // Not cancellable, so the TV hears it even if this screen goes away straight after.
        if (leaving != null) viewModelScope.launch(io + NonCancellable) { leaving.stop(reason) }
        mutableState.value = next
    }

    override fun onCleared() {
        job?.cancel()
        tv?.close()
        tv = null
    }
}
