package ani.saikou.neo.tv.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.shared.home.PickUp
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.shared.home.daysSince
import ani.saikou.neo.shared.playback.formatPlaybackTime
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.keepsArrowsIn
import coil3.compose.AsyncImage
import java.util.TimeZone

const val PICK_UP_TAG = "pick-up"
const val PICK_UP_ANILIST_TAG = "pick-up-anilist"
const val PICK_UP_THIS_TV_TAG = "pick-up-this-tv"

private val DialogWidth = 660.dp
private val ChoiceWidth = 320.dp
private val ChoiceGap = 20.dp
private val ChoiceShape = RoundedCornerShape(18.dp)
private val StillHeight = 130.dp
private val StillShape = RoundedCornerShape(12.dp)
private const val SCRIM_ALPHA = 0.96f
private const val BODY_ALPHA = 0.8f
private const val GHOST_ALPHA = 0.28f
private const val FOCUS_SCALE = 1.03f

/**
 * Asks where [show] picks up when AniList and this TV disagree (ADR 0050): AniList's place first, with focus, then this
 * TV's. [art] stands in for a still neither has. Back closes it with nothing changed.
 */
@Composable
internal fun PickUpDialog(
    show: String,
    asked: PickUp.Ask,
    art: String?,
    onAniList: () -> Unit,
    onThisTv: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val colors = NeoTheme.colors
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Box(Modifier.fillMaxSize().background(colors.ink.copy(alpha = SCRIM_ALPHA)).testTag(PICK_UP_TAG), Alignment.Center) {
        Column(Modifier.width(DialogWidth), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(show.uppercase(), style = Eyebrow, color = colors.pink, maxLines = 1)
                Text(stringResource(R.string.details_pick_up_title), style = NeoTheme.typography.headline, color = colors.paper)
                Text(
                    stringResource(R.string.details_pick_up_body),
                    style = NeoTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = colors.paper.copy(alpha = BODY_ALPHA),
                )
            }
            Row(Modifier.keepsArrowsIn(), horizontalArrangement = Arrangement.spacedBy(ChoiceGap)) {
                // AniList's place is its last episode watched, so it carries on with the one after.
                val listed = asked.aniList
                Choice(
                    episode = listed.episode + 1,
                    still = art,
                    source = stringResource(R.string.details_pick_up_anilist),
                    choice = stringResource(R.string.details_pick_up_start, listed.episode + 1),
                    detail = aniListDetail(listed),
                    onClick = onAniList,
                    recommended = true,
                    modifier = Modifier.focusRequester(first).testTag(PICK_UP_ANILIST_TAG),
                )
                val here = asked.thisTv
                val resumeAt = here.resumeAtMs(here.episode)
                val next = if (here.watchedToEnd) here.episode + 1 else here.episode
                Choice(
                    episode = if (resumeAt != null) here.episode else next,
                    still = here.still ?: art,
                    source = stringResource(R.string.details_pick_up_this_tv),
                    choice =
                        if (resumeAt != null) {
                            stringResource(R.string.details_pick_up_resume, here.episode)
                        } else {
                            stringResource(R.string.details_pick_up_start, next)
                        },
                    detail = thisTvDetail(here, resumeAt, next - 1),
                    onClick = onThisTv,
                    modifier = Modifier.testTag(PICK_UP_THIS_TV_TAG),
                )
            }
        }
    }
}

/** One side's place: its episode's still with the number large over it, where it came from, and what picking it does. */
@Composable
private fun Choice(
    episode: Int,
    still: String?,
    source: String,
    choice: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier,
    recommended: Boolean = false,
) {
    val colors = NeoTheme.colors
    val typography = NeoTheme.typography
    Surface(
        onClick = onClick,
        modifier = modifier.width(ChoiceWidth),
        shape = ClickableSurfaceDefaults.shape(ChoiceShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colors.surface,
                contentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.focus,
                pressedContentColor = colors.onFocus,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = FOCUS_SCALE),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(colors.paper.copy(alpha = 0.28f), 9.dp)),
    ) {
        val focused = LocalContentColor.current == colors.onFocus
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(StillHeight)
                    .clip(StillShape)
                    .background(colors.surfaceRaised),
            ) {
                if (still != null) {
                    AsyncImage(
                        model = still,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    text = episode.toString().padStart(2, '0'),
                    style = typography.display.copy(fontSize = 110.sp, lineHeight = 110.sp, letterSpacing = (-4).sp),
                    color = colors.paper.copy(alpha = GHOST_ALPHA),
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp),
                )
            }
            val sourceColor =
                when {
                    recommended && focused -> colors.pinkDeep
                    recommended -> colors.pink
                    focused -> colors.onFocusMuted
                    else -> colors.textMuted
                }
            Text(source.uppercase(), style = Eyebrow, color = sourceColor, maxLines = 1)
            Text(choice, style = typography.title.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            Text(
                detail,
                style = typography.caption,
                color = if (focused) colors.onFocusMuted else colors.textMuted,
                maxLines = 2,
            )
        }
    }
}

private val Eyebrow
    @Composable get() =
        NeoTheme.typography.caption.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )

/** "Episode 7 marked watched on AniList, 2 days ago". */
@Composable
private fun aniListDetail(listed: WatchProgress): String {
    val watched = listed.episode
    val what =
        if (watched > 0) {
            stringResource(R.string.details_pick_up_listed, watched)
        } else {
            stringResource(R.string.details_pick_up_listed_none)
        }
    return withWhen(what, listed.savedAt)
}

/** "At 12:04, 2 days ago", or the episodes watched here for a place that isn't part-watched. */
@Composable
private fun thisTvDetail(
    here: WatchProgress,
    resumeAt: Long?,
    watched: Int,
): String {
    val what =
        when {
            resumeAt != null -> stringResource(R.string.details_pick_up_at, formatPlaybackTime(resumeAt))
            watched > 0 -> stringResource(R.string.details_pick_up_watched_here, watched)
            else -> stringResource(R.string.details_pick_up_watched_here_none)
        }
    return withWhen(what, here.savedAt)
}

/** [what], then how long ago [savedAt] was, in days where the viewer is. Just [what] when that's not known. */
@Composable
private fun withWhen(
    what: String,
    savedAt: Long?,
): String {
    savedAt ?: return what
    val nowMs = System.currentTimeMillis()
    val days = daysSince(savedAt, nowMs / 1000, TimeZone.getDefault().getOffset(nowMs) / 1000).coerceAtLeast(0)
    val ago =
        when (days) {
            0 -> stringResource(R.string.details_pick_up_today)
            1 -> stringResource(R.string.details_pick_up_yesterday)
            else -> pluralStringResource(R.plurals.details_pick_up_days_ago, days, days)
        }
    return stringResource(R.string.details_pick_up_when, what, ago)
}
