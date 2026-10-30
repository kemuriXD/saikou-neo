package ani.saikou.neo.tv.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AnimeCard
import ani.saikou.neo.shared.home.FeaturedReason
import ani.saikou.neo.shared.home.Logo
import ani.saikou.neo.shared.home.Spotlight
import ani.saikou.neo.shared.home.WatchProgress
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.LogoHeight
import ani.saikou.neo.tv.ui.LogoWidth
import ani.saikou.neo.tv.ui.NeoButton
import ani.saikou.neo.tv.ui.NeoButtonRow
import ani.saikou.neo.tv.ui.NeoIconButton
import ani.saikou.neo.tv.ui.NeoIcons
import ani.saikou.neo.tv.ui.ShowTitle
import ani.saikou.neo.tv.ui.outerGlow
import ani.saikou.neo.tv.ui.placeholder
import ani.saikou.neo.tv.ui.rememberPulse
import kotlinx.coroutines.delay
import java.util.Calendar

internal const val SPOTLIGHT_TAG = "spotlight"
internal const val SPOTLIGHT_TITLE_TAG = "spotlight-title"
internal const val SPOTLIGHT_LOGO_TAG = "spotlight-logo"
internal const val SPOTLIGHT_META_TAG = "spotlight-meta"
internal const val SPOTLIGHT_PLAY_TAG = "spotlight-play"
internal const val SPOTLIGHT_DOTS_TAG = "spotlight-dots"
internal const val SPOTLIGHT_SYNOPSIS_TAG = "spotlight-synopsis"
internal const val SPOTLIGHT_REASON_TAG = "spotlight-reason"

// From the design: dots are 6 dp, the current one 18 dp wide.

// The design puts the buttons 8 dp further down than the 10 dp between lines of text.
// While loading, still bars stand in for the text: the design's eyebrow, logo, meta line and two lines of story.
private val ButtonsGap = 18.dp
private val Dot = 6.dp
private val CurrentDot = 18.dp
private val DotGap = 7.dp
private const val OTHER_DOT_ALPHA = 0.45f
private const val SYNOPSIS_ALPHA = 0.78f

private const val SYNOPSIS_LINES = 2
private val SynopsisWidth = 360.dp
private const val WEEK_DAYS = 7
private val ReasonTracking = 1.2.sp
private val PlaceholderBars = listOf(150.dp to 14.dp, 400.dp to 64.dp, 260.dp to 18.dp, 420.dp to 16.dp, 340.dp to 16.dp)
private val PlaceholderDots = listOf(CurrentDot, Dot, Dot, Dot)

// Logos are preloaded with the shows' art, but a slow one gets this long before the title shows in words instead.
private const val LOGO_WAIT_MILLIS = 1_000L
private const val TITLE_LINES = 2

// Whole lines, so the title and story are exactly as tall as their lines.
private val FullLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/**
 * The featured show's title, details and actions. The text sits on the buttons and fades from one show to the next,
 * while the buttons stay put, so focus never moves under the viewer. Down from any button goes to the dots.
 * Until [spotlight] arrives, still bars hold the text's place and Play waits for the show. With [progress] on the
 * show, Play offers to resume it.
 */
