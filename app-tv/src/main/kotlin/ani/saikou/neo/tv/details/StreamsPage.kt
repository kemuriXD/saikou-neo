package ani.saikou.neo.tv.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import ani.saikou.neo.shared.playback.FailReason
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
import ani.saikou.neo.tv.ui.NeoButton
import ani.saikou.neo.tv.ui.NeoIcons
import ani.saikou.neo.tv.ui.NeoTab
import ani.saikou.neo.tv.ui.SourceLogo
import java.io.File

internal const val STREAMS_RETRY_TAG = "streams-retry"

internal fun sourceTag(sourceId: String) = "streams-source-$sourceId"

internal fun serverTag(
    sourceId: String,
    serverId: String,
) = "streams-server-$sourceId-$serverId"

/** The pill that shows [subOrDub]: All for both. */
internal fun editionTag(subOrDub: SubOrDub) = "streams-edition-${subOrDub.name.lowercase()}"

// From the design: a 320 dp list of sources, 40 dp, then 484 dp for the focused one's servers.
private val SourcesWidth = 320.dp
private val ColumnGap = 40.dp
private val ServersWidth = 484.dp
private val SourceHeight = 60.dp
private val SourceLogoSize = 36.dp
private val LogoRadius = 10.dp
private val LogoInset = 4.dp
private val ServerHeight = 40.dp
private val PillHeight = 30.dp
private val FadeHeight = 48.dp
private const val ROW_FOCUS_SCALE = 1.02f

/**
 * An episode's streams: every installed source on the left, in the order they're installed, with how it's doing, and
 * the servers of the one focused on the right, under All, Sub and Dub. A source that failed can be asked again with
 * [onRetry]. When nothing can play, it says why and offers the next step through [actions]. [title] names the show
 * where a source couldn't find it. Focus stays on the page.
 */
@Composable
internal fun StreamsPage(
    state: ServersState,
    episode: Int,
    title: String,
    onPick: (InstalledSource, FoundServer) -> Unit,
    onEdition: (SubOrDub) -> Unit,
    onRetry: (InstalledSource) -> Unit,
    actions: StreamsActions,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { actions.onOpen() }
    val page = modifier.focusProperties { onExit = { cancelFocusChange() } }.focusGroup()
    when (val outcome = state.outcome) {
        StreamsOutcome.NoSources -> NoSources(episode, actions.onAddSource, page)
        StreamsOutcome.NothingPlays -> NothingPlays(state, title, actions, page)
        is StreamsOutcome.MissingEdition -> MissingEdition(episode, outcome.missing, outcome.have, actions.onBack, page)
        null -> SourcesAndServers(state, title, onPick, onEdition, onRetry, page)
    }
}

/**
 * The sources on the left and the focused one's servers on the right. Focus starts on the first source. After Retry,
 * focus waits on the source and moves to its first server once it has one, unless the viewer has moved by then.
 */
@Composable
private fun SourcesAndServers(
    state: ServersState,
    title: String,
    onPick: (InstalledSource, FoundServer) -> Unit,
    onEdition: (SubOrDub) -> Unit,
    onRetry: (InstalledSource) -> Unit,
    modifier: Modifier,
) {
    var currentId by rememberSaveable { mutableStateOf<String?>(null) }
    val current = state.sources.firstOrNull { it.source.id == currentId } ?: state.sources.firstOrNull()
    val sourceFocus = remember { mutableMapOf<String, FocusRequester>() }

    fun focusOf(id: String) = sourceFocus.getOrPut(id) { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    var moved by remember { mutableStateOf(false) }
    var retried by remember { mutableStateOf<String?>(null) }
    var inPanel by remember { mutableStateOf(false) }
    val sourcesDescription = stringResource(R.string.streams_sources)

    LaunchedEffect(current != null) {
        if (!moved) current?.let { focusOf(it.source.id).requestFocus() }
    }
    val shown = current?.let { state.groups(it).all }.orEmpty()
    LaunchedEffect(retried, shown.isNotEmpty()) {
        if (retried != null && retried == current?.source?.id && shown.isNotEmpty()) {
            retried = null
            panelFocus.requestFocus()
        }
    }

    Row(
        modifier.onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown) {
                moved = true
                retried = null
            }
            false
        },
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
    ) {
        Box(Modifier.width(SourcesWidth).fillMaxHeight()) {
            LazyColumn(
                modifier =
                    Modifier
                        .semantics { contentDescription = sourcesDescription }
                        .focusProperties {
                            onEnter = {
                                if (requestedFocusDirection == FocusDirection.Left) current?.let { focusOf(it.source.id).requestFocus() }
                            }
                        }.focusGroup(),
                verticalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space2),
                contentPadding = PaddingValues(top = 4.dp, bottom = FadeHeight),
            ) {
                items(state.sources, key = { it.source.id }) { entry ->
                    val id = entry.source.id
                    SourceRow(
                        entry,
                        state,
                        title,
                        chosen = inPanel && id == current?.source?.id,
                        onClick = { panelFocus.requestFocus() },
                        modifier =
                            Modifier
                                .focusRequester(focusOf(id))
                                .onFocusChanged { if (it.isFocused) currentId = id }
                                .testTag(sourceTag(id)),
                    )
                }
            }
            val ink = NeoTheme.colors.ink
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(FadeHeight)
                    .background(Brush.verticalGradient(listOf(ink.copy(alpha = 0f), ink.copy(alpha = 0.95f)))),
            )
        }
        Box(
            Modifier
                .width(ServersWidth)
                .onFocusChanged { inPanel = it.hasFocus }
                .focusProperties {
                    onEnter = { if (requestedFocusDirection == FocusDirection.Right) panelFocus.requestFocus() }
                }.focusGroup(),
        ) {
            if (current != null) {
                Panel(
                    current,
                    state,
                    title,
                    panelFocus,
                    onPick,
                    onEdition,
                    onRetry = {
                        retried = current.source.id
                        focusOf(current.source.id).requestFocus()
                        onRetry(current.source)
                    },
                )
            }
        }
    }
}

