package ani.saikou.neo.tv.player

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import ani.saikou.neo.extension.api.Stream
import ani.saikou.neo.shared.playback.ControlsViewModel
import ani.saikou.neo.shared.playback.FoundStream
import ani.saikou.neo.shared.playback.PlayRequest
import ani.saikou.neo.shared.playback.PlayerState
import ani.saikou.neo.shared.playback.SegmentKind
import ani.saikou.neo.shared.playback.Stretch
import ani.saikou.neo.shared.playback.at
import ani.saikou.neo.shared.playback.endsAtMs
import ani.saikou.neo.shared.playback.formatJump
import ani.saikou.neo.shared.playback.formatPlaybackTime
import ani.saikou.neo.shared.playback.stretches
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.details.editionName
import ani.saikou.neo.tv.home.displayTitle
import ani.saikou.neo.tv.home.parseColour
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoIconButton
import ani.saikou.neo.tv.ui.NeoIcons
import java.util.Date
import kotlin.math.abs

internal const val PLAYER_CONTROLS_TAG = "player-controls"
internal const val PLAYER_BAR_TAG = "player-bar"
internal const val PLAYER_PLAY_PAUSE_TAG = "player-play-pause"
internal const val PLAYER_SCRUB_TAG = "player-scrub"
internal const val PLAYER_NUDGE_TAG = "player-nudge"

// From the design.
private val SideMargin = 48.dp
private val EdgeMargin = 27.dp
private val BarAboveButtons = 96.dp
private val BarAlone = 64.dp
private val BarFocused = 8.dp
private val BarResting = 5.dp
private val BarGap = 3.dp
private val BarRadius = 3.dp
private val BarRow = 24.dp
private val Head = 18.dp
private val HeadRing = 26.dp
private val FromMark = 20.dp
private val ButtonsHeight = 52.dp
private val NextThumbWidth = 78.dp
private val NextThumbHeight = 44.dp
private val ScrubAbove = 132.dp
private val ScrubWidth = 240.dp
private val PillHeight = 44.dp
private val PillPadding = 18.dp
private val PartColor = Color(0xFF9FB4FF)
private const val PART_FILL_ALPHA = 0.45f
private const val PART_LABEL_ALPHA = 0.95f
private const val PLAIN_FILL_ALPHA = 0.25f
private const val HEAD_RING_ALPHA = 0.25f
private const val FROM_MARK_ALPHA = 0.6f
private const val HINT_ALPHA = 0.85f
private const val DIMMED_ALPHA = 0.38f

// How often the time on screen is read from the player while it shows.
private const val TICK_MS = 250L

/**
 * The remote's controls over the playing video. Hidden, OK pauses and shows them, Down shows them, and Left or Right
 * seek 10 s with a slim line. Shown, they sit on the bar. Holding Left or Right picks a spot to jump to: OK jumps there
 * and Back stays. Back hides them.
 */
