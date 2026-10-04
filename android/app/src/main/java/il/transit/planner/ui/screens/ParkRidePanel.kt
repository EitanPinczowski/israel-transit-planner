@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package il.transit.planner.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.transit.core.features.NavLinks
import il.transit.core.features.ParkRideOption
import il.transit.core.features.ParkRideResult
import il.transit.core.present.ParkRideRow
import il.transit.core.present.driveHomeMin
import il.transit.core.present.hhmm
import il.transit.core.present.parkRideRow
import il.transit.planner.R
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.PlaceRef
import il.transit.planner.ui.UiState

@Composable
internal fun ParkRideList(state: UiState, result: ParkRideResult, vm: MainViewModel) {
    val context = LocalContext.current
    LazyColumn(Modifier.heightIn(max = 360.dp)) {
        if (result.options.isEmpty()) {
            item { Text(stringResource(R.string.no_park_ride, state.parkRide.maxDriveMin), Modifier.padding(vertical = 8.dp)) }
        }
        itemsIndexed(result.options) { i, option ->
            val row = remember(option, result.baseline, state.settings.fareProfile) {
                parkRideRow(option.payload, result.baseline, state.settings.fareProfile)
            }
            val at = option.payload.stationAt
            ParkRideCard(row, selected = i == state.selected, onClick = { vm.select(i) }) {
                // The user drives: open the navigation app straight away (Waze, else the browser).
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(NavLinks.waze(at))))
            }
        }
        if (state.parkRide.selected(state.selected) != null) {
            item { WayBackRow(vm, R.string.way_back_to_car) }
        }
        result.baseline?.let { b ->
            item {
                Text(
                    stringResource(R.string.without_car, hhmm(b.end)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ParkRideCard(row: ParkRideRow, selected: Boolean, onClick: () -> Unit, onNavigate: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).background(bg, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(stringResource(R.string.park_at, row.station, row.driveMin), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.you_arrive, row.arrive), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val transfers = if (row.transfers == 0) stringResource(R.string.direct) else pluralStringResource(R.plurals.transfers, row.transfers, row.transfers)
            Text(transfers, style = MaterialTheme.typography.labelLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.park_ride_leave, row.leave, row.board), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            val gains = listOfNotNull(
                row.savedMin?.takeIf { it > 0 }?.let { stringResource(R.string.saves_min, it) },
                row.transfersSaved?.takeIf { it > 0 }?.let { stringResource(R.string.fewer_transfers) },
            )
            if (gains.isNotEmpty()) Text(gains.joinToString(" · "), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            row.summary.chips.forEach { LegChipView(it) }
            FareText(row.summary.fare)
        }
        if (selected) {
            TextButton(onClick = onNavigate) { Text(stringResource(R.string.navigate_to_station)) }
        }
    }
}

/** The parked car, while the Trip tab shows the way back to its station. */
internal fun parkedCarFor(state: UiState): ParkRideOption? =
    state.parkRide.parked?.takeIf { p -> (state.to as? PlaceRef.Point)?.at == p.stationAt }

/** "Then drive home from Be'er Sheva North: about 19 min" — an estimate, no request. */
@Composable
internal fun DriveHomeRow(state: UiState, parked: ParkRideOption) {
    val backAt = state.selectedItinerary?.end ?: return
    val min = remember(parked, backAt, state.settings) { driveHomeMin(parked, backAt, state.settings.traffic()) }
    Text(
        stringResource(R.string.drive_home_from, parked.station.name, min),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}
