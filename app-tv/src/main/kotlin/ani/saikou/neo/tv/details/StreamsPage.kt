package ani.saikou.neo.tv.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.shared.playback.BestPick
import ani.saikou.neo.shared.playback.FoundServer
import ani.saikou.neo.shared.playback.ServerEdition
import ani.saikou.neo.shared.playback.ServersState
import ani.saikou.neo.shared.playback.SourceAnswer
import ani.saikou.neo.shared.playback.SourceServers
import ani.saikou.neo.shared.playback.StreamsOutcome
import ani.saikou.neo.shared.settings.SubOrDub
import ani.saikou.neo.shared.sources.InstalledSource
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoIcons
import ani.saikou.neo.tv.ui.NeoTab
import ani.saikou.neo.tv.ui.SourceLogo
import java.io.File

internal const val STREAMS_BEST_TAG = "streams-best"

internal fun serverTag(
    sourceId: String,
    serverId: String,
) = "streams-server-$sourceId-$serverId"

/** The chip for the source with [sourceId], or All for null. */
internal fun sourceChipTag(sourceId: String?) = "streams-chip-${sourceId ?: "all"}"

// From the design: a 340 dp column for the best pick, 28 dp, then 476 dp of servers.
private val PickWidth = 340.dp
private val ColumnGap = 28.dp
private val ServersWidth = 476.dp
private val RowHeight = 44.dp
private val ChipHeight = 28.dp
private val PickLogo = 28.dp
private val HeadingLogo = 20.dp
private const val ROW_FOCUS_SCALE = 1.02f

/**
 * An episode's streams: the best pick on the left, with chips to show one source, and each source's servers on the
 * right. Sources still looking say so while the others can be picked. When nothing can play, it says why and offers
 * the next step through [actions]. [title] names the show where a source couldn't find it. Focus stays on the page.
 */
@Composable
internal fun StreamsPage(
    state: ServersState,
    episode: Int,
    title: String,
    onPick: (InstalledSource, FoundServer) -> Unit,
    onFilter: (String?) -> Unit,
    actions: StreamsActions,
    modifier: Modifier = Modifier,
) {
    val page = modifier.focusProperties { onExit = { cancelFocusChange() } }.focusGroup()
    when (val outcome = state.outcome) {
        StreamsOutcome.NoSources -> NoSources(episode, actions.onAddSource, page)
        StreamsOutcome.NothingPlays -> NothingPlays(state, title, actions, page)
        is StreamsOutcome.MissingEdition -> MissingEdition(episode, outcome.missing, outcome.have, actions.onBack, page)
        null -> ServersList(state, title, onPick, onFilter, modifier)
    }
}