@Composable
internal fun BoxScope.PlayerControls(
    state: PlayerState<Stream>,
    answered: FoundStream<Stream>,
    request: PlayRequest,
    playback: Playback,
    controls: ControlsViewModel,
) {
    val shown by controls.state.collectAsStateWithLifecycle()
    val visible = shown.shown || shown.nudgeMs != null
    var positionMs by remember { mutableLongStateOf(playback.positionMs) }
    var durationMs by remember { mutableLongStateOf(playback.durationMs) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun read() {
        positionMs = playback.positionMs
        durationMs = playback.durationMs
        nowMs = System.currentTimeMillis()
    }
    LaunchedEffect(visible) {
        read()
        var last = 0L
        // Every frame, but the time is only read a few times a second.
        while (visible) {
            withInfiniteAnimationFrameMillis {
                if (it - last >= TICK_MS) {
                    last = it
                    read()
                }
            }
        }
    }

    val root = remember { FocusRequester() }
    val bar = remember { FocusRequester() }
    var onBar by remember { mutableStateOf(false) }
    val hold = remember { HeldKey() }
    LaunchedEffect(shown.shown) { if (shown.shown) bar.requestFocus() else root.requestFocus() }

    fun toggle() {
        if (playback.playing) playback.pause() else playback.play()
        controls.show(playback.playing)
    }

    fun seek(forward: Boolean) {
        val to = playback.positionMs + controls.nudge(forward)
        playback.seekTo(to.coerceIn(0, playback.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE))
        read()
    }

    fun onKey(event: KeyEvent): Boolean {
        val scrubbing = shown.scrub != null
        val native = event.nativeKeyEvent
        val forward = event.key == Key.DirectionRight || event.key == Key.MediaFastForward
        return when (event.key) {
            Key.DirectionLeft, Key.DirectionRight -> {
                // On the buttons, Left and Right move between them.
                if (shown.shown && !onBar && !scrubbing) return false
                if (event.type == KeyEventType.KeyDown) {
                    if (native.repeatCount == 0) {
                        hold.press(event.key)
                    } else {
                        hold.held = true
                        controls.scrub(forward, held = true, playback.positionMs, playback.durationMs)
                    }
                } else if (event.type == KeyEventType.KeyUp && hold.pressed == event.key) {
                    when {
                        hold.held -> controls.released()
                        scrubbing -> controls.scrub(forward, held = false, playback.positionMs, playback.durationMs)
                        else -> seek(forward)
                    }
                    hold.press(null)
                }
                true
            }

            Key.MediaFastForward, Key.MediaRewind -> {
                if (event.type == KeyEventType.KeyDown) seek(forward)
                true
            }

            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (shown.shown && !onBar && !scrubbing) return false
                if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                    val target = controls.jump()
                    if (target != null) {
                        playback.seekTo(target)
                        read()
                    } else {
                        toggle()
                    }
                }
                true
            }

            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                if (event.type == KeyEventType.KeyDown) toggle()
                true
            }

            Key.DirectionDown -> {
                if (!shown.shown && event.type == KeyEventType.KeyDown) controls.show(playback.playing)
                // From the bar, Down moves to the buttons.
                !(shown.shown && onBar && !scrubbing)
            }

            // The episodes drawer comes with #243.
            Key.DirectionUp -> {
                true
            }

            else -> {
                false
            }
        }
    }

    BackHandler(enabled = shown.shown) { if (shown.scrub != null) controls.stay() else controls.hide() }

    Box(
        Modifier
            .matchParentSize()
            .focusRequester(root)
            .focusable()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && shown.shown) controls.touched(playback.playing)
                false
            }.onKeyEvent(::onKey),
    ) {
        val bars = remember(state.segments, durationMs) { stretches(state.segments, durationMs) }
        val scrub = shown.scrub
        val headMs = scrub?.targetMs ?: positionMs
        when {
            shown.shown -> {
                Box(Modifier.matchParentSize().background(controlsScrim(scrub != null)).testTag(PLAYER_CONTROLS_TAG))
                TopStrip(state, answered, request, nowMs, Modifier.align(Alignment.TopStart))
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = SideMargin, end = SideMargin, bottom = if (scrub == null) BarAboveButtons else BarAlone),
                ) {
                    SegmentBar(
                        bars,
                        positionMs,
                        headMs,
                        durationMs,
                        fromMs = scrub?.fromMs,
                        focused = onBar || scrub != null,
                        modifier =
                            Modifier
                                .focusRequester(bar)
                                .onFocusChanged { onBar = it.isFocused }
                                .focusable()
                                .testTag(PLAYER_BAR_TAG),
                    )
                    TimeLine(bars, headMs, durationMs, nowMs, state.segments.isNotEmpty())
                }
                if (scrub == null) {
                    Buttons(answered, request, playback, controls, Modifier.align(Alignment.BottomStart))
                } else {
                    ScrubReadout(scrub.targetMs, scrub.jumpMs, durationMs)
                    ScrubHints(scrub.fromMs, Modifier.align(Alignment.BottomCenter))
                }
            }

            shown.nudgeMs != null -> {
                Nudge(shown.nudgeMs ?: 0, bars, positionMs, durationMs, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

/** Which of Left or Right is down, and whether it has been held long enough to repeat. */
private class HeldKey {
    var pressed: Key? = null
        private set
    var held = false

    fun press(key: Key?) {
        pressed = key
        held = false
    }
}

@Composable
private fun controlsScrim(scrubbing: Boolean): Brush {
    val scrim = NeoTheme.colors.scrimPlayer
    val top = if (scrubbing) 0.7f else 0.85f
    val middle = if (scrubbing) 0.2f else 0f
    return Brush.verticalGradient(
        0f to scrim.copy(alpha = top),
        0.25f to scrim.copy(alpha = 0f),
        0.5f to scrim.copy(alpha = middle),
        1f to scrim.copy(alpha = 0.92f),
    )
}

/** The show and episode on the left; the clock and what's playing on the right. */
@Composable
private fun TopStrip(
    state: PlayerState<Stream>,
    answered: FoundStream<Stream>,
    request: PlayRequest,
    nowMs: Long,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val typography = NeoTheme.typography
    val context = LocalContext.current
    Row(
        modifier.fillMaxWidth().padding(start = SideMargin, end = SideMargin, top = EdgeMargin),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                request.show.card
                    .displayTitle()
                    .uppercase(),
                style =
                    typography.overline.copy(
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
                episodeTitle(request),
                style = typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
                color = colors.paper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                DateFormat.getTimeFormat(context).format(Date(nowMs)),
                style = typography.title.copy(fontSize = 22.sp, lineHeight = 28.sp, fontFeatureSettings = "tnum"),
                color = colors.paper,
            )
            val server = state.server
            Text(
                stringResource(
                    R.string.player_stream_line,
                    server.source.name,
                    server.server.name,
                    answered.height,
                    editionName(server.server.edition),
                ),
                style = typography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = colors.textMuted,
            )
        }
    }
}

/**
 * The episode as stretches, with what has played filled in, a head at [headMs], and while scrubbing a mark at [fromMs].
 * Under it, the names of the recap, opening, ending and preview.
 */
@Composable
private fun SegmentBar(
    bars: List<Stretch>,
    positionMs: Long,
    headMs: Long,
    durationMs: Long,
    fromMs: Long?,
    focused: Boolean,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val position = stringResource(R.string.player_position)
    val time = formatPlaybackTime(headMs)
    Column(
        modifier.semantics {
            contentDescription = position
            stateDescription = time
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(BarRow), contentAlignment = Alignment.CenterStart) {
            val width = maxWidth
            Row(
                Modifier.fillMaxWidth().height(if (focused) BarFocused else BarResting),
                horizontalArrangement = Arrangement.spacedBy(BarGap),
            ) {
                // An unknown length still shows an empty track.
                val track = bars.ifEmpty { listOf(Stretch(null, 0, 1)) }
                for (stretch in track) {
                    val length = (stretch.endMs - stretch.startMs).coerceAtLeast(1)
                    val played = ((positionMs - stretch.startMs).toFloat() / length).coerceIn(0f, 1f)
                    val fill =
                        if (stretch.kind ==
                            null
                        ) {
                            colors.paper.copy(alpha = PLAIN_FILL_ALPHA)
                        } else {
                            PartColor.copy(alpha = PART_FILL_ALPHA)
                        }
                    Box(
                        Modifier
                            .weight(length.toFloat())
                            .fillMaxHeight()
                            .background(fill, RoundedCornerShape(BarRadius)),
                    ) {
                        if (durationMs > 0 && played > 0f) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(played).background(colors.pink, RoundedCornerShape(BarRadius)))
                        }
                    }
                }
            }
            if (durationMs > 0) {
                if (fromMs != null) {
                    Box(
                        Modifier
                            .offset(x = at(width, fromMs, durationMs) - 1.dp)
                            .size(2.dp, FromMark)
                            .background(colors.paper.copy(alpha = FROM_MARK_ALPHA)),
                    )
                }
                val head = at(width, headMs, durationMs)
                if (focused) {
                    Box(
                        Modifier
                            .offset(
                                x = head - HeadRing / 2,
                            ).size(HeadRing)
                            .background(Color.White.copy(alpha = HEAD_RING_ALPHA), CircleShape),
                    )
                }
                Box(Modifier.offset(x = head - Head / 2).size(Head).background(Color.White, CircleShape))
            }
        }
        if (bars.any { it.kind != null }) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(BarGap)) {
                for (stretch in bars) {
                    Text(
                        stretch.kind?.let { partName(it) }.orEmpty(),
                        style = NeoTheme.typography.caption.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = PartColor.copy(alpha = PART_LABEL_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight((stretch.endMs - stretch.startMs).coerceAtLeast(1).toFloat()),
                    )
                }
            }
        }
    }
}

