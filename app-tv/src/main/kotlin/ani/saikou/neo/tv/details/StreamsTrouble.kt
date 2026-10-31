package ani.saikou.neo.tv.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import ani.saikou.neo.shared.playback.ANSWER_WITHIN
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.playback.ServerEdition
import ani.saikou.neo.shared.playback.ServersState
import ani.saikou.neo.shared.playback.SourceAnswer
import ani.saikou.neo.shared.settings.SubOrDub
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoButton
import ani.saikou.neo.tv.ui.NeoButtonRow
import ani.saikou.neo.tv.ui.NeoIcons

internal const val STREAMS_TRY_AGAIN_TAG = "streams-try-again"
internal const val STREAMS_BACK_TAG = "streams-back"
internal const val STREAMS_ADD_SOURCE_TAG = "streams-add-source"

/** What the streams page can do when nothing plays: ask again, go back to the episodes, or add a source in Settings. */
internal data class StreamsActions(
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onAddSource: () -> Unit = {},
)

// From the design: the message is 520 dp wide with no sources, 560 dp with the sources listed.
private val NoSourcesWidth = 520.dp
private val FailedWidth = 560.dp
private val IconBox = 52.dp
private val ReasonRowHeight = 56.dp

/** No usable source is installed: what that means, and Add a source. */
@Composable
internal fun NoSources(
    episode: Int,
    onAddSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val add = remember { FocusRequester() }
    LaunchedEffect(Unit) { add.requestFocus() }
    Column(modifier.width(NoSourcesWidth), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(IconBox).background(colors.paper.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(NeoIcons.Plug, contentDescription = null, tint = colors.paper, modifier = Modifier.size(26.dp))
        }
        Text(
            stringResource(R.string.streams_no_sources_title),
            style = NeoTheme.typography.titleLarge.copy(fontSize = 26.sp),
            color = colors.paper,
        )
        Text(
            stringResource(R.string.streams_no_sources_body, episode),
            style = NeoTheme.typography.bodyLarge,
            color = colors.paper.copy(alpha = 0.8f),
        )
        NeoButtonRow(Modifier.padding(top = NeoTheme.dimensions.space2)) {
            NeoButton(
                stringResource(R.string.streams_add_source),
                onAddSource,
                Modifier.focusRequester(add).testTag(STREAMS_ADD_SOURCE_TAG),
            )
        }
    }
}

/** Every source answered with nothing to play: each one's reason, then Try again and Back to episodes. */
@Composable
internal fun NothingPlays(
    state: ServersState,
    title: String,
    actions: StreamsActions,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val retry = remember { FocusRequester() }
    LaunchedEffect(Unit) { retry.requestFocus() }
    Column(modifier.width(FailedWidth), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(NeoIcons.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(22.dp))
            Text(
                stringResource(R.string.streams_nothing_plays),
                style = NeoTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
                color = colors.paper,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(NeoTheme.dimensions.space2)) {
            for (entry in state.sources) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(ReasonRowHeight)
                        .background(colors.surfaceScrim, RoundedCornerShape(NeoTheme.dimensions.radiusLg))
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(entry.source.name, style = NeoTheme.typography.label.copy(fontWeight = FontWeight.SemiBold), color = colors.paper)
                    Text(whyNothing(entry.answer, title, state.subOrDub), style = NeoTheme.typography.caption, color = colors.textMuted)
                }
            }
        }
        NeoButtonRow(Modifier.padding(top = NeoTheme.dimensions.space2)) {
            NeoButton(
                stringResource(R.string.streams_try_again),
                actions.onRetry,
                Modifier.focusRequester(retry).testTag(STREAMS_TRY_AGAIN_TAG),
            )
            NeoButton(stringResource(R.string.streams_back), actions.onBack, Modifier.testTag(STREAMS_BACK_TAG))
        }
    }
}

/** The sources only have [have], not the [missing] edition the setting asks for. Plain words and Back. */
@Composable
internal fun MissingEdition(
    episode: Int,
    missing: ServerEdition,
    have: ServerEdition,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeoTheme.colors
    val back = remember { FocusRequester() }
    LaunchedEffect(Unit) { back.requestFocus() }
    Column(modifier.width(FailedWidth), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            stringResource(R.string.streams_missing_title, editionName(missing), episode),
            style = NeoTheme.typography.titleLarge,
            color = colors.paper,
        )
        Text(
            stringResource(R.string.streams_missing_body, editionName(have)),
            style = NeoTheme.typography.bodyLarge,
            color = colors.paper.copy(alpha = 0.8f),
        )
        NeoButtonRow(Modifier.padding(top = NeoTheme.dimensions.space2)) {
            NeoButton(stringResource(R.string.streams_back), onBack, Modifier.focusRequester(back).testTag(STREAMS_BACK_TAG))
        }
    }
}

/** Why a source that answered has nothing to pick under [subOrDub], such as "Didn't answer in 25 s" or "Only Dub here". */
@Composable
internal fun whyNothing(
    answer: SourceAnswer,
    title: String,
    subOrDub: SubOrDub,
): String =
    when (answer) {
        is SourceAnswer.Failed -> {
            reason(answer.reason, title)
        }

        is SourceAnswer.Found -> {
            if (answer.hidden > 0) {
                // The setting hid them all, so they were the edition it leaves out. Both leaves none out.
                val other = if (subOrDub == SubOrDub.DUB) ServerEdition.SUB else ServerEdition.DUB
                stringResource(R.string.streams_only_edition, editionName(other))
            } else {
                stringResource(R.string.streams_no_servers)
            }
        }

        SourceAnswer.Looking -> {
            ""
        }
    }

@Composable
private fun reason(
    reason: FailReason,
    title: String,
): String =
    when (reason) {
        FailReason.NoAnswer -> {
            stringResource(R.string.streams_no_answer, ANSWER_WITHIN.inWholeSeconds.toInt())
        }

        FailReason.NotFound -> {
            stringResource(R.string.streams_not_found, title)
        }

        FailReason.Blocked -> {
            stringResource(R.string.streams_blocked)
        }

        FailReason.SourceDown -> {
            stringResource(R.string.streams_source_down)
        }

        FailReason.WontStart -> {
            stringResource(R.string.streams_wont_start)
        }

        is FailReason.RateLimited -> {
            val seconds = reason.retryAfterSeconds
            when {
                seconds == null -> stringResource(R.string.streams_rate_limited)
                seconds < 60 -> stringResource(R.string.streams_rate_limited_seconds, seconds.toInt())
                else -> stringResource(R.string.streams_rate_limited_minutes, ((seconds + 59) / 60).toInt())
            }
        }
    }
