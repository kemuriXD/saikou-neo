package ani.saikou.neo.tv.signin

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import ani.saikou.neo.shared.pairing.TvPairingNote
import ani.saikou.neo.shared.pairing.TvSignInState
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoButton
import ani.saikou.neo.tv.ui.NeoButtonRow
import ani.saikou.neo.tv.ui.NeoIcons
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

// From the boards (Figma "TV · Start and Home", Start section): the message is 430 dp wide at 58, 64, and the panel
// 380 dp wide at 520, 80, under a violet glow centred off its top right.
private val MessageStart = 58.dp
private val MessageTop = 64.dp
private val MessageWidth = 430.dp
private val PanelStart = 520.dp
private val PanelTop = 80.dp
private val PanelWidth = 380.dp
private val PanelHeight = 336.dp
private val PanelShape = RoundedCornerShape(22.dp)
private val GlowCenter = Offset(760f, 260f)
private val GlowRadius = 380.dp
private val GlowColor = Color(0xFF5F4BB6).copy(alpha = 0.4f)
private const val PANEL_ALPHA = 0.9f
private val QrSize = 168.dp
private val QrPadding = 12.dp
private val CodeBox = Size(44f, 56f)
private const val SPIN_MILLIS = 900

/**
 * Sign in on the TV with a phone (#116): what the TV shows at each [state]. [onNewCode] gets a new code, [onLeave] is
 * Not now and Cancel (Back does the same), and [onHome] is Go to Home once signed in.
 */
@Composable
fun SignInScreen(
    state: TvSignInState,
    onNewCode: () -> Unit,
    onLeave: () -> Unit,
    onHome: () -> Unit,
) {
    val first = remember { FocusRequester() }
    SignInBoard(
        message = { Message(state, first, onNewCode, onLeave, onHome) },
        // The panel isn't drawn while the listener opens.
        panel = if (state == TvSignInState.Starting) null else ({ Panel(state) }),
        panelHeight = PanelHeight.takeIf { state !is TvSignInState.Waiting },
    )
}

/**
 * The layout every Sign in board shares (Figma "TV · Start and Home", Start section): ink under a violet glow, the
 * [message] on the left and the [panel] on the right, [panelHeight] tall or as tall as it needs.
 */
@Composable
internal fun SignInBoard(
    message: @Composable ColumnScope.() -> Unit,
    panel: (@Composable ColumnScope.() -> Unit)?,
    panelHeight: Dp? = null,
) {
    val colors = NeoTheme.colors
    Box(Modifier.fillMaxSize().background(colors.ink)) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(GlowCenter.x.dp.toPx(), GlowCenter.y.dp.toPx())
            drawCircle(
                Brush.radialGradient(listOf(GlowColor, GlowColor.copy(alpha = 0f)), center, GlowRadius.toPx()),
                GlowRadius.toPx(),
                center,
            )
        }
        Column(
            Modifier.padding(start = MessageStart, top = MessageTop).width(MessageWidth),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = message,
        )
        if (panel != null) {
            Column(
                Modifier
                    .padding(start = PanelStart, top = PanelTop)
                    .width(PanelWidth)
                    .then(if (panelHeight == null) Modifier else Modifier.height(panelHeight))
                    .shadow(30.dp, PanelShape)
                    .background(colors.surface.copy(alpha = PANEL_ALPHA), PanelShape)
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = panel,
            )
        }
    }
}

@Composable
private fun ColumnScope.Message(
    state: TvSignInState,
    first: FocusRequester,
    onNewCode: () -> Unit,
    onLeave: () -> Unit,
    onHome: () -> Unit,
) {
    val signedIn = state is TvSignInState.SignedIn
    Text(
        stringResource(if (signedIn) R.string.sign_in_done_eyebrow else R.string.sign_in_eyebrow).uppercase(),
        style = Eyebrow,
        color = NeoTheme.colors.pink,
    )
    val (title, body) =
        when (state) {
            TvSignInState.Starting, is TvSignInState.Waiting -> R.string.sign_in_title to null
            TvSignInState.Connecting -> R.string.sign_in_connecting_title to R.string.sign_in_connecting_body
            is TvSignInState.CheckCode -> R.string.sign_in_check_title to R.string.sign_in_check_body
            is TvSignInState.SignedIn -> R.string.sign_in_done_title to R.string.sign_in_done_body
            is TvSignInState.Expired -> R.string.sign_in_expired_title to R.string.sign_in_expired_body
            TvSignInState.Stopped -> R.string.sign_in_stopped_title to R.string.sign_in_stopped_body
            TvSignInState.NoNetwork -> R.string.sign_in_no_network_title to R.string.sign_in_no_network_body
        }
    Text(stringResource(title), style = Title, color = NeoTheme.colors.paper)
    body?.let { Text(stringResource(it), style = Body, color = NeoTheme.colors.textMuted) }
    if (body == null) Steps()
    // Each board's buttons are new, and the first takes focus: Get a new code, Cancel or Go to Home. When the focused
    // button goes, focus can land on the new one before it watches for focus, and it then has focus without showing it
    // (seen on the Fire TV going to Signed in). So focus is let go, and taken again a frame later.
    val focusManager = LocalFocusManager.current
    key(state::class) {
        Buttons(state, first, onNewCode, onLeave, onHome)
        LaunchedEffect(Unit) {
            focusManager.clearFocus()
            withFrameNanos {}
            first.requestFocus()
        }
    }
}

