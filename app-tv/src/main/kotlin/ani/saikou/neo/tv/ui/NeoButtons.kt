package ani.saikou.neo.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.tv.theme.NeoTheme

// From the design: buttons are 44 dp pills, 16 dp apart. Dialog buttons are at least 48 dp and 10 dp apart.
private val ButtonHeight = 44.dp
private val ButtonPadding = 22.dp
private val ButtonIconGap = 10.dp
private val ButtonIcon = 14.dp
private val IconButtonIcon = 20.dp
private val DialogButtonHeight = 48.dp
private val DialogButtonPadding = 18.dp
private val DialogButtonGap = 10.dp
private val TabHeight = 40.dp
private val TabGlow = 14.dp

/** How far a tab's label sits inside its pill. */
private val NeoTabPadding = 18.dp

// Whole lines, so a two-line dialog button is the design's height.
private val FullLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

// The second line of a dialog button, dimmer than the first on both fills.
private const val SUPPORTING_ALPHA = 0.72f

// From the design: section tabs are 36 dp tall, with a 2 dp line under the current one. Focus thickens it to 3 dp and
// makes it glow.
private val SectionTabHeight = 36.dp
private val SectionLine = 2.dp
private val SectionLineFocused = 3.dp
private val SectionGlow = 10.dp
private const val SECTION_LINE_ALPHA = 0.52f
private val SectionGlowColor = Color.White.copy(alpha = 0.75f)

private val FocusGlowColor = Color.White.copy(alpha = 0.32f)
private val TabGlowColor = Color.White.copy(alpha = 0.16f)

/** The main action, such as "Resume episode 7": a pill with words and at most one icon. */
@Composable
fun NeoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    // After the words, such as See more's arrow.
    trailingIcon: ImageVector? = null,
) {
    NeoSurface(onClick, CircleShape, NeoTheme.dimensions.focusScaleButton, modifier.height(ButtonHeight)) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = ButtonPadding),
            horizontalArrangement = Arrangement.spacedBy(ButtonIconGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(ButtonIcon)) }
            Text(text = text, style = NeoTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1)
            trailingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(ButtonIcon)) }
        }
    }
}

/** Any other action, such as Add to list or Details: a round button with an icon that TalkBack reads out. */
@Composable
fun NeoIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NeoSurface(onClick, CircleShape, NeoTheme.dimensions.focusScaleButton, modifier.size(ButtonHeight)) {
        Box(Modifier.size(ButtonHeight), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(IconButtonIcon))
        }
    }
}

/** Buttons side by side, the design's distance apart. */
@Composable
fun NeoButtonRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) = Row(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space4),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
)

/** A dialog's choices, stacked and as wide as the dialog. */
@Composable
fun NeoDialogButtons(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Column(modifier, verticalArrangement = Arrangement.spacedBy(DialogButtonGap), content = content)

/** One choice in a dialog. [supportingText] adds a dimmer second line, such as when the progress was saved. */
@Composable
fun NeoDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
) {
    val dimensions = NeoTheme.dimensions
    NeoSurface(
        onClick,
        RoundedCornerShape(dimensions.radiusLg),
        dimensions.focusScale,
        modifier.fillMaxWidth().heightIn(min = DialogButtonHeight),
    ) {
        Row(
            Modifier.heightIn(min = DialogButtonHeight).padding(horizontal = DialogButtonPadding, vertical = dimensions.space3),
            horizontalArrangement = Arrangement.spacedBy(dimensions.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(ButtonIcon)) }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = text, style = NeoTheme.typography.labelLarge.copy(lineHeightStyle = FullLine))
                supportingText?.let {
                    Text(
                        text = it,
                        style = NeoTheme.typography.body.copy(lineHeightStyle = FullLine),
                        color = LocalContentColor.current.copy(alpha = SUPPORTING_ALPHA),
                    )
                }
            }
        }
    }
}

/**
 * A tab, in the nav bar or within a screen. The current one is a soft pill with a faint glow; the others are just
 * their label. Focus fills either with paper, as on a button.
 */
@Composable
fun NeoTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = TabHeight,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val focusedGlow = Glow(FocusGlowColor, dimensions.glowFocus)
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(height).semantics { role = Role.Tab },
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
        scale =
            SelectableSurfaceDefaults.scale(
                focusedScale = dimensions.focusScaleButton,
                focusedSelectedScale = dimensions.focusScaleButton,
            ),
        glow =
            SelectableSurfaceDefaults.glow(
                focusedGlow = focusedGlow,
                selectedGlow = Glow(TabGlowColor, TabGlow),
                focusedSelectedGlow = focusedGlow,
            ),
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = NeoTabPadding), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = NeoTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

/**
 * A section of a screen, such as Details' Episodes: just its label. The current one is bold with a faint line under it,
 * and focus turns the line paper and makes it glow. Unlike [NeoTab], it doesn't look like the nav bar's screens.
 */
@Composable
fun NeoSectionTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = SectionTabHeight,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val focus by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(dimensions.durationIndicatorMillis, easing = dimensions.easeStandard),
        label = "focus",
    )
    // The line sits beside the tab rather than in it, so the tab's clip doesn't cut its glow off.
    Box(Modifier.height(height)) {
        Surface(
            selected = selected,
            onClick = {},
            modifier = modifier.fillMaxHeight().semantics { role = Role.Tab },
            shape = SelectableSurfaceDefaults.shape(RectangleShape),
            colors =
                SelectableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = colors.textMuted,
                    focusedContainerColor = Color.Transparent,
                    focusedContentColor = colors.paper,
                    pressedContainerColor = Color.Transparent,
                    pressedContentColor = colors.paper,
                    selectedContainerColor = Color.Transparent,
                    selectedContentColor = colors.paper,
                    focusedSelectedContainerColor = Color.Transparent,
                    focusedSelectedContentColor = colors.paper,
                    pressedSelectedContainerColor = Color.Transparent,
                    pressedSelectedContentColor = colors.paper,
                ),
            scale =
                SelectableSurfaceDefaults.scale(
                    focusedScale = 1f,
                    pressedScale = 1f,
                    focusedSelectedScale = 1f,
                    pressedSelectedScale = 1f,
                ),
            interactionSource = interactions,
        ) {
            Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(
                    text = text,
                    style = NeoTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
        if (selected || focused) {
            val thickness = SectionLine + (SectionLineFocused - SectionLine) * focus
            Box(
                Modifier
                    .matchParentSize()
                    .wrapContentHeight(Alignment.Bottom)
                    .padding(bottom = 1.dp)
                    .height(thickness)
                    .outerGlow(SectionGlowColor.copy(alpha = SectionGlowColor.alpha * focus), SectionGlow, thickness)
                    .background(lerp(colors.paper.copy(alpha = SECTION_LINE_ALPHA), colors.paper, focus), CircleShape),
            )
        }
    }
}

/** What every button shares: a tonal fill at rest; paper, a glow and growth on focus. */
@Composable
private fun NeoSurface(
    onClick: () -> Unit,
    shape: Shape,
    focusedScale: Float,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val colors = NeoTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colors.fillTonal,
                contentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.focus,
                pressedContentColor = colors.onFocus,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlowColor, NeoTheme.dimensions.glowFocus)),
    ) {
        content()
    }
}
