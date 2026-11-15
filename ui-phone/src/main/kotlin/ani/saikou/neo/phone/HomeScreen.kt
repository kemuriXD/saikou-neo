package ani.saikou.neo.phone

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ani.saikou.neo.phone.theme.PhoneColors
import ani.saikou.neo.shared.account.AniListProfile

/**
 * 2.1 Home with no TVs yet: Pair your first TV and an empty Your TVs. Scanning and typing the code come in #248, so
 * those buttons don't lead anywhere yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    profile: AniListProfile,
    onAccount: () -> Unit,
    onScan: () -> Unit = {},
    onType: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Icon(
                        painterResource(R.drawable.icon_logo),
                        contentDescription = null,
                        Modifier.padding(start = 16.dp, end = 8.dp).size(24.dp),
                        tint = PhoneColors.pink,
                    )
                },
                title = { Wordmark() },
                actions = {
                    val account = stringResource(R.string.home_account)
                    IconButton(
                        onClick = onAccount,
                        modifier =
                            Modifier.padding(end = 4.dp).clearAndSetSemantics {
                                contentDescription = account
                                role =
                                    Role.Button
                            },
                    ) {
                        Avatar(profile.name, size = 32.dp, style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        PhoneColumn(Modifier.padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                PairFirstTv(onScan, onType)
                YourTvs()
            }
        }
    }
}

/** "Saikou Neo", with Neo in pink, as on the splash. */
@Composable
private fun Wordmark() {
    val saikou = stringResource(R.string.home_title_saikou)
    val neo = stringResource(R.string.home_title_neo)
    Text(
        buildAnnotatedString {
            append(saikou)
            append(' ')
            withStyle(SpanStyle(color = PhoneColors.pink)) { append(neo) }
        },
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun PairFirstTv(
    onScan: () -> Unit,
    onType: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(PhoneColors.surface, RoundedCornerShape(16.dp))
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(R.drawable.icon_tv), contentDescription = null, Modifier.size(32.dp), tint = PhoneColors.pink)
        Text(
            stringResource(R.string.home_pair_first),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Step(1, R.string.home_step_one)
            Step(2, R.string.home_step_two)
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(painterResource(R.drawable.icon_qr_scan), contentDescription = null, Modifier.size(24.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.home_scan), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onType, modifier = Modifier.height(48.dp)) {
                Icon(painterResource(R.drawable.icon_keyboard), contentDescription = null, Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.home_type), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Step(
    number: Int,
    @StringRes text: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp).background(PhoneColors.fillTonal, CircleShape), contentAlignment = Alignment.Center) {
            Text(number.toString(), style = MaterialTheme.typography.labelMedium)
        }
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun YourTvs() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.home_your_tvs),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Text(
            stringResource(R.string.home_no_tvs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, PhoneColors.line, RoundedCornerShape(16.dp))
                    .padding(16.dp),
        )
    }
}
