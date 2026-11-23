package ani.saikou.neo.tv.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import ani.saikou.neo.shared.sources.ChoiceStatus
import ani.saikou.neo.shared.sources.FromPhoneState
import ani.saikou.neo.shared.sources.InstallOutcome
import ani.saikou.neo.shared.sources.PickedSources
import ani.saikou.neo.shared.sources.size
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.signin.CheckCode
import ani.saikou.neo.tv.signin.CodeCharacter
import ani.saikou.neo.tv.signin.CodeOverline
import ani.saikou.neo.tv.signin.Eyebrow
import ani.saikou.neo.tv.signin.Note
import ani.saikou.neo.tv.signin.Problem
import ani.saikou.neo.tv.signin.Qr
import ani.saikou.neo.tv.signin.SignInBoard
import ani.saikou.neo.tv.signin.Spinner
import ani.saikou.neo.tv.signin.Status
import ani.saikou.neo.tv.signin.StepNumber
import ani.saikou.neo.tv.signin.Title
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoButton
import ani.saikou.neo.tv.ui.NeoButtonRow
import ani.saikou.neo.tv.ui.NeoDialogButton
import ani.saikou.neo.tv.ui.NeoDialogButtons
import ani.saikou.neo.tv.ui.NeoIcons
import java.net.URI
import ani.saikou.neo.tv.signin.Body as BoardBody
import ani.saikou.neo.tv.signin.Small as BoardSmall

internal const val FROM_PHONE_TAG = "from-phone"
internal const val FROM_PHONE_PANEL_TAG = "from-phone-panel"

/**
 * Add from my phone (ADR 0042, ADR 0058): its [state], or null while it's closed, and what its buttons do.
 * [onNewCode] gets a new code, [onMatch] is It matches on the TV for the code shown, and [onNoMatch] tells the phone the codes didn't
 * match. [onInstall] installs what arrived, and [onClose] leaves, dropping anything that hasn't been installed. Sources
 * opens it with [SourceActions.onFromPhone].
 */
class FromPhone(
    val state: FromPhoneState? = null,
    val onNewCode: () -> Unit = {},
    val onMatch: (shown: String) -> Unit = {},
    val onNoMatch: () -> Unit = {},
    val onInstall: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * Lets OK through only when its press began on this screen. The TV's buttons act on OK's release, so without this, OK
 * held on Cancel as the phone moves things on would be let go on Install or It matches, which take focus.
 */
private class OwnPresses {
    private var down = false

    fun filter(event: KeyEvent): Boolean {
        if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) return false
        if (event.type == KeyEventType.KeyDown) {
            down = true
            return false
        }
        // A release whose press began elsewhere is kept from the button.
        val ours = down
        down = false
        return !ours
    }
}

/** Whether [this] shows on the board, rather than in the panel over Sources. */
internal val FromPhoneState.onBoard: Boolean
    get() = this !is FromPhoneState.Confirm && this !is FromPhoneState.Installing

/**
 * The board a phone connects to, over the whole screen like Sign in: what to do on the left, and the code, the check
 * code or what went wrong on the right. Focus stays on it, and Back leaves.
 */
@Composable
internal fun FromPhoneBoard(fromPhone: FromPhone) {
    val state = fromPhone.state ?: return
    BackHandler(onBack = fromPhone.onClose)
    val presses = remember(state::class, (state as? FromPhoneState.CheckCode)?.confirmed) { OwnPresses() }
    Box(
        Modifier
            .testTag(FROM_PHONE_TAG)
            .fillMaxSize()
            .onPreviewKeyEvent(presses::filter)
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
    ) {
        val first = remember { FocusRequester() }
        SignInBoard(
            message = { BoardMessage(state, first, fromPhone) },
            panel = if (state == FromPhoneState.Starting) null else ({ BoardPanel(state) }),
            panelHeight = 336.dp.takeIf { state !is FromPhoneState.Waiting },
        )
    }
}

