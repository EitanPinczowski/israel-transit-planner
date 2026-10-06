package il.transit.planner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import il.transit.planner.R
import il.transit.planner.ui.*

/** The mode tabs, then the ⋮ menu: always one line, never scrolling sideways (owner, 2026-10-05:
 *  "I don't want two lines for the buttons"). Five labelled chips need ~470 dp, a small phone
 *  gives them ~270, so each mode is an icon in an equal segment and the selected one sits in a
 *  pill with its name under it, like a navigation bar. Where every name fits under its own icon
 *  (wide phones, landscape, tablets) all names show. Each tab still says its name: content
 *  description for TalkBack, and a tooltip on long press. */
@Composable
internal fun ModeRow(state: UiState, vm: MainActions) {
    Row(verticalAlignment = Alignment.Top) {
        ModeTabs(state.mode, vm::setMode, Modifier.weight(1f))
        OverflowMenu(vm)
    }
}

/** Test tag of a mode tab (the tabs have no text node: their name is the content description). */
internal fun modeTabTag(mode: AppMode): String = "mode_tab_${mode.name.lowercase()}"

internal fun modeIcon(mode: AppMode): Int = when (mode) {
    AppMode.TRIP -> R.drawable.ic_mode_trip
    AppMode.BETTER_START -> R.drawable.ic_mode_better_start
    AppMode.DROP_OFF -> R.drawable.ic_mode_drop_off
    AppMode.PICK_UP -> R.drawable.ic_mode_pick_up
    AppMode.PARK_RIDE -> R.drawable.ic_mode_park_ride
}

/** A tab is a full 48 dp tap target; the names start a little above its bottom edge. */
private val TAB_HEIGHT = 48.dp
private val LABEL_TOP = 42.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeTabs(selected: AppMode, onSelect: (AppMode) -> Unit, modifier: Modifier = Modifier) {
    val modes = AppMode.entries
    val colors = MaterialTheme.colorScheme
    Layout(
        modifier = modifier.selectableGroup(),
        content = {
            modes.forEach { mode ->
                val name = stringResource(modeTabLabel(mode))
                val on = mode == selected
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(name) } },
                    state = rememberTooltipState(),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(TAB_HEIGHT)
                            .testTag(modeTabTag(mode))
                            .selectable(selected = on, role = Role.Tab, onClick = { onSelect(mode) })
                            .semantics { contentDescription = name },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .widthIn(max = 64.dp)
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(if (on) colors.secondaryContainer else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(modeIcon(mode)),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (on) colors.onSecondaryContainer else colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            // The names, laid out under the icons below. The tab above already says its name.
            modes.forEach { mode ->
                val on = mode == selected
                Text(
                    stringResource(modeTabLabel(mode)),
                    Modifier.clearAndSetSemantics {},
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    ) { measurables, constraints ->
        val n = modes.size
        val width = constraints.maxWidth
        val seg = width / n
        val start = (width - seg * n) / 2
        val tabH = TAB_HEIGHT.roundToPx()
        val tabs = measurables.take(n).map { it.measure(Constraints.fixed(seg, tabH)) }
        val labels = measurables.drop(n).map { it.measure(Constraints(maxWidth = width)) }
        // All names, or only the selected one: a mix would look like a glitch.
        val showAll = labels.all { it.width <= seg }
        val labelTop = LABEL_TOP.roundToPx()
        layout(width, maxOf(tabH, labelTop + labels.maxOf { it.height })) {
            tabs.forEachIndexed { i, p -> p.placeRelative(start + i * seg, 0) }
            labels.forEachIndexed { i, p ->
                if (showAll || modes[i] == selected) {
                    // Centred under its icon; a name wider than its segment slides inwards at the ends.
                    val x = (start + i * seg + (seg - p.width) / 2).coerceIn(0, width - p.width)
                    p.placeRelative(x, labelTop)
                }
            }
        }
    }
}
