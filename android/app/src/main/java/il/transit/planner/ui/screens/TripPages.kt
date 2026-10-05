package il.transit.planner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.transit.core.plan.TripPages
import il.transit.core.present.StopPlatform
import il.transit.core.present.hhmm
import il.transit.planner.R
import il.transit.planner.ui.UiState
import il.transit.planner.ui.MainActions

/** "Platform 12 · floor 6 · stop 47899", or null when the stop data has none of them. */
@Composable
internal fun platformText(p: StopPlatform): String? {
    if (p.isEmpty) return null
    return listOfNotNull(
        p.platform?.let { stringResource(R.string.stop_platform, it) },
        p.floor?.let { stringResource(R.string.stop_floor, it) },
        p.stopCode?.let { stringResource(R.string.stop_code, it) },
    ).joinToString(" · ")
}

/** The line under a stop name; nothing at all when the data is blank. */
@Composable
internal fun PlatformLine(p: StopPlatform) {
    val text = platformText(p) ?: return
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** True when the Trip tab lists a plain A → B answer that can be paged (not an errand chain). */
internal fun UiState.canPage(): Boolean = mode == il.transit.planner.ui.AppMode.TRIP && chain == null && results != null

/**
 * On the selected Trip option: "If I miss this: next at 08:35 (+30 min)", from the options
 * already loaded. When none leaves later, "show later" does what the Later button does.
 * Never sends a request on its own.
 */
@Composable
internal fun MissLine(state: UiState, vm: MainActions) {
    val sel = state.selectedItinerary ?: return
    val options = state.options
    val miss = remember(sel, options) { TripPages.ifIMissIt(sel, options) } ?: return
    when (miss) {
        is TripPages.Miss.Next -> Text(
            stringResource(R.string.miss_next, hhmm(miss.next.firstTransitLeg!!.start), miss.laterMin),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TripPages.Miss.ShowLater -> if (state.results?.laterCursor != null) {
            TextButton(onClick = vm::later, enabled = state.pages.loading == null, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(stringResource(R.string.miss_show_later), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** "Earlier" and "Later" under the options: one request per tap, merged into the list. */
@Composable
internal fun PageButtons(state: UiState, vm: MainActions) {
    val r = state.results ?: return
    if (r.earlierCursor == null && r.laterCursor == null) return
    val busy = state.pages.loading
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PageButton(R.string.page_earlier, r.earlierCursor != null && busy == null, busy == TripPages.Direction.EARLIER, vm::earlier, Modifier.weight(1f))
            PageButton(R.string.page_later, r.laterCursor != null && busy == null, busy == TripPages.Direction.LATER, vm::later, Modifier.weight(1f))
        }
        if (state.pages.failed) {
            Text(stringResource(R.string.page_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PageButton(label: Int, enabled: Boolean, loading: Boolean, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(label))
    }
}
