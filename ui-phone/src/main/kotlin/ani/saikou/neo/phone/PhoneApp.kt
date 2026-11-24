package ani.saikou.neo.phone

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import ani.saikou.neo.shared.settings.Setting
import ani.saikou.neo.shared.settings.SettingsViewModel
import ani.saikou.neo.shared.sources.SendSourcesState
import ani.saikou.neo.shared.sources.SendSourcesViewModel

/**
 * The screens behind Home, kept as a back stack. Pair is 3.4 to 3.8, which follow [PairTvViewModel]. SendScan and
 * SendType find the TV for Send a source, and Send is 4.2 to 4.10, which follow [SendSourcesViewModel].
 */
internal enum class PhoneScreen { Home, Account, Scan, Type, Pair, SendScan, SendType, Send }

/**
 * Welcome until the phone is signed in, then 1.3 Signed in once and Home, with Account behind the avatar and pairing
 * behind its buttons. A sign-in that didn't work shows 1.4 in place of Welcome.
 */
@Composable
internal fun PhoneApp(
    accounts: PhoneAccountViewModel,
    pairing: PairTvViewModel,
    sources: SendSourcesViewModel,
    settings: SettingsViewModel,
    version: String,
    openInBrowser: (url: String) -> Unit,
) {
    val state by accounts.state.collectAsStateWithLifecycle()
    LaunchedEffect(accounts) { accounts.browser.collect(openInBrowser) }
    var screens by rememberSaveable(stateSaver = screensSaver) { mutableStateOf(listOf(PhoneScreen.Home)) }
    val open = { screen: PhoneScreen -> screens = screens + screen }
    val back = { screens = screens.dropLast(1) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        // A fade between Welcome, checking, the result and Home, in place of a hard cut.
        AnimatedContent(
            state,
            transitionSpec = { accountFade() },
            contentKey = ::screenOf,
            label = "account",
        ) { current ->
            when (current) {
                AccountState.Loading -> {
                    Box(Modifier.fillMaxSize())
                }

                is AccountState.SignedIn if current.justSignedIn -> {
                    // Back from 1.3 goes Home; Pair a TV goes to scanning, with Home behind it.
                    BackHandler(onBack = accounts::confirmSignIn)
                    SignedInScreen(current.profile) {
                        screens = listOf(PhoneScreen.Home, PhoneScreen.Scan)
                        accounts.confirmSignIn()
                    }
                }

                is AccountState.SignedIn -> {
                    BackHandler(enabled = screens.size > 1, onBack = back)
                    val signOut = {
                        // Next time the phone signs in, it starts on Home.
                        screens = listOf(PhoneScreen.Home)
                        accounts.signOut()
                    }
                    SignedIn(current, pairing, sources, settings, screens.last(), version, openInBrowser, open, back, signOut) {
                        screens = it
                    }
                }

                is AccountState.SignedOut -> {
                    val problem = current.problem
                    if (problem == null) {
                        val saved by settings.state.collectAsStateWithLifecycle()
                        WelcomeScreen(
                            onSignIn = accounts::signIn,
                            crashReports = saved?.crashReports ?: true,
                            onCrashReports = { settings.choose(Setting.CRASH_REPORTS, it) },
                        )
                    } else {
                        // Back from 1.4 goes to plain Welcome.
                        BackHandler(onBack = accounts::dismissProblem)
                        SignInProblemScreen(problem, onTryAgain = accounts::signIn)
                    }
                }

                AccountState.Verifying -> {
                    CheckingScreen()
                }
            }
        }
    }
}

/**
 * Checking appears at once, since it's what the app comes back from the browser on; a fade from Welcome would show
 * Welcome during the return. Everything after it fades.
 */
private fun AnimatedContentTransitionScope<AccountState>.accountFade(): ContentTransform =
    if (targetState == AccountState.Verifying) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        fadeIn() togetherWith fadeOut()
    }

/** Which account screen a state shows. The fade runs only when this changes, not when the profile updates. */
private fun screenOf(state: AccountState): Any =
    when (state) {
        is AccountState.SignedIn -> state.justSignedIn
        is AccountState.SignedOut -> state.problem ?: "welcome"
        else -> state
    }

