@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package il.transit.planner.ui.screens

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import il.transit.core.api.Itinerary
import il.transit.core.fare.FareEstimate
import il.transit.core.fare.FareProfile
import il.transit.core.fare.FareTable
import il.transit.core.features.BetterStartResult
import il.transit.core.features.DropOffKind
import il.transit.core.features.DropOffResult
import il.transit.core.features.ISRAEL
import il.transit.core.features.NavLinks
import il.transit.core.features.PickUpResult
import il.transit.core.geo.LatLon
import il.transit.core.plan.CarCompare
import il.transit.core.plan.ChainPlanner
import il.transit.core.plan.ChainResult
import il.transit.core.plan.LastRide
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripSort
import il.transit.core.present.BetterStartRow
import il.transit.core.present.DepartureRow
import il.transit.core.present.DropOffRow
import il.transit.core.present.LegChip
import il.transit.core.present.LegKind
import il.transit.core.present.PickUpRow
import il.transit.core.present.Turn
import il.transit.core.present.betterStartRow
import il.transit.core.present.dropOffRow
import il.transit.core.present.fareLabel
import il.transit.core.present.hhmm
import il.transit.core.present.lastRideNote
import il.transit.core.present.onColor
import il.transit.core.present.pickUpRow
import il.transit.core.present.summarize
import il.transit.core.present.walkSteps
import il.transit.core.user.FavoriteLine
import il.transit.core.user.ModeFilter
import il.transit.core.user.PlaceRoutine
import il.transit.core.user.Home
import il.transit.core.user.SavedPlace
import il.transit.core.user.UserSettings
import il.transit.core.user.WalkSpeed
import il.transit.planner.R
import il.transit.planner.ui.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun SettingsDialog(state: UiState, vm: MainActions, actions: ScreenActions) {
    var editingRoutine by remember { mutableStateOf<SavedPlace?>(null) }
    editingRoutine?.let { p ->
        RoutineDialog(p, state.history, onDismiss = { editingRoutine = null }) { r -> vm.setRoutine(p, r); editingRoutine = null }
    }
    AlertDialog(
        onDismissRequest = { vm.showSettings(false) },
        confirmButton = { TextButton(onClick = { vm.showSettings(false) }) { Text(stringResource(R.string.done)) } },
        title = { Text(stringResource(R.string.settings)) },
        text = { SettingsContent(state, vm, actions, onRoutine = { editingRoutine = it }) },
    )
}

/** The dialog's body on its own, so screenshot tests can render it (Paparazzi draws no dialog windows). */
@Composable
internal fun SettingsContent(state: UiState, vm: MainActions, actions: ScreenActions, onRoutine: (SavedPlace) -> Unit = {}) {
    val s = state.settings
    fun set(n: UserSettings) = vm.updateSettings(n)
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
        item { NightRefreshSection(s, state.savedPlaces, ::set) }
        item { OfflineSection(actions) }
        item { CrashLogSection(actions.crashLog) }
        if (state.savedPlaces.isNotEmpty() || state.savedTrips.isNotEmpty()) {
            item { Text(stringResource(R.string.saved), style = MaterialTheme.typography.labelLarge) }
            item { HomeSuggestion(s, state.savedPlaces, ::set) }
            items(state.savedTrips) { t -> SavedRow("↗ ${t.name}") { vm.deleteTrip(t) } }
            items(state.savedPlaces) { p ->
                SavedPlaceRow(p, onRoutine = { onRoutine(p) }, isHome = s.homePlace == p.name, onSetHome = { set(s.copy(homePlace = p.name, homeOffered = true)) }) {
                    vm.deletePlace(p)
                    if (s.homePlace == p.name) set(Home.afterDelete(s, p))
                }
            }
        }
    }
}

@Composable
internal fun HistoryDialog(state: UiState, vm: MainActions) {
    AlertDialog(
        onDismissRequest = { vm.showHistory(false) },
        confirmButton = { TextButton(onClick = { vm.showHistory(false) }) { Text(stringResource(R.string.done)) } },
        dismissButton = {
            if (state.history.isNotEmpty()) TextButton(onClick = { vm.clearHistory() }) { Text(stringResource(R.string.history_clear)) }
        },
        title = { Text(stringResource(R.string.history)) },
        text = { HistoryContent(state, vm) },
    )
}

/** The dialog's body on its own, so screenshot tests can render it (Paparazzi draws no dialog windows). */
@Composable
internal fun HistoryContent(state: UiState, vm: MainActions, now: Instant = Instant.now()) {
    // `now` is a parameter so screenshot tests get the same "this week" count every day.
    val st = il.transit.core.history.History.stats(state.history, now)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state.history.isEmpty()) {
            item { Text(stringResource(R.string.history_empty)) }
        } else {
            item {
                // A steady 2 x 2 grid: equal tiles, whatever the label lengths or text size.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        StatTile(st.trips.toString(), stringResource(R.string.stat_trips), Modifier.weight(1f))
                        StatTile(st.tripsThisWeek.toString(), stringResource(R.string.stat_week), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        StatTile(String.format(Locale.US, "%.1f", st.transitHours), stringResource(R.string.stat_transit_hours), Modifier.weight(1f))
                        StatTile(st.minutesSaved.toString(), stringResource(R.string.stat_saved), Modifier.weight(1f))
                    }
                }
            }
            item { PassAdviceLine(state.history, state.settings.fareProfile, now) }
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
}

@Composable
internal fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun OfflineSection(actions: ScreenActions) {
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
internal fun Section(titleRes: Int, chips: @Composable () -> Unit) {
    Column {
        Text(stringResource(titleRes), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips() }
    }
}

/** A saved place: its routine under the name, ⌂ to make it Home (filled ⌂ marks Home), ⏰ to edit the routine, 🗑 to delete the place. */
@Composable
internal fun SavedPlaceRow(p: SavedPlace, onRoutine: () -> Unit, isHome: Boolean = false, onSetHome: (() -> Unit)? = null, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            // The mark stays beside the first line; only the name wraps.
            Row(verticalAlignment = Alignment.Top) {
                if (isHome) {
                    Icon(Icons.Filled.Home, stringResource(R.string.home_mark), Modifier.padding(top = 2.dp, end = 4.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                } else {
                    Text("★ ")
                }
                Text(p.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            p.routine?.let { Text(routineLabel(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (!isHome && onSetHome != null) IconButton(onClick = onSetHome) { Icon(Icons.Outlined.Home, stringResource(R.string.set_as_home)) }
        TextButton(onClick = onRoutine) { Text("⏰") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
    }
}

/** "Sun Mon Tue 07:00–10:00", days in Israeli week order. */
internal fun routineLabel(r: PlaceRoutine): String {
    val days = PlaceRoutine.WEEK.filter { it in r.days }
        .joinToString(" ") { java.time.DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    return "$days \u2068${hhmmOf(r.fromMin)}–${hhmmOf(r.toMin)}\u2069"
}

internal fun hhmmOf(min: Int) = String.format(Locale.US, "%02d:%02d", min / 60, min % 60)

/** Days (Sunday first) and a window; Save, Remove (when one is set) or Cancel. */
@Composable
internal fun RoutineDialog(p: SavedPlace, history: List<il.transit.core.history.TripRecord>, onDismiss: () -> Unit, onSave: (PlaceRoutine?) -> Unit) {
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
                BestLeaveLine(history, p, days.toList())
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
internal fun SavedRow(label: String, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
    }
}
