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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.transit.core.features.DropOffKind
import il.transit.core.features.DropOffResult
import il.transit.core.features.NavLinks
import il.transit.core.present.DropOffRow
import il.transit.core.present.dropOffRow
import il.transit.planner.R
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.UiState

@Composable
internal fun DropOffList(state: UiState, result: DropOffResult, vm: MainViewModel) {
    val context = LocalContext.current
    LazyColumn(Modifier.heightIn(max = 360.dp)) {
        result.directDriveSec?.let { sec ->
            item {
                Text(
                    stringResource(R.string.drive_takes, Math.round(sec / 60.0).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (result.options.none { it.payload.kind == DropOffKind.STOP_ON_THE_WAY }) {
            item { Text(stringResource(R.string.no_stop_on_the_way, state.maxDetourMin), Modifier.padding(vertical = 8.dp)) }
        }
        itemsIndexed(result.options) { i, option ->
            val row = remember(option, state.settings.fareProfile) { dropOffRow(option.payload, state.settings.fareProfile) }
            // Locals, not row.stop/row.stopAt: no smart casts across modules.
            val stop = row.stop
            val at = row.stopAt
            val shareText = if (stop != null && at != null) {
                stringResource(R.string.share_dropoff, stop, NavLinks.waze(at), NavLinks.googleMaps(at))
            } else {
                null
            }
            DropOffCard(row, selected = i == state.selected, onClick = { vm.select(i) }, onSend = shareText?.let { text ->
                {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, null))
                }
            })
        }
    }
}

@Composable
private fun DropOffCard(row: DropOffRow, selected: Boolean, onClick: () -> Unit, onSend: (() -> Unit)?) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).background(bg, RoundedCornerShape(12.dp)).padding(10.dp)) {
        val title = when (row.kind) {
            DropOffKind.STOP_ON_THE_WAY -> stringResource(R.string.get_out_at, row.stop.orEmpty(), row.detourMin)
            DropOffKind.RIDE_TO_END -> stringResource(R.string.ride_to_end)
            DropOffKind.TRANSIT_FROM_START -> stringResource(R.string.transit_from_start)
        }
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.you_arrive, row.arrive), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val transfers = if (row.transfers == 0) stringResource(R.string.direct) else pluralStringResource(R.plurals.transfers, row.transfers, row.transfers)
            Text(transfers, style = MaterialTheme.typography.labelLarge)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            row.summary.chips.forEach { LegChipView(it) }
            FareText(row.summary.fare)
        }
        if (selected && onSend != null) {
            TextButton(onClick = onSend) { Text(stringResource(R.string.send_to_driver)) }
        }
    }
}
