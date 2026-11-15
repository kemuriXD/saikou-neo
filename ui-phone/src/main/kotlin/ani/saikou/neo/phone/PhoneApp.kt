package ani.saikou.neo.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ani.saikou.neo.shared.account.AccountState
import ani.saikou.neo.shared.account.PhoneAccountViewModel

/** Welcome until the phone is signed in, then Home, with Account behind the avatar. */
@Composable
internal fun PhoneApp(
    accounts: PhoneAccountViewModel,
    version: String,
    openInBrowser: (url: String) -> Unit,
) {
    val state by accounts.state.collectAsStateWithLifecycle()
    LaunchedEffect(accounts) { accounts.browser.collect(openInBrowser) }
    var showAccount by rememberSaveable { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (val current = state) {
            AccountState.Loading -> {
                Box(Modifier.fillMaxSize())
            }

            is AccountState.SignedIn -> {
                if (showAccount) {
                    BackHandler { showAccount = false }
                    AccountScreen(current.profile, version, onBack = { showAccount = false }, onOpen = openInBrowser)
                } else {
                    HomeScreen(current.profile, onAccount = { showAccount = true })
                }
            }

            is AccountState.SignedOut, AccountState.Verifying -> {
                WelcomeScreen(current, onSignIn = accounts::signIn)
            }
        }
    }
}