@Composable
private fun Buttons(
    state: TvSignInState,
    first: FocusRequester,
    onNewCode: () -> Unit,
    onLeave: () -> Unit,
    onHome: () -> Unit,
) {
    NeoButtonRow(Modifier.padding(top = 14.dp)) {
        val focused = Modifier.focusRequester(first)
        when (state) {
            TvSignInState.Connecting, is TvSignInState.CheckCode -> {
                NeoButton(stringResource(R.string.sign_in_cancel), onClick = onLeave, modifier = focused)
            }

            is TvSignInState.SignedIn -> {
                NeoButton(stringResource(R.string.sign_in_go_home), onClick = onHome, modifier = focused)
            }

            TvSignInState.NoNetwork -> {
                NeoButton(stringResource(R.string.sign_in_try_again), onClick = onNewCode, modifier = focused)
                NeoButton(stringResource(R.string.sign_in_not_now), onClick = onLeave)
            }

            else -> {
                NeoButton(stringResource(R.string.sign_in_new_code), onClick = onNewCode, modifier = focused)
                NeoButton(stringResource(R.string.sign_in_not_now), onClick = onLeave)
            }
        }
    }
}

@Composable
private fun Steps() {
    val colors = NeoTheme.colors
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Step 2 names the phone's button in bold.
        val pairATv = stringResource(R.string.sign_in_pair_a_tv)
        val step2 = stringResource(R.string.sign_in_step_2, pairATv)
        val steps =
            listOf(
                AnnotatedString(stringResource(R.string.sign_in_step_1)),
                buildAnnotatedString {
                    append(step2)
                    val at = step2.indexOf(pairATv)
                    if (at >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), at, at + pairATv.length)
                },
                AnnotatedString(stringResource(R.string.sign_in_step_3)),
            )
        steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(28.dp).background(colors.fillTonal, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = StepNumber, color = colors.paper)
                }
                Text(step, style = Body, color = colors.paper)
            }
        }
    }
}

@Composable
private fun Panel(state: TvSignInState) {
    val colors = NeoTheme.colors
    val typography = NeoTheme.typography
    when (state) {
        TvSignInState.Starting -> {
            // The panel isn't drawn while the listener opens.
        }

        is TvSignInState.Waiting -> {
            Qr(state.qrText)
            val spoken = stringResource(R.string.sign_in_code_label, state.code.toList().joinToString(" "))
            Row(Modifier.clearAndSetSemantics { contentDescription = spoken }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.code.forEach { CodeCharacter(it) }
            }
            val time = "%d:%02d".format(state.secondsLeft / 60, state.secondsLeft % 60)
            Status(stringResource(R.string.sign_in_waiting, time), 12.dp)
            state.note?.let { Note(it) }
        }

        TvSignInState.Connecting -> {
            Spinner(56.dp, colors.pink)
            Text(
                stringResource(R.string.sign_in_connecting_status),
                style = typography.titleLarge.copy(fontSize = 20.sp),
                color = colors.paper,
            )
            Text(stringResource(R.string.sign_in_connecting_detail), style = Small, color = colors.textMuted)
        }

        is TvSignInState.CheckCode -> {
            val spoken = stringResource(R.string.sign_in_check_label, state.code.toList().joinToString(" "))
            Column(Modifier.clearAndSetSemantics { contentDescription = spoken }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.sign_in_check_overline), style = Overline, color = colors.textMuted)
                Text("${state.code.take(3)} ${state.code.drop(3)}", style = CheckCode, color = colors.paper)
            }
            Status(stringResource(R.string.sign_in_waiting_phone), 14.dp)
        }

        is TvSignInState.SignedIn -> {
            ProfileAvatar(state.profile, 96.dp, typography.display)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.profile.name, style = typography.headline, color = colors.paper, maxLines = 1)
                Icon(NeoIcons.Passed, contentDescription = null, tint = colors.pink, modifier = Modifier.size(24.dp))
            }
            Text(stringResource(R.string.sign_in_done_account), style = Small, color = colors.textMuted)
        }

        is TvSignInState.Expired -> {
            Problem(stringResource(R.string.sign_in_expired_status), stringResource(R.string.sign_in_expired_detail, state.code))
        }

        TvSignInState.Stopped -> {
            Problem(stringResource(R.string.sign_in_stopped_status), stringResource(R.string.sign_in_stopped_detail))
        }

        TvSignInState.NoNetwork -> {
            Problem(
                stringResource(R.string.sign_in_no_network_status),
                stringResource(R.string.sign_in_no_network_detail),
                NeoIcons.WifiOff,
            )
        }
    }
}

