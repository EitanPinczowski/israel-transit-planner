package il.transit.planner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.transit.core.api.Itinerary
import il.transit.core.present.RideLeg
import il.transit.core.present.TransferWarning
import il.transit.core.present.rideDetails
import il.transit.core.present.tightTransfers
import il.transit.planner.R

/**
 * Ride details on an option card (Phase 10 D1): one compact line for the whole option on an
 * unselected card, one line per ride on the selected one. All numbers come from
 * `present/RideDetails.kt`; this file only joins strings.
 */
@Composable
internal fun RideDetailsLines(itin: Itinerary, selected: Boolean) {
    val d = remember(itin) { rideDetails(itin) }
    val total = d.total ?: return
    val style = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (!selected) {
        Text(
            stringResource(
                R.string.ride_total,
                pluralStringResource(R.plurals.ride_stops, total.stops, total.stops),
                stringResource(R.string.ride_min_on_board, total.minutes),
                stringResource(R.string.ride_off_at, total.getOff),
            ),
            style = style,
            color = muted,
            maxLines = 2,
        )
        return
    }
    Column {
        d.shown.forEach { r -> Text(rideLegText(r), style = style, color = muted) }
        if (d.more > 0) Text(pluralStringResource(R.plurals.ride_more, d.more, d.more), style = style, color = muted)
    }
}

@Composable
private fun rideLegText(r: RideLeg): String = stringResource(
    R.string.ride_leg,
    r.line?.let { stringResource(R.string.ride_line, it) } ?: kindName(r.kind),
    pluralStringResource(R.plurals.ride_stops, r.stops, r.stops),
    stringResource(R.string.ride_min, r.minutes),
    r.stopCode?.let { stringResource(R.string.ride_get_off_code, r.getOff, it) } ?: stringResource(R.string.ride_get_off, r.getOff),
)

/**
 * Amber chips on the changes of vehicle that are tight: under 5 min, under 8 with a train,
 * never under the user's buffer (owner decision 1). The text says the risk, so colour is never
 * the only signal; `tertiaryContainer` on its text passes WCAG AA in both themes (`ContrastTest`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransferChips(itin: Itinerary, bufferMin: Int?) {
    val tight = remember(itin, bufferMin) { tightTransfers(itin, bufferMin) }
    if (tight.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 2.dp)) {
        tight.forEach { (t, w) ->
            val text = when (w) {
                TransferWarning.CHANGE_IN -> stringResource(R.string.transfer_change_in, t.stop, t.slackMin)
                TransferWarning.WALK_SPARE -> stringResource(R.string.transfer_walk_spare, t.walkMin, t.slackMin)
                TransferWarning.TIGHT -> stringResource(R.string.transfer_tight)
            }
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                Text("⚠ $text", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

