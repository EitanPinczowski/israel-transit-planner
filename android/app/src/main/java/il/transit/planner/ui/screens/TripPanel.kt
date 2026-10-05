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
internal fun SearchCard(state: UiState, vm: MainActions, onSavePlace: (LatLon) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        // While typing, the tapped row is the text field, in place (SearchEditing.kt); nothing
        // below it shows, so the suggestions sit right under it.
        val e = state.editing
        val compactEdit = e != null && LocalCompactEditing.current
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            if (!compactEdit) ModeRow(state, vm)
            Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PlaceRow(R.string.from, placeLabel(state.from), e == Field.FROM) { vm.startEditing(Field.FROM) }
                    if (e != Field.FROM) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        if (state.mode == AppMode.DROP_OFF) {
                            PlaceRow(
                                R.string.driver_to,
                                state.driverTo?.let { placeLabel(it) },
                                e == Field.DRIVER_TO,
                                placeholderRes = R.string.choose_driver_destination,
                            ) { vm.startEditing(Field.DRIVER_TO) }
                            if (e != Field.DRIVER_TO) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (state.mode == AppMode.TRIP && (!compactEdit || e == Field.STOP)) ChainStopsRows(state, vm)
                        val toLabel = when (state.mode) {
                            AppMode.DROP_OFF -> R.string.me_to
                            AppMode.PICK_UP -> R.string.driver_at
                            else -> R.string.to
                        }
                        if (e != Field.DRIVER_TO && e != Field.STOP) {
                            PlaceRow(toLabel, state.to?.let { placeLabel(it) }, e == Field.TO) { vm.startEditing(Field.TO) }
                        }
                    }
                }
                Column {
                    IconButton(onClick = vm::swap, enabled = state.to != null) {
                        Icon(painterResource(R.drawable.ic_swap_vert), contentDescription = stringResource(R.string.swap))
                    }
                    val to = state.to
                    if (to is PlaceRef.Point && state.savedPlaces.none { it.latLon == to.at }) {
                        IconButton(onClick = { onSavePlace(to.at) }) {
                            Icon(Icons.Default.Star, contentDescription = stringResource(R.string.save_place), tint = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
            Column(Modifier.padding(end = 8.dp)) {
                if (e == null) {
                    TimeRow(state, vm)
                    when (state.mode) {
                        AppMode.BETTER_START -> MinutesSlider(R.string.drive_up_to, state.maxDriveMin, vm::setMaxDrive)
                        AppMode.DROP_OFF -> MinutesSlider(R.string.detour_up_to, state.maxDetourMin, vm::setMaxDetour)
                        AppMode.PICK_UP -> MinutesSlider(R.string.pickup_drive_up_to, state.maxPickUpDriveMin, vm::setMaxPickUpDrive)
                        AppMode.PARK_RIDE -> MinutesSlider(R.string.park_ride_drive_up_to, state.parkRide.maxDriveMin, vm::setMaxParkRideDrive, max = ParkRideUi.MAX_DRIVE)
                        AppMode.TRIP -> LocalCalendarChip.current(state)
                    }
                }
            }
        }
    }
}

internal fun modeLabel(mode: AppMode): Int = when (mode) {
    AppMode.TRIP -> R.string.mode_trip
    AppMode.BETTER_START -> R.string.mode_better_start
    AppMode.DROP_OFF -> R.string.mode_drop_off
    AppMode.PICK_UP -> R.string.mode_pick_up
    AppMode.PARK_RIDE -> R.string.mode_park_ride
}

@Composable
internal fun OverflowMenu(vm: MainActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings)) },
                leadingIcon = { Icon(Icons.Default.Settings, null) },
                onClick = { open = false; vm.showSettings(true) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history)) },
                leadingIcon = { Icon(Icons.Default.DateRange, null) },
                onClick = { open = false; vm.showHistory(true) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.my_lines)) },
                leadingIcon = { Icon(Icons.Default.Favorite, null) },
                onClick = { open = false; vm.showFavorites(true) },
            )
        }
    }
}

@Composable
internal fun placeLabel(ref: PlaceRef): String = when (ref) {
    PlaceRef.MyLocation -> stringResource(R.string.my_location)
    is PlaceRef.Point -> ref.name ?: stringResource(R.string.dropped_pin)
}

