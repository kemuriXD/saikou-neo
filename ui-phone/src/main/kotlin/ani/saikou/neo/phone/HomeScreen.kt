package ani.saikou.neo.phone

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ani.saikou.neo.phone.theme.PhoneColors
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.shared.pairing.PairedTv
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 2.1 Home with no TVs yet: Pair your first TV, with Scan the TV code and Type the code instead, and an empty Your
 * TVs. Once a TV is paired, 2.2: Your TVs lists each one, and Pair a TV opens the scanner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    profile: AniListProfile,
    onAccount: () -> Unit,
    tvs: List<PairedTv> = emptyList(),
    onScan: () -> Unit = {},
    onType: () -> Unit = {},
    onRemoveTv: (PairedTv) -> Unit = {},
    onSendSource: (PairedTv) -> Unit = {},
) {
    // 2.3 the options sheet and 2.4 the confirm dialog, by TV ID; 2.5 the snackbar once one is removed.
    var optionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmFor by rememberSaveable { mutableStateOf<String?>(null) }
    var removed by remember { mutableStateOf<String?>(null) }
    val snackbars = remember { SnackbarHostState() }
    val removedMessage = removed?.let { stringResource(R.string.home_removed, it) }
    LaunchedEffect(removedMessage) {
        if (removedMessage != null) {
            snackbars.showSnackbar(removedMessage)
            removed = null
        }
    }

    tvs.find { it.id == optionsFor }?.let { tv ->
        TvOptions(
            tv,
            onDismiss = { optionsFor = null },
            onSend = {
                optionsFor = null
                onSendSource(tv)
            },
        ) {
            optionsFor = null
            confirmFor = tv.id
        }
    }
    tvs.find { it.id == confirmFor }?.let { tv ->
        RemoveTvDialog(tv, onDismiss = { confirmFor = null }) {
            confirmFor = null
            onRemoveTv(tv)
            removed = tv.name
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        floatingActionButton = {
            if (tvs.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onScan,
                    icon = { Icon(painterResource(R.drawable.icon_add), contentDescription = null, Modifier.size(24.dp)) },
                    text = { Text(stringResource(R.string.home_pair_tv), style = MaterialTheme.typography.titleMedium) },
                    containerColor = PhoneColors.pink,
                    contentColor = PhoneColors.ink,
                )
            }
        },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Image(
                        painterResource(R.drawable.saikou_neo_mark),
                        contentDescription = null,
                        Modifier.padding(start = 16.dp, end = 8.dp).testTag(PHONE_LOGO_TAG).size(24.dp),
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
                        Avatar(profile.name, profile.avatarUrl, size = 32.dp, style = MaterialTheme.typography.labelLarge)
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
                if (tvs.isEmpty()) PairFirstTv(onScan, onType)
                YourTvs(tvs) { optionsFor = it.id }
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
private fun YourTvs(
    tvs: List<PairedTv>,
    onOptions: (PairedTv) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.home_your_tvs),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        if (tvs.isNotEmpty()) {
            Column(Modifier.clip(RoundedCornerShape(16.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                tvs.forEach { tv -> TvRow(tv) { onOptions(tv) } }
            }
            return@Column
        }
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

/** A paired TV: its name, and that it's signed in with the day it was paired. The row and its menu open its options. */
@Composable
private fun TvRow(
    tv: PairedTv,
    onOptions: () -> Unit,
) {
    ListItem(
        supportingContent = { Text(stringResource(R.string.home_tv_paired, pairedDay(tv.pairedAtEpochSeconds))) },
        leadingContent = { Icon(painterResource(R.drawable.icon_tv), contentDescription = null, Modifier.size(24.dp)) },
        trailingContent = {
            IconButton(onClick = onOptions) {
                Icon(
                    painterResource(R.drawable.icon_more_vert),
                    contentDescription = stringResource(R.string.home_tv_options, tv.name),
                    Modifier.size(24.dp),
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = PhoneColors.surface),
        modifier = Modifier.clickable(onClick = onOptions),
    ) {
        Text(tv.name, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * 2.3 A TV's options: the TV, when it was paired, Send a source and Remove this TV. It doesn't name the account, since the phone may
 * have signed in as someone else since.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvOptions(
    tv: PairedTv,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
    onRemove: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PhoneColors.surfaceRaised) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.icon_tv), contentDescription = null, Modifier.size(24.dp), tint = PhoneColors.pink)
                Column {
                    Text(tv.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(
                        stringResource(R.string.home_tv_paired, pairedDay(tv.pairedAtEpochSeconds)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = PhoneColors.line)
            ListItem(
                supportingContent = { Text(stringResource(R.string.home_send_source_body)) },
                leadingContent = { Icon(painterResource(R.drawable.icon_send), contentDescription = null, Modifier.size(24.dp)) },
                colors = ListItemDefaults.colors(containerColor = PhoneColors.surfaceRaised),
                modifier = Modifier.clickable(onClick = onSend).padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.home_send_source), style = MaterialTheme.typography.bodyLarge)
            }
            ListItem(
                supportingContent = { Text(stringResource(R.string.home_remove_tv_body)) },
                leadingContent = { Icon(painterResource(R.drawable.icon_delete), contentDescription = null, Modifier.size(24.dp)) },
                colors = ListItemDefaults.colors(containerColor = PhoneColors.surfaceRaised),
                modifier = Modifier.clickable(onClick = onRemove).padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.home_remove_tv), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** 2.4 Remove a TV? It only leaves Your TVs; the TV signs out in its own Settings (ADR 0040). */
@Composable
private fun RemoveTvDialog(
    tv: PairedTv,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.icon_delete), contentDescription = null, Modifier.size(24.dp)) },
        title = { Text(stringResource(R.string.home_remove_title, tv.name), textAlign = TextAlign.Center) },
        text = { Text(stringResource(R.string.home_remove_body)) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_remove_cancel)) } },
        confirmButton = {
            TextButton(onClick = onRemove, colors = ButtonDefaults.textButtonColors(contentColor = PhoneColors.error)) {
                Text(stringResource(R.string.home_remove_confirm))
            }
        },
        containerColor = PhoneColors.surfaceRaised,
    )
}

/** The day and month, such as 31 Oct, in the order the phone's language uses. */
private fun pairedDay(epochSeconds: Long): String {
    val locale = Locale.getDefault()
    return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale).format(Date(epochSeconds * 1_000))
}
