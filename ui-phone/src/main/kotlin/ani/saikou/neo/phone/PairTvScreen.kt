package ani.saikou.neo.phone

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ani.saikou.neo.phone.theme.PhoneColors
import ani.saikou.neo.shared.pairing.PairTvState
import ani.saikou.neo.shared.pairing.PairingStop

/** What the pairing screens can do. Each screen uses the ones its buttons need. */
internal class PairTvActions(
    val close: () -> Unit,
    val confirm: () -> Unit,
    val reject: () -> Unit,
    val retry: () -> Unit,
    val scanNew: () -> Unit,
    val done: () -> Unit,
    val signInAgain: () -> Unit,
)

/** 3.4 to 3.8: connecting, checking the codes match, and how pairing ended. */
@Composable
internal fun PairTvScreen(
    state: PairTvState,
    actions: PairTvActions,
) {
    when (state) {
        PairTvState.Idle, PairTvState.Connecting -> {
            PairFrame(actions.close) {
                Status(Modifier.weight(1f), R.string.pair_connecting, R.string.pair_connecting_body) { Spinner() }
                Actions { OutlinedButton(onClick = actions.close, modifier = BIG_BUTTON) { ButtonLabel(R.string.pair_cancel) } }
            }
        }

        is PairTvState.CheckCode -> {
            PairFrame(actions.close, closeEnabled = !state.sending) { CheckCode(state, actions) }
        }

        is PairTvState.SignedIn -> {
            Scaffold { padding ->
                PhoneColumn(Modifier.padding(padding)) {
                    Column(Modifier.fillMaxSize()) {
                        Status(
                            Modifier.weight(1f),
                            title = stringResource(R.string.pair_signed_in, state.tvName),
                            body = stringResource(R.string.pair_signed_in_body),
                        ) { Badge(R.drawable.icon_check_circle, PhoneColors.pinkLight, PhoneColors.fillTonal) }
                        Actions { Button(onClick = actions.done, modifier = BIG_BUTTON) { ButtonLabel(R.string.pair_done) } }
                    }
                }
            }
        }

        PairTvState.Unreachable -> {
            PairFrame(actions.close) {
                Status(Modifier.weight(1f), R.string.pair_unreachable, R.string.pair_unreachable_body) {
                    Badge(R.drawable.icon_wifi_off, PhoneColors.error, PhoneColors.surfaceRaised)
                }
                Actions {
                    Button(onClick = actions.retry, modifier = BIG_BUTTON) { ButtonLabel(R.string.pair_try_again, R.drawable.icon_refresh) }
                    SmallButton(R.string.pair_scan_new, R.drawable.icon_qr_scan, actions.scanNew)
                }
            }
        }

        is PairTvState.Stopped -> {
            Stopped(state.reason, actions)
        }
    }
}

/** 3.8a to 3.8d: why pairing stopped. Only a refused account needs this phone to sign in again first. */
@Composable
private fun Stopped(
    reason: PairingStop,
    actions: PairTvActions,
) {
    val (title, body) =
        when (reason) {
            PairingStop.NoMatch -> R.string.pair_stopped_you to R.string.pair_stopped_you_body
            PairingStop.Cancelled -> R.string.pair_stopped_cancelled to R.string.pair_stopped_cancelled_body
            PairingStop.Refused -> R.string.pair_stopped_refused to R.string.pair_stopped_refused_body
            PairingStop.TimedOut -> R.string.pair_stopped_timed_out to R.string.pair_stopped_timed_out_body
        }
    PairFrame(actions.close) {
        Status(Modifier.weight(1f), title, body) { Badge(R.drawable.icon_warning, PhoneColors.error, PhoneColors.surfaceRaised) }
        Actions {
            if (reason == PairingStop.Refused) {
                Button(onClick = actions.signInAgain, modifier = BIG_BUTTON) {
                    ButtonLabel(R.string.pair_sign_in_again, R.drawable.icon_person)
                }
            } else {
                Button(onClick = actions.scanNew, modifier = BIG_BUTTON) { ButtonLabel(R.string.pair_start_again, R.drawable.icon_qr_scan) }
            }
            SmallButton(R.string.pair_back_home, null, actions.close)
        }
    }
}

/** 3.5: the check code in two groups of three, read out as one label, with It matches and It doesn't match. */
@Composable
private fun ColumnScope.CheckCode(
    state: PairTvState.CheckCode,
    actions: PairTvActions,
) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Icon(painterResource(R.drawable.icon_tv), contentDescription = null, Modifier.size(32.dp), tint = PhoneColors.pink)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.pair_check_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(24.dp))
        val label = stringResource(R.string.pair_check_code, state.code.toList().joinToString(" "))
        Box(
            Modifier
                .fillMaxWidth()
                .background(PhoneColors.surface, RoundedCornerShape(20.dp))
                .border(1.dp, PhoneColors.line, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 28.dp)
                .clearAndSetSemantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                state.code.chunked(3).joinToString(" "),
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
                fontSize = 56.sp,
                lineHeight = 64.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.pair_check_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Actions {
        Button(onClick = actions.confirm, enabled = !state.sending, modifier = BIG_BUTTON) {
            ButtonLabel(R.string.pair_matches, R.drawable.icon_check)
        }
        SmallButton(R.string.pair_no_match, null, actions.reject, enabled = !state.sending)
    }
}

/** The Pair a TV top bar with Close, over [content]. Close waits while the account is going to the TV. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairFrame(
    onClose: () -> Unit,
    closeEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pair_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = closeEnabled) {
                        Icon(painterResource(R.drawable.icon_close), contentDescription = stringResource(R.string.pair_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        PhoneColumn(Modifier.padding(padding)) { Column(Modifier.fillMaxSize(), content = content) }
    }
}

@Composable
private fun SmallButton(
    @StringRes text: Int,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.height(48.dp)) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(stringResource(text), style = MaterialTheme.typography.labelLarge)
    }
}