@Composable
private fun ColumnScope.BoardMessage(
    state: FromPhoneState,
    first: FocusRequester,
    fromPhone: FromPhone,
) {
    val colors = NeoTheme.colors
    Text(stringResource(R.string.from_phone_eyebrow).uppercase(), style = Eyebrow, color = colors.pink)
    val (title, body) =
        when (state) {
            is FromPhoneState.CheckCode -> R.string.sign_in_check_title to R.string.from_phone_check_body
            FromPhoneState.Connecting -> R.string.sign_in_connecting_title to R.string.sign_in_connecting_body
            is FromPhoneState.Picking -> R.string.from_phone_picking_title to R.string.from_phone_picking_body
            is FromPhoneState.Expired -> R.string.sign_in_expired_title to R.string.sign_in_expired_body
            FromPhoneState.Stopped -> R.string.from_phone_stopped_title to R.string.from_phone_stopped_body
            FromPhoneState.NoNetwork -> R.string.from_phone_no_network_title to R.string.sign_in_no_network_body
            else -> R.string.from_phone_title to null
        }
    Text(stringResource(title), style = Title, color = colors.paper)
    body?.let { Text(stringResource(it), style = BoardBody, color = colors.textMuted) }
    if (body == null) Steps()
    // As on Sign in: each board's buttons are new, so focus is let go and taken by the first a frame later.
    val focusManager = LocalFocusManager.current
    key(state::class, (state as? FromPhoneState.CheckCode)?.confirmed) {
        NeoButtonRow(Modifier.padding(top = 14.dp)) {
            val focused = Modifier.focusRequester(first)
            val cancel = stringResource(R.string.sign_in_cancel)
            when (state) {
                is FromPhoneState.CheckCode -> {
                    if (state.confirmed) {
                        NeoButton(cancel, onClick = fromPhone.onClose, modifier = focused)
                    } else {
                        NeoButton(
                            stringResource(R.string.from_phone_it_matches),
                            onClick = { fromPhone.onMatch(state.checkCode) },
                            modifier = focused,
                        )
                        // Cancel here means the codes don't match, so the phone hears that someone else may be listening.
                        NeoButton(
                            cancel,
                            onClick = {
                                fromPhone.onNoMatch()
                                fromPhone.onClose()
                            },
                        )
                    }
                }

                FromPhoneState.Connecting, is FromPhoneState.Picking -> {
                    NeoButton(cancel, onClick = fromPhone.onClose, modifier = focused)
                }

                FromPhoneState.NoNetwork -> {
                    NeoButton(stringResource(R.string.sign_in_try_again), onClick = fromPhone.onNewCode, modifier = focused)
                    NeoButton(stringResource(R.string.from_phone_back), onClick = fromPhone.onClose)
                }

                else -> {
                    NeoButton(stringResource(R.string.sign_in_new_code), onClick = fromPhone.onNewCode, modifier = focused)
                    NeoButton(stringResource(R.string.from_phone_back), onClick = fromPhone.onClose)
                }
            }
        }
        LaunchedEffect(Unit) {
            focusManager.clearFocus()
            withFrameNanos {}
            first.requestFocus()
        }
    }
}

/** The three steps, with the phone's button named in bold. */
@Composable
private fun Steps() {
    val colors = NeoTheme.colors
    val button = stringResource(R.string.from_phone_send_a_source)
    val step2 = stringResource(R.string.sign_in_step_2, button)
    val steps =
        listOf(
            AnnotatedString(stringResource(R.string.sign_in_step_1)),
            buildAnnotatedString {
                append(step2)
                val at = step2.indexOf(button)
                if (at >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), at, at + button.length)
            },
            AnnotatedString(stringResource(R.string.from_phone_step_3)),
        )
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(28.dp).background(colors.fillTonal, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = StepNumber, color = colors.paper)
                }
                Text(step, style = BoardBody, color = colors.paper)
            }
        }
    }
}

