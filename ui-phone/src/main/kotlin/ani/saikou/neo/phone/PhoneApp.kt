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
import ani.saikou.neo.shared.pairing.PairTvState
import ani.saikou.neo.shared.pairing.PairTvViewModel
import ani.saikou.neo.shared.pairing.PairingRequest

/** The screens behind Home, kept as a back stack. Pair is 3.4 to 3.8, which follow [PairTvViewModel]. */
internal enum class PhoneScreen { Home, Account, Scan, Type, Pair }

/** Welcome until the phone is signed in, then Home, with Account behind the avatar and pairing behind its buttons. */
@Composable
internal fun PhoneApp(
    accounts: PhoneAccountViewModel,
    pairing: PairTvViewModel,
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
                val signOut = {
                    // Next time the phone signs in, it starts on Home.
                    screens = listOf(PhoneScreen.Home)
                    accounts.signOut()
                }
                SignedIn(current, pairing, screens.last(), version, openInBrowser, open, back, signOut) { screens = it }
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
    pairing: PairTvViewModel,
    screen: PhoneScreen,
    version: String,
    openInBrowser: (url: String) -> Unit,
    open: (PhoneScreen) -> Unit,
    back: () -> Unit,
    signOut: () -> Unit,
    replace: (List<PhoneScreen>) -> Unit,
) {
    val codes: PairCodeViewModel = viewModel { PairCodeViewModel() }
    val tvs by pairing.tvs.collectAsStateWithLifecycle()
    // Scanning or typing gives way to pairing, so Back from any pairing screen goes Home.
    val pair = { request: PairingRequest ->
        pairing.start(request)
        replace(listOf(PhoneScreen.Home, PhoneScreen.Pair))
    }
    val leavePairing = {
        // While the account is going to the TV, stay and wait for its answer.
        if ((pairing.state.value as? PairTvState.CheckCode)?.sending != true) {
            pairing.cancel()
            replace(listOf(PhoneScreen.Home))
        }
    }

    when (screen) {
        PhoneScreen.Home -> {
            HomeScreen(
                account.profile,
                onAccount = { open(PhoneScreen.Account) },
                tvs = tvs,
                onScan = { open(PhoneScreen.Scan) },
                onType = { open(PhoneScreen.Type) },
                onRemoveTv = { pairing.removeTv(it.id) },
            )
        }

        PhoneScreen.Account -> {
            AccountScreen(account.profile, version, onBack = back, onOpen = openInBrowser, onSignOut = signOut)
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

        PhoneScreen.Pair -> {
            val state by pairing.state.collectAsStateWithLifecycle()
            // Nothing in progress, such as after the app was closed mid-pairing: there's nothing to show here.
            LaunchedEffect(state) { if (state == PairTvState.Idle) leavePairing() }
            BackHandler(onBack = leavePairing)
            PairTvScreen(
                state,
                PairTvActions(
                    close = leavePairing,
                    confirm = pairing::confirm,
                    reject = pairing::reject,
                    retry = pairing::retry,
                    scanNew = {
                        pairing.cancel()
                        replace(listOf(PhoneScreen.Home, PhoneScreen.Scan))
                    },
                    done = leavePairing,
                ),
            )
        }
    }
}

private val screensSaver =
    listSaver<List<PhoneScreen>, String>(
        save = { screens -> screens.map { it.name } },
        restore = { names -> names.map(PhoneScreen::valueOf) },
    )
