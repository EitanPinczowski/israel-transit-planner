@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package il.transit.planner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import il.transit.core.fare.FareProfile
import il.transit.core.features.ISRAEL
import il.transit.core.user.ModeFilter
import il.transit.core.user.PlaceRoutine
import il.transit.core.user.SavedPlace
import il.transit.core.user.UserSettings
import il.transit.core.user.WalkSpeed
import il.transit.planner.R
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.OfflineState
import il.transit.planner.ui.ScreenActions
import il.transit.planner.ui.UiState
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun SettingsDialog(state: UiState, vm: MainViewModel, actions: ScreenActions) {
    val s = state.settings
    fun set(n: UserSettings) = vm.updateSettings(n)
    var editingRoutine by remember { mutableStateOf<SavedPlace?>(null) }
    editingRoutine?.let { p ->
        RoutineDialog(p, onDismiss = { editingRoutine = null }) { r -> vm.setRoutine(p, r); editingRoutine = null }
    }
    AlertDialog(
        onDismissRequest = { vm.showSettings(false) },
        confirmButton = { TextButton(onClick = { vm.showSettings(false) }) { Text(stringResource(R.string.done)) } },
        title = { Text(stringResource(R.string.settings)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Section(R.string.max_transfers) {
                        UserSettings.TRANSFER_CHOICES.forEach { n ->
                            FilterChip(s.maxTransfers == n, { set(s.copy(maxTransfers = n)) }, label = { Text(n?.toString() ?: stringResource(R.string.any)) })
                        }
                    }
                }
                item {
                    Section(R.string.max_walk) {
                        UserSettings.WALK_CHOICES.forEach { m ->
                            FilterChip(s.maxWalkMin == m, { set(s.copy(maxWalkMin = m)) }, label = { Text(stringResource(R.string.minutes_short, m)) })
                        }
                    }
                }
                item {
                    Section(R.string.modes) {
                        ModeFilter.entries.forEach { f ->
                            val label = when (f) {
                                ModeFilter.ALL -> R.string.mode_all
                                ModeFilter.TRAINS_ONLY -> R.string.mode_trains
                                ModeFilter.NO_BUSES -> R.string.mode_no_buses
                            }
                            FilterChip(s.modeFilter == f, { set(s.copy(modeFilter = f)) }, label = { Text(stringResource(label)) })
                        }
                    }
                }
                item {
                    Section(R.string.walk_speed) {
                        WalkSpeed.entries.forEach { w ->
                            val label = when (w) {
                                WalkSpeed.SLOW -> R.string.speed_slow
                                WalkSpeed.NORMAL -> R.string.speed_normal
                                WalkSpeed.FAST -> R.string.speed_fast
                            }
                            FilterChip(s.walkSpeed == w, { set(s.copy(walkSpeed = w)) }, label = { Text(stringResource(label)) })
                        }
                    }
                }
                item {
                    Section(R.string.fare_profile) {
                        FareProfile.entries.forEach { f ->
                            val label = when (f) {
                                FareProfile.REGULAR -> R.string.fare_regular
                                FareProfile.HALF -> R.string.fare_half
                                FareProfile.FREE -> R.string.fare_free
                            }
                            FilterChip(s.fareProfile == f, { set(s.copy(fareProfile = f)) }, label = { Text(stringResource(label)) })
                        }
                    }
                }
                item {
                    Section(R.string.ride_alerts) {
                        FilterChip(s.speakAlerts, { set(s.copy(speakAlerts = !s.speakAlerts)) }, label = { Text(stringResource(R.string.speak_alerts)) })
                    }
                }
                item {
                    Section(R.string.accessibility) {
                        FilterChip(s.accessible, { set(s.copy(accessible = !s.accessible)) }, label = { Text(stringResource(R.string.accessible_routes)) })
                    }
                    Text(stringResource(R.string.accessible_help), style = MaterialTheme.typography.bodySmall)
                }
                item {
                    Text(stringResource(R.string.traffic_factor, String.format(Locale.US, "%.1f", s.peakFactor)), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = s.peakFactor.toFloat(),
                        onValueChange = { v -> set(s.copy(peakFactor = Math.round(v * 10) / 10.0)) },
                        valueRange = UserSettings.PEAK_RANGE.start.toFloat()..UserSettings.PEAK_RANGE.endInclusive.toFloat(),
                        steps = 7,
                    )
                    Text(stringResource(R.string.traffic_factor_help), style = MaterialTheme.typography.bodySmall)
                }
                item { CalendarBufferSetting(s.calendarBufferMin) { set(s.copy(calendarBufferMin = it)) } }
                item { OfflineSection(actions) }
                if (state.savedPlaces.isNotEmpty() || state.savedTrips.isNotEmpty()) {
                    item { Text(stringResource(R.string.saved), style = MaterialTheme.typography.labelLarge) }
                    items(state.savedTrips) { t -> SavedRow("↗ ${t.name}") { vm.deleteTrip(t) } }
                    items(state.savedPlaces) { p -> SavedPlaceRow(p, onRoutine = { editingRoutine = p }) { vm.deletePlace(p) } }
                }
            }
        },
    )
}

