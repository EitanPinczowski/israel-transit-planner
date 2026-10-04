@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package il.transit.planner.ui.screens

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import il.transit.core.api.Itinerary
import il.transit.core.fare.FareProfile
import il.transit.core.fare.FareTable
import il.transit.core.features.ISRAEL
import il.transit.core.geo.LatLon
import il.transit.core.plan.CarCompare
import il.transit.core.plan.ChainPlanner
import il.transit.core.plan.ChainResult
import il.transit.core.plan.LastRide
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripSort
import il.transit.core.present.Turn
import il.transit.core.present.hhmm
import il.transit.core.present.lastRideNote
import il.transit.core.present.summarize
import il.transit.core.present.walkSteps
import il.transit.planner.R
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.ParkRideUi
import il.transit.planner.ui.Field
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.PlaceRef
import il.transit.planner.ui.ScreenActions
import il.transit.planner.ui.UiError
import il.transit.planner.ui.UiState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun SearchCard(state: UiState, vm: MainViewModel, onSavePlace: (LatLon) -> Unit) {
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
                    AppMode.PARK_RIDE -> MinutesSlider(R.string.park_ride_drive_up_to, state.parkRide.maxDriveMin, vm::setMaxParkRideDrive, max = ParkRideUi.MAX_DRIVE)
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
            AppMode.PARK_RIDE to R.string.mode_park_ride,
        ).forEach { (mode, label) ->
            FilterChip(state.mode == mode, { vm.setMode(mode) }, label = { Text(stringResource(label)) })
        }
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
internal fun SuggestionList(state: UiState, vm: MainViewModel) {
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
internal fun SavedChips(state: UiState, vm: MainViewModel) {
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

@Composable
internal fun ResultsPanel(state: UiState, vm: MainViewModel, actions: ScreenActions, onSaveTrip: () -> Unit) {
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
                state.mode == AppMode.PARK_RIDE && state.parkRide.result != null -> ParkRideList(state, state.parkRide.result, vm)
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
                    if (state.mode == AppMode.TRIP) parkedCarFor(state)?.let { p -> item { DriveHomeRow(state, p) } }
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

/** "Way back after 1 h · 2 h · 3 h": the return of the selected option, same settings.
 *  Park & ride reuses it with its own label: the way back goes to the parked car. */
@Composable
internal fun WayBackRow(vm: MainViewModel, labelRes: Int = R.string.way_back_after) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodySmall)
        listOf(60, 120, 180).forEach { m ->
            TextButton(onClick = { vm.wayBack(m) }) { Text(stringResource(R.string.hours_short, m / 60)) }
        }
    }
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
