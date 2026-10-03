@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package il.transit.planner.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import il.transit.core.user.FavoriteLine
import il.transit.core.user.PlaceRoutine
import il.transit.core.user.SavedPlace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import il.transit.core.api.Itinerary
import il.transit.core.features.BetterStartResult
import il.transit.core.features.DropOffKind
import il.transit.core.features.DropOffResult
import il.transit.core.features.NavLinks
import il.transit.core.features.PickUpResult
import il.transit.core.features.ISRAEL
import il.transit.core.geo.LatLon
import il.transit.core.plan.TimeMode
import il.transit.core.present.BetterStartRow
import il.transit.core.present.DepartureRow
import il.transit.core.present.DropOffRow
import il.transit.core.present.PickUpRow
import il.transit.core.present.pickUpRow
import il.transit.core.present.fareLabel
import il.transit.core.present.lastRideNote
import il.transit.core.plan.CarCompare
import il.transit.core.plan.ChainPlanner
import il.transit.core.plan.ChainResult
import il.transit.core.plan.LastRide
import il.transit.core.plan.TripSort
import il.transit.core.present.Turn
import il.transit.core.present.walkSteps
import il.transit.core.fare.FareEstimate
import il.transit.core.fare.FareProfile
import il.transit.core.fare.FareTable
import il.transit.core.present.dropOffRow
import il.transit.core.present.betterStartRow
import il.transit.core.present.LegChip
import il.transit.core.present.LegKind
import il.transit.core.present.hhmm
import il.transit.core.present.summarize
import il.transit.core.user.ModeFilter
import il.transit.core.user.UserSettings
import il.transit.core.user.WalkSpeed
import il.transit.planner.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

private const val TRANSITOUS_SOURCES = "https://transitous.org/sources/"

/** What only the Activity can do: permissions, the map camera, the offline store. */
class ScreenActions(
    val offline: OfflineState,
    val downloadOffline: () -> Unit,
    val deleteOffline: () -> Unit,
    /** Asks for the notification permission if needed, then arms the reminder. */
    val remind: () -> Unit,
    /** Same permission dance, then starts the "get off at the next stop" ride. */
    val startRide: () -> Unit,
)

@Composable
fun MainScreen(state: UiState, vm: MainViewModel, actions: ScreenActions, map: @Composable () -> Unit) {
    var savingPlace by remember { mutableStateOf<LatLon?>(null) }
    var savingTrip by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        map()

        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.update?.let { UpdateBanner(it, vm) }
            SearchCard(state, vm, onSavePlace = { savingPlace = it })
            when {
                state.editing != null -> SuggestionList(state, vm)
                !state.hasResults && !state.loading && state.stopSheet == null -> SavedChips(state, vm)
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            AttributionChip(Modifier.padding(8.dp))
            when {
                state.stopSheet != null -> StopPanel(state.stopSheet, state.favorites, vm)
                state.loading || state.hasResults || state.error != null ->
                    ResultsPanel(state, vm, actions, onSaveTrip = { savingTrip = true })
                else -> Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    if (state.showSettings) SettingsDialog(state, vm, actions)
    if (state.showHistory) HistoryDialog(state, vm)
    if (state.showFavorites) FavoritesDialog(state, vm)
    savingPlace?.let { at ->
        NameDialog(R.string.save_place, onDismiss = { savingPlace = null }) { name -> vm.savePlace(name, at); savingPlace = null }
    }
    if (savingTrip) {
        NameDialog(R.string.save_trip, onDismiss = { savingTrip = false }) { name -> vm.saveTrip(name); savingTrip = false }
    }
}

@Composable
private fun UpdateBanner(latest: il.transit.core.update.LatestRelease, vm: MainViewModel) {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.update_available, latest.version), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.apkUrl ?: latest.pageUrl)))
            }) { Text(stringResource(R.string.update_download)) }
            IconButton(onClick = vm::dismissUpdate) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
        }
    }
}

// --- search ---------------------------------------------------------------------------------

