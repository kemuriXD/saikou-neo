package ani.saikou.neo.tv.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoButton
import ani.saikou.neo.tv.ui.NeoButtonRow
import ani.saikou.neo.tv.ui.NeoIcons

// From the board (Figma "TV · Browse", "Library: signed out"): the preview of the lists is dimmed, as it isn't yours yet.
private const val PREVIEW_ALPHA = 0.55f
private val PreviewWidth = 240.dp
private val ListRow = 44.dp

private val previewLists =
    listOf(
        R.string.profile_list_watching,
        R.string.profile_list_planning,
        R.string.profile_list_completed,
        R.string.profile_list_paused,
        R.string.profile_list_dropped,
    )

/**
 * Profile while the TV is signed out (#116): what signing in brings, with [onSignIn] (Sign in with AniList, which has
 * focus) and [onHome] (Back to Home). Signed in, Profile opens the Library instead (#74).
 */
@Composable
fun ProfileSignedOutScreen(
    onSignIn: () -> Unit,
    onHome: () -> Unit,
) {
    val colors = NeoTheme.colors
    val signIn = remember { FocusRequester() }
    LaunchedEffect(Unit) { signIn.requestFocus() }
    SignInBoard(
        message = {
            Text(stringResource(R.string.profile_signed_out_eyebrow).uppercase(), style = Eyebrow, color = colors.pink)
            Text(stringResource(R.string.profile_signed_out_title), style = Title, color = colors.paper)
            Text(stringResource(R.string.profile_signed_out_body), style = Body, color = colors.textMuted)
            Text(stringResource(R.string.profile_signed_out_note), style = Small.copy(lineHeight = 20.sp), color = colors.textFaint)
            NeoButtonRow(Modifier.padding(top = 14.dp)) {
                NeoButton(
                    stringResource(R.string.profile_signed_out_sign_in),
                    onClick = onSignIn,
                    modifier = Modifier.focusRequester(signIn),
                    logo = painterResource(R.drawable.ic_anilist_tile),
                )
                NeoButton(stringResource(R.string.profile_signed_out_home), onClick = onHome)
            }
        },
        panel = {
            ListsPreview()
            Text(
                stringResource(R.string.profile_signed_out_caption),
                style = Small,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
        },
    )
}

/** The Library's side panel with nothing of yours in it: a blank picture, a stand-in name and the list names. */
@Composable
private fun ListsPreview() {
    val colors = NeoTheme.colors
    Column(
        Modifier.width(PreviewWidth).alpha(PREVIEW_ALPHA).clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).border(1.5.dp, colors.outline, CircleShape), contentAlignment = Alignment.Center) {
                Icon(NeoIcons.Profile, contentDescription = null, tint = colors.paper, modifier = Modifier.size(22.dp))
            }
            Text(
                stringResource(R.string.profile_signed_out_name),
                style = NeoTheme.typography.rowTitle.copy(fontSize = 16.sp),
                color = colors.paper,
                maxLines = 1,
            )
        }
        previewLists.forEachIndexed { index, list ->
            val current = index == 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ListRow)
                    .then(if (current) Modifier.background(colors.fillTonal, CircleShape) else Modifier)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).then(if (current) Modifier.background(colors.paper, CircleShape) else Modifier))
                Text(
                    stringResource(list),
                    style = NeoTheme.typography.label.copy(fontWeight = if (current) FontWeight.Bold else FontWeight.Medium),
                    color = if (current) colors.paper else colors.textMuted,
                )
            }
        }
    }
}
