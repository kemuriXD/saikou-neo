package ani.saikou.neo.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import ani.saikou.neo.shared.playback.FailReason
import ani.saikou.neo.shared.sources.CheckStatus
import ani.saikou.neo.shared.sources.CheckStep
import ani.saikou.neo.shared.sources.InstalledSource
import ani.saikou.neo.shared.sources.SourceCheck
import ani.saikou.neo.shared.sources.StepOutcome
import ani.saikou.neo.shared.sources.StepResult
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.details.failReasonText
import ani.saikou.neo.tv.theme.NeoTheme
import ani.saikou.neo.tv.ui.NeoButton
import ani.saikou.neo.tv.ui.NeoButtonRow
import ani.saikou.neo.tv.ui.NeoIcons
import java.io.File
import kotlin.time.Duration

internal const val CHECK_ALL_TAG = "check-all"

internal fun checkTag(id: String) = "check-$id"

/** Whether a check is still to finish. */
internal val SourceCheck.going get() = status == CheckStatus.WAITING || status == CheckStatus.RUNNING

private val SourceCheck.failed get() = steps.firstNotNullOfOrNull { (it.outcome as? StepOutcome.Failed)?.reason }

private val SourceCheck.took get() = steps.fold(Duration.ZERO) { total, step -> total + step.took }

@Composable
private fun seconds(took: Duration) = stringResource(R.string.checks_seconds, took.inWholeMilliseconds / 1000f)

/** Why a check failed, after "Failed:". A missing show is the check's test episode, not one the viewer picked. */
@Composable
private fun failedText(reason: FailReason): String {
    val why = if (reason == FailReason.NotFound) stringResource(R.string.checks_not_found) else failReasonText(reason, "")
    return stringResource(R.string.checks_failed, why.replaceFirstChar { it.lowercase() })
}

/** Where a source's check has got to, in a row's second line, with an icon in its colour. */
@Composable
internal fun checkStatus(check: SourceCheck): Pair<String, Pair<ImageVector, Color>> {
    val colors = NeoTheme.colors
    val reason = check.failed
    return when {
        check.status == CheckStatus.WAITING -> stringResource(R.string.checks_waiting) to (NeoIcons.Waiting to colors.textMuted)
        check.status == CheckStatus.RUNNING -> stringResource(check.running) to (NeoIcons.Busy to colors.pink)
        reason != null -> failedText(reason) to (NeoIcons.Warning to colors.warning)
        check.status == CheckStatus.STOPPED -> stringResource(R.string.checks_stopped) to (NeoIcons.Waiting to colors.textMuted)
        else -> stringResource(R.string.checks_works, seconds(check.took)) to (NeoIcons.Passed to colors.paper)
    }
}

// Steps run in order, so the one running is the one after those done.
private val SourceCheck.running
    get() =
        when (CheckStep.entries.getOrNull(steps.size)) {
            CheckStep.START, null -> R.string.checks_running_start
            CheckStep.SITE -> R.string.checks_running_site
            CheckStep.SERVERS -> R.string.checks_running_servers
            CheckStep.STREAM -> R.string.checks_running_stream
        }

/** Check all, or Stop checks while any check is [going]. */
@Composable
internal fun CheckAllPill(
    going: Boolean,
    onCheckAll: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier,
) {
    val text = stringResource(if (going) R.string.checks_stop_all else R.string.checks_check_all)
    RowPill(text, if (going) NeoIcons.Stop else NeoIcons.Pulse, text, if (going) onStop else onCheckAll, modifier)
}

private enum class Phase(
    val title: Int,
) {
    RUNNING(R.string.checks_title_running),
    PASSED(R.string.checks_title_passed),
    FAILED(R.string.checks_title_failed),
    STOPPED(R.string.checks_title_stopped),
}

private val SourceCheck?.phase
    get() =
        when {
            this == null || going -> Phase.RUNNING
            failed != null -> Phase.FAILED
            status == CheckStatus.STOPPED -> Phase.STOPPED
            else -> Phase.PASSED
        }

/**
 * A source's check: each step with what happened and how long it took, and a line under them. While it runs, Stop is
 * focused; once it passes, Close; once it fails or stops, Check again.
 */
