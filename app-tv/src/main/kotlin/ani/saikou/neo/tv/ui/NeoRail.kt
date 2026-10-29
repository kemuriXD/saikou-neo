package ani.saikou.neo.tv.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme

internal const val RAIL_TAG = "rail"
internal const val RAIL_SCRIM_TAG = "rail-scrim"

internal fun railTag(section: Section) = "rail-${section.name.lowercase()}"

/** The parts of the app the rail moves between, top to bottom. The profile shows the account's picture, not an icon. */
enum class Section(
    @param:StringRes val label: Int,
) {
    SEARCH(R.string.section_search),
    HOME(R.string.section_home),
    DISCOVER(R.string.section_discover),
    SCHEDULE(R.string.section_schedule),
    LIBRARY(R.string.section_library),
    SETTINGS(R.string.section_settings),
    PROFILE(R.string.section_profile),
    ;

    val icon: ImageVector?
        get() =
            when (this) {
                SEARCH -> NeoIcons.Search
                HOME -> NeoIcons.Home
                DISCOVER -> NeoIcons.Discover
                SCHEDULE -> NeoIcons.Schedule
                LIBRARY -> NeoIcons.Library
                SETTINGS -> NeoIcons.Settings
                PROFILE -> null
            }
}

// From the design: 72 dp of icons, opening to 240 dp with their names. The icons and logo stay put as it opens.
val RailWidth = 72.dp
private val OpenWidth = 240.dp
private val RailPaddingX = 14.dp
private val ItemHeight = 44.dp
private val ItemGap = 8.dp
private val ItemIcon = 20.dp
private val ItemPadding = 12.dp
private val LabelGap = 14.dp
private val LogoSize = 32.dp
private val LogoPadding = 6.dp
private val LogoGap = 30.dp
private val AvatarSize = 36.dp
private val AvatarPadding = 4.dp
private val MarkWidth = 4.dp
private val MarkHeight = 12.dp
private val DotSize = 6.dp
private val ShadowWidth = 40.dp
private const val SCRIM_ALPHA = 0.55f
private val FocusGlowColor = Color.White.copy(alpha = 0.32f)
private val ShadowColor = Color.Black.copy(alpha = 0.4f)

// Until signing in brings the account's picture.
private val AvatarColors = listOf(Color(0xFF5F4BB6), Color(0xFFFF4F91))

// A section's name sits beside the open rail, so it shows while focus is on the rail.
private val PlaceholderTop = 88.dp
private val PlaceholderGap = 6.dp

/**
 * The rail down the left of the screen, with the [current] section marked. Focus coming in lands on the current section
 * and opens the rail, with names and the screen dimmed behind it. Selecting a section calls [onSelect]. Right or Back
 * calls [onClose] to send focus back to the screen; it returns false when the screen has nothing to take it, and then
 * Back is left to leave the section.
 */