@Composable
private fun BoardPanel(state: FromPhoneState) {
    val colors = NeoTheme.colors
    val typography = NeoTheme.typography
    when (state) {
        is FromPhoneState.Waiting -> {
            Qr(state.qrText)
            val spoken = stringResource(R.string.sign_in_code_label, state.code.toList().joinToString(" "))
            Row(Modifier.clearAndSetSemantics { contentDescription = spoken }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.code.forEach { CodeCharacter(it) }
            }
            val time = "%d:%02d".format(state.secondsLeft / 60, state.secondsLeft % 60)
            Status(stringResource(R.string.sign_in_waiting, time), 12.dp)
            state.note?.let { Note(it) }
        }

        FromPhoneState.Connecting, is FromPhoneState.Picking -> {
            Spinner(56.dp, colors.pink)
            val picking = state is FromPhoneState.Picking
            Text(
                stringResource(if (picking) R.string.from_phone_waiting_sources else R.string.sign_in_connecting_status),
                style = typography.titleLarge,
                color = colors.paper,
            )
            Text(
                stringResource(if (picking) R.string.from_phone_keep_open else R.string.sign_in_connecting_detail),
                style = BoardSmall,
                color = colors.textMuted,
            )
        }

        is FromPhoneState.CheckCode -> {
            val code = state.checkCode
            val spoken = stringResource(R.string.sign_in_check_label, code.toList().joinToString(" "))
            Column(Modifier.clearAndSetSemantics { contentDescription = spoken }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.sign_in_check_overline), style = CodeOverline, color = colors.textMuted)
                Text("${code.take(3)} ${code.drop(3)}", style = CheckCode, color = colors.paper)
            }
            Status(
                stringResource(if (state.confirmed) R.string.from_phone_waiting_confirm else R.string.from_phone_check_phone),
                14.dp,
            )
        }

        is FromPhoneState.Expired -> {
            Problem(stringResource(R.string.sign_in_expired_status), stringResource(R.string.sign_in_expired_detail, state.code))
        }

        FromPhoneState.Stopped -> {
            Problem(stringResource(R.string.from_phone_stopped_status), stringResource(R.string.from_phone_nothing_installed))
        }

        FromPhoneState.NoNetwork -> {
            Problem(
                stringResource(R.string.sign_in_no_network_status),
                stringResource(R.string.sign_in_no_network_detail),
                NeoIcons.WifiOff,
            )
        }

        else -> {}
    }
}

/**
 * What the phone sent, over Sources: each source as the TV sees it, new, an update, already installed or not for this
 * app, and Install for the ones that can be, with Cancel. While they install, each says how it went, one at a time,
 * and once all have been tried, Done.
 */
@Composable
internal fun BoxScope.FromPhonePanel(fromPhone: FromPhone) {
    val state = fromPhone.state?.takeUnless { it.onBoard }
    SlidingPanel(state) { shown, active, motion ->
        val picked =
            when (shown) {
                is FromPhoneState.Confirm -> shown.picked
                is FromPhoneState.Installing -> shown.picked
                else -> return@SlidingPanel
            }
        val statuses = (shown as? FromPhoneState.Confirm)?.statuses
        val toInstall = (shown as? FromPhoneState.Confirm)?.toInstall ?: 0
        val outcomes = (shown as? FromPhoneState.Installing)?.outcomes
        val going = outcomes != null && outcomes.any { it == null }
        val presses = remember(outcomes == null, going) { OwnPresses() }
        SidePanel(
            stringResource(R.string.from_phone_panel_title),
            SourcePanelWidth,
            active,
            // Back cancels before Install and closes once all are tried. While they install, it waits.
            onClose = { if (!going) fromPhone.onClose() },
            modifier = motion.onPreviewKeyEvent(presses::filter),
            tag = FROM_PHONE_PANEL_TAG,
        ) {
            val colors = NeoTheme.colors
            Text(stringResource(R.string.from_phone_panel_title), style = PaneTitle, color = colors.paper)
            Text(panelBody(picked, outcomes, toInstall), style = Body, color = colors.textMuted)
            // The list scrolls under the buttons, and Up goes through each source, so all of them can be read.
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rows(picked).forEachIndexed { index, (name, version) ->
                    val meta =
                        when {
                            outcomes == null -> arrivingMeta(version, statuses?.getOrNull(index))
                            else -> outcomeMeta(outcomes[index], outcomes.indexOfFirst { it == null } == index)
                        }
                    PhoneRow(name, meta.first, warn = meta.second)
                }
            }
            // One button always has focus: Install, then a resting Installing, then Done.
            val first = remember { FocusRequester() }
            key(outcomes == null, going) {
                NeoDialogButtons {
                    val focused = Modifier.focusRequester(first)
                    when {
                        outcomes == null && toInstall > 0 -> {
                            NeoDialogButton(
                                pluralStringResource(R.plurals.from_phone_install, toInstall, toInstall),
                                onClick = fromPhone.onInstall,
                                modifier = focused,
                            )
                            NeoDialogButton(stringResource(R.string.sign_in_cancel), onClick = fromPhone.onClose)
                        }

                        outcomes == null -> {
                            NeoDialogButton(stringResource(R.string.sources_close), onClick = fromPhone.onClose, modifier = focused)
                        }

                        going -> {
                            NeoDialogButton(stringResource(R.string.from_phone_installing_button), onClick = {}, modifier = focused)
                        }

                        else -> {
                            NeoDialogButton(stringResource(R.string.from_phone_done), onClick = fromPhone.onClose, modifier = focused)
                        }
                    }
                }
                LaunchedEffect(Unit) { first.requestFocus() }
            }
        }
    }
}