/** The focused source's servers, or why it has none, with Retry when asking again could help. */
@Composable
private fun Panel(
    entry: SourceServers,
    state: ServersState,
    title: String,
    first: FocusRequester,
    onPick: (InstalledSource, FoundServer) -> Unit,
    onEdition: (SubOrDub) -> Unit,
    onRetry: () -> Unit,
) {
    val source = entry.source
    when (val answer = entry.answer) {
        SourceAnswer.Looking -> {
            Text(
                stringResource(R.string.streams_still_looking, source.name),
                style = NeoTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                color = NeoTheme.colors.textMuted,
                modifier = Modifier.padding(top = NeoTheme.dimensions.space2),
            )
        }

        is SourceAnswer.Failed -> {
            Trouble(source.name, failedText(answer, title), canRetry = answer.waitSeconds == null, first, onRetry)
        }

        is SourceAnswer.Found -> {
            if (answer.servers.isEmpty()) {
                Trouble(source.name, stringResource(R.string.streams_no_servers), canRetry = true, first, onRetry)
            } else {
                Servers(entry, state, title, first, onPick, onEdition)
            }
        }
    }
}

/** A source with nothing to pick: its name, why, and Retry when it can be asked again yet. */
@Composable
private fun Trouble(
    name: String,
    why: String,
    canRetry: Boolean,
    first: FocusRequester,
    onRetry: () -> Unit,
) {
    val colors = NeoTheme.colors
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(NeoIcons.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(20.dp))
            Text(name, style = NeoTheme.typography.titleLarge.copy(fontSize = 20.sp), color = colors.paper, maxLines = 1)
        }
        Text(why, style = NeoTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = colors.paper.copy(alpha = 0.8f))
        if (canRetry) {
            Text(
                stringResource(R.string.streams_retry_hint),
                style = NeoTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = colors.paper.copy(alpha = 0.8f),
            )
            Row(Modifier.padding(top = 6.dp)) {
                NeoButton(stringResource(R.string.streams_retry), onRetry, Modifier.focusRequester(first).testTag(STREAMS_RETRY_TAG))
            }
        }
    }
}

/** The All, Sub and Dub pills, then the servers they show: under Sub and Dub headings for All, as one list otherwise. */
@Composable
private fun Servers(
    entry: SourceServers,
    state: ServersState,
    title: String,
    first: FocusRequester,
    onPick: (InstalledSource, FoundServer) -> Unit,
    onEdition: (SubOrDub) -> Unit,
) {
    val source = entry.source
    val groups = state.groups(entry)
    val firstServer = groups.all.firstOrNull()
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space2),
        contentPadding = PaddingValues(bottom = NeoTheme.dimensions.space6),
    ) {
        item(key = "pills") { Pills(state.subOrDub, onEdition) }
        if (firstServer == null) {
            item(key = "none") {
                Text(
                    whyNothing(entry.answer, title, state.subOrDub),
                    style = NeoTheme.typography.caption,
                    color = NeoTheme.colors.textMuted,
                    modifier = Modifier.padding(top = NeoTheme.dimensions.space2),
                )
            }
        }

        fun LazyListScope.group(
            edition: ServerEdition,
            servers: List<FoundServer>,
        ) {
            if (servers.isEmpty()) return
            if (state.subOrDub == SubOrDub.BOTH) item(key = "heading-$edition") { GroupHeading(edition, servers.size) }
            items(servers, key = { "server-$edition-${it.id}" }) { server ->
                val tag = Modifier.testTag(serverTag(source.id, server.id))
                ServerRow(server, onClick = { onPick(source, server) }, if (server == firstServer) tag.focusRequester(first) else tag)
            }
        }
        group(ServerEdition.SUB, groups.sub)
        group(ServerEdition.DUB, groups.dub)
    }
}