/** Why the last phone left, under the code it can try again with (#414). Read out when it appears. */
@Composable
private fun Note(note: TvPairingNote) {
    val colors = NeoTheme.colors
    val reason =
        when (note) {
            TvPairingNote.StoppedOnPhone -> R.string.sign_in_note_stopped
            TvPairingNote.CancelledOnPhone -> R.string.sign_in_note_cancelled
            TvPairingNote.AniListRefused -> R.string.sign_in_note_refused
            TvPairingNote.LostConnection -> R.string.sign_in_note_lost
            TvPairingNote.TookTooLong -> R.string.sign_in_note_too_long
        }
    Row(
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (note == TvPairingNote.LostConnection) NeoIcons.WifiOff else NeoIcons.Warning,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.padding(top = 2.dp).size(18.dp),
        )
        Column {
            Text(stringResource(reason), style = Small, color = colors.paper)
            Text(stringResource(R.string.sign_in_note_same_code), style = Small, color = colors.textMuted)
        }
    }
}

/** The QR code the phone scans: dark modules on a paper square. */
@Composable
private fun Qr(text: String) {
    val colors = NeoTheme.colors
    val matrix = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0)) }
    Canvas(
        Modifier
            .size(QrSize)
            .background(colors.paper, RoundedCornerShape(14.dp))
            .padding(QrPadding)
            .clearAndSetSemantics { },
    ) {
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(colors.ink, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

@Composable
private fun CodeCharacter(character: Char) {
    val colors = NeoTheme.colors
    Box(
        Modifier
            .size(CodeBox.width.dp, CodeBox.height.dp)
            .background(colors.ink, RoundedCornerShape(10.dp))
            .border(1.5.dp, colors.line, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(character.toString(), style = NeoTheme.typography.headline.copy(fontSize = 28.sp, lineHeight = 34.sp), color = colors.paper)
    }
}

/** A small spinner and [text], read out as it changes. */
@Composable
private fun Status(
    text: String,
    spinner: Dp,
) {
    Row(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spinner(spinner, NeoTheme.colors.textMuted)
        Text(text, style = Small, color = NeoTheme.colors.textMuted)
    }
}

@Composable
private fun Spinner(
    size: Dp,
    tint: Color,
) {
    val angle by rememberInfiniteTransition(label = "spinner").animateFloat(
        0f,
        360f,
        infiniteRepeatable(tween(SPIN_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Icon(NeoIcons.Busy, contentDescription = null, tint = tint, modifier = Modifier.size(size).rotate(angle))
}

@Composable
private fun Problem(
    title: String,
    detail: String,
    icon: ImageVector = NeoIcons.Warning,
) {
    val colors = NeoTheme.colors
    Icon(icon, contentDescription = null, tint = colors.warning, modifier = Modifier.size(48.dp))
    Text(
        title,
        style = NeoTheme.typography.titleLarge,
        color = colors.paper,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Text(detail, style = Small, color = colors.textMuted, textAlign = TextAlign.Center)
}

internal val Eyebrow
    @Composable get() =
        NeoTheme.typography.overline.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )

internal val Title
    @Composable get() = NeoTheme.typography.headline.copy(fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = 0.sp)

internal val Body
    @Composable get() = NeoTheme.typography.bodyLarge.copy(lineHeight = 24.sp)

private val StepNumber
    @Composable get() = NeoTheme.typography.caption.copy(fontWeight = FontWeight.Bold, lineHeight = 16.sp)

internal val Small
    @Composable get() = NeoTheme.typography.body.copy(fontSize = 14.sp)

private val Overline
    @Composable get() = NeoTheme.typography.overline.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.96.sp)

private val CheckCode
    @Composable get() = NeoTheme.typography.display.copy(fontSize = 64.sp, lineHeight = 76.sp, letterSpacing = 2.56.sp)