@Composable
internal fun SpotlightShow(
    spotlight: Spotlight?,
    progress: Map<Int, WatchProgress>,
    play: FocusRequester,
    dots: FocusRequester,
    onOpen: (animeId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val show = spotlight?.show
    // Until the show is in, Play keeps focus, even if rows below have loaded.
    val toDots =
        Modifier.focusProperties {
            down = if (spotlight != null) dots else FocusRequester.Cancel
            if (spotlight == null) right = FocusRequester.Cancel
        }
    val dimensions = NeoTheme.dimensions
    // The old text fades out before the new fades in, as overlapping lines of text are hard to read.
    val half = dimensions.durationBackdropMillis / 2
    // Play's label changes as the new show's text comes in, not as the old one starts to fade.
    var labelShow by remember { mutableStateOf(show) }
    LaunchedEffect(show) {
        if (labelShow != null && show != null) delay(half.toLong())
        labelShow = show
    }
    Column(modifier.testTag(SPOTLIGHT_TAG)) {
        AnimatedContent(
            targetState = show,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { fadeIn(tween(half, delayMillis = half)) togetherWith fadeOut(tween(half)) using null },
            label = "show",
        ) { shown ->
            if (spotlight == null || shown == null) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom)) {
                    PlaceholderBars.forEach { (width, height) -> Skeleton(width, height) }
                }
                return@AnimatedContent
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom)) {
                spotlight.reasons[shown.id]?.let { reason ->
                    Text(
                        text = reason.text().uppercase(),
                        style = NeoTheme.typography.overline.copy(fontWeight = FontWeight.Bold, letterSpacing = ReasonTracking),
                        color = NeoTheme.colors.pink,
                        modifier = Modifier.testTag(SPOTLIGHT_REASON_TAG),
                    )
                }
                SpotlightTitle(shown.displayTitle(), spotlight.logo(shown.id))
                Text(
                    text = listOfNotNull(shown.rating(), shown.meta()).joinToString(" · "),
                    style = NeoTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = NeoTheme.colors.paper,
                    modifier = Modifier.testTag(SPOTLIGHT_META_TAG),
                )
                spotlight.synopses[shown.id]?.let { story ->
                    Text(
                        text = story,
                        style = NeoTheme.typography.body.copy(lineHeightStyle = FullLine),
                        color = NeoTheme.colors.paper.copy(alpha = SYNOPSIS_ALPHA),
                        maxLines = SYNOPSIS_LINES,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(SPOTLIGHT_SYNOPSIS_TAG).widthIn(max = SynopsisWidth),
                    )
                }
            }
        }
        // Play opens Details until there's a player.
        NeoButtonRow(Modifier.padding(top = ButtonsGap)) {
            // Focus grows it from its middle, so room for that keeps the gap to the next button.
            val density = LocalDensity.current
            val grows = dimensions.focusScaleButton - 1
            var width by remember { mutableIntStateOf(0) }
            Box(Modifier.padding(end = with(density) { (width * grows / 2).toDp() })) {
                NeoButton(
                    text =
                        labelShow?.let { progress[it.id] }?.let { stringResource(R.string.home_resume, it.episode) }
                            ?: stringResource(R.string.home_play),
                    onClick = { show?.let { onOpen(it.id) } },
                    icon = NeoIcons.Play,
                    modifier = toDots.onSizeChanged { width = it.width }.focusRequester(play).testTag(SPOTLIGHT_PLAY_TAG),
                )
            }
            if (show != null) {
                // Lists arrive with AniList sign-in, so this does nothing yet.
                NeoIconButton(NeoIcons.Add, stringResource(R.string.home_add_to_list), onClick = {}, modifier = toDots)
                // Nothing is to its right, so Right stays here rather than jumping to the dots.
                NeoIconButton(
                    NeoIcons.Info,
                    stringResource(R.string.home_details),
                    onClick = { onOpen(show.id) },
                    modifier = toDots.focusProperties { right = FocusRequester.Cancel },
                )
            }
        }
    }
}

/** What the line above the title says, such as "New episode Saturday" or "#2 trending". */
@Composable
private fun FeaturedReason.text(): String =
    when (this) {
        is FeaturedReason.NewEpisode -> {
            val now = Calendar.getInstance()
            when (daysUntil(airingAtEpochSeconds, now)) {
                0 -> {
                    stringResource(R.string.home_reason_episode_today)
                }

                1 -> {
                    stringResource(R.string.home_reason_episode_tomorrow)
                }

                else -> {
                    val airing = (now.clone() as Calendar).apply { timeInMillis = airingAtEpochSeconds * 1000 }
                    val day = airing.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, LocalConfiguration.current.locales[0])
                    stringResource(R.string.home_reason_episode_day, day.orEmpty())
                }
            }
        }

        is FeaturedReason.HighestRated -> {
            stringResource(R.string.home_reason_highest_rated, rank)
        }

        is FeaturedReason.Trending -> {
            stringResource(R.string.home_reason_trending, rank)
        }
    }

/**
 * How many days after [now]'s date, in its time zone, an episode out at [airingAtEpochSeconds] airs. Episodes are at
 * most a week away, so anything later counts as 8. Calendar rather than java.time, which older Fire TVs don't have.
 */