@Composable
private fun Pills(
    selected: SubOrDub,
    onEdition: (SubOrDub) -> Unit,
) {
    val description = stringResource(R.string.streams_show)
    Row(
        Modifier.padding(vertical = 4.dp).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((edition, label) in listOf(
            SubOrDub.BOTH to R.string.streams_all,
            SubOrDub.SUB to R.string.streams_sub,
            SubOrDub.DUB to R.string.streams_dub,
        )) {
            NeoTab(
                text = stringResource(label),
                selected = selected == edition,
                onClick = { onEdition(edition) },
                modifier = Modifier.testTag(editionTag(edition)),
                height = PillHeight,
            )
        }
    }
}

/** Such as "SUB · 3". */
@Composable
private fun GroupHeading(
    edition: ServerEdition,
    count: Int,
) = Text(
    stringResource(R.string.streams_group, editionName(edition), count).uppercase(),
    style = NeoTheme.typography.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    color = NeoTheme.colors.textMuted,
    modifier = Modifier.padding(top = 6.dp),
)

/** One source on the left: its logo, name and how it's doing. [chosen] marks the one whose servers have focus. */
@Composable
private fun SourceRow(
    entry: SourceServers,
    state: ServersState,
    title: String,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    val shape = RoundedCornerShape(dimensions.radiusLg)
    val source = entry.source
    val failed = entry.answer is SourceAnswer.Failed
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(SourceHeight).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (chosen) colors.paper.copy(alpha = 0.16f) else colors.surfaceScrim,
                contentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.focus,
                pressedContentColor = colors.onFocus,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = if (chosen) Border(BorderStroke(1.5.dp, colors.outline), shape = shape) else Border.None,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = ROW_FOCUS_SCALE),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.White.copy(alpha = 0.3f), dimensions.glowFocus)),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // On a dark tile of its own, so a logo made for dark screens still shows when the row turns light on focus.
            Box(
                Modifier.size(SourceLogoSize).background(colors.surfaceRaised, RoundedCornerShape(LogoRadius)),
                contentAlignment = Alignment.Center,
            ) {
                SourceLogo(
                    source.name,
                    source.icon?.let(::File),
                    SourceLogoSize - LogoInset * 2,
                    radius = LogoRadius - LogoInset,
                    background = colors.surfaceRaised,
                    letterSize = 16.sp,
                    dimmed = failed,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    source.name,
                    style = NeoTheme.typography.label.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.status(state, title),
                    style = NeoTheme.typography.caption,
                    color =
                        when {
                            focused -> colors.onFocusMuted
                            failed -> colors.warning
                            else -> colors.textMuted
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Such as "Looking…", "5 servers" or why it failed. */
@Composable
private fun SourceServers.status(
    state: ServersState,
    title: String,
): String =
    when (val answer = answer) {
        SourceAnswer.Looking -> {
            stringResource(R.string.streams_looking)
        }

        is SourceAnswer.Failed -> {
            failedText(answer, title)
        }

        is SourceAnswer.Found -> {
            val count = state.groups(this).all.size
            if (count == 0) whyNothing(answer, title, state.subOrDub) else pluralStringResource(R.plurals.streams_servers, count, count)
        }
    }

/** Why [answer] failed, counting down a rate limit's wait while there is one. */
@Composable
private fun failedText(
    answer: SourceAnswer.Failed,
    title: String,
): String {
    val reason = answer.reason
    return failReasonText(if (reason is FailReason.RateLimited) FailReason.RateLimited(answer.waitSeconds) else reason, title)
}

@Composable
private fun ServerRow(
    server: FoundServer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(ServerHeight),
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
            Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = server.name,
                style = NeoTheme.typography.label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (server.edition == ServerEdition.HARD_SUB) {
                Text(
                    text = stringResource(R.string.streams_hard_sub),
                    style = NeoTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                    modifier =
                        Modifier
                            .background(LocalContentColor.current.copy(alpha = PILL), CircleShape)
                            .padding(horizontal = dimensions.space2, vertical = 2.dp),
                )
            }
            serverMeta(server)?.let {
                Text(
                    text = it,
                    style = NeoTheme.typography.caption,
                    color = LocalContentColor.current.copy(alpha = MUTED),
                    maxLines = 1,
                )
            }
        }
    }
}

/** A server's qualities, highest first, then its runtime, such as "1080p · 720p · 24 min". Null when it has neither. */
@Composable
private fun serverMeta(server: FoundServer): String? {
    val parts =
        server.qualities.map { stringResource(R.string.streams_quality, it) } +
            listOfNotNull(server.runtimeMinutes?.let { stringResource(R.string.details_minutes, it) })
    return if (parts.isEmpty()) null else parts.joinToString(stringResource(R.string.streams_meta_separator))
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
