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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ani.saikou.neo.shared.account.AccountState
import ani.saikou.neo.shared.account.PhoneAccountViewModel
import ani.saikou.neo.shared.pairing.PairCodeViewModel
import ani.saikou.neo.shared.pairing.PairingRequest

/** The screens behind Home, kept as a back stack. */
internal enum class PhoneScreen { Home, Account, Scan, Type }

/** Welcome until the phone is signed in, then Home, with Account behind the avatar and pairing behind its buttons. */
@Composable
internal fun PhoneApp(
    accounts: PhoneAccountViewModel,
    version: String,
    openInBrowser: (url: String) -> Unit,
) {
    val state by accounts.state.collectAsStateWithLifecycle()
    LaunchedEffect(accounts) { accounts.browser.collect(openInBrowser) }
    var screens by rememberSaveable(stateSaver = screensSaver) { mutableStateOf(listOf(PhoneScreen.Home)) }
    val open = { screen: PhoneScreen -> screens = screens + screen }
    val back = { screens = screens.dropLast(1) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (val current = state) {
            AccountState.Loading -> {
                Box(Modifier.fillMaxSize())
            }

            is AccountState.SignedIn -> {
                BackHandler(enabled = screens.size > 1, onBack = back)
                SignedIn(current, screens.last(), version, openInBrowser, open, back) { screens = listOf(PhoneScreen.Home) }
            }

            is AccountState.SignedOut, AccountState.Verifying -> {
                WelcomeScreen(current, onSignIn = accounts::signIn)
            }
        }
    }
}

@Composable
private fun SignedIn(
    account: AccountState.SignedIn,
    screen: PhoneScreen,
    version: String,
    openInBrowser: (url: String) -> Unit,
    open: (PhoneScreen) -> Unit,
    back: () -> Unit,
    home: () -> Unit,
) {
    val codes: PairCodeViewModel = viewModel { PairCodeViewModel() }
    // #249 connects to the TV from here; until then a found TV leads back to Home.
    val pair = { _: PairingRequest -> home() }

    when (screen) {
        PhoneScreen.Home -> {
            HomeScreen(
                account.profile,
                onAccount = { open(PhoneScreen.Account) },
                onScan = { open(PhoneScreen.Scan) },
                onType = { open(PhoneScreen.Type) },
            )
        }

        PhoneScreen.Account -> {
            AccountScreen(account.profile, version, onBack = back, onOpen = openInBrowser)
        }

        PhoneScreen.Scan -> {
            PairScanRoute(
                onClose = back,
                onType = { open(PhoneScreen.Type) },
                onQrCode = { text -> codes.scanned(text)?.also(pair) != null },
            )
        }

        PhoneScreen.Type -> {
            val code by codes.state.collectAsStateWithLifecycle()
            PairCodeScreen(code, onCodeChange = codes::onCodeChange, onConnect = { codes.connect()?.let(pair) }, onBack = back)
        }
    }
}

private val screensSaver =
    listSaver<List<PhoneScreen>, String>(
        save = { screens -> screens.map { it.name } },
        restore = { names -> names.map(PhoneScreen::valueOf) },
    )