internal fun daysUntil(
    airingAtEpochSeconds: Long,
    now: Calendar,
): Int {
    val airing = (now.clone() as Calendar).apply { timeInMillis = airingAtEpochSeconds * 1000 }
    val day = now.clone() as Calendar
    var days = 0
    while (days <= WEEK_DAYS &&
        (day.get(Calendar.YEAR) != airing.get(Calendar.YEAR) || day.get(Calendar.DAY_OF_YEAR) != airing.get(Calendar.DAY_OF_YEAR))
    ) {
        day.add(Calendar.DAY_OF_YEAR, 1)
        days++
    }
    return days
}

/** AniList's average score out of 10, such as "★ 8.4", or null without one. */
@Composable
private fun AnimeCard.rating(): String? = averageScore?.let { stringResource(R.string.home_rating, it / 10, it % 10) }

/** The show's logo, or its title in words when there's no logo or it won't load. */
@Composable
private fun SpotlightTitle(
    title: String,
    logo: Logo,
) {
    ShowTitle(
        title = title,
        logo = logo,
        logoTag = SPOTLIGHT_LOGO_TAG,
        waitMillis = LOGO_WAIT_MILLIS,
        // Logos are preloaded, so an empty box keeps the place for one that's on its way.
        placeholder = { Box(Modifier.size(LogoWidth, LogoHeight)) },
    ) {
        Text(
            text = title,
            // Smaller than the design's 44, so a title in words doesn't outweigh the logos.
            style = NeoTheme.typography.headline.copy(lineHeightStyle = FullLine),
            color = NeoTheme.colors.paper,
            maxLines = TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(SPOTLIGHT_TITLE_TAG),
        )
    }
}

/** Pulsing dots in the dots' place while the spotlight loads. */
@Composable
internal fun SpotlightDotsPlaceholder(modifier: Modifier = Modifier) {
    val pulse = rememberPulse()
    Row(modifier.padding(horizontal = 14.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(DotGap)) {
        PlaceholderDots.forEach { width ->
            Box(Modifier.size(width, Dot).placeholder(NeoTheme.colors.line, RoundedCornerShape(Dot / 2), pulse))
        }
    }
}

/** One dot per featured show. Focused, Left and Right change the show; Up goes back to [play]. */
@Composable
internal fun SpotlightDots(
    spotlight: Spotlight,
    dots: FocusRequester,
    play: FocusRequester,
    onPick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val label = stringResource(R.string.home_featured_shows)
    val position =
        stringResource(R.string.home_featured_position, spotlight.index + 1, spotlight.shows.size, spotlight.show.displayTitle())
    Row(
        modifier
            .testTag(SPOTLIGHT_DOTS_TAG)
            .outerGlow(if (focused) Color.White.copy(alpha = 0.32f) else Color.Transparent, dimensions.glowFocus, Dot * 3)
            .background(if (focused) colors.fillTonal else Color.Transparent, CircleShape)
            .semantics {
                contentDescription = label
                stateDescription = position
            }.focusRequester(dots)
            .focusProperties { up = play }
            // Left and Right stay on the dots, even at the first and last show.
            .onKeyEvent { event ->
                val step =
                    when (event.key) {
                        Key.DirectionLeft -> -1
                        Key.DirectionRight -> 1
                        else -> return@onKeyEvent false
                    }
                val index = spotlight.index + step
                if (event.type == KeyEventType.KeyDown && index in spotlight.shows.indices) onPick(index)
                true
            }.focusable(interactionSource = interactions)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(DotGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        spotlight.shows.indices.forEach { index ->
            val current = index == spotlight.index
            val width by animateDpAsState(
                targetValue = if (current) CurrentDot else Dot,
                animationSpec = tween(dimensions.durationIndicatorMillis, easing = dimensions.easeStandard),
                label = "dot",
            )
            Box(
                Modifier
                    .size(width, Dot)
                    .background(if (current) colors.paper else colors.paper.copy(alpha = OTHER_DOT_ALPHA), RoundedCornerShape(Dot / 2)),
            )
        }
    }
}