@Composable
private fun SignedIn(
    account: AccountState.SignedIn,
    pairing: PairTvViewModel,
    sources: SendSourcesViewModel,
    settings: SettingsViewModel,
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
    val openType = {
        codes.clearCode()
        open(PhoneScreen.Type)
    }
    // The TV whose options opened Send a source. Its name heads the screens that follow.
    var sendTo by rememberSaveable { mutableStateOf("") }
    val send = { request: PairingRequest ->
        sources.start(request)
        replace(listOf(PhoneScreen.Home, PhoneScreen.Send))
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
                onType = { openType() },
                onRemoveTv = { pairing.removeTv(it.id) },
                onSendSource = {
                    sendTo = it.name
                    open(PhoneScreen.SendScan)
                },
            )
        }

        PhoneScreen.Account -> {
            val saved by settings.state.collectAsStateWithLifecycle()
            AccountScreen(
                account.profile,
                version,
                onBack = back,
                onOpen = openInBrowser,
                onSignOut = signOut,
                crashReports = saved?.crashReports ?: true,
                onCrashReports = { settings.choose(Setting.CRASH_REPORTS, it) },
            )
        }

        PhoneScreen.Scan -> {
            PairScanRoute(
                onClose = back,
                onType = { openType() },
                onQrCode = { text -> codes.scanned(text)?.also(pair) != null },
            )
        }

        PhoneScreen.Type -> {
            val code by codes.state.collectAsStateWithLifecycle()
            PairCodeScreen(code, onCodeChange = codes::onCodeChange, onConnect = { codes.connect()?.let(pair) }, onBack = back)
        }

        PhoneScreen.SendScan -> {
            PairScanRoute(
                onClose = back,
                onType = {
                    codes.clearCode()
                    open(PhoneScreen.SendType)
                },
                onQrCode = { text -> codes.scanned(text)?.also(send) != null },
                title = R.string.send_title,
                body = R.string.send_scan_body,
            )
        }

        PhoneScreen.SendType -> {
            val code by codes.state.collectAsStateWithLifecycle()
            PairCodeScreen(code, onCodeChange = codes::onCodeChange, onConnect = { codes.connect()?.let(send) }, onBack = back)
        }

        PhoneScreen.Send -> {
            SendRoute(sources, sendTo) { next -> replace(listOfNotNull(PhoneScreen.Home, next)) }
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
                    // AniList refused this phone's account on the TV: sign out, so Welcome offers a fresh sign-in.
                    signInAgain = {
                        pairing.cancel()
                        signOut()
                    },
                ),
            )
        }
    }
}

/** 4.2 to 4.10. Back on the picks goes back to the link; anywhere else it leaves, as Close does, for [leave]. */
@Composable
private fun SendRoute(
    sources: SendSourcesViewModel,
    tvName: String,
    leave: (then: PhoneScreen?) -> Unit,
) {
    val state by sources.state.collectAsStateWithLifecycle()
    val close = {
        // While the picks are going to the TV, stay and wait for its answer.
        if ((sources.state.value as? SendSourcesState.Pick)?.sending != true) {
            sources.cancel()
            leave(null)
        }
    }
    // Nothing in progress, such as after the app was closed mid-way: there's nothing to show here.
    LaunchedEffect(state) { if (state == SendSourcesState.Idle) leave(null) }
    BackHandler { if (state is SendSourcesState.Pick) sources.backToLink() else close() }
    SendSourcesScreen(
        state,
        tvName,
        SendSourcesActions(
            close = close,
            confirm = sources::confirm,
            reject = sources::reject,
            linkChange = sources::onLinkChange,
            check = sources::check,
            toggle = sources::toggle,
            back = sources::backToLink,
            send = sources::send,
            scanAgain = {
                sources.cancel()
                leave(PhoneScreen.SendScan)
            },
            retry = sources::retry,
        ),
    )
}

private val screensSaver =
    listSaver<List<PhoneScreen>, String>(
        save = { screens -> screens.map { it.name } },
        restore = { names -> names.map(PhoneScreen::valueOf) },
    )
