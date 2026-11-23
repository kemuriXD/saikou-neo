package ani.saikou.neo.phone

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ani.saikou.neo.phone.theme.PhoneColors
import ani.saikou.neo.shared.sources.AddError
import ani.saikou.neo.shared.sources.Choice
import ani.saikou.neo.shared.sources.ChoiceStatus
import ani.saikou.neo.shared.sources.SendSourcesState
import ani.saikou.neo.shared.sources.SourceProblem
import ani.saikou.neo.shared.sources.canPick
import ani.saikou.neo.shared.sources.size

/** What the Send a source screens can do. Each screen uses the ones its buttons need. */
internal class SendSourcesActions(
    val close: () -> Unit,
    val confirm: () -> Unit,
    val reject: () -> Unit,
    val linkChange: (String) -> Unit,
    val check: () -> Unit,
    val toggle: (String) -> Unit,
    val back: () -> Unit,
    val send: () -> Unit,
    val scanAgain: () -> Unit,
)

/** 4.2 to 4.10: connecting, the check code, the link, the picks, and how it ended. [tvName] is the TV's in Your TVs. */
@Composable
internal fun SendSourcesScreen(
    state: SendSourcesState,
    tvName: String,
    actions: SendSourcesActions,
) {
    when (state) {
        SendSourcesState.Idle, SendSourcesState.Connecting -> {
            PairFrame(actions.close, title = R.string.send_title) {
                Status(Modifier.weight(1f), R.string.send_connecting, R.string.send_connecting_body) { Spinner() }
                Actions { OutlinedButton(onClick = actions.close, modifier = BIG_BUTTON) { ButtonLabel(R.string.pair_cancel) } }
            }
        }

        is SendSourcesState.CheckCode -> {
            PairFrame(actions.close, title = R.string.send_title) {
                CheckCode(state.code, stringResource(R.string.send_check_body), state.waiting, actions.confirm, actions.reject)
            }
        }

        is SendSourcesState.Link -> {
            LinkScreen(state, tvName, actions)
        }

        is SendSourcesState.Pick -> {
            PickScreen(state, tvName, actions)
        }

        SendSourcesState.Sent -> {
            PairFrame(actions.close, title = R.string.send_title) {
                Status(Modifier.weight(1f), stringResource(R.string.send_sent, tvName), stringResource(R.string.send_sent_body)) {
                    Badge(R.drawable.icon_check_circle, PhoneColors.pinkLight, PhoneColors.fillTonal)
                }
                Actions { Button(onClick = actions.close, modifier = BIG_BUTTON) { ButtonLabel(R.string.send_done) } }
            }
        }

        SendSourcesState.Lost -> {
            PairFrame(actions.close, title = R.string.send_title) {
                Status(Modifier.weight(1f), stringResource(R.string.send_lost, tvName), stringResource(R.string.send_lost_body)) {
                    Badge(R.drawable.icon_wifi_off, PhoneColors.error, PhoneColors.surfaceRaised)
                }
                Actions {
                    Button(
                        onClick = actions.scanAgain,
                        modifier = BIG_BUTTON,
                    ) { ButtonLabel(R.string.send_scan_again, R.drawable.icon_qr_scan) }
                }
            }
        }
    }
}

/** 4.3 Paste a link or code, 4.4 while it's checked and 4.8 when it can't be read. */
@Composable
private fun LinkScreen(
    state: SendSourcesState.Link,
    tvName: String,
    actions: SendSourcesActions,
) {
    val error = state.error?.let { stringResource(errorText(it)) }
    HeadedFrame(R.string.send_title, tvName, R.drawable.icon_close, R.string.pair_close, actions.close) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
            Text(
                stringResource(R.string.send_link_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = state.text,
                onValueChange = actions.linkChange,
                modifier = Modifier.fillMaxWidth(),
                readOnly = state.checking,
                label = { Text(stringResource(R.string.send_link_field)) },
                leadingIcon = { Icon(painterResource(R.drawable.icon_link), contentDescription = null, Modifier.size(24.dp)) },
                supportingText = if (state.checking) null else ({ Text(error ?: stringResource(R.string.send_link_hint)) }),
                isError = error != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { actions.check() }),
            )
            if (state.checking) {
                Row(
                    Modifier.padding(start = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), trackColor = PhoneColors.line)
                    Text(stringResource(R.string.send_checking), style = MaterialTheme.typography.bodyMedium, color = PhoneColors.textMuted)
                }
            }
            Spacer(Modifier.height(if (state.checking) 24.dp else 40.dp))
            Note(R.drawable.icon_shield, PhoneColors.textMuted, R.string.send_trust)
        }
        Actions {
            Button(onClick = actions.check, enabled = !state.checking && state.text.isNotBlank(), modifier = BIG_BUTTON) {
                ButtonLabel(R.string.send_check_link)
            }
        }
    }
}