@Composable
internal fun HistoryDialog(state: UiState, vm: MainViewModel) {
    val st = state.historyStats
    AlertDialog(
        onDismissRequest = { vm.showHistory(false) },
        confirmButton = { TextButton(onClick = { vm.showHistory(false) }) { Text(stringResource(R.string.done)) } },
        dismissButton = {
            if (state.history.isNotEmpty()) TextButton(onClick = { vm.clearHistory() }) { Text(stringResource(R.string.history_clear)) }
        },
        title = { Text(stringResource(R.string.history)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.history.isEmpty()) {
                    item { Text(stringResource(R.string.history_empty)) }
                } else {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(st.trips.toString(), stringResource(R.string.stat_trips))
                            StatTile(st.tripsThisWeek.toString(), stringResource(R.string.stat_week))
                            StatTile(String.format(Locale.US, "%.1f", st.transitHours), stringResource(R.string.stat_transit_hours))
                            StatTile(st.minutesSaved.toString(), stringResource(R.string.stat_saved))
                        }
                    }
                    st.topDestination?.takeIf { it.isNotBlank() }?.let { top ->
                        item { Text(stringResource(R.string.stat_top_destination, top), style = MaterialTheme.typography.bodySmall) }
                    }
                    item { HorizontalDivider() }
                    items(state.history.take(30)) { r ->
                        val from = r.from.ifBlank { stringResource(R.string.my_location) }
                        val to = r.to.ifBlank { stringResource(R.string.my_location) }
                        Column {
                            Text("$from – $to", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val date = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm").format(r.startedAt.atZone(ISRAEL))
                            val saved = r.savedMin?.let { " · " + stringResource(R.string.saves_min, it) }.orEmpty()
                            Text(
                                "$date · " + stringResource(R.string.minutes_short, r.transitMin + r.walkMin) + saved,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun StatTile(value: String, label: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun OfflineSection(actions: ScreenActions) {
    val o = actions.offline
    Column {
        Text(stringResource(R.string.offline_map), style = MaterialTheme.typography.labelLarge)
        val status = when (o.status) {
            OfflineState.Status.NONE -> stringResource(R.string.offline_none)
            OfflineState.Status.DOWNLOADING -> stringResource(R.string.offline_downloading, o.percent)
            OfflineState.Status.READY -> stringResource(R.string.offline_ready, String.format(Locale.US, "%.1f", o.sizeMb))
            OfflineState.Status.TOO_BIG -> stringResource(R.string.offline_too_big)
            OfflineState.Status.FAILED -> stringResource(R.string.offline_failed)
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(onClick = actions.downloadOffline, enabled = o.status != OfflineState.Status.DOWNLOADING) {
                Text(stringResource(R.string.offline_download))
            }
            if (o.status == OfflineState.Status.READY) {
                TextButton(onClick = actions.deleteOffline) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}

@Composable
private fun Section(titleRes: Int, chips: @Composable () -> Unit) {
    Column {
        Text(stringResource(titleRes), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips() }
    }
}

/** A saved place: its routine under the name, ⏰ to edit it, 🗑 to delete the place. */
@Composable
private fun SavedPlaceRow(p: SavedPlace, onRoutine: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("★ ${p.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            p.routine?.let { Text(routineLabel(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        TextButton(onClick = onRoutine) { Text("⏰") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
    }
}

/** "Sun Mon Tue 07:00–10:00", days in Israeli week order. */
private fun routineLabel(r: PlaceRoutine): String {
    val days = PlaceRoutine.WEEK.filter { it in r.days }
        .joinToString(" ") { java.time.DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    return "$days \u2068${hhmmOf(r.fromMin)}–${hhmmOf(r.toMin)}\u2069"
}

private fun hhmmOf(min: Int) = String.format(Locale.US, "%02d:%02d", min / 60, min % 60)

/** Days (Sunday first) and a window; Save, Remove (when one is set) or Cancel. */
@Composable
private fun RoutineDialog(p: SavedPlace, onDismiss: () -> Unit, onSave: (PlaceRoutine?) -> Unit) {
    val r = p.routine
    val days = remember { mutableStateListOf<Int>().apply { addAll(r?.days ?: listOf(7, 1, 2, 3, 4)) } }
    val from = rememberTimePickerState(initialHour = (r?.fromMin ?: 420) / 60, initialMinute = (r?.fromMin ?: 420) % 60, is24Hour = true)
    val to = rememberTimePickerState(initialHour = (r?.toMin ?: 600) / 60, initialMinute = (r?.toMin ?: 600) % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routine_title, p.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.routine_help), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlaceRoutine.WEEK.forEach { d ->
                        FilterChip(
                            selected = d in days,
                            onClick = { if (d in days) days.remove(d) else days.add(d) },
                            label = { Text(java.time.DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
                Text(stringResource(R.string.routine_from), style = MaterialTheme.typography.labelLarge)
                TimeInput(state = from)
                Text(stringResource(R.string.routine_to), style = MaterialTheme.typography.labelLarge)
                TimeInput(state = to)
            }
        },
        confirmButton = {
            TextButton(
                enabled = days.isNotEmpty(),
                onClick = { onSave(PlaceRoutine(days.toList().sorted(), from.hour * 60 + from.minute, to.hour * 60 + to.minute)) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (r != null) TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.routine_remove)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun SavedRow(label: String, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
    }
}
