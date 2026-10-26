package ani.saikou.neo.tv.details

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ani.saikou.neo.shared.catalogue.AirDate
import ani.saikou.neo.shared.catalogue.AniZipEpisode
import ani.saikou.neo.shared.details.EpisodeList
import ani.saikou.neo.shared.details.episodeRanges
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoTab
import ani.saikou.neo.tv.ui.outToTheScreenEdge
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal fun episodeTag(number: Int) = "episode-$number"

internal const val EPISODE_RANGES_TAG = "episode-ranges"

// From the design: lifted, the section starts 118 dp down, so the list has the rest of the screen.
internal val SectionHeight = 422.dp
private val RowThumbWidth = 128.dp
private val RowThumbHeight = 72.dp
private val NumberedRowHeight = 56.dp
private val NumberWidth = 56.dp
private val ChipHeight = 36.dp

// The backdrop in place of a still is dimmed by half.
private const val PLACEHOLDER_DIM = 0.5f
private const val ROW_FOCUS_SCALE = 1.025f
private const val SYNOPSIS_LINES = 2
private const val RANGE_REST_MILLIS = 150L

/**
 * The show's episodes: a line on how many there are, ranges of 50 to jump between for a long series, then a row for
 * each. Rows have a still, a title, the date and a short synopsis when AniZip knows them, or just their number. An
 * episode with no still shows the show's [backdrop], dimmed. A film lists itself, then its extras.
 */
@Composable
internal fun EpisodesSection(
    list: EpisodeList,
    backdrop: String?,
    modifier: Modifier = Modifier,
) {
    val dimensions = NeoTheme.dimensions
    val ranges = remember(list.count) { episodeRanges(list.count) }
    var range by rememberSaveable { mutableIntStateOf(0) }
    // The range focus is on. It's marked as chosen at once, but its episodes show once focus rests there, so holding
    // Right doesn't rebuild the list for every range it passes.
    var focusedRange by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(focusedRange) {
        val resting = focusedRange ?: return@LaunchedEffect
        delay(RANGE_REST_MILLIS)
        range = resting
    }
    // Set when Down leaves the ranges before the chosen one shows, for the list to take focus once it has.
    var intoList by remember { mutableStateOf(false) }
    val listFocus = remember { FocusRequester() }
    LaunchedEffect(intoList, range) {
        if (!intoList) return@LaunchedEffect
        // The list lays its new rows out a frame after it's composed.
        withFrameNanos {}
        listFocus.requestFocus()
        intoList = false
    }
    val shown = ranges.getOrNull(range)?.let { list.episodes.subList(it.first - 1, it.last) } ?: list.episodes
    val rich = !list.detailsMissing && !list.film
    Column(modifier.heightIn(max = SectionHeight), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = list.header(),
            style = NeoTheme.typography.label.copy(fontWeight = FontWeight.Normal),
            color = NeoTheme.colors.textMuted,
        )
        if (ranges.isNotEmpty()) {
            // Resting on a range shows it, with nothing to press. Up or down into them lands on the one shown.
            val chips = remember(ranges.size) { List(ranges.size) { FocusRequester() } }
            LazyRow(
                modifier =
                    Modifier
                        .outToTheScreenEdge(dimensions.gridMargin)
                        .testTag(EPISODE_RANGES_TAG)
                        .onPreviewKeyEvent { event ->
                            val pending = focusedRange?.takeIf { it != range }
                            if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionDown || pending == null) {
                                return@onPreviewKeyEvent false
                            }
                            range = pending
                            intoList = true
                            true
                        }.focusProperties {
                            onEnter = {
                                if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
                                    chips[range].requestFocus()
                                }
                            }
                        }.focusGroup(),
                contentPadding = PaddingValues(horizontal = dimensions.gridMargin),
                horizontalArrangement = Arrangement.spacedBy(dimensions.space2),
            ) {
                itemsIndexed(ranges) { index, episodes ->
                    NeoTab(
                        text = stringResource(R.string.details_episode_range, episodes.first, episodes.last),
                        selected = index == (focusedRange ?: range),
                        onClick = {},
                        modifier = Modifier.focusRequester(chips[index]).onFocusChanged { if (it.isFocused) focusedRange = index },
                        height = ChipHeight,
                    )
                }
            }
        }
        // Room above and below so a focused row's growth and glow aren't cut off.
        LazyColumn(
            modifier = Modifier.focusRequester(listFocus),
            verticalArrangement = Arrangement.spacedBy(if (rich) dimensions.space2 else 6.dp),
            contentPadding = PaddingValues(vertical = dimensions.space4),
        ) {
            items(shown, key = { it.number }) { episode ->
                if (rich) {
                    EpisodeRow(episode, backdrop)
                } else {
                    NumberedRow(
                        number = episode.number.takeUnless { list.film },
                        title = episode.title ?: stringResource(R.string.details_episode_number, episode.number),
                        meta = episode.meta(list.film),
                        modifier = Modifier.testTag(episodeTag(episode.number)),
                    )
                }
            }
            if (list.extras.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.details_extras).uppercase(),
                        style = NeoTheme.typography.overline,
                        color = NeoTheme.colors.pink,
                        modifier = Modifier.padding(top = dimensions.space2),
                    )
                }
                items(list.extras) { extra ->
                    NumberedRow(number = null, title = extra.title, meta = extra.minutes?.let { duration(it) })
                }
            }
        }
    }
}