/** The servers to pick from. Focus starts on the best pick once there is one, unless the viewer has moved by then. */
@Composable
private fun ServersList(
    state: ServersState,
    title: String,
    onPick: (InstalledSource, FoundServer) -> Unit,
    onFilter: (String?) -> Unit,
    modifier: Modifier,
) {
    val dimensions = NeoTheme.dimensions
    val bestFocus = remember { FocusRequester() }
    val allFocus = remember { FocusRequester() }
    var moved by remember { mutableStateOf(false) }
    val best = state.best
    LaunchedEffect(best != null) {
        if (moved) return@LaunchedEffect
        if (best != null) bestFocus.requestFocus() else allFocus.requestFocus()
    }
    Row(
        modifier
            .onPreviewKeyEvent {
                moved = true
                false
            }.focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
    ) {
        Column(Modifier.width(PickWidth), verticalArrangement = Arrangement.spacedBy(dimensions.space3)) {
            if (best != null) {
                BestPickCard(best, onClick = { onPick(best.source, best.server) }, Modifier.focusRequester(bestFocus))
            }
            Chips(state, onFilter, allFocus)
            Text(text = state.status(), style = NeoTheme.typography.caption, color = NeoTheme.colors.textMuted)
        }
        LazyColumn(
            modifier = Modifier.width(ServersWidth),
            verticalArrangement = Arrangement.spacedBy(dimensions.space2),
            contentPadding = PaddingValues(bottom = dimensions.space6),
        ) {
            for (entry in state.shown) sourceServers(entry, state, title, onPick)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sourceServers(
    entry: SourceServers,
    state: ServersState,
    title: String,
    onPick: (InstalledSource, FoundServer) -> Unit,
) {
    val source = entry.source
    when (val answer = entry.answer) {
        SourceAnswer.Looking -> {
            item(key = "looking-${source.id}") {
                Heading(stringResource(R.string.streams_still_looking, source.name), looking = true)
                Box(
                    Modifier
                        .padding(top = NeoTheme.dimensions.space2)
                        .fillMaxWidth()
                        .height(RowHeight)
                        .background(NeoTheme.colors.surfaceRaised, RoundedCornerShape(NeoTheme.dimensions.radiusLg)),
                )
            }
        }

        is SourceAnswer.Failed -> {
            item(key = "failed-${source.id}") { Quiet(source, whyNothing(answer, title, state.subOrDub)) }
        }

        is SourceAnswer.Found -> {
            if (answer.servers.isEmpty()) {
                item(key = "none-${source.id}") { Quiet(source, whyNothing(answer, title, state.subOrDub)) }
            } else {
                item(key = "heading-${source.id}") {
                    Heading(
                        pluralStringResource(R.plurals.streams_source_servers, answer.servers.size, source.name, answer.servers.size),
                        logo = source,
                    )
                }
                items(answer.servers, key = { "server-${source.id}-${it.id}" }) { server ->
                    val used = state.lastUsed?.isServer(source, server) == true
                    ServerRow(server, used, onClick = { onPick(source, server) }, Modifier.testTag(serverTag(source.id, server.id)))
                }
            }
        }
    }
}

@Composable
private fun BestPickCard(
    best: BestPick,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag(STREAMS_BEST_TAG),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colors.surfaceScrim,
                contentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.focus,
                pressedContentColor = colors.onFocus,
            ),
        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.5.dp, colors.pink), shape = shape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = ROW_FOCUS_SCALE),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.White.copy(alpha = 0.28f), dimensions.glowFocus)),
    ) {
        Column(Modifier.padding(dimensions.space4), verticalArrangement = Arrangement.spacedBy(dimensions.space2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimensions.space2), verticalAlignment = Alignment.CenterVertically) {
                Icon(NeoIcons.Star, contentDescription = null, tint = colors.pink, modifier = Modifier.size(14.dp))
                Text(
                    text = stringResource(R.string.streams_best_pick).uppercase(),
                    style = NeoTheme.typography.overline.copy(fontWeight = FontWeight.Bold),
                    color = colors.pink,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(dimensions.space3), verticalAlignment = Alignment.CenterVertically) {
                val source = best.source
                SourceLogo(
                    source.name,
                    source.icon?.let(::File),
                    PickLogo,
                    radius = 7.dp,
                    background = colors.surfaceRaised,
                    letterSize = 14.sp,
                )
                Text(
                    text = stringResource(R.string.streams_best_server, source.name, best.server.name),
                    style = NeoTheme.typography.rowTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = best.why(),
                style = NeoTheme.typography.caption,
                color = LocalContentColor.current.copy(alpha = MUTED),
            )
        }
    }
}

/** Such as "Sub · last used, answered first". */
@Composable
private fun BestPick.why(): String {
    val edition = editionName(server.edition)
    val used = stringResource(R.string.streams_last_used)
    val first = stringResource(R.string.streams_answered_first)
    val reasons =
        when {
            lastUsed && answeredFirst -> stringResource(R.string.streams_reasons, used, first)
            lastUsed -> used
            answeredFirst -> first
            else -> return edition
        }
    return stringResource(R.string.streams_why, edition, reasons)
}

