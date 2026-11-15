package ani.saikou.neo.shared.pairing

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** How the phone found the TV: from its QR code, or by the code typed in, which still has to be looked up. */
public sealed interface PairingRequest {
    public data class Scanned(
        val invite: PairingInvite,
    ) : PairingRequest

    public data class Typed(
        val code: String,
    ) : PairingRequest
}

/** What's been typed on 3.3 Type the code. */
public data class PairCodeState(
    val code: String = "",
) {
    val complete: Boolean get() = code.length == TV_CODE_LENGTH
}

/** Screen state for scanning (3.1) and typing (3.3) the TV's code. Connecting is the next screen's job. */
public class PairCodeViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(PairCodeState())
    public val state: StateFlow<PairCodeState> = mutableState.asStateFlow()

    /** Takes what's in the text field, tidied up by [normalizeTvCode]. */
    public fun onCodeChange(text: String) {
        mutableState.update { it.copy(code = normalizeTvCode(text)) }
    }

    /** The typed code to look up, once all of it is in. */
    public fun connect(): PairingRequest.Typed? = state.value.takeIf { it.complete }?.let { PairingRequest.Typed(it.code) }

    /** The TV to connect to if [text], read from a QR code, is a TV's invite. Any other QR code gives null. */
    public fun scanned(text: String): PairingRequest.Scanned? = PairingInvite.parse(text)?.let(PairingRequest::Scanned)
}