/** Each source's name and version, or for a link to one APK, the file's name and site. */
@Composable
private fun rows(picked: PickedSources): List<Pair<String, String?>> =
    when (picked) {
        is PickedSources.FromList -> {
            picked.sources.map { it.name to it.version }
        }

        is PickedSources.OneApk -> {
            val file = pathEnd(picked.apk)
            val site = host(picked.apk)
            listOf((if (file.isEmpty()) site else stringResource(R.string.from_phone_apk_from, file, site)) to null)
        }
    }

/** The site [link] is on, so the TV says where the code comes from. */
private fun host(link: String): String = runCatching { URI(link).host }.getOrNull() ?: link

/** The last part of [link]'s path, decoded, leaving out any query. */
private fun pathEnd(link: String): String =
    runCatching { URI(link).path }
        .getOrNull()
        .orEmpty()
        .trimEnd('/')
        .substringAfterLast('/')

@Composable
private fun panelBody(
    picked: PickedSources,
    outcomes: List<InstallOutcome?>?,
    toInstall: Int,
): String {
    val total = picked.size
    return when {
        outcomes == null && toInstall == 0 -> {
            stringResource(R.string.from_phone_nothing_to_install)
        }

        outcomes == null -> {
            val from = (picked as? PickedSources.FromList)?.list ?: (picked as PickedSources.OneApk).apk
            pluralStringResource(R.plurals.from_phone_sent, total, total, host(from))
        }

        outcomes.any { it == null } -> {
            stringResource(R.string.from_phone_installing, outcomes.indexOfFirst { it == null } + 1, total)
        }

        else -> {
            val done = outcomes.count { it is InstallOutcome.Installed }
            pluralStringResource(R.plurals.from_phone_installed, total, done, total)
        }
    }
}

/**
 * What arriving at [version] means here, from the TV's own [status]: new, an update, already installed or not for this
 * app. A link to one APK has neither, and is checked as it installs. True when it can't be installed.
 */
@Composable
private fun arrivingMeta(
    version: String?,
    status: ChoiceStatus?,
): Pair<String, Boolean> =
    when {
        version == null || status == null -> stringResource(R.string.from_phone_one_apk) to false
        status is ChoiceStatus.Update -> stringResource(R.string.sources_update, status.from, version) to false
        status == ChoiceStatus.AddAgain -> stringResource(R.string.from_phone_add_again, version) to false
        status == ChoiceStatus.Installed -> stringResource(R.string.from_phone_already) to true
        status is ChoiceStatus.CantUse -> stringResource(status.problem.short) to true
        else -> stringResource(R.string.from_phone_new, version) to false
    }

/** How one source went, or that it's installing or waiting its turn. True when it went wrong. */
@Composable
private fun outcomeMeta(
    outcome: InstallOutcome?,
    current: Boolean,
): Pair<String, Boolean> =
    when (outcome) {
        null -> stringResource(if (current) R.string.from_phone_row_installing else R.string.from_phone_row_waiting) to false
        is InstallOutcome.Installed -> stringResource(R.string.sources_installed_version, outcome.source.version) to false
        is InstallOutcome.CantUse -> stringResource(outcome.problem.short) to true
        InstallOutcome.AlreadyInstalled -> stringResource(R.string.from_phone_already) to false
        InstallOutcome.NotDownloaded -> stringResource(R.string.from_phone_not_downloaded) to true
        InstallOutcome.NotTheSource -> stringResource(R.string.from_phone_not_the_source) to true
    }

/** A source in the panel: its letter, name and how it stands. Focus can rest on it, so a long list scrolls. */
@Composable
private fun PhoneRow(
    name: String,
    meta: String,
    warn: Boolean,
) {
    val colors = NeoTheme.colors
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .background(if (focused) colors.line else colors.surfaceRaised, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceBadge(name, null, colors.surface)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = RowTitle, color = colors.paper, maxLines = 1)
            Text(meta, style = Small, color = if (warn) colors.warning else colors.textMuted)
        }
    }
}