@Composable
internal fun TimeRow(state: UiState, vm: MainActions) {
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
internal fun timeLabel(res: Int, state: UiState, mode: TimeMode): String {
    val base = stringResource(res)
    val t = state.time
    return if (state.timeMode == mode && t != null) "$base ${hhmm(t)}" else base
}

/** Fills whatever room is left above the keyboard ([modifier] carries the weight). */
@Composable
internal fun SuggestionList(state: UiState, vm: MainActions, modifier: Modifier) {
    ElevatedCard(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
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
internal fun SuggestionRow(title: String, detail: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SavedChips(state: UiState, vm: MainActions) {
    if (state.savedPlaces.isEmpty() && state.savedTrips.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Over the map: a surface fill, or the outline-only chip disappears into the streets.
        val colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        state.savedTrips.forEach { t ->
            // ↗ as the leading icon, so it sits at the start whatever language the name is in.
            AssistChip(
                onClick = { vm.runTrip(t) },
                label = { Text(t.name) },
                colors = colors,
                leadingIcon = { Text("↗", color = MaterialTheme.colorScheme.tertiary) },
            )
        }
        state.savedPlaces.forEach { p ->
            AssistChip(
                onClick = { vm.goTo(p) },
                label = { Text(p.name) },
                colors = colors,
                leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary) },
            )
        }
    }
}

@Composable
internal fun ResultsPanel(
    state: UiState,
    vm: MainActions,
    actions: ScreenActions,
    collapsed: Boolean,
    onCollapse: (Boolean) -> Unit,
    onSaveTrip: () -> Unit,
    modifier: Modifier,
    shape: Shape,
) {
    Sheet(modifier, shape) {
        SheetHeader(collapsed, onCollapse) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.results), style = MaterialTheme.typography.titleMedium)
                    val at = state.resultsAt
                    if (state.mode == AppMode.TRIP && at != null && !state.loading && state.error == null) {
                        Text(
                            stringResource(R.string.updated_at, hhmm(at)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.hasResults && !state.loading) {
                    IconButton(onClick = vm::plan) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
                }
                if (state.mode == AppMode.TRIP && state.to is PlaceRef.Point && state.results != null) {
                    IconButton(onClick = onSaveTrip) {
                        Icon(Icons.Default.Star, stringResource(R.string.save_trip), tint = MaterialTheme.colorScheme.tertiary)
                    }
                }
                IconButton(onClick = vm::clearResults) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
        }
        if (!collapsed) {
            state.offlineSince?.let { since ->
                // A warning, not an error: the answers are still useful, just maybe stale.
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
                    Text(
                        offlineBanner(state.offlineNight, hhmm(since)),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            if (state.mode == AppMode.TRIP) state.activeRoutine?.let { name ->
                AssistChip(onClick = vm::dismissRoutine, label = { Text(stringResource(R.string.routine_chip, name)) })
            }
            if (state.mode == AppMode.TRIP && !state.loading) ReminderRow(state, vm)
            if (!state.loading) RideRow(state, vm)
            val list = Modifier.weight(1f, fill = false).padding(end = 8.dp)
            CompositionLocalProvider(LocalTrackActions provides { TrackChips(state, actions) }) {
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.error != null -> ErrorRow(state.error, vm)
                state.mode == AppMode.BETTER_START && state.betterStart != null -> BetterStartList(state, state.betterStart, vm, list)
                state.mode == AppMode.DROP_OFF && state.dropOff != null -> DropOffList(state, state.dropOff, vm, list)
                state.mode == AppMode.PICK_UP && state.pickUp != null -> PickUpList(state, state.pickUp, vm, list)
                state.mode == AppMode.PARK_RIDE && state.parkRide.result != null -> ParkRideList(state, state.parkRide.result, vm, list)
                else -> LazyColumn(list) {
                    if (state.mode == AppMode.TRIP && (state.results?.itineraries?.size ?: 0) > 1) item { SortChips(state, vm) }
                    state.chain?.let { c -> item { ChainSummary(state, c) } }
                    itemsIndexed(state.options) { i, itin ->
                        if (state.chain != null) {
                            Text(chainLegTitle(state, i), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        }
                        ItineraryCard(
                            itin, selected = i == state.selected, fareProfile = state.settings.fareProfile, searchedAt = state.time,
                            onLeg = { li -> actions.openLeg(itin.legs[li]) },
                        ) { vm.select(i) }
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
}

/** A reminder that is set, with its Cancel. (Setting one is a chip on the selected option.) */
@Composable
internal fun ReminderRow(state: UiState, vm: MainActions) {
    val r = state.reminder ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.reminder_set, hhmm(r.leaveAt)),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = vm::cancelReminder) { Text(stringResource(R.string.cancel)) }
    }
}

/** A ride being tracked, with its progress and Stop. (Starting one is a chip on the selected option.) */
@Composable
internal fun RideRow(state: UiState, vm: MainActions) {
    if (!state.riding) return
    val context = LocalContext.current
    val p = state.rideProgress
    Row(verticalAlignment = Alignment.CenterVertically) {
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
}

/**
 * The chips that act on the selected option: leave reminder (Trip tab) and the get-off
 * alert. Provided by the results sheet, drawn by whichever card is selected, so they sit
 * next to the trip they act on instead of taking two rows above the list.
 */
internal val LocalTrackActions = compositionLocalOf<@Composable () -> Unit> { {} }

@Composable
internal fun TrackChips(state: UiState, actions: ScreenActions) {
    val canTrack = state.selectedItinerary?.firstTransitLeg != null && state.offlineSince == null
    if (state.mode == AppMode.TRIP && state.reminder == null && canTrack) {
        ActionChip(R.string.remind_me, Icons.Default.Notifications, actions.remind)
    }
    if (!state.riding && canTrack) ActionChip(R.string.ride_start, Icons.Default.PlayArrow, actions.startRide)
}

@Composable
internal fun ErrorRow(error: UiError, vm: MainActions) {
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
internal fun ItineraryCard(
    itin: Itinerary,
    selected: Boolean,
    fareProfile: FareProfile,
    searchedAt: Instant?,
    onLeg: ((Int) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val s = remember(itin, fareProfile, searchedAt) { summarize(itin, fareProfile, searchedAt ?: Instant.now()) }
    Column(Modifier.option(selected, onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A trip on another day than the one searched (Friday → Saturday night) says so.
            val depart = s.departDay?.let { d -> stringResource(R.string.on_day, d.getDisplayName(TextStyle.SHORT, Locale.getDefault()), s.depart) } ?: s.depart
            val arrive = if (s.arriveDaysLater > 0) stringResource(R.string.next_day, s.arrive, s.arriveDaysLater) else s.arrive
            Text("$depart–$arrive", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.minutes_short, s.durationMin), style = MaterialTheme.typography.titleSmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            // On the selected option a bus or train chip opens that vehicle's stops (trip sheet).
            s.chips.forEach { c ->
                val tappable = selected && onLeg != null && c.legIndex >= 0 && itin.legs[c.legIndex].isTransit
                LegChipView(c, onClick = if (tappable) ({ onLeg!!(c.legIndex) }) else null)
            }
            FareText(s.fare)
        }
        if (selected && onLeg != null && itin.firstTransitLeg != null) {
            Text(stringResource(R.string.trip_tap_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        if (selected) {
            WalkDirections(itin)
            OptionActions(onSend = null)
        }
    }
}

/** Errands: each stop on the way with its stay chips and ✕, then "+ Stop on the way". */
@Composable
internal fun ChainStopsRows(state: UiState, vm: MainActions) {
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
    if (state.editing == Field.STOP) {
        PlaceRow(R.string.chain_stop_label, null, active = true) {}
    } else if (state.chainStops.size < ChainPlanner.MAX_STOPS) {
        TextButton(onClick = { vm.startEditing(Field.STOP) }) { Text(stringResource(R.string.add_stop)) }
    }
}

/** "1. Home → Post office": which leg of the errand chain a card is. */
@Composable
internal fun chainLegTitle(state: UiState, i: Int): String {
    val names = listOf(placeLabel(state.from)) +
        state.chainStops.map { it.name ?: stringResource(R.string.dropped_pin) } +
        listOf(state.to?.let { placeLabel(it) }.orEmpty())
    return stringResource(R.string.chain_leg_title, i + 1, names.getOrElse(i) { "" }, names.getOrElse(i + 1) { "" })
}

/** "Leave 08:00 · arrive 09:10", or which leg has no way to make it. */
@Composable
internal fun ChainSummary(state: UiState, c: ChainResult) {
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
internal fun CarRow(state: UiState, vm: MainActions) {
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
internal fun WayBackRow(vm: MainActions, labelRes: Int = R.string.way_back_after) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodySmall)
        listOf(60, 120, 180).forEach { m ->
            TextButton(onClick = { vm.wayBack(m) }) { Text(stringResource(R.string.hours_short, m / 60)) }
        }
    }
}

/** Fastest · Fewest transfers · Least walking: the same answers, re-ordered. */
@Composable
internal fun SortChips(state: UiState, vm: MainActions) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

/** Screenshot tests open the walking directions; the app always starts them folded. */
internal val LocalWalkDirectionsOpen = compositionLocalOf { false }

/**
 * Turn-by-turn directions for the walking parts of the selected trip, folded by default.
 * Arrows are real-world directions: left is left in Hebrew too, so they are never mirrored.
 */
@Composable
internal fun WalkDirections(itin: Itinerary) {
    val walks = remember(itin) {
        itin.legs.filter { it.mode == "WALK" && it.duration >= 60 }.map { it to walkSteps(it) }.filter { it.second.size > 1 }
    }
    if (walks.isEmpty()) return
    val startOpen = LocalWalkDirectionsOpen.current
    var open by remember(itin) { mutableStateOf(startOpen) }
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
internal fun LastRideSection(state: UiState, vm: MainActions) {
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
internal fun lastRideText(lr: LastRide, selected: Itinerary?, always: Boolean, back: Boolean): String? {
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
internal fun FareNote() {
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