@Composable
fun NeoRail(
    current: Section,
    onSelect: (Section) -> Unit,
    onClose: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    var open by remember { mutableStateOf(false) }
    // A Back press the rail used, so its release doesn't go on to leave the screen.
    var tookBack by remember { mutableStateOf(false) }
    val currentItem = remember { FocusRequester() }
    val animation = tween<Float>(dimensions.durationPanelMillis, easing = dimensions.easeStandard)
    val width by animateDpAsState(
        if (open) OpenWidth else RailWidth,
        tween(dimensions.durationPanelMillis, easing = dimensions.easeStandard),
    )
    val opened by animateFloatAsState(if (open) 1f else 0f, animation)
    Box(modifier.fillMaxSize()) {
        if (opened > 0f) {
            Box(
                Modifier
                    .testTag(RAIL_SCRIM_TAG)
                    .fillMaxSize()
                    .alpha(opened)
                    .background(colors.ink.copy(alpha = SCRIM_ALPHA)),
            )
            Box(
                Modifier
                    .offset { IntOffset(width.roundToPx(), 0) }
                    .width(ShadowWidth)
                    .fillMaxHeight()
                    .alpha(opened)
                    .background(Brush.horizontalGradient(listOf(ShadowColor, Color.Transparent))),
            )
        }
        val background =
            if (open) {
                Brush.horizontalGradient(0f to colors.ink, 0.8f to colors.ink.copy(alpha = 0.96f), 1f to colors.ink.copy(alpha = 0.9f))
            } else {
                Brush.horizontalGradient(listOf(colors.ink.copy(alpha = 0.9f), colors.ink.copy(alpha = 0f)))
            }
        Column(
            Modifier
                .testTag(RAIL_TAG)
                .width(width)
                .fillMaxHeight()
                .background(background)
                .onFocusChanged { open = it.hasFocus }
                .onPreviewKeyEvent {
                    when {
                        it.key == Key.DirectionRight -> {
                            if (it.type == KeyEventType.KeyDown) onClose()
                            true
                        }

                        it.key == Key.Back && it.type == KeyEventType.KeyDown -> {
                            onClose().also { closed -> tookBack = closed }
                        }

                        it.key == Key.Back && it.type == KeyEventType.KeyUp -> {
                            tookBack.also { tookBack = false }
                        }

                        else -> {
                            false
                        }
                    }
                }.focusProperties { onEnter = { currentItem.requestFocus() } }
                .focusGroup()
                .padding(horizontal = RailPaddingX, vertical = dimensions.safeY),
            verticalArrangement = Arrangement.spacedBy(ItemGap),
        ) {
            Row(
                Modifier.padding(start = LogoPadding, bottom = LogoGap - ItemGap),
                horizontalArrangement = Arrangement.spacedBy(ItemPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.saikou_neo_symbol), contentDescription = null, modifier = Modifier.size(LogoSize))
                if (open) {
                    Text(
                        stringResource(R.string.app_name),
                        style = NeoTheme.typography.title.copy(fontSize = NeoTheme.typography.label.fontSize, fontWeight = FontWeight.Bold),
                        color = colors.paper,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Section.entries.forEach { section ->
                if (section == Section.SETTINGS) Spacer(Modifier.weight(1f))
                RailItem(
                    section,
                    selected = section == current,
                    open = open,
                    onClick = { onSelect(section) },
                    modifier =
                        Modifier
                            .testTag(railTag(section))
                            .then(if (section == current) Modifier.focusRequester(currentItem) else Modifier),
                )
            }
        }
    }
}

/** One section: its icon, and its name while the rail is [open]. The current one has a soft fill and a pink mark. */
@Composable
private fun RailItem(
    section: Section,
    selected: Boolean,
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    Box(contentAlignment = Alignment.CenterStart) {
        // Closed, the mark sits on the rail's edge beside the current section.
        if (selected && !open) {
            Box(
                Modifier
                    .offset(x = -RailPaddingX)
                    .size(MarkWidth, MarkHeight)
                    .background(colors.pink, RoundedCornerShape(MarkWidth / 2)),
            )
        }
        Surface(
            selected = selected,
            onClick = onClick,
            modifier = modifier.fillMaxWidth().height(ItemHeight),
            shape = SelectableSurfaceDefaults.shape(CircleShape),
            colors =
                SelectableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = colors.textMuted,
                    focusedContainerColor = colors.focus,
                    focusedContentColor = colors.onFocus,
                    pressedContainerColor = colors.focus,
                    pressedContentColor = colors.onFocus,
                    selectedContainerColor = colors.fillTonal,
                    selectedContentColor = colors.paper,
                    focusedSelectedContainerColor = colors.focus,
                    focusedSelectedContentColor = colors.onFocus,
                    pressedSelectedContainerColor = colors.focus,
                    pressedSelectedContentColor = colors.onFocus,
                ),
            scale = SelectableSurfaceDefaults.scale(focusedScale = dimensions.focusScale, focusedSelectedScale = dimensions.focusScale),
            glow =
                SelectableSurfaceDefaults.glow(
                    focusedGlow = Glow(FocusGlowColor, dimensions.glowFocus),
                    focusedSelectedGlow = Glow(FocusGlowColor, dimensions.glowFocus),
                ),
        ) {
            val icon = section.icon
            Row(
                Modifier.fillMaxSize().padding(start = if (icon == null) AvatarPadding else ItemPadding, end = ItemPadding),
                horizontalArrangement = Arrangement.spacedBy(LabelGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon == null) {
                    Box(
                        Modifier
                            .size(AvatarSize)
                            .background(Brush.linearGradient(AvatarColors), CircleShape)
                            .border(2.dp, colors.outline, CircleShape),
                    )
                } else {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(ItemIcon))
                }
                if (open) {
                    Text(
                        stringResource(section.label),
                        style = NeoTheme.typography.bodyLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Box(Modifier.size(DotSize).background(colors.pink, CircleShape))
                }
            }
        }
    }
}

/** A section whose screen isn't built yet: its name and a line saying it's coming, beside the open rail. */
@Composable
fun SectionPlaceholder(section: Section) {
    val colors = NeoTheme.colors
    Box(Modifier.fillMaxSize().background(colors.ink)) {
        Column(
            Modifier.padding(start = OpenWidth + NeoTheme.dimensions.gridMargin, top = PlaceholderTop),
            verticalArrangement = Arrangement.spacedBy(PlaceholderGap),
        ) {
            Text(stringResource(section.label), style = NeoTheme.typography.headline, color = colors.paper)
            Text(stringResource(R.string.section_coming_soon), style = NeoTheme.typography.bodyLarge, color = colors.textMuted)
        }
    }
}