/** 4.5 to 4.6 a list's sources, 4.5b a link to one APK, and 4.9 when nothing in the list fits. */
@Composable
private fun PickScreen(
    state: SendSourcesState.Pick,
    tvName: String,
    actions: SendSourcesActions,
) {
    val from = state.from.removePrefix("https://")
    HeadedFrame(R.string.send_pick_title, tvName, R.drawable.icon_arrow_back, R.string.send_back, actions.back) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 16.dp)) {
            val intro = if (state.apk == null && !state.nothingFits) R.string.send_from_list else R.string.send_from
            Text(
                stringResource(intro, from),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
            if (state.nothingFits) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    Note(R.drawable.icon_warning, PhoneColors.textMuted, R.string.send_nothing_fits)
                }
            }
            val apk = state.apk
            if (apk != null) {
                SourceRow(apk.substringAfterLast('/').substringBefore('?'), stringResource(R.string.send_one_apk), state.apkTicked) {
                    actions.toggle(apk)
                }
            }
            state.choices.forEach { choice -> ChoiceRow(choice) { actions.toggle(choice.source.id) } }
        }
        Actions {
            val count = state.picked?.size ?: 0
            Button(onClick = actions.send, enabled = count > 0 && !state.sending, modifier = BIG_BUTTON) {
                Icon(painterResource(R.drawable.icon_send), contentDescription = null, Modifier.size(24.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    if (count == 0) stringResource(R.string.send_to_tv) else pluralStringResource(R.plurals.send_count, count, count),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** A source in the list: available and update rows have a checkbox; installed and unusable ones are dimmed. */
@Composable
private fun ChoiceRow(
    choice: Choice,
    onToggle: () -> Unit,
) {
    val source = choice.source
    val status = choice.status
    val text =
        when (status) {
            ChoiceStatus.New, ChoiceStatus.Failed -> stringResource(R.string.send_version, source.version)
            is ChoiceStatus.Update -> stringResource(R.string.send_update, status.from, source.version)
            ChoiceStatus.Installed -> stringResource(R.string.send_installed, source.version)
            ChoiceStatus.AddAgain -> stringResource(R.string.send_add_again)
            is ChoiceStatus.CantUse -> stringResource(problemText(status.problem))
        }
    if (status.canPick) {
        SourceRow(source.name, text, choice.ticked, onToggle)
        return
    }
    val cantUse = status is ChoiceStatus.CantUse
    ListItem(
        supportingContent = { Text(text, color = if (cantUse) PhoneColors.warning else Color.Unspecified) },
        trailingContent =
            if (cantUse) {
                {
                    Icon(
                        painterResource(R.drawable.icon_warning),
                        contentDescription = null,
                        Modifier.size(20.dp),
                        tint = PhoneColors.warning,
                    )
                }
            } else {
                null
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.alpha(DISABLED_ALPHA),
    ) {
        Text(source.name, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A row that can be ticked: the whole row toggles, and TalkBack reads it as one checkbox. */
@Composable
private fun SourceRow(
    name: String,
    text: String,
    ticked: Boolean,
    onToggle: () -> Unit,
) {
    ListItem(
        supportingContent = { Text(text) },
        trailingContent = { Checkbox(checked = ticked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = ticked, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A card with an icon and a line of help, as the trust note and Nothing fits are. */
@Composable
private fun Note(
    @DrawableRes icon: Int,
    tint: Color,
    @StringRes text: Int,
) {
    Row(
        Modifier.fillMaxWidth().background(PhoneColors.surface, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(20.dp), tint = tint)
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = PhoneColors.textMuted,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A plain top bar with [navigation], then [title] and To the TV, over [content], as the Medium app bar frames show. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeadedFrame(
    @StringRes title: Int,
    tvName: String,
    @DrawableRes navigation: Int,
    @StringRes navigationLabel: Int,
    onNavigation: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onNavigation,
                    ) { Icon(painterResource(navigation), contentDescription = stringResource(navigationLabel)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        PhoneColumn(Modifier.padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    Text(
                        stringResource(title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.send_to, tvName),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content()
            }
        }
    }
}

@StringRes
private fun errorText(error: AddError): Int =
    when (error) {
        AddError.NO_SUCH_CODE -> R.string.send_error_code

        AddError.NOT_SECURE -> R.string.send_error_not_secure

        AddError.BROKEN_LIST -> R.string.send_error_broken_list

        // A list where nothing fits shows its rows (4.9), so the last two don't come up here.
        AddError.NOT_A_SOURCE, AddError.NOTHING_IN_LIST, AddError.ALL_INSTALLED -> R.string.send_error_not_a_source
    }

@StringRes
private fun problemText(problem: SourceProblem): Int =
    when (problem) {
        SourceProblem.BUILT_FOR_NEWER_APP -> R.string.send_needs_newer_app
        SourceProblem.BUILT_FOR_OLDER_APP -> R.string.send_needs_newer_source
        SourceProblem.BROKEN -> R.string.send_does_not_work
    }

/** Material's disabled content opacity, as the kit's disabled list items use. */
private const val DISABLED_ALPHA = 0.38f
