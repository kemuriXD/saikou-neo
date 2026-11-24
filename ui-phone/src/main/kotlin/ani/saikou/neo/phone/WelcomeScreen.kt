package ani.saikou.neo.phone

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ani.saikou.neo.phone.theme.PhoneColors
import ani.saikou.neo.shared.account.AccountState

/** Tags the round mark on Welcome and Home, for tests. */
internal const val PHONE_LOGO_TAG = "phone_logo"

/**
 * 1.2 Welcome: what the phone is for, and Sign in with AniList. Back from the browser, 1.3a shows while AniList
 * checks, and 1.4 if the sign-in didn't work. Crash reports can be turned off here too, before signing in.
 */
@Composable
internal fun WelcomeScreen(
    onSignIn: () -> Unit,
    crashReports: Boolean = true,
    onCrashReports: (Boolean) -> Unit = {},
) {
    PhoneColumn(Modifier.safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 32.dp),
            ) {
                Image(painterResource(R.drawable.saikou_neo_mark), contentDescription = null, Modifier.testTag(PHONE_LOGO_TAG).size(40.dp))
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.welcome_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Promise(R.drawable.icon_person, R.string.welcome_sign_in_once, R.string.welcome_sign_in_once_body)
                    Promise(R.drawable.icon_qr_scan, R.string.welcome_pair, R.string.welcome_pair_body)
                    Promise(R.drawable.icon_tv, R.string.welcome_finish, R.string.welcome_finish_body)
                }
                Spacer(Modifier.height(24.dp))
                CrashReportsRow(crashReports, onCrashReports, Modifier.clip(RoundedCornerShape(16.dp)))
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(painterResource(R.drawable.icon_open_external), contentDescription = null, Modifier.size(24.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.welcome_sign_in), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    stringResource(R.string.welcome_browser_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** One thing the phone does, as a two-line list item with a pink icon. */
@Composable
private fun Promise(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    @StringRes body: Int,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(24.dp), tint = PhoneColors.pink)
        Spacer(Modifier.size(16.dp))
        Column {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