/** How far along a bar [width] wide [ms] sits. */
private fun at(
    width: Dp,
    ms: Long,
    durationMs: Long,
) = width * (ms.toFloat() / durationMs).coerceIn(0f, 1f)

/** Where [headMs] is of the length, and the part it's in, with when the episode ends on the right. */
@Composable
private fun TimeLine(
    bars: List<Stretch>,
    headMs: Long,
    durationMs: Long,
    nowMs: Long,
    named: Boolean,
) {
    val colors = NeoTheme.colors
    val style = NeoTheme.typography.timecode.copy(lineHeight = 20.sp)
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        val head = formatPlaybackTime(headMs)
        val text =
            when {
                durationMs <= 0 -> head
                named -> stringResource(R.string.player_time_part, head, formatPlaybackTime(durationMs), partName(bars.at(headMs)?.kind))
                else -> stringResource(R.string.player_time, head, formatPlaybackTime(durationMs))
            }
        Text(text, style = style, color = colors.paper)
        endsAtMs(nowMs, headMs, durationMs)?.let {
            Text(
                stringResource(R.string.player_ends, DateFormat.getTimeFormat(context).format(Date(it))),
                style = style,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun partName(kind: SegmentKind?) =
    stringResource(
        when (kind) {
            null -> R.string.player_part_episode
            SegmentKind.Recap -> R.string.player_part_recap
            SegmentKind.Opening -> R.string.player_part_opening
            SegmentKind.Ending -> R.string.player_part_ending
            SegmentKind.Preview -> R.string.player_part_preview
        },
    )

/** Play or pause, then the buttons later issues bring to life, dimmed and passed over by focus. */
@Composable
private fun Buttons(
    answered: FoundStream<Stream>,
    request: PlayRequest,
    playback: Playback,
    controls: ControlsViewModel,
    modifier: Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(start = SideMargin, end = SideMargin, bottom = EdgeMargin).height(ButtonsHeight),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val playing = playback.playing
        NeoIconButton(
            if (playing) NeoIcons.Pause else NeoIcons.Play,
            stringResource(if (playing) R.string.player_pause else R.string.player_play),
            {
                if (playback.playing) playback.pause() else playback.play()
                controls.touched(playback.playing)
            },
            Modifier.testTag(PLAYER_PLAY_PAUSE_TAG),
        )
        // Episodes come with #243, and the tracks, quality and speed with #242.
        DimmedPill(stringResource(R.string.player_episodes), NeoIcons.Episodes)
        DimmedPill(stringResource(R.string.player_audio_subtitles), NeoIcons.Subtitles)
        DimmedPill(stringResource(R.string.player_quality, answered.height), null)
        DimmedPill(stringResource(R.string.player_speed, "1"), null)
        Spacer(Modifier.weight(1f))
        val episodes = request.show.card.episodes
        if (episodes == null || request.episode < episodes) NextCard(request)
    }
}

@Composable
private fun DimmedPill(
    text: String,
    icon: ImageVector?,
) {
    val colors = NeoTheme.colors
    Row(
        Modifier
            .alpha(DIMMED_ALPHA)
            .height(PillHeight)
            .background(colors.fillTonal, CircleShape)
            .padding(horizontal = PillPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = colors.paper, modifier = Modifier.size(18.dp)) }
        Text(text, style = NeoTheme.typography.label.copy(fontSize = 14.sp), color = colors.paper)
    }
}

/** The next episode, dimmed until #77 plays it. */
@Composable
private fun NextCard(request: PlayRequest) {
    val colors = NeoTheme.colors
    val tint = parseColour(request.show.card.coverColor) ?: colors.pink
    Row(
        Modifier
            .alpha(DIMMED_ALPHA)
            .height(ButtonsHeight)
            .background(colors.fillTonal, RoundedCornerShape(12.dp))
            .padding(start = 4.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(NextThumbWidth, NextThumbHeight)
                .background(Brush.linearGradient(listOf(colors.surface, tint)), RoundedCornerShape(8.dp)),
        )
        Column {
            Text(
                stringResource(R.string.player_next).uppercase(),
                style = NeoTheme.typography.caption.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
                color = colors.textMuted,
            )
            Text(
                stringResource(R.string.streams_episode, request.episode + 1),
                style = NeoTheme.typography.label.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.paper,
            )
        }
    }
}

/** The time a scrub would jump to and how far, over its spot on the bar. There's no frame: streams carry no thumbnails. */
@Composable
private fun BoxScope.ScrubReadout(
    targetMs: Long,
    jumpMs: Long,
    durationMs: Long,
) {
    val colors = NeoTheme.colors
    BoxWithConstraints(Modifier.matchParentSize()) {
        val track = maxWidth - SideMargin * 2
        val centre = SideMargin + if (durationMs > 0) at(track, targetMs, durationMs) else 0.dp
        val left = (centre - ScrubWidth / 2).coerceIn(SideMargin, maxWidth - SideMargin - ScrubWidth)
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = left)
                .padding(bottom = ScrubAbove)
                .width(ScrubWidth)
                .testTag(PLAYER_SCRUB_TAG),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                formatPlaybackTime(targetMs),
                style =
                    NeoTheme.typography.title.copy(
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                color = colors.paper,
            )
            Text(
                stringResource(R.string.player_scrub_jump, formatJump(jumpMs)),
                style = NeoTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textMuted,
                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun ScrubHints(
    fromMs: Long,
    modifier: Modifier,
) {
    Row(modifier.padding(bottom = EdgeMargin), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Hint(stringResource(R.string.player_key_ok), stringResource(R.string.player_hint_jump))
        Hint(stringResource(R.string.player_key_back), stringResource(R.string.player_hint_stay, formatPlaybackTime(fromMs)))
        Hint(stringResource(R.string.player_key_hold), stringResource(R.string.player_hint_faster))
    }
}

@Composable
private fun Hint(
    key: String,
    does: String,
) {
    val style = NeoTheme.typography.caption
    val color = NeoTheme.colors.paper.copy(alpha = HINT_ALPHA)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(key, style = style.copy(fontWeight = FontWeight.Bold), color = color)
        Text(does, style = style, color = color)
    }
}

/** Quick presses of Left or Right while the controls are hidden: how far they've gone in all, over a slim bar. */
@Composable
private fun Nudge(
    nudgeMs: Long,
    bars: List<Stretch>,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier,
) {
    Column(modifier.padding(start = SideMargin, end = SideMargin, bottom = BarAlone).testTag(PLAYER_NUDGE_TAG)) {
        Text(
            stringResource(R.string.player_nudge, if (nudgeMs < 0) "−" else "+", abs(nudgeMs) / 1000),
            style = NeoTheme.typography.timecode.copy(lineHeight = 20.sp),
            color = NeoTheme.colors.paper,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        SegmentBar(bars, positionMs, positionMs, durationMs, fromMs = null, focused = false, modifier = Modifier)
    }
}
