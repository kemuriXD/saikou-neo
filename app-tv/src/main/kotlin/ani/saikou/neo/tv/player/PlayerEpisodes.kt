package ani.saikou.neo.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AniZipEpisode
import ani.saikou.neo.shared.catalogue.EpisodeKind
import ani.saikou.neo.shared.details.EpisodeLine
import ani.saikou.neo.shared.details.EpisodeList
import ani.saikou.neo.shared.details.EpisodeMark
import ani.saikou.neo.shared.details.episodeRanges
import ani.saikou.neo.shared.details.lineFor
import ani.saikou.neo.shared.playback.PlayRequest
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.details.ThumbOverlay
import ani.saikou.neo.tv.details.WatchedLine
import ani.saikou.neo.tv.details.duration
import ani.saikou.neo.tv.details.meta
import ani.saikou.neo.tv.details.thumb
import ani.saikou.neo.tv.home.displayTitle
import ani.saikou.neo.tv.home.parseColour
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoTab
import coil3.compose.AsyncImage

internal const val PLAYER_EPISODES_TAG = "player-episodes"

internal fun drawerEpisodeTag(number: Int) = "player-episode-$number"

// From the design.
private val DrawerWidth = 420.dp
private val DrawerSide = 24.dp
private val DrawerEdge = 27.dp
private val DrawerGap = 12.dp
private val ChipHeight = 28.dp
private val ChipPadding = 12.dp
private val RowHeight = 72.dp
private val RowRadius = 12.dp
private val RowGlow = 20.dp
private val ThumbWidth = 112.dp
private val ThumbHeight = 63.dp
private val ThumbRadius = 8.dp
private val FocusGlowColor = Color.White.copy(alpha = 0.3f)
private const val ROW_SCALE = 1.03f
private const val DRAWER_SCRIM = 0.35f
private const val DRAWER_FILL = 0.94f
private const val NOW_FILL = 0.12f
private const val PLACEHOLDER_DIM = 0.5f

/**
 * The episodes drawer, over the video as it keeps playing. Focus starts on the one [playing][PlayRequest.episode], in its
 * range. Picking another calls [onPick], and picking the one playing just closes the drawer through [onClose].
 */
