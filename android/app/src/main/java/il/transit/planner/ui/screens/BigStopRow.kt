package il.transit.planner.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.transit.core.present.BigStopHow
import il.transit.core.present.LegKind
import il.transit.core.present.bigStopText
import il.transit.planner.R
import il.transit.planner.ui.MainActions
import il.transit.planner.ui.UiState
import il.transit.planner.ui.bigStopFor

/**
 * "Safer change" on the selected card (Phase 10 D5): the same two vehicles, changing at a big
 * stop with more time; "Use this change" re-cuts the option, "Back to the original change"
 * undoes it. Shown only once the check found one (it runs when the option is opened).
 */
@Composable
internal fun BigStopRow(state: UiState, vm: MainActions) {
    val (change, applied) = bigStopFor(state) ?: return
    val t = remember(change) { bigStopText(change) }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)) {
            if (applied) {
                Text(stringResource(R.string.big_stop_applied, t.boardStop, t.slackMin, t.originalSlackMin), style = MaterialTheme.typography.bodySmall)
            } else {
                val first = line(t.firstLine, t.firstKind)
                val second = line(t.secondLine, t.secondKind)
                val what = when (t.how) {
                    BigStopHow.STAY_ON -> pluralStringResource(R.plurals.big_stop_stay, t.stops, first, t.stops, t.stop, second)
                    BigStopHow.EARLIER -> pluralStringResource(R.plurals.big_stop_earlier, t.stops, first, t.stops, t.stop, second)
                    BigStopHow.SAME_STOP -> stringResource(R.string.big_stop_board, second, t.boardStop)
                }
                Text(what, style = MaterialTheme.typography.bodySmall)
                // Owner decision B: the second vehicle may leave from a big stop nearby; say the walk.
                if (t.walkMin > 0) {
                    Text(stringResource(R.string.big_stop_walk, t.walkMin, t.boardStop), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.big_stop_same, t.slackMin, t.originalSlackMin), style = MaterialTheme.typography.bodySmall)
            }
            // No start padding, so the label lines up with the text above it (LTR and RTL).
            TextButton(onClick = { vm.useBigStop(!applied) }, contentPadding = PaddingValues(start = 0.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
                Text(stringResource(if (applied) R.string.big_stop_back else R.string.big_stop_use))
            }
        }
    }
}

/** "Line 5", or the vehicle kind when it has no short label (a train). */
@Composable
private fun line(label: String?, kind: LegKind): String =
    label?.let { stringResource(R.string.ride_line, it) } ?: kindName(kind)