@Composable
private fun SearchCard(state: UiState, vm: MainViewModel, onSavePlace: (LatLon) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            ModeRow(state, vm)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PlaceRow(R.string.from, placeLabel(state.from), state.editing == Field.FROM) { vm.startEditing(Field.FROM) }
                    HorizontalDivider()
                    if (state.mode == AppMode.DROP_OFF) {
                        PlaceRow(
                            R.string.driver_to,
                            state.driverTo?.let { placeLabel(it) },
                            state.editing == Field.DRIVER_TO,
                            placeholderRes = R.string.choose_driver_destination,
                        ) { vm.startEditing(Field.DRIVER_TO) }
                        HorizontalDivider()
                    }
                    if (state.mode == AppMode.TRIP) ChainStopsRows(state, vm)
                    val toLabel = when (state.mode) {
                        AppMode.DROP_OFF -> R.string.me_to
                        AppMode.PICK_UP -> R.string.driver_at
                        else -> R.string.to
                    }
                    PlaceRow(toLabel, state.to?.let { placeLabel(it) }, state.editing == Field.TO) { vm.startEditing(Field.TO) }
                }
                Column {
                    TextButton(onClick = vm::swap, enabled = state.to != null) { Text("⇅") }
                    val to = state.to
                    if (to is PlaceRef.Point && state.savedPlaces.none { it.latLon == to.at }) {
                        IconButton(onClick = { onSavePlace(to.at) }) {
                            Icon(Icons.Default.Star, contentDescription = stringResource(R.string.save_place))
                        }
                    }
                    IconButton(onClick = { vm.showSettings(true) }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    IconButton(onClick = { vm.showHistory(true) }) {
                        Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.history))
                    }
                    IconButton(onClick = { vm.showFavorites(true) }) {
                        Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.my_lines))
                    }
                }
            }
            if (state.editing != null) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = vm::cancelEditing) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                )
            } else {
                TimeRow(state, vm)
                when (state.mode) {
                    AppMode.BETTER_START -> MinutesSlider(R.string.drive_up_to, state.maxDriveMin, vm::setMaxDrive)
                    AppMode.DROP_OFF -> MinutesSlider(R.string.detour_up_to, state.maxDetourMin, vm::setMaxDetour)
                    AppMode.PICK_UP -> MinutesSlider(R.string.pickup_drive_up_to, state.maxPickUpDriveMin, vm::setMaxPickUpDrive)
                    AppMode.TRIP -> Unit
                }
            }
        }
    }
}

@Composable
private fun ModeRow(state: UiState, vm: MainViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            AppMode.TRIP to R.string.mode_trip,
            AppMode.BETTER_START to R.string.mode_better_start,
            AppMode.DROP_OFF to R.string.mode_drop_off,
            AppMode.PICK_UP to R.string.mode_pick_up,
        ).forEach { (mode, label) ->
            FilterChip(state.mode == mode, { vm.setMode(mode) }, label = { Text(stringResource(label)) })
        }
    }
}

/** 5–30 minutes in steps of 5. Local while dragging; [onDone] (a search) runs once, on release. */
@Composable
private fun MinutesSlider(labelRes: Int, current: Int, onDone: (Int) -> Unit) {
    var value by remember(current) { mutableStateOf(current.toFloat()) }
    Column {
        Text(stringResource(labelRes, value.toInt()), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = { value = (Math.round(it / 5f) * 5).toFloat() },
            onValueChangeFinished = { onDone(value.toInt()) },
            valueRange = 5f..30f,
            steps = 4,
        )
    }
}

