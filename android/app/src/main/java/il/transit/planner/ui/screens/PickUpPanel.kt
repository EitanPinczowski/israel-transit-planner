@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package il.transit.planner.ui.screens

import android.content.Intent
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.transit.core.features.NavLinks
import il.transit.core.features.PickUpResult
import il.transit.core.present.PickUpRow
import il.transit.core.present.hhmm
import il.transit.core.present.pickUpRow
import il.transit.planner.R
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.UiState

@Composable
internal fun PickUpList(state: UiState, result: PickUpResult, vm: MainViewModel) {
    val context = LocalContext.current
    LazyColumn(Modifier.heightIn(max = 360.dp)) {
        if (result.options.isEmpty()) {
            item { Text(stringResource(R.string.no_pick_up, state.maxPickUpDriveMin), Modifier.padding(vertical = 8.dp)) }
        }
        itemsIndexed(result.options) { i, option ->
            val row = remember(option, result.baseline, state.settings.fareProfile) { pickUpRow(option, result.baseline, state.settings.fareProfile) }
            val shareText = stringResource(
                R.string.share_pickup,
                row.stop,
                row.pickUpTime,
                row.driverLeaves,
                NavLinks.waze(row.stopAt),
                NavLinks.googleMaps(row.stopAt),
            )
            PickUpCard(row, selected = i == state.selected, onClick = { vm.select(i) }) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                context.startActivity(Intent.createChooser(send, null))
            }
        }
        result.baseline?.let { b ->
            item {
                Text(
                    stringResource(R.string.transit_all_the_way, hhmm(b.end)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PickUpCard(row: PickUpRow, selected: Boolean, onClick: () -> Unit, onSend: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).background(bg, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(stringResource(R.string.picked_up_at, row.stop, row.pickUpTime), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_at, row.arriveHome), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val gains = listOfNotNull(
                row.savedMin?.takeIf { it > 0 }?.let { stringResource(R.string.saves_min, it) },
                row.transfersSaved?.takeIf { it > 0 }?.let { stringResource(R.string.fewer_transfers) },
            )
            if (gains.isNotEmpty()) Text(gains.joinToString(" · "), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        Text(
            stringResource(R.string.driver_leaves, row.driverLeaves, row.roundTripMin),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            row.summary.chips.forEach { LegChipView(it) }
            FareText(row.summary.fare)
        }
        if (selected) {
            TextButton(onClick = onSend) { Text(stringResource(R.string.send_to_driver)) }
        }
    }
}