@Composable
private fun Chips(
    state: ServersState,
    onFilter: (String?) -> Unit,
    allFocus: FocusRequester,
) {
    val description = stringResource(R.string.streams_from)
    // By name rather than in the order they answered, so the chips don't move about while sources answer.
    val sources = state.sources.map { it.source }.sortedBy { it.name.lowercase() }
    val sourceFocus = remember(sources) { sources.associate { it.id to FocusRequester() } }
    // Up or down into the chips lands on the one chosen, as on the tabs.
    Row(
        Modifier
            .semantics { contentDescription = description }
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
                        (state.filter?.let { sourceFocus[it] } ?: allFocus).requestFocus()
                    }
                }
            }.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space2),
    ) {
        NeoTab(
            text = stringResource(R.string.streams_all),
            selected = state.filter == null,
            onClick = { onFilter(null) },
            modifier = Modifier.focusRequester(allFocus).testTag(sourceChipTag(null)),
            height = ChipHeight,
        )
        for (source in sources) {
            NeoTab(
                text = source.name,
                selected = state.filter == source.id,
                onClick = { onFilter(source.id) },
                modifier = Modifier.focusRequester(sourceFocus.getValue(source.id)).testTag(sourceChipTag(source.id)),
                height = ChipHeight,
            )
        }
    }
}

/** Such as "Sub first, from Settings. 1 of 2 sources has answered." With one source, only the setting. */
@Composable
private fun ServersState.status(): String {
    val setting =
        stringResource(
            when (subOrDub) {
                SubOrDub.BOTH -> R.string.streams_setting_both
                SubOrDub.SUB -> R.string.streams_setting_sub
                SubOrDub.DUB -> R.string.streams_setting_dub
            },
        )
    if (sources.size < 2) return setting
    return setting + " " + pluralStringResource(R.plurals.streams_answered, answered, answered, sources.size)
}

/** A source's heading: a still ring while it's [looking], else the [logo] of the source it names, if given. */
@Composable
private fun Heading(
    text: String,
    looking: Boolean = false,
    logo: InstalledSource? = null,
) {
    val colors = NeoTheme.colors
    Row(
        Modifier.padding(top = NeoTheme.dimensions.space2),
        horizontalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (looking) {
            // A still ring, as on the board. Nothing spins, to keep slow TVs smooth.
            Box(Modifier.size(10.dp).border(2.dp, colors.textMuted, CircleShape))
        }
        logo?.let {
            SourceLogo(
                it.name,
                it.icon?.let(::File),
                HeadingLogo,
                radius = 5.dp,
                background = colors.surfaceRaised,
                letterSize = 12.sp,
            )
        }
        Text(
            text = text,
            style = NeoTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = colors.textMuted,
        )
    }
}

/** A source with nothing to pick: its name and why. */
@Composable
private fun Quiet(
    source: InstalledSource,
    why: String,
) = Column {
    Heading(source.name, logo = source)
    Text(text = why, style = NeoTheme.typography.caption, color = NeoTheme.colors.textFaint)
}

@Composable
private fun ServerRow(
    server: FoundServer,
    used: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(RowHeight),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(dimensions.radiusLg)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colors.surfaceScrim,
                contentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.focus,
                pressedContentColor = colors.onFocus,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = ROW_FOCUS_SCALE),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.White.copy(alpha = 0.28f), dimensions.glowFocus)),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp).height(RowHeight),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = server.name,
                style = NeoTheme.typography.label.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val muted = LocalContentColor.current.copy(alpha = MUTED)
            if (used) {
                // In the row's own colour, so it reads on paper when focused as well as on the dark row.
                Text(text = stringResource(R.string.streams_used), style = NeoTheme.typography.caption.copy(fontWeight = FontWeight.Bold))
            }
            Text(
                text = editionName(server.edition).uppercase(),
                style = NeoTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                modifier =
                    Modifier
                        .background(LocalContentColor.current.copy(alpha = PILL), CircleShape)
                        .padding(horizontal = dimensions.space2, vertical = 2.dp),
            )
            server.runtimeMinutes?.let {
                Text(text = stringResource(R.string.details_minutes, it), style = NeoTheme.typography.caption, color = muted)
            }
        }
    }
}

@Composable
internal fun editionName(edition: ServerEdition) =
    stringResource(
        when (edition) {
            ServerEdition.SUB -> R.string.streams_sub
            ServerEdition.DUB -> R.string.streams_dub
            ServerEdition.HARD_SUB -> R.string.streams_hard_sub
        },
    )

private const val MUTED = 0.72f
private const val PILL = 0.14f
