package ani.saikou.neo.tv.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import ani.saikou.neo.shared.account.AniListProfile
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.signin.ProfileAvatar
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoDialogButton
import ani.saikou.neo.tv.ui.NeoDialogButtons
import ani.saikou.neo.tv.ui.NeoIcons
import ani.saikou.neo.tv.ui.remembersFocus

/**
 * Settings > Account: the AniList [profile] this TV is signed in to, or null. [onSignIn] opens Sign in, and
 * [onSignOut] forgets the account on this TV once the user says so.
 */
class AccountSettings(
    val profile: AniListProfile? = null,
    val onSignIn: () -> Unit = {},
    val onSignOut: () -> Unit = {},
)

internal const val SIGN_IN_ENTRY_TAG = "settings-sign-in"
internal const val SIGN_IN_LOGO_TAG = "settings-sign-in-logo"
internal const val SIGN_OUT_TAG = "settings-sign-out"
internal const val SIGN_OUT_DIALOG_TAG = "settings-sign-out-dialog"

/** Where focus goes in Account, and whether Sign out is asking. */
internal class AccountUi {
    val signIn = FocusRequester()
    val signOut = FocusRequester()
    var asking by mutableStateOf(false)

    /** Where focus goes once the dialog closes: Sign in after signing out, Sign out after staying. */
    var focusNext by mutableStateOf<FocusRequester?>(null)
}

/**
 * Laid out like Sources. Signed out, it's the empty look: an icon, a line on signing in, and Sign in with your phone as
 * a button with AniList's mark. Signed in, Sign in with your phone sits on top, as Add a source does, then the account
 * with Sign out where a source has Remove. Left goes back to [category].
 */
@Composable
internal fun AccountPane(
    account: AccountSettings,
    ui: AccountUi,
    category: FocusRequester,
) {
    val colors = NeoTheme.colors
    val profile = account.profile
    val signedOut = profile == null
    LaunchedEffect(ui.focusNext, profile) {
        ui.focusNext?.requestFocus()
        ui.focusNext = null
    }
    val toCategory = Modifier.focusProperties { left = category }
    // One column for both looks, so Sign in stays the same node and takes focus once the account goes.
    Column(
        if (signedOut) Modifier.fillMaxWidth().height(EmptyHeight) else Modifier,
        verticalArrangement = if (signedOut) Arrangement.spacedBy(16.dp, Alignment.CenterVertically) else Arrangement.spacedBy(12.dp),
        horizontalAlignment = if (signedOut) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (signedOut) {
            EmptyHeader(NeoIcons.Profile, R.string.settings_account_signed_out_title, R.string.settings_account_signed_out_intro)
        } else {
            Text(stringResource(R.string.settings_account_intro), style = Body, color = colors.textMuted)
        }
        WideRow(
            onClick = account.onSignIn,
            height = if (signedOut) ButtonHeight else AccountRowHeight,
            container = if (signedOut) colors.fillTonal else colors.surfaceRaised,
            modifier =
                toCategory
                    .testTag(SIGN_IN_ENTRY_TAG)
                    .focusRequester(ui.signIn)
                    .remembersFocus(SIGN_IN_ENTRY_TAG)
                    .padding(top = if (signedOut) 4.dp else 0.dp),
            button = signedOut,
        ) {
            if (signedOut) {
                Image(
                    painterResource(R.drawable.ic_anilist_tile),
                    contentDescription = null,
                    modifier = Modifier.testTag(SIGN_IN_LOGO_TAG).size(20.dp),
                )
                Text(stringResource(R.string.settings_account_sign_in), style = RowTitle.copy(fontSize = 16.sp))
            } else {
                Icon(NeoIcons.Phone, contentDescription = null, modifier = Modifier.size(22.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.settings_account_sign_in), style = RowTitle)
                    Text(
                        stringResource(R.string.settings_account_sign_in_help),
                        style = Small,
                        color = LocalContentColor.current.copy(alpha = 0.72f),
                    )
                }
            }
        }
        if (profile == null) {
            EmptyNote(R.string.settings_account_signed_out_note)
        } else {
            Text(
                stringResource(R.string.settings_account_signed_in).uppercase(),
                style = Overline,
                color = colors.textFaint,
                modifier = Modifier.padding(top = 4.dp),
            )
            SignedInRow(profile, onSignOut = { ui.asking = true }, modifier = toCategory.focusRequester(ui.signOut))
        }
    }
}

/** The account: its picture or initial, name, and how it got here, with a Sign out pill at the end. */
@Composable
private fun SignedInRow(
    profile: AniListProfile,
    onSignOut: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    var focused by remember { mutableStateOf(false) }
    val lift by animateColorAsState(if (focused) colors.surfaceRaised else colors.surface, label = "row")
    val detail = stringResource(R.string.settings_account_from_phone)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = SignedInRowHeight)
            .background(lift, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .onFocusChanged { focused = it.hasFocus },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(profile, 40.dp, NeoTheme.typography.label)
        Column(Modifier.weight(1f).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(profile.name, style = RowTitle, color = colors.paper, maxLines = 1)
            Text(detail, style = Small, color = colors.textMuted)
        }
        val signOut = stringResource(R.string.settings_account_sign_out)
        RowPill(signOut, NeoIcons.SignOut, "${profile.name}, $detail. $signOut", onSignOut, modifier.testTag(SIGN_OUT_TAG))
    }
}

/**
 * Sign out on this TV? In the middle of the dimmed screen, with Stay signed in focused first. Focus stays in it, and
 * Back stays signed in, while it's [active].
 */
@Composable
internal fun BoxScope.SignOutDialog(
    active: Boolean,
    onStay: () -> Unit,
    onSignOut: () -> Unit,
) {
    val stay = remember { FocusRequester() }
    LaunchedEffect(Unit) { stay.requestFocus() }
    BackHandler(enabled = active, onBack = onStay)
    val colors = NeoTheme.colors
    val title = stringResource(R.string.settings_account_sign_out_title)
    Box(Modifier.fillMaxSize().background(colors.scrimPlayer.copy(alpha = DIALOG_SCRIM_ALPHA)))
    Column(
        Modifier
            .align(Alignment.Center)
            .testTag(SIGN_OUT_DIALOG_TAG)
            .width(DialogWidth)
            .background(colors.surface, RoundedCornerShape(20.dp))
            .padding(24.dp)
            .semantics { paneTitle = title }
            .focusProperties { onExit = { if (active) cancelFocusChange() } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = PaneTitle, color = colors.paper)
        Text(stringResource(R.string.settings_account_sign_out_body), style = Body, color = colors.textMuted)
        NeoDialogButtons(Modifier.padding(top = 6.dp)) {
            NeoDialogButton(stringResource(R.string.settings_account_stay), onClick = onStay, modifier = Modifier.focusRequester(stay))
            NeoDialogButton(stringResource(R.string.settings_account_sign_out), onClick = onSignOut)
        }
    }
}

// From the design: the Sign in row is 60 dp like Add a source, the account 68 dp like a source; the dialog is 420 dp.
private val AccountRowHeight = 60.dp
private val SignedInRowHeight = 68.dp
private val DialogWidth = 420.dp
private const val DIALOG_SCRIM_ALPHA = 0.55f