@Composable
internal fun BoxScope.PlayerEpisodes(
    request: PlayRequest,
    list: EpisodeList,
    playback: Playback,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val colors = NeoTheme.colors
    // How far the playing episode had got when the drawer opened.
    val watched = remember { if (playback.durationMs > 0) (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f) else 0f }
    val ranges = remember(list.episodes.size) { episodeRanges(list.episodes.size) }
    val startRange = remember { ranges.indexOfFirst { request.episode in it }.coerceAtLeast(0) }
    var range by remember { mutableIntStateOf(startRange) }
    val shown = ranges.getOrNull(range)?.let { list.episodes.subList(it.first - 1, it.last) } ?: list.episodes
    // One that isn't listed, such as an upcoming one left out, starts on the first.
    val start = remember { request.episode.takeIf { number -> shown.any { it.number == number } } ?: shown.first().number }
    val startFocus = remember { FocusRequester() }
    // The one before shows above it, as nearby episodes on either side.
    val rows = rememberLazyListState((shown.indexOfFirst { it.number == start } - 1).coerceAtLeast(0))
    LaunchedEffect(Unit) {
        // The list lays its rows out a frame after it's composed.
        withFrameNanos {}
        startFocus.requestFocus()
    }
    Box(Modifier.matchParentSize().background(colors.scrimPlayer.copy(alpha = DRAWER_SCRIM)))
    Column(
        Modifier
            .align(Alignment.TopEnd)
            .width(DrawerWidth)
            .fillMaxHeight()
            .background(colors.ink.copy(alpha = DRAWER_FILL))
            .padding(top = DrawerEdge)
            .testTag(PLAYER_EPISODES_TAG)
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(DrawerGap),
    ) {
        Column(Modifier.padding(horizontal = DrawerSide), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                request.show.card
                    .displayTitle()
                    .uppercase(),
                style =
                    NeoTheme.typography.overline.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    ),
                color = parseColour(request.show.card.coverColor) ?: colors.pink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.player_episodes),
                style = NeoTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
                color = colors.paper,
            )
        }
        if (ranges.isNotEmpty()) {
            val chips = remember(ranges.size) { List(ranges.size) { FocusRequester() } }
            LazyRow(
                state = rememberLazyListState(startRange),
                modifier =
                    Modifier
                        .focusProperties {
                            onEnter = { if (requestedFocusDirection == FocusDirection.Up) chips[range].requestFocus() }
                        }.focusGroup(),
                contentPadding = PaddingValues(horizontal = DrawerSide, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(ranges) { index, each ->
                    NeoTab(
                        stringResource(R.string.details_episode_range, each.first, each.last),
                        index == range,
                        { range = index },
                        Modifier
                            .focusRequester(chips[index])
                            .onFocusChanged { if (it.isFocused) range = index },
                        height = ChipHeight,
                        padding = ChipPadding,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        LazyColumn(
            state = rows,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = DrawerSide, end = DrawerSide, top = 8.dp, bottom = DrawerEdge),
            verticalArrangement = Arrangement.spacedBy(DrawerGap),
        ) {
            items(shown, key = { it.number }) { episode ->
                val playing = episode.number == request.episode
                EpisodeRow(
                    episode,
                    list.lineFor(episode.number, request.episode, request.progress),
                    playing,
                    request.backdrop,
                    watched,
                    onClick = { if (playing) onClose() else onPick(episode.number) },
                    (if (episode.number == start) Modifier.focusRequester(startFocus) else Modifier)
                        .testTag(drawerEpisodeTag(episode.number)),
                )
            }
        }
    }
}

/** A row of the drawer: the still with its number and how much was watched, the title, and what it says of the episode. */
@Composable
private fun EpisodeRow(
    episode: AniZipEpisode,
    line: EpisodeLine,
    playing: Boolean,
    backdrop: String?,
    watched: Float,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(RowHeight),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(RowRadius)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (playing) colors.paper.copy(alpha = NOW_FILL) else Color.Transparent,
                contentColor = colors.paper,
                focusedContainerColor = colors.focus,
                focusedContentColor = colors.onFocus,
                pressedContainerColor = colors.focus,
                pressedContentColor = colors.onFocus,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = ROW_SCALE),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(FocusGlowColor, RowGlow)),
        interactionSource = interactions,
    ) {
        Row(
            Modifier.fillMaxHeight().padding(start = 4.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(ThumbWidth, ThumbHeight)
                    .clip(RoundedCornerShape(ThumbRadius))
                    .background(colors.surfaceRaised),
            ) {
                val thumb = episode.thumb(backdrop)
                AsyncImage(thumb?.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                if (thumb?.dimmed == true) Box(Modifier.matchParentSize().background(colors.ink.copy(alpha = PLACEHOLDER_DIM)))
                if (playing) WatchedLine(watched) else ThumbOverlay((line as? EpisodeLine.Marked)?.mark)
                Text(
                    episode.number.toString(),
                    style = NeoTheme.typography.title.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
                    color = colors.paper,
                    modifier =
                        Modifier
                            .padding(4.dp)
                            .background(colors.ink, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    episode.title ?: stringResource(R.string.details_episode_number, episode.number),
                    style = NeoTheme.typography.label.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                lineText(episode, line)?.let {
                    Text(
                        it,
                        style = NeoTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color =
                            when {
                                focused -> colors.onFocusMuted
                                playing -> colors.pink
                                else -> colors.textMuted
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** "Playing now", "✓ Watched", "12 min left", "Filler · 24 min", or else its length and when it aired or airs. */
@Composable
private fun lineText(
    episode: AniZipEpisode,
    line: EpisodeLine,
): String? =
    when (line) {
        EpisodeLine.Playing -> {
            stringResource(R.string.player_episode_playing)
        }

        is EpisodeLine.Marked -> {
            markText(line.mark)
        }

        is EpisodeLine.Kind -> {
            listOfNotNull(kindText(line.kind), episode.minutes?.let { duration(it) }).joinToString(" · ")
        }

        EpisodeLine.Plain -> {
            episode.meta(film = false)
        }
    }

@Composable
private fun markText(mark: EpisodeMark) =
    when (mark) {
        EpisodeMark.Watched -> stringResource(R.string.details_episode_watched)
        is EpisodeMark.InProgress -> stringResource(R.string.details_episode_left, mark.progress.minutesLeft)
    }

@Composable
private fun kindText(kind: EpisodeKind) =
    stringResource(
        when (kind) {
            EpisodeKind.Filler -> R.string.details_episode_filler
            EpisodeKind.Recap -> R.string.details_episode_recap
        },
    )
