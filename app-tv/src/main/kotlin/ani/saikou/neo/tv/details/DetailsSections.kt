package ani.saikou.neo.tv.details

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ani.saikou.neo.shared.details.DetailsSection
import ani.saikou.neo.tv.R
import ani.saikou.neo.tv.ui.NeoSectionTab

internal fun detailsTabTag(section: DetailsSection) = "details-tab-${section.name.lowercase()}"

// From the design: 28 dp between the tabs' labels.
private val TabGap = 28.dp

private val sectionNames =
    mapOf(
        DetailsSection.OVERVIEW to R.string.details_overview,
        DetailsSection.EPISODES to R.string.details_episodes,
        DetailsSection.RELATED to R.string.details_related,
        DetailsSection.WATCH_ORDER to R.string.details_watch_order,
        DetailsSection.RECOMMENDATIONS to R.string.details_recommendations,
    )

/**
 * A row of tabs for [sections] with the [selected] one's [content] under it. Moving along the tabs selects each one
 * with nothing to press. Up or down into the row lands on the selected tab. [onInside] says whether focus is in the
 * content, which lifts Details.
 */
@Composable
internal fun DetailsSections(
    sections: List<DetailsSection>,
    selected: DetailsSection,
    onSelect: (DetailsSection) -> Unit,
    onInside: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (DetailsSection) -> Unit,
) {
    val tabs = remember { DetailsSection.entries.associateWith { FocusRequester() } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .focusProperties {
                    onEnter = {
                        if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
                            tabs.getValue(selected).requestFocus()
                        }
                    }
                }.focusGroup()
                .semantics { selectableGroup() },
            horizontalArrangement = Arrangement.spacedBy(TabGap),
        ) {
            for (section in sections) {
                NeoSectionTab(
                    text = stringResource(sectionNames.getValue(section)),
                    selected = section == selected,
                    modifier =
                        Modifier
                            .focusRequester(tabs.getValue(section))
                            .onFocusChanged { if (it.isFocused) onSelect(section) }
                            .testTag(detailsTabTag(section)),
                )
            }
        }
        Box(Modifier.onFocusChanged { onInside(it.hasFocus) }) { content(selected) }
    }
}
