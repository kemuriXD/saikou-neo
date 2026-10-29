package ani.saikou.neo.tv.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.aspectRatio
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
internal const val RAIL_LOGO_TAG = "rail-logo"
internal const val RAIL_AVATAR_TAG = "rail-avatar"

internal fun railTag(section: Section) = "rail-${section.name.lowercase()}"

/** The parts of the app the rail moves between, top to bottom. The profile, which holds the library, shows a picture. */
enum class Section(
    @param:StringRes val label: Int,
) {
    SEARCH(R.string.section_search),
    HOME(R.string.section_home),
    DISCOVER(R.string.section_discover),
    SCHEDULE(R.string.section_schedule),
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
                SETTINGS -> NeoIcons.Settings
                PROFILE -> null
            }
}

// From the design: 72 dp of icons, opening to 240 dp with their names. The icons, logo and picture stay put as it
// opens. The logo and the picture are the same size, the same way in from each end.
val RailWidth = 72.dp
private val OpenWidth = 240.dp
private val RailPaddingX = 14.dp
private val ItemHeight = 44.dp
private val ItemGap = 8.dp
private val ItemIcon = 20.dp
private val ItemPadding = 12.dp
private val LabelGap = 14.dp
private val PictureSize = 36.dp
private val PicturePadding = 4.dp

// The logo's artwork is 246 wide by 228 tall.
private const val LOGO_ASPECT = 246f / 228f
private val MarkWidth = 4.dp
private val MarkHeight = 12.dp
private val DotSize = 6.dp
private val ShadowWidth = 40.dp
private const val SCRIM_ALPHA = 0.55f
private val FocusGlowColor = Color.White.copy(alpha = 0.32f)
private val ShadowColor = Color.Black.copy(alpha = 0.4f)

// Until signing in brings the account's picture.
private val AvatarColors = listOf(Color(0xFF5F4BB6), Color(0xFFFF4F91))

private val PlaceholderTop = 40.dp
private val PlaceholderGap = 6.dp

/**
 * The rail down the left of Home, with Home marked. Focus coming in lands on Home and opens the rail, with names and the
 * screen dimmed behind it. Selecting another section calls [onSelect]. Right, Back or selecting Home calls [onClose] to
 * send focus back to the screen.
 */
@Composable
fun NeoRail(
    onSelect: (Section) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    var open by remember { mutableStateOf(false) }
    val home = remember { FocusRequester() }
    // Back comes through the activity, not as a key, and is the rail's while it's open.
    BackHandler(enabled = open, onBack = onClose)
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
        // The logo at the top and the picture at the bottom, with the sections centred between them.
        Column(
            Modifier
                .testTag(RAIL_TAG)
                .width(width)
                .fillMaxHeight()
                .background(background)
                .onFocusChanged { open = it.hasFocus }
                .onPreviewKeyEvent {
                    if (it.key != Key.DirectionRight) return@onPreviewKeyEvent false
                    if (it.type == KeyEventType.KeyDown) onClose()
                    true
                }.focusProperties { onEnter = { home.requestFocus() } }
                .focusGroup()
                .padding(horizontal = RailPaddingX, vertical = dimensions.safeY),
        ) {
            // The logo is as tall as the picture and centred over it, so the gaps either side of the sections match.
            Row(
                Modifier.height(ItemHeight),
                horizontalArrangement = Arrangement.spacedBy(LabelGap - PicturePadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(ItemHeight), contentAlignment = Alignment.Center) {
                    Image(
                        painterResource(R.drawable.saikou_neo_symbol),
                        contentDescription = null,
                        modifier = Modifier.testTag(RAIL_LOGO_TAG).height(PictureSize).aspectRatio(LOGO_ASPECT),
                    )
                }
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
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(ItemGap)) {
                Section.entries.filter { it != Section.PROFILE }.forEach { RailItem(it, open, home, onSelect, onClose) }
            }
            Spacer(Modifier.weight(1f))
            RailItem(Section.PROFILE, open, home, onSelect, onClose)
        }
    }
}

/**
 * One section: its icon, and its name while the rail is [open]. Home, the one showing, has a soft fill and a pink mark,
 * and selecting it closes the rail.
 */
@Composable
private fun RailItem(
    section: Section,
    open: Boolean,
    home: FocusRequester,
    onSelect: (Section) -> Unit,
    onClose: () -> Unit,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val selected = section == Section.HOME
    Box(contentAlignment = Alignment.CenterStart) {
        // Closed, the mark sits on the rail's edge beside Home.
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
            onClick = { if (selected) onClose() else onSelect(section) },
            modifier =
                Modifier
                    .testTag(railTag(section))
                    .then(if (selected) Modifier.focusRequester(home) else Modifier)
                    .fillMaxWidth()
                    .height(ItemHeight),
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
            // The picture fills the closed item, 4 dp in all round, where an icon has 12 dp either side.
            val padding = if (icon == null) PicturePadding else ItemPadding
            Row(
                Modifier.fillMaxSize().padding(horizontal = padding),
                horizontalArrangement = Arrangement.spacedBy(LabelGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon == null) {
                    Box(
                        Modifier
                            .testTag(RAIL_AVATAR_TAG)
                            .size(PictureSize)
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

/** A section whose screen isn't built yet: its name and a line saying it's coming. */
@Composable
fun SectionPlaceholder(section: Section) {
    val colors = NeoTheme.colors
    Box(Modifier.fillMaxSize().background(colors.ink)) {
        Column(
            Modifier.padding(start = NeoTheme.dimensions.gridMargin, top = PlaceholderTop),
            verticalArrangement = Arrangement.spacedBy(PlaceholderGap),
        ) {
            Text(stringResource(section.label), style = NeoTheme.typography.headline, color = colors.paper)
            Text(stringResource(R.string.section_coming_soon), style = NeoTheme.typography.bodyLarge, color = colors.textMuted)
        }
    }
}