@Composable
internal fun BoxScope.CheckPanel(
    source: InstalledSource,
    check: SourceCheck?,
    active: Boolean,
    onStop: () -> Unit,
    onAgain: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val colors = NeoTheme.colors
    val phase = check.phase
    val primary = remember { FocusRequester() }
    LaunchedEffect(phase) { primary.requestFocus() }
    val title = stringResource(phase.title)
    SidePanel(title, SourcePanelWidth, active, onClose, modifier) {
        Text(title, style = PaneTitle, color = colors.paper)
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            Row(
                Modifier.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceBadge(source.name, source.icon?.let(::File))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(source.name, style = RowTitle, color = colors.paper)
                    Text(stringResource(R.string.sources_version, source.version), style = Small, color = colors.textMuted)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        }
        val steps = check?.steps.orEmpty()
        CheckStep.entries.forEachIndexed { index, step ->
            StepLine(step, steps.firstOrNull { it.step == step }, running = phase == Phase.RUNNING && index == steps.size, phase)
        }
        Text(
            when (phase) {
                Phase.RUNNING -> stringResource(R.string.checks_summary_running)
                Phase.PASSED -> stringResource(R.string.checks_summary_passed, seconds(check?.took ?: Duration.ZERO))
                Phase.FAILED -> stringResource(R.string.checks_summary_failed)
                Phase.STOPPED -> stringResource(R.string.checks_summary_stopped)
            },
            style = Body,
            color = colors.textMuted,
        )
        Spacer(Modifier.weight(1f))
        val first = Modifier.focusRequester(primary)
        key(phase) {
            NeoButtonRow {
                when (phase) {
                    Phase.RUNNING -> {
                        NeoButton(stringResource(R.string.checks_stop), onStop, first)
                    }

                    Phase.PASSED -> {
                        NeoButton(stringResource(R.string.sources_close), onClose, first)
                        NeoButton(stringResource(R.string.checks_again), onAgain)
                    }

                    Phase.FAILED, Phase.STOPPED -> {
                        NeoButton(stringResource(R.string.checks_again), onAgain, first)
                        NeoButton(stringResource(R.string.sources_close), onClose)
                    }
                }
            }
        }
    }
}

/** One step: an icon, its name, what happened, and how long it took. The words carry the result, not the icon. */
@Composable
private fun StepLine(
    step: CheckStep,
    result: StepResult?,
    running: Boolean,
    phase: Phase,
) {
    val colors = NeoTheme.colors
    val outcome = result?.outcome
    val (icon, tint) =
        when {
            outcome is StepOutcome.Failed -> NeoIcons.Warning to colors.warning
            outcome != null -> NeoIcons.Passed to colors.paper
            running -> NeoIcons.Busy to colors.pink
            else -> NeoIcons.Waiting to colors.textFaint
        }
    val detail =
        when {
            outcome is StepOutcome.Failed -> failedText(outcome.reason)
            outcome == StepOutcome.Skipped -> stringResource(R.string.checks_skipped_site)
            outcome != null -> stringResource(step.passed)
            running -> stringResource(R.string.checks_in_progress)
            phase == Phase.RUNNING -> stringResource(R.string.checks_waiting)
            else -> stringResource(R.string.checks_not_checked)
        }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 1.dp).size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(step.label), style = RowTitle, color = if (result == null) colors.textMuted else colors.paper)
            Text(detail, style = Small, color = colors.textMuted)
        }
        result?.let {
            Text(
                seconds(it.took),
                style = Body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = colors.textMuted,
            )
        }
    }
}

private val CheckStep.label
    get() =
        when (this) {
            CheckStep.START -> R.string.checks_step_start
            CheckStep.SITE -> R.string.checks_step_site
            CheckStep.SERVERS -> R.string.checks_step_servers
            CheckStep.STREAM -> R.string.checks_step_stream
        }

private val CheckStep.passed
    get() =
        when (this) {
            CheckStep.START -> R.string.checks_passed_start
            CheckStep.SITE -> R.string.checks_passed_site
            CheckStep.SERVERS -> R.string.checks_passed_servers
            CheckStep.STREAM -> R.string.checks_passed_stream
        }