/** Such as "37 episodes", "12 episodes · 4 aired", or "201 known episodes · Titles and air dates unavailable". */
@Composable
private fun EpisodeList.header(): String {
    val count =
        when {
            film && extras.isNotEmpty() -> {
                stringResource(R.string.details_film) + " · " + pluralStringResource(R.plurals.details_extras, extras.size, extras.size)
            }

            film -> {
                stringResource(R.string.details_film)
            }

            countSoFar -> {
                pluralStringResource(R.plurals.details_known_episodes, count, count)
            }

            else -> {
                pluralStringResource(R.plurals.details_episode_count, count, count)
            }
        }
    return when {
        film -> count
        detailsMissing -> count + " · " + stringResource(R.string.details_episodes_missing)
        aired < this.count -> count + " · " + stringResource(R.string.details_episodes_aired, aired)
        else -> count
    }
}

/** What an episode's tile shows: its still, or else the show's backdrop, dimmed so it isn't taken for a still. */
internal data class EpisodeThumb(
    val url: String,
    val dimmed: Boolean,
)

internal fun AniZipEpisode.thumb(backdrop: String?): EpisodeThumb? =
    thumbnail?.let { EpisodeThumb(it, dimmed = false) } ?: backdrop?.let { EpisodeThumb(it, dimmed = true) }

/** An episode AniZip knows: its still, "1 · Rebirth", its length and date, and two lines of its synopsis. */
@Composable
private fun EpisodeRow(
    episode: AniZipEpisode,
    backdrop: String?,
) {
    val dimensions = NeoTheme.dimensions
    RowSurface(Modifier.testTag(episodeTag(episode.number))) {
        Row(
            Modifier.padding(start = dimensions.space2, top = dimensions.space2, end = dimensions.space4, bottom = dimensions.space2),
            horizontalArrangement = Arrangement.spacedBy(dimensions.space4),
        ) {
            val thumb = episode.thumb(backdrop)
            Box(
                Modifier
                    .size(RowThumbWidth, RowThumbHeight)
                    .clip(RoundedCornerShape(dimensions.radiusSm))
                    .background(NeoTheme.colors.surfaceRaised),
            ) {
                AsyncImage(
                    model = thumb?.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                if (thumb?.dimmed == true) Box(Modifier.matchParentSize().background(NeoTheme.colors.ink.copy(alpha = PLACEHOLDER_DIM)))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val title = episode.title?.let { stringResource(R.string.details_episode_titled, episode.number, it) }
                Text(
                    text = title ?: stringResource(R.string.details_episode_number, episode.number),
                    style = NeoTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.meta(film = false)?.let { Meta(it) }
                episode.synopsis?.let {
                    Text(
                        text = it,
                        style = NeoTheme.typography.label.copy(fontWeight = FontWeight.Normal),
                        color = LocalContentColor.current.copy(alpha = MUTED),
                        maxLines = SYNOPSIS_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A row with no still: the episode's number, if it has one, then its title and length. */
@Composable
private fun NumberedRow(
    number: Int?,
    title: String,
    meta: String?,
    modifier: Modifier = Modifier,
) {
    val dimensions = NeoTheme.dimensions
    RowSurface(modifier.heightIn(min = NumberedRowHeight)) {
        Row(
            Modifier.heightIn(min = NumberedRowHeight).padding(horizontal = dimensions.space4, vertical = dimensions.space2),
            horizontalArrangement = Arrangement.spacedBy(dimensions.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            number?.let {
                Text(
                    text = it.toString(),
                    style = NeoTheme.typography.titleLarge.copy(fontSize = NeoTheme.typography.titleLarge.fontSize * NUMBER_SCALE),
                    color = LocalContentColor.current.copy(alpha = FAINT),
                    modifier = Modifier.width(NumberWidth),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(text = title, style = NeoTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                meta?.let { Meta(it) }
            }
        }
    }
}

@Composable
private fun Meta(text: String) =
    Text(text = text, style = NeoTheme.typography.caption, color = LocalContentColor.current.copy(alpha = MUTED), maxLines = 1)

/** A row's box: see-through surface at rest; paper, a glow and a little growth on focus. Rows don't play anything yet. */
@Composable
private fun RowSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = NeoTheme.colors
    val dimensions = NeoTheme.dimensions
    Surface(
        onClick = {},
        modifier = modifier.width(RowWidth),
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
        Box { content() }
    }
}

// The rows span the grid's content width.
private val RowWidth: Dp = 844.dp
private const val MUTED = 0.72f
private const val FAINT = 0.52f
private const val NUMBER_SCALE = 22f / 24f

/** Such as "23 min · Aired 4 Oct 2006", or "Released" for a film. */
@Composable
private fun AniZipEpisode.meta(film: Boolean): String? {
    val date =
        airDate?.let {
            val res =
                when {
                    film -> R.string.details_released_on
                    it.isFuture() -> R.string.details_airs_on
                    else -> R.string.details_aired_on
                }
            stringResource(res, it.format())
        }
    return listOfNotNull(minutes?.let { duration(it) }, date).joinToString(" · ").ifEmpty { null }
}

/** Such as "23 min", or "2 h 5 min". */
@Composable
private fun duration(minutes: Int): String =
    if (minutes < 60) {
        stringResource(R.string.details_minutes, minutes)
    } else {
        stringResource(R.string.details_hours_minutes, minutes / 60, minutes % 60)
    }

private fun AirDate.calendar(): Calendar = Calendar.getInstance().apply { set(year, month - 1, day, 0, 0, 0) }

private fun AirDate.isFuture() = calendar().after(Calendar.getInstance())

private fun AirDate.format(): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(calendar().time)
