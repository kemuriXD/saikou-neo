package ani.saikou.neo.shared.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.neo.shared.account.AccountStore
import ani.saikou.neo.shared.account.AniListProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The AniList account this TV is signed in to, for Settings > Account. Null while signed out. */
public class TvAccountViewModel(
    private val store: AccountStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableProfile = MutableStateFlow<AniListProfile?>(null)
    public val profile: StateFlow<AniListProfile?> = mutableProfile.asStateFlow()

    init {
        refresh()
    }

    /** Reads the account again, such as after Sign in. */
    public fun refresh() {
        viewModelScope.launch { mutableProfile.value = withContext(io) { store.readAccount() }?.profile }
    }
}