@Composable
private fun PlaceRow(
    labelRes: Int,
    value: String?,
    active: Boolean,
    placeholderRes: Int = R.string.choose_destination,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
        Text(
            value ?: stringResource(placeholderRes),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun placeLabel(ref: PlaceRef): String = when (ref) {
    PlaceRef.MyLocation -> stringResource(R.string.my_location)
    is PlaceRef.Point -> ref.name ?: stringResource(R.string.dropped_pin)
}

@Composable
private fun TimeRow(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    fun pickTime(mode: TimeMode) {
        val now = ZonedDateTime.now(ISRAEL)
        TimePickerDialog(context, { _, h, m ->
            var at = ZonedDateTime.of(LocalDate.now(ISRAEL), LocalTime.of(h, m), ISRAEL)
            // A time more than an hour in the past means tomorrow.
            if (at.isBefore(now.minusHours(1))) at = at.plusDays(1)
            vm.setTime(mode, at.toInstant())
        }, now.hour, now.minute, true).show()
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = state.timeMode == TimeMode.NOW, onClick = { vm.setTime(TimeMode.NOW, null) }, label = { Text(stringResource(R.string.now)) })
        FilterChip(
            selected = state.timeMode == TimeMode.DEPART_AT,
            onClick = { pickTime(TimeMode.DEPART_AT) },
            label = { Text(timeLabel(R.string.depart_at, state, TimeMode.DEPART_AT)) },
        )
        if (state.mode == AppMode.TRIP && state.chainStops.isEmpty()) {
            FilterChip(
                selected = state.timeMode == TimeMode.ARRIVE_BY,
                onClick = { pickTime(TimeMode.ARRIVE_BY) },
                label = { Text(timeLabel(R.string.arrive_by, state, TimeMode.ARRIVE_BY)) },
            )
        }
    }
}

@Composable
private fun timeLabel(res: Int, state: UiState, mode: TimeMode): String {
    val base = stringResource(res)
    val t = state.time
    return if (state.timeMode == mode && t != null) "$base ${hhmm(t)}" else base
}

@Composable
private fun SuggestionList(state: UiState, vm: MainViewModel) {
    Card(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
        LazyColumn {
            item {
                SuggestionRow(stringResource(R.string.my_location), null, Icons.Default.Place) { vm.pickMyLocation() }
            }
            if (state.searchHint) {
                item {
                    Text(
                        stringResource(R.string.search_full_name_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            items(state.suggestions) { s ->
                val detail = s.detail ?: if (s.isStop) stringResource(R.string.stop) else null
                SuggestionRow(s.name, detail, if (s.saved) Icons.Default.Star else Icons.Default.Place) { vm.pick(s) }
            }
        }
    }
}

@Composable
private fun SuggestionRow(title: String, detail: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SavedChips(state: UiState, vm: MainViewModel) {
    if (state.savedPlaces.isEmpty() && state.savedTrips.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        state.savedTrips.forEach { t ->
            AssistChip(onClick = { vm.runTrip(t) }, label = { Text("↗ ${t.name}") })
        }
        state.savedPlaces.forEach { p ->
            AssistChip(onClick = { vm.goTo(p) }, label = { Text(p.name) }, leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) })
        }
    }
}

// --- results ---------------------------------------------------------------------------------

@Composable
private fun ResultsPanel(state: UiState, vm: MainViewModel, actions: ScreenActions, onSaveTrip: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.results), style = MaterialTheme.typography.titleMedium)
                    val at = state.resultsAt
                    if (state.mode == AppMode.TRIP && at != null && !state.loading) {
                        Text(
                            stringResource(R.string.updated_at, hhmm(at)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.hasResults && !state.loading) {
                    IconButton(onClick = { vm.plan() }) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
                }
                if (state.mode == AppMode.TRIP && state.to is PlaceRef.Point && state.results != null) {
                    TextButton(onClick = onSaveTrip) { Text(stringResource(R.string.save_trip)) }
                }
                IconButton(onClick = vm::clearResults) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
            state.offlineSince?.let { since ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.offline_showing, hhmm(since)),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            if (state.mode == AppMode.TRIP) state.activeRoutine?.let { name ->
                AssistChip(onClick = vm::dismissRoutine, label = { Text(stringResource(R.string.routine_chip, name)) })
            }
            if (state.mode == AppMode.TRIP && !state.loading) ReminderRow(state, vm, actions)
            if (!state.loading) RideRow(state, vm, actions)
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.error != null -> ErrorRow(state.error, vm)
                state.mode == AppMode.BETTER_START && state.betterStart != null -> BetterStartList(state, state.betterStart, vm)
                state.mode == AppMode.DROP_OFF && state.dropOff != null -> DropOffList(state, state.dropOff, vm)
                state.mode == AppMode.PICK_UP && state.pickUp != null -> PickUpList(state, state.pickUp, vm)
                else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    if (state.mode == AppMode.TRIP && (state.results?.itineraries?.size ?: 0) > 1) item { SortChips(state, vm) }
                    state.chain?.let { c -> item { ChainSummary(state, c) } }
                    itemsIndexed(state.options) { i, itin ->
                        if (state.chain != null) {
                            Text(chainLegTitle(state, i), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        }
                        ItineraryCard(itin, selected = i == state.selected, fareProfile = state.settings.fareProfile, searchedAt = state.time) { vm.select(i) }
                    }
                    // Both answer for the direct A → B trip, which is not what an errand chain is.
                    if (state.options.isNotEmpty() && state.chain == null) item { LastRideSection(state, vm) }
                    if (state.mode == AppMode.TRIP && state.selectedItinerary?.firstTransitLeg != null && state.chain == null) item { WayBackRow(vm) }
                    if (state.mode == AppMode.TRIP && state.options.isNotEmpty() && state.chain == null) item { CarRow(state, vm) }
                    if (state.options.isNotEmpty()) item { FareNote() }
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(state: UiState, vm: MainViewModel, actions: ScreenActions) {
    val r = state.reminder
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (r != null) {
            Text(
                stringResource(R.string.reminder_set, hhmm(r.leaveAt)),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::cancelReminder) { Text(stringResource(R.string.cancel)) }
        } else if (state.selectedItinerary?.firstTransitLeg != null && state.offlineSince == null) {
            TextButton(onClick = actions.remind) { Text(stringResource(R.string.remind_me)) }
        }
    }
}

@Composable
private fun RideRow(state: UiState, vm: MainViewModel, actions: ScreenActions) {
    when {
        state.riding -> Row(verticalAlignment = Alignment.CenterVertically) {
            val context = LocalContext.current
            val p = state.rideProgress
            Column(Modifier.weight(1f)) {
                if (p == null) {
                    Text(stringResource(R.string.ride_active), style = MaterialTheme.typography.bodySmall)
                } else {
                    // "Line 5 · 3 stops left" / "Get off at X ~08:47 (+2 min) · arrive 08:55"
                    Text(il.transit.planner.ride.RideService.rideProgressTitle(context, p), style = MaterialTheme.typography.titleSmall)
                    Text(il.transit.planner.ride.RideService.rideProgressText(context, p), style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = vm::stopRide) { Text(stringResource(R.string.ride_stop)) }
        }
        state.selectedItinerary?.firstTransitLeg != null && state.offlineSince == null ->
            TextButton(onClick = actions.startRide) { Text(stringResource(R.string.ride_start)) }
    }
}

@Composable
private fun ErrorRow(error: UiError, vm: MainViewModel) {
    val msg = when (error) {
        UiError.NO_LOCATION -> R.string.err_no_location
        UiError.NETWORK -> R.string.err_network
        UiError.NO_RESULTS -> R.string.err_no_results
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(msg), modifier = Modifier.weight(1f))
        if (error != UiError.NO_LOCATION) TextButton(onClick = vm::plan) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun ItineraryCard(itin: Itinerary, selected: Boolean, fareProfile: FareProfile, searchedAt: Instant?, onClick: () -> Unit) {
    val s = remember(itin, fareProfile, searchedAt) { summarize(itin, fareProfile, searchedAt ?: Instant.now()) }
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).background(bg, RoundedCornerShape(12.dp)).padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A trip on another day than the one searched (Friday → Saturday night) says so.
            val depart = s.departDay?.let { d -> stringResource(R.string.on_day, d.getDisplayName(TextStyle.SHORT, Locale.getDefault()), s.depart) } ?: s.depart
            val arrive = if (s.arriveDaysLater > 0) stringResource(R.string.next_day, s.arrive, s.arriveDaysLater) else s.arrive
            Text("$depart–$arrive", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.minutes_short, s.durationMin), style = MaterialTheme.typography.titleSmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            s.chips.forEach { LegChipView(it) }
            FareText(s.fare)
        }
        val transfers = if (s.transfers == 0) stringResource(R.string.direct) else pluralStringResource(R.plurals.transfers, s.transfers, s.transfers)
        val walk = stringResource(R.string.walk_minutes, s.walkMin)
        val board = s.firstBoarding?.let { b ->
            val line = b.line ?: kindName(b.kind)
            val live = when {
                b.delayMin != null && b.delayMin!! > 0 -> " " + stringResource(R.string.late_paren, b.delayMin!!)
                b.realTime -> " ●"
                else -> ""
            }
            stringResource(R.string.board_line, line, b.time, b.stop) + live
        }
        Text(listOfNotNull(board, "$transfers · $walk").joinToString("\n"), style = MaterialTheme.typography.bodySmall)
        if (selected) WalkDirections(itin)
    }
}

/** Errands: each stop on the way with its stay chips and ✕, then "+ Stop on the way". */
@Composable
private fun ChainStopsRows(state: UiState, vm: MainViewModel) {
    state.chainStops.forEachIndexed { i, c ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
            Text("• ${c.name ?: stringResource(R.string.dropped_pin)}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { vm.removeChainStop(i) }) { Icon(Icons.Default.Close, stringResource(R.string.delete)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 16.dp)) {
            Text(stringResource(R.string.stay), style = MaterialTheme.typography.labelMedium)
            ChainPlanner.STAY_CHOICES.forEach { m ->
                FilterChip(c.stayMin == m, { vm.setStay(i, m) }, label = { Text(stringResource(R.string.minutes_short, m)) })
            }
        }
        HorizontalDivider()
    }
    if (state.chainStops.size < ChainPlanner.MAX_STOPS) {
        TextButton(onClick = { vm.startEditing(Field.STOP) }) { Text(stringResource(R.string.add_stop)) }
    }
}

/** "1. Home → Post office": which leg of the errand chain a card is. */
@Composable
private fun chainLegTitle(state: UiState, i: Int): String {
    val names = listOf(placeLabel(state.from)) +
        state.chainStops.map { it.name ?: stringResource(R.string.dropped_pin) } +
        listOf(state.to?.let { placeLabel(it) }.orEmpty())
    return stringResource(R.string.chain_leg_title, i + 1, names.getOrElse(i) { "" }, names.getOrElse(i + 1) { "" })
}

/** "Leave 08:00 · arrive 09:10", or which leg has no way to make it. */
@Composable
private fun ChainSummary(state: UiState, c: ChainResult) {
    val leave = c.leaveAt?.let(::hhmm).orEmpty()
    val text = when {
        c.failedAt != null -> stringResource(R.string.chain_failed, c.failedAt!! + 1)
        else -> stringResource(R.string.chain_summary, leave, c.arriveAt?.let(::hhmm).orEmpty())
    }
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = if (c.failedAt != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

/** "🚗 By car?" → "By car ~95 min at this time (traffic ×1.3) · 113 km" + the official taxi calculator. */
@Composable
private fun CarRow(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    val car = state.carTime
    when {
        state.carLoading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        car == null -> TextButton(onClick = vm::checkCar) { Text(stringResource(R.string.by_car_check)) }
        else -> Column {
            Text(
                stringResource(R.string.by_car, car.minutes, String.format(Locale.US, "%.1f", car.factor), Math.round(car.km).toInt()),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.taxi_calculator),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CarCompare.TAXI_CALCULATOR_URL)))
                }.padding(vertical = 4.dp),
            )
        }
    }
}

/** "Way back after 1 h · 2 h · 3 h": the return of the selected option, same settings. */
@Composable
private fun WayBackRow(vm: MainViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.way_back_after), style = MaterialTheme.typography.bodySmall)
        listOf(60, 120, 180).forEach { m ->
            TextButton(onClick = { vm.wayBack(m) }) { Text(stringResource(R.string.hours_short, m / 60)) }
        }
    }
}

