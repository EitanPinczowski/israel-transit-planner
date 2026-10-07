@file:OptIn(ExperimentalLayoutApi::class)

package il.transit.planner.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import il.transit.core.geo.LatLon
import il.transit.core.search.PinName
import il.transit.planner.R
import il.transit.planner.ui.Field
import il.transit.planner.ui.MainActions
import il.transit.planner.ui.MapPin
import il.transit.planner.ui.PlaceDraft

/** "Pin · 32.1801, 34.8712": a point nothing names (offline, open country). */
@Composable
internal fun pinFallback(at: LatLon): String = stringResource(R.string.pin_name, PinName.coords(at))

/**
 * Long-press on the map (S1): the point's name (the coordinates until reverse geocoding
 * answers, or for good offline), then From here · To here · Save as place…. Shown in the
 * results' place, so it works the same in every tab.
 */
@Composable
internal fun MapPinPanel(pin: MapPin, vm: MainActions, modifier: Modifier, shape: Shape) {
    Sheet(modifier, shape) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(pin.name ?: pinFallback(pin.at), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (pin.naming) {
                    Text(stringResource(R.string.pin_naming), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = vm::closePin) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedButton(onClick = { vm.pinTo(Field.FROM) }) { Text(stringResource(R.string.pin_from)) }
            Button(onClick = { vm.pinTo(Field.TO) }) { Text(stringResource(R.string.pin_to)) }
            TextButton(onClick = vm::saveFromPin) { Text(stringResource(R.string.pin_save)) }
        }
    }
}

/** Saved places: "Save my current location" (S2), the fix named by one reverse geocode. */
@Composable
internal fun SaveHereRow(vm: MainActions) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = vm::saveHere).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.save_here), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.save_here_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The save dialog for a pin or the location fix: a name (the address to start from) and "Set as Home". */
@Composable
internal fun SavePlaceDialog(draft: PlaceDraft, vm: MainActions) {
    var name by remember(draft.at) { mutableStateOf(draft.name.orEmpty()) }
    var home by remember(draft.at) { mutableStateOf(draft.asHome) }
    // The suggested name arrives after the dialog opens: take it unless the user typed already.
    LaunchedEffect(draft.name) { if (name.isEmpty()) name = draft.name.orEmpty() }
    AlertDialog(
        onDismissRequest = vm::cancelDraft,
        title = { Text(stringResource(R.string.save_place)) },
        text = { SavePlaceContent(draft, name, { name = it }, home, { home = it }) },
        confirmButton = {
            if (draft.at != null) {
                TextButton(onClick = { vm.saveDraft(name.trim(), home) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
            }
        },
        dismissButton = { TextButton(onClick = vm::cancelDraft) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The dialog's body on its own, for screenshot tests (Paparazzi draws no dialog windows). */
@Composable
internal fun SavePlaceContent(draft: PlaceDraft, name: String, onName: (String) -> Unit, home: Boolean, onHome: (Boolean) -> Unit) {
    if (draft.at == null) {
        Text(stringResource(R.string.save_here_no_fix), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            singleLine = true,
            placeholder = { Text(if (draft.naming) stringResource(R.string.pin_naming) else stringResource(R.string.name_hint)) },
            supportingText = { Text(PinName.coords(draft.at)) },
        )
        Row(Modifier.fillMaxWidth().clickable { onHome(!home) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = home, onCheckedChange = onHome)
            Text(stringResource(R.string.set_as_home))
        }
    }
}
