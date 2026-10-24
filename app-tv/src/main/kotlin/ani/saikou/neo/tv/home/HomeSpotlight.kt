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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import ani.saikou.neo.shared.home.Logo
import ani.saikou.neo.shared.home.Spotlight
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoButton
import ani.saikou.neo.tv.ui.NeoButtonRow
import ani.saikou.neo.tv.ui.NeoIconButton
import ani.saikou.neo.tv.ui.NeoIcons
import ani.saikou.neo.tv.ui.outerGlow
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations

internal const val SPOTLIGHT_TAG = "spotlight"
internal const val SPOTLIGHT_TITLE_TAG = "spotlight-title"
internal const val SPOTLIGHT_LOGO_TAG = "spotlight-logo"
internal const val SPOTLIGHT_PLAY_TAG = "spotlight-play"
internal const val SPOTLIGHT_DOTS_TAG = "spotlight-dots"
internal const val SPOTLIGHT_SYNOPSIS_TAG = "spotlight-synopsis"

// From the design: the logo fits a 360 × 80 box, sitting on the meta line. Dots are 6 dp, the current one 18 dp wide.
private val LogoWidth = 360.dp
private val LogoHeight = 80.dp

// The design puts the buttons 8 dp further down than the 10 dp between lines of text.
private val ButtonsGap = 18.dp
private val Dot = 6.dp
private val CurrentDot = 18.dp
private val DotGap = 7.dp
private const val OTHER_DOT_ALPHA = 0.45f
private const val SYNOPSIS_ALPHA = 0.78f

private const val SYNOPSIS_LINES = 3
private const val TITLE_LINES = 2

// Whole lines, so the title and story are exactly as tall as their lines.
private val FullLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

// Title logos are wide. Anything squarer is key art filed as a logo.
private const val MIN_LOGO_ASPECT = 1.3f

internal fun looksLikeALogo(
    width: Int,
    height: Int,
) = height > 0 && width >= height * MIN_LOGO_ASPECT

/**
 * The featured show's title, details and actions. The text sits on the buttons and fades from one show to the next,
 * while the buttons stay put, so focus never moves under the viewer. Down from any button goes to the dots.
 */
@Composable
internal fun SpotlightShow(
    spotlight: Spotlight,
    play: FocusRequester,
    dots: FocusRequester,
    onOpen: (animeId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val show = spotlight.show
    val toDots = Modifier.focusProperties { down = dots }
    val dimensions = NeoTheme.dimensions
    Column(modifier.testTag(SPOTLIGHT_TAG)) {
        // The old text fades out before the new fades in, as overlapping lines of text are hard to read.
        val half = dimensions.durationBackdropMillis / 2
        AnimatedContent(
            targetState = show,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { fadeIn(tween(half, delayMillis = half)) togetherWith fadeOut(tween(half)) using null },
            label = "show",
        ) { shown ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom)) {
                SpotlightTitle(shown.displayTitle(), spotlight.logo(shown.id))
                Text(
                    text = shown.meta(),
                    style = NeoTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = NeoTheme.colors.paper,
                )
                spotlight.synopses[shown.id]?.let { story ->
                    Text(
                        text = story,
                        style = NeoTheme.typography.bodyLarge.copy(lineHeightStyle = FullLine),
                        color = NeoTheme.colors.paper.copy(alpha = SYNOPSIS_ALPHA),
                        maxLines = SYNOPSIS_LINES,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(SPOTLIGHT_SYNOPSIS_TAG),
                    )
                }
            }
        }
        // Play opens Details until there's a player.
        NeoButtonRow(Modifier.padding(top = ButtonsGap)) {
            NeoButton(
                text = stringResource(R.string.home_play),
                onClick = { onOpen(show.id) },
                icon = NeoIcons.Play,
                modifier = toDots.focusRequester(play).testTag(SPOTLIGHT_PLAY_TAG),
            )
            // Lists arrive with AniList sign-in, so this does nothing yet.
            NeoIconButton(NeoIcons.Add, stringResource(R.string.home_add_to_list), onClick = {}, modifier = toDots)
            NeoIconButton(NeoIcons.Info, stringResource(R.string.home_details), onClick = { onOpen(show.id) }, modifier = toDots)
        }
    }
}

/** The show's logo, or its title in words when there's no logo, or it won't load or isn't one. */
@Composable
private fun SpotlightTitle(
    title: String,
    logo: Logo,
) {
    var failed by remember(logo) { mutableStateOf(false) }
    val context = LocalPlatformContext.current
    when {
        logo == Logo.Loading -> {
            Box(Modifier.size(LogoWidth, LogoHeight))
        }

        logo is Logo.Image && !failed -> {
            AsyncImage(
                model = remember(logo.url) { logoRequest(context, logo.url) },
                contentDescription = title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                onSuccess = { failed = !looksLikeALogo(it.result.image.width, it.result.image.height) },
                onError = { failed = true },
                modifier = Modifier.size(LogoWidth, LogoHeight).testTag(SPOTLIGHT_LOGO_TAG),
            )
        }

        else -> {
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
}

/** A logo trimmed of empty margins. Preloading uses the same request, so the cached copy is the one shown. */
internal fun logoRequest(
    context: PlatformContext,
    url: String,
) = ImageRequest
    .Builder(context)
    .data(url)
    .transformations(TrimTransparentEdges)
    .build()

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