/** My lines: each pinned line's next departures at its stop, with live delays. */
@Composable
private fun FavoritesDialog(state: UiState, vm: MainViewModel) {
    AlertDialog(
        onDismissRequest = { vm.showFavorites(false) },
        confirmButton = { TextButton(onClick = { vm.showFavorites(false) }) { Text(stringResource(R.string.done)) } },
        title = { Text(stringResource(R.string.my_lines)) },
        text = {
            if (state.favorites.isEmpty()) {
                Text(stringResource(R.string.my_lines_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.favoritesOfflineSince?.let { at ->
                        item { Text(stringResource(R.string.my_lines_offline, hhmm(at)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    items(state.favorites) { f ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (f.headsign.isBlank()) f.line else stringResource(R.string.line_to, f.line, f.headsign),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(f.stopName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { vm.removeFavorite(f) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                            }
                            when {
                                f !in state.favoriteBoards -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                state.favoriteBoards[f] == null -> Text(stringResource(R.string.err_network), style = MaterialTheme.typography.bodySmall)
                                state.favoriteBoards[f]!!.isEmpty() -> Text(stringResource(R.string.no_departures), style = MaterialTheme.typography.bodySmall)
                                else -> state.favoriteBoards[f]!!.forEach { DepartureView(it) }
                            }
                        }
                    }
                }
            }
        },
    )
}

/** Fastest · Fewest transfers · Least walking: the same answers, re-ordered. */
@Composable
private fun SortChips(state: UiState, vm: MainViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TripSort.entries.forEach { s ->
            val label = when (s) {
                TripSort.FASTEST -> R.string.sort_fastest
                TripSort.FEWEST_TRANSFERS -> R.string.sort_fewest_transfers
                TripSort.LEAST_WALKING -> R.string.sort_least_walking
            }
            FilterChip(state.settings.tripSort == s, { vm.setTripSort(s) }, label = { Text(stringResource(label)) })
        }
    }
}

/**
 * Turn-by-turn directions for the walking parts of the selected trip, folded by default.
 * Arrows are real-world directions: left is left in Hebrew too, so they are never mirrored.
 */
@Composable
private fun WalkDirections(itin: Itinerary) {
    val walks = remember(itin) {
        itin.legs.filter { it.mode == "WALK" && it.duration >= 60 }.map { it to walkSteps(it) }.filter { it.second.size > 1 }
    }
    if (walks.isEmpty()) return
    var open by remember(itin) { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) {
        Text(stringResource(if (open) R.string.walk_directions_hide else R.string.walk_directions_show))
    }
    if (!open) return
    walks.forEach { (leg, steps) ->
        Text(stringResource(R.string.walk_to, leg.to.name), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        steps.forEach { st ->
            val (arrow, word) = when (st.turn) {
                Turn.START -> "↑" to R.string.turn_start
                Turn.STRAIGHT -> "↑" to R.string.turn_straight
                Turn.SLIGHT_LEFT -> "↖" to R.string.turn_slight_left
                Turn.LEFT -> "←" to R.string.turn_left
                Turn.SHARP_LEFT -> "↙" to R.string.turn_sharp_left
                Turn.SLIGHT_RIGHT -> "↗" to R.string.turn_slight_right
                Turn.RIGHT -> "→" to R.string.turn_right
                Turn.SHARP_RIGHT -> "↘" to R.string.turn_sharp_right
                Turn.U_TURN -> "↶" to R.string.turn_u_turn
                Turn.STAIRS -> "⇵" to R.string.turn_stairs
            }
            val parts = listOfNotNull(stringResource(word), stringResource(R.string.meters, st.meters), st.street)
            Row(Modifier.padding(vertical = 1.dp)) {
                Text(arrow, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(24.dp))
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** "≈ ₪8", isolated so Hebrew text around it cannot reorder the amount. */
@Composable
private fun FareText(f: FareEstimate?) {
    if (f == null) return
    val text = if (f.agorot == 0) stringResource(R.string.fare_free_label) else "\u2068${fareLabel(f)}\u2069"
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
}

/**
 * "Last trip today" and "last trip back". Shown by itself on evenings, Fridays and
 * Saturdays when it matters (the last trip is near, or service stops for Shabbat/a holiday);
 * otherwise one tap away. Each lookup is at most [il.transit.core.plan.LastRideFinder.BUDGET] requests.
 */
@Composable
private fun LastRideSection(state: UiState, vm: MainViewModel) {
    val warn = MaterialTheme.colorScheme.error
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val lr = state.lastRide
        val line = lr?.let { lastRideText(it, state.selectedItinerary, always = state.lastRideAsked, back = false) }
        if (line != null) Text(line, style = MaterialTheme.typography.bodySmall, color = warn)
        state.lastRideBack?.let { b ->
            Text(lastRideText(b, null, always = true, back = true)!!, style = MaterialTheme.typography.bodySmall, color = warn)
        }
        Row {
            // Also when an automatic look found nothing worth saying: asking shows it anyway.
            if ((lr == null || (line == null && !state.lastRideAsked)) && !state.lastRideLoading) {
                TextButton(onClick = vm::checkLastRide) { Text(stringResource(R.string.last_ride_check)) }
            }
            if (state.lastRideBack == null && !state.lastRideLoading) {
                TextButton(onClick = vm::checkLastRideBack) { Text(stringResource(R.string.last_ride_back_check)) }
            }
            if (state.lastRideLoading) CircularProgressIndicator(Modifier.size(18.dp).padding(2.dp), strokeWidth = 2.dp)
        }
    }
}

/** One line for a [LastRide]; null when an automatic lookup has nothing worth saying. */
@Composable
private fun lastRideText(lr: LastRide, selected: Itinerary?, always: Boolean, back: Boolean): String? {
    if (lr.runsAllNight) return if (always) stringResource(R.string.last_ride_all_night) else null
    if (lr.last == null) return if (always) stringResource(R.string.last_ride_none) else null
    val n = lastRideNote(lr, selected, always) ?: return null
    val head = when {
        back -> stringResource(R.string.last_ride_back_at, n.lastTime)
        n.selectedIsLast -> stringResource(R.string.last_ride_this)
        else -> stringResource(R.string.last_ride_at, n.lastTime)
    }
    val tail = when {
        !n.longGap -> ""
        n.resumesTime == null -> stringResource(R.string.last_ride_none_after)
        else -> {
            val day = n.resumesDay
            val whenText = if (day != null) stringResource(R.string.on_day, day.getDisplayName(TextStyle.SHORT, Locale.getDefault()), n.resumesTime!!) else n.resumesTime!!
            stringResource(R.string.last_ride_resumes, whenText)
        }
    }
    return head + tail
}

/** Fares are an estimate from a copied price list; the official one is a tap away. */
@Composable
private fun FareNote() {
    val context = LocalContext.current
    Text(
        stringResource(R.string.fare_estimate_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FareTable.OFFICIAL_URL)))
        }.padding(vertical = 6.dp),
    )
}

@Composable
private fun LegChipView(c: LegChip) {
    val color = parseColor(c.color)
    val text = when (c.kind) {
        LegKind.WALK, LegKind.CAR -> "${kindName(c.kind)} ${c.minutes}′"
        else -> c.label ?: kindName(c.kind)
    }
    Box(
        Modifier.background(if (c.kind == LegKind.WALK) Color.Transparent else color, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        val live = when {
            c.delayMin != null && c.delayMin!! > 0 -> " +${c.delayMin}"
            c.realTime -> " ●"
            else -> ""
        }
        Text(
            text + live,
            style = MaterialTheme.typography.labelMedium,
            color = if (c.kind == LegKind.WALK) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
        )
    }
}

@Composable
private fun kindName(k: LegKind): String = stringResource(
    when (k) {
        LegKind.WALK -> R.string.kind_walk
        LegKind.BUS -> R.string.kind_bus
        LegKind.TRAIN -> R.string.kind_train
        LegKind.LIGHT_RAIL -> R.string.kind_light_rail
        LegKind.CAR -> R.string.kind_car
        LegKind.OTHER -> R.string.kind_other
    },
)

private fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

// --- better start ---------------------------------------------------------------------------------

@Composable
private fun BetterStartList(state: UiState, result: BetterStartResult, vm: MainViewModel) {
    val context = LocalContext.current
    LazyColumn(Modifier.heightIn(max = 340.dp)) {
        if (result.options.isEmpty()) {
            item { Text(stringResource(R.string.no_better_start, state.maxDriveMin), Modifier.padding(vertical = 8.dp)) }
        }
        itemsIndexed(result.options) { i, option ->
            val row = remember(option, result.baseline, state.settings.fareProfile) { betterStartRow(option.payload, result.baseline, state.settings.fareProfile) }
            val shareText = stringResource(
                R.string.share_dropoff,
                row.stop,
                NavLinks.waze(option.payload.dropOffAt),
                NavLinks.googleMaps(option.payload.dropOffAt),
            )
            BetterStartCard(row, selected = i == state.selected, onClick = { vm.select(i) }) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                context.startActivity(Intent.createChooser(send, null))
            }
        }
        result.baseline?.let { b ->
            item {
                Text(
                    stringResource(R.string.without_ride, hhmm(b.end)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun BetterStartCard(row: BetterStartRow, selected: Boolean, onClick: () -> Unit, onSend: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).background(bg, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(stringResource(R.string.drive_to, row.driveMin, row.stop), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${row.depart}–${row.arrive}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
            TextButton(onClick = onSend) { Text(stringResource(R.string.send_to_driver)) }
        }
    }
}

// --- let me off on the way -----------------------------------------------------------------------

@Composable
private fun DropOffList(state: UiState, result: DropOffResult, vm: MainViewModel) {
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

// --- best pick-up point --------------------------------------------------------------------------

@Composable
private fun PickUpList(state: UiState, result: PickUpResult, vm: MainViewModel) {
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

// --- stop departures --------------------------------------------------------------------------

@Composable
private fun StopPanel(sheet: StopSheet, favorites: List<FavoriteLine>, vm: MainViewModel) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sheet.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.departures), style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = vm::closeStop) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
            when {
                sheet.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                sheet.failed -> Text(stringResource(R.string.err_network), Modifier.padding(vertical = 12.dp))
                sheet.rows.isEmpty() -> Text(stringResource(R.string.no_departures), Modifier.padding(vertical = 12.dp))
                else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(sheet.rows) { r ->
                        val pinned = favorites.any { it.stopId == sheet.stopId && it.matches(r) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { DepartureView(r) }
                            // ☆ pins this line in this direction at this stop ("My lines").
                            TextButton(onClick = { vm.toggleFavorite(sheet, r) }) { Text(if (pinned) "★" else "☆") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartureView(r: DepartureRow) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        ) { Text(r.line.ifBlank { kindName(r.kind) }, style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.width(10.dp))
        Text(r.headsign, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val status = when {
            r.cancelled -> stringResource(R.string.cancelled)
            r.delayMin == null -> ""
            r.delayMin!! > 0 -> stringResource(R.string.late, r.delayMin!!)
            else -> stringResource(R.string.on_time)
        }
        if (status.isNotEmpty()) {
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                color = if (r.cancelled || (r.delayMin ?: 0) > 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(r.time, style = MaterialTheme.typography.titleSmall)
    }
}

// --- settings & dialogs --------------------------------------------------------------------------

@Composable
private fun SettingsDialog(state: UiState, vm: MainViewModel, actions: ScreenActions) {
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
private fun HistoryDialog(state: UiState, vm: MainViewModel) {
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

@Composable
private fun NameDialog(titleRes: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text(stringResource(R.string.name_hint)) })
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AttributionChip(modifier: Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TRANSITOUS_SOURCES))) },
        shape = CircleShape,
        tonalElevation = 2.dp,
    ) {
        Text(stringResource(R.string.attribution), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
