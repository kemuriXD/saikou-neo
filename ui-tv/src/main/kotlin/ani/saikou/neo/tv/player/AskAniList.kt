package ani.saikou.neo.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoSurface

internal const val ASK_ANILIST_TAG = "player-ask-anilist"
internal const val ASK_ANILIST_UPDATE_TAG = "player-ask-anilist-update"
internal const val ASK_ANILIST_NOT_TAG = "player-ask-anilist-not"

// From the design: 460 dp wide, 28 dp in, a 50 dp button over a 48 dp one.
private val AskWidth = 460.dp
private val AskPadding = 28.dp
private const val ASK_SCRIM_ALPHA = 0.8f

/**
 * "Update AniList as you watch?" for [show], after [episode] counted, over the dimmed player (ADR 0053). Update AniList
 * has focus first, and focus stays in it. Back is handled by the player, as no answer.
 */
@Composable
internal fun BoxScope.AskAniList(
    show: String,
    episode: Int,
    onAnswer: (update: Boolean) -> Unit,
) {
    val colors = NeoTheme.colors
    val typography = NeoTheme.typography
    val update = remember { FocusRequester() }
    LaunchedEffect(Unit) { update.requestFocus() }
    val title = stringResource(R.string.player_ask_anilist_title)
    Box(Modifier.fillMaxSize().background(colors.scrimPlayer.copy(alpha = ASK_SCRIM_ALPHA)))
    Column(
        Modifier
            .align(Alignment.Center)
            .testTag(ASK_ANILIST_TAG)
            .width(AskWidth)
            .background(colors.surfaceRaised, RoundedCornerShape(20.dp))
            .padding(AskPadding)
            .semantics { paneTitle = title }
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            show.uppercase(),
            style = typography.overline.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = colors.pinkLight,
            maxLines = 1,
        )
        Text(title, style = typography.titleLarge, color = colors.paper)
        Text(
            stringResource(R.string.player_ask_anilist_body, episode),
            style = typography.body.copy(lineHeight = 22.sp),
            color = colors.textMuted,
        )
        Spacer(Modifier.height(14.dp))
        Answer(stringResource(R.string.player_ask_anilist_update), 50, Modifier.focusRequester(update).testTag(ASK_ANILIST_UPDATE_TAG)) {
            onAnswer(true)
        }
        Answer(stringResource(R.string.player_ask_anilist_not), 48, Modifier.testTag(ASK_ANILIST_NOT_TAG)) { onAnswer(false) }
        Text(stringResource(R.string.player_ask_anilist_note), style = typography.caption.copy(fontSize = 12.sp), color = colors.textFaint)
    }
}

@Composable
private fun Answer(
    text: String,
    height: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    NeoSurface(onClick, CircleShape, NeoTheme.dimensions.focusScale, modifier.fillMaxWidth().height(height.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, style = NeoTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
    }
}
