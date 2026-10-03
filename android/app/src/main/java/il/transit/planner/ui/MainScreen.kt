@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package il.transit.planner.ui

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.verticalScroll
import il.transit.core.user.FavoriteLine
import il.transit.core.user.PlaceRoutine
import il.transit.core.user.SavedPlace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import il.transit.core.present.onColor
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
import kotlin.math.roundToInt

private const val TRANSITOUS_SOURCES = "https://transitous.org/sources/"

/** Wider than this (landscape, foldables, tablets), the panels move into one side column. */
private val WIDE = 600.dp

/** Shorter than this, the search card folds to one line while results are shown. */
private val SHORT = 700.dp

/** What only the Activity can do: permissions, the map camera, the offline store. */
class ScreenActions(
    val offline: OfflineState,
    val downloadOffline: () -> Unit,
    val deleteOffline: () -> Unit,
    /** Asks for the notification permission if needed, then arms the reminder. */
    val remind: () -> Unit,
    /** Same permission dance, then starts the "get off at the next stop" ride. */
    val startRide: () -> Unit,
    /** How much of the map our panels and the system bars cover, whenever that changes. */
    val onMapPadding: (MapPadding) -> Unit = {},
)

/** Pixels of the map hidden on each side (absolute left/right, not start/end). */
data class MapPadding(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)

/** System bars and the camera cutout, without the keyboard (that one only matters while typing). */
private val barsAndCutout: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

/**
 * The whole screen over the map. Phones: search card on top, results as a bottom sheet.
 * Wide windows: both in one side column at the start edge. Every edge respects the status
 * and navigation bars, the camera cutout (which is on a side in landscape) and the keyboard.
 */
@Composable
fun MainScreen(state: UiState, vm: MainActions, actions: ScreenActions, map: @Composable () -> Unit) {
    var savingPlace by remember { mutableStateOf<LatLon?>(null) }
    var savingTrip by remember { mutableStateOf(false) }
    var collapsed by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    val showPanel = state.editing == null &&
        (state.stopSheet != null || state.loading || state.hasResults || state.error != null)
    // A new search always shows its answer; closing the results unfolds the search card.
    LaunchedEffect(state.loading) { if (state.loading) collapsed = false }
    LaunchedEffect(showPanel) { if (!showPanel) searchOpen = false }

    val panel: @Composable (Modifier, Shape) -> Unit = { modifier, shape ->
        if (state.stopSheet != null) {
            StopPanel(state.stopSheet, state.favorites, vm, collapsed, { collapsed = it }, modifier, shape)
        } else {
            ResultsPanel(state, vm, actions, collapsed, { collapsed = it }, { savingTrip = true }, modifier, shape)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val dir = LocalLayoutDirection.current
        val bars = barsAndCutout
        val rootWidth = constraints.maxWidth
        val rootHeight = constraints.maxHeight
        val compactSearch = showPanel && maxHeight < SHORT && !searchOpen
        val sheetMaxHeight = maxHeight * 0.5f

        map()
        StatusBarScrim()

        val search: @Composable () -> Unit = {
            state.update?.let { UpdateBanner(it, vm) }
            if (compactSearch) {
                CompactSearch(state) { searchOpen = true }
            } else {
                SearchCard(state, vm, onSavePlace = { savingPlace = it })
            }
        }

        if (maxWidth >= WIDE) {
            var side by remember { mutableStateOf(0 to 0) } // left and right edge, px
            Column(
                Modifier.fillMaxHeight()
                    .onGloballyPositioned { c -> c.boundsInRoot().let { side = it.left.roundToInt() to it.right.roundToInt() } }
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                    .width(minOf(420.dp, maxWidth * 0.5f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                search()
                when {
                    state.editing != null -> SuggestionList(state, vm, Modifier.weight(1f, fill = false))
                    showPanel -> panel(Modifier.weight(1f, fill = false), MaterialTheme.shapes.extraLarge)
                    else -> SavedChips(state, vm)
                }
                AttributionChip(Modifier)
            }
            val atLeft = side.first < rootWidth / 2
            val pad = MapPadding(
                left = if (atLeft) side.second else bars.getLeft(density, dir),
                top = bars.getTop(density),
                right = if (atLeft) bars.getRight(density, dir) else rootWidth - side.first,
                bottom = bars.getBottom(density),
            )
            LaunchedEffect(pad) { actions.onMapPadding(pad) }
        } else {
            var topEdge by remember { mutableIntStateOf(0) }
            var bottomEdge by remember { mutableIntStateOf(rootHeight) }
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
                Column(
                    Modifier.fillMaxWidth().onGloballyPositioned { topEdge = it.boundsInRoot().bottom.roundToInt() },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    search()
                    when {
                        state.editing != null -> SuggestionList(state, vm, Modifier.weight(1f, fill = false))
                        !showPanel -> SavedChips(state, vm)
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .onGloballyPositioned { bottomEdge = it.boundsInRoot().top.roundToInt() },
            ) {
                AttributionChip(Modifier.windowInsetsPadding(bars.only(WindowInsetsSides.Horizontal)).padding(8.dp))
                if (showPanel) {
                    val top = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
                    panel(Modifier.heightIn(max = sheetMaxHeight), top)
                } else {
                    Spacer(Modifier.windowInsetsBottomHeight(bars))
                }
            }
            val pad = MapPadding(bars.getLeft(density, dir), topEdge, bars.getRight(density, dir), rootHeight - bottomEdge)
            LaunchedEffect(pad) { actions.onMapPadding(pad) }
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

/** Keeps the clock and battery icons readable over a busy map. */
@Composable
private fun StatusBarScrim() {
    val c = MaterialTheme.colorScheme.surface
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.85f), c.copy(alpha = 0f))))
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(12.dp),
    )
}

@Composable
private fun UpdateBanner(latest: il.transit.core.update.LatestRelease, vm: MainActions) {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
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
private fun SearchCard(state: UiState, vm: MainActions, onSavePlace: (LatLon) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            ModeRow(state, vm)
            Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PlaceRow(R.string.from, placeLabel(state.from), state.editing == Field.FROM) { vm.startEditing(Field.FROM) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (state.mode == AppMode.DROP_OFF) {
                        PlaceRow(
                            R.string.driver_to,
                            state.driverTo?.let { placeLabel(it) },
                            state.editing == Field.DRIVER_TO,
                            placeholderRes = R.string.choose_driver_destination,
                        ) { vm.startEditing(Field.DRIVER_TO) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                if (state.editing != null) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = vm::onQuery,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
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
}

/** The search card folded to one line, so results get the room on short phones. Tap to unfold. */
@Composable
private fun CompactSearch(state: UiState, onOpen: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.to?.let { placeLabel(it) } ?: stringResource(R.string.choose_destination),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.compact_from, placeLabel(state.from)) + " · " + stringResource(modeLabel(state.mode)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.edit_search),
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

private fun modeLabel(mode: AppMode): Int = when (mode) {
    AppMode.TRIP -> R.string.mode_trip
    AppMode.BETTER_START -> R.string.mode_better_start
    AppMode.DROP_OFF -> R.string.mode_drop_off
    AppMode.PICK_UP -> R.string.mode_pick_up
}

/** One scrolling line of mode chips (never two lines, even in Hebrew), then the ⋮ menu. */
@Composable
private fun ModeRow(state: UiState, vm: MainActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppMode.entries.forEach { mode ->
                FilterChip(state.mode == mode, { vm.setMode(mode) }, label = { Text(stringResource(modeLabel(mode))) })
            }
        }
        OverflowMenu(vm)
    }
}

@Composable
private fun OverflowMenu(vm: MainActions) {
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
    // The label column grows with the font size, so "Driver to" never clips at 200%.
    val labelWidth = 64.dp * LocalDensity.current.fontScale.coerceIn(1f, 2f)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(labelWidth).padding(end = 6.dp),
        )
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
private fun TimeRow(state: UiState, vm: MainActions) {
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
        if (state.mode == AppMode.TRIP) {
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

/** Fills whatever room is left above the keyboard ([modifier] carries the weight). */
@Composable
private fun SuggestionList(state: UiState, vm: MainActions, modifier: Modifier) {
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
private fun SuggestionRow(title: String, detail: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
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
private fun SavedChips(state: UiState, vm: MainActions) {
    if (state.savedPlaces.isEmpty() && state.savedTrips.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Over the map: a surface fill, or the outline-only chip disappears into the streets.
        val colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        state.savedTrips.forEach { t ->
            AssistChip(onClick = { vm.runTrip(t) }, label = { Text("↗ ${t.name}") }, colors = colors)
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

// --- panels ----------------------------------------------------------------------------------

/**
 * The bottom sheet's top: a handle that folds the sheet to its title row (drag down or tap)
 * and unfolds it (drag up or tap), so a small phone can see the route on the map.
 */
@Composable
private fun SheetHeader(collapsed: Boolean, onCollapse: (Boolean) -> Unit, title: @Composable () -> Unit) {
    val threshold = with(LocalDensity.current) { 24.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    val label = stringResource(if (collapsed) R.string.panel_expand else R.string.panel_collapse)
    Column(
        Modifier.fillMaxWidth()
            .draggable(
                rememberDraggableState { drag += it },
                Orientation.Vertical,
                onDragStarted = { drag = 0f },
                onDragStopped = {
                    if (drag > threshold) onCollapse(true) else if (drag < -threshold) onCollapse(false)
                },
            )
            .clickable(onClickLabel = label) { onCollapse(!collapsed) },
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(width = 32.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
            )
        }
        title()
    }
}

/** The sheet itself: content clear of the navigation bar, side cutouts and rounded corners. */
@Composable
private fun Sheet(modifier: Modifier, shape: Shape, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.windowInsetsPadding(barsAndCutout.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 8.dp, bottom = 12.dp),
            content = content,
        )
    }
}

@Composable
private fun ResultsPanel(
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
                        stringResource(R.string.offline_showing, hhmm(since)),
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
                else -> LazyColumn(list) {
                    if (state.mode == AppMode.TRIP && (state.results?.itineraries?.size ?: 0) > 1) item { SortChips(state, vm) }
                    state.chain?.let { c -> item { ChainSummary(state, c) } }
                    itemsIndexed(state.options) { i, itin ->
                        if (state.chain != null) {
                            Text(chainLegTitle(state, i), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        }
                        ItineraryCard(itin, selected = i == state.selected, fareProfile = state.settings.fareProfile, searchedAt = state.time) { vm.select(i) }
                    }
                    if (state.options.isNotEmpty()) item { LastRideSection(state, vm) }
                    if (state.mode == AppMode.TRIP && state.selectedItinerary?.firstTransitLeg != null && state.chain == null) item { WayBackRow(vm) }
                    if (state.mode == AppMode.TRIP && state.options.isNotEmpty()) item { CarRow(state, vm) }
                    if (state.options.isNotEmpty()) item { FareNote() }
                }
            }
            }
        }
    }
}

/** A selectable option in a results list: tinted and outlined when it is the one on the map. */
@Composable
private fun Modifier.option(selected: Boolean, onClick: () -> Unit): Modifier {
    val shape = MaterialTheme.shapes.medium
    val c = MaterialTheme.colorScheme
    return fillMaxWidth()
        .padding(vertical = 2.dp)
        .clip(shape)
        .background(if (selected) c.primaryContainer else Color.Transparent)
        .border(1.dp, if (selected) c.primary.copy(alpha = 0.6f) else Color.Transparent, shape)
        .clickable(onClick = onClick)
        .padding(10.dp)
}

/** A reminder that is set, with its Cancel. (Setting one is a chip on the selected option.) */
@Composable
private fun ReminderRow(state: UiState, vm: MainActions) {
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
private fun RideRow(state: UiState, vm: MainActions) {
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
private val LocalTrackActions = compositionLocalOf<@Composable () -> Unit> { {} }

@Composable
private fun TrackChips(state: UiState, actions: ScreenActions) {
    val canTrack = state.selectedItinerary?.firstTransitLeg != null && state.offlineSince == null
    if (state.mode == AppMode.TRIP && state.reminder == null && canTrack) {
        ActionChip(R.string.remind_me, Icons.Default.Notifications, actions.remind)
    }
    if (!state.riding && canTrack) ActionChip(R.string.ride_start, Icons.Default.PlayArrow, actions.startRide)
}

/** The selected card's action chips: "send to driver" (car tabs) and [LocalTrackActions]. */
@Composable
private fun OptionActions(onSend: (() -> Unit)?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
        if (onSend != null) ActionChip(R.string.send_to_driver, Icons.AutoMirrored.Filled.Send, onSend)
        LocalTrackActions.current()
    }
}

@Composable
private fun ActionChip(labelRes: Int, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize), tint = MaterialTheme.colorScheme.primary) },
    )
}

@Composable
private fun ErrorRow(error: UiError, vm: MainActions) {
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
    Column(Modifier.option(selected, onClick)) {
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
        if (selected) {
            WalkDirections(itin)
            OptionActions(onSend = null)
        }
    }
}

/** Errands: each stop on the way with its stay chips and ✕, then "+ Stop on the way". */
@Composable
private fun ChainStopsRows(state: UiState, vm: MainActions) {
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
    return "${i + 1}. ${names.getOrElse(i) { "" }} → ${names.getOrElse(i + 1) { "" }}"
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
private fun CarRow(state: UiState, vm: MainActions) {
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
private fun WayBackRow(vm: MainActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.way_back_after), style = MaterialTheme.typography.bodySmall)
        listOf(60, 120, 180).forEach { m ->
            TextButton(onClick = { vm.wayBack(m) }) { Text(stringResource(R.string.hours_short, m / 60)) }
        }
    }
}

/** My lines: each pinned line's next departures at its stop, with live delays. */
@Composable
private fun FavoritesDialog(state: UiState, vm: MainActions) {
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
                                        listOf(f.line, f.headsign).filter { it.isNotBlank() }.joinToString(" → "),
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
private fun SortChips(state: UiState, vm: MainActions) {
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
private fun WalkDirections(itin: Itinerary) {
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
private fun LastRideSection(state: UiState, vm: MainActions) {
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
        Modifier.background(if (c.kind == LegKind.WALK) Color.Transparent else color, MaterialTheme.shapes.extraSmall)
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
            // Black on a yellow line, white on a blue one: whatever the operator's colour is.
            color = if (c.kind == LegKind.WALK) MaterialTheme.colorScheme.onSurfaceVariant else parseColor(onColor(c.color)),
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
private fun BetterStartList(state: UiState, result: BetterStartResult, vm: MainActions, modifier: Modifier) {
    val context = LocalContext.current
    LazyColumn(modifier) {
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
    Column(Modifier.option(selected, onClick)) {
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
        if (selected) OptionActions(onSend)
    }
}

// --- let me off on the way -----------------------------------------------------------------------

@Composable
private fun DropOffList(state: UiState, result: DropOffResult, vm: MainActions, modifier: Modifier) {
    val context = LocalContext.current
    LazyColumn(modifier) {
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
    Column(Modifier.option(selected, onClick)) {
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
        if (selected) OptionActions(onSend)
    }
}

// --- best pick-up point --------------------------------------------------------------------------

@Composable
private fun PickUpList(state: UiState, result: PickUpResult, vm: MainActions, modifier: Modifier) {
    val context = LocalContext.current
    LazyColumn(modifier) {
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
    Column(Modifier.option(selected, onClick)) {
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
        if (selected) OptionActions(onSend)
    }
}

// --- stop departures --------------------------------------------------------------------------

@Composable
private fun StopPanel(
    sheet: StopSheet,
    favorites: List<FavoriteLine>,
    vm: MainActions,
    collapsed: Boolean,
    onCollapse: (Boolean) -> Unit,
    modifier: Modifier,
    shape: Shape,
) {
    Sheet(modifier, shape) {
        SheetHeader(collapsed, onCollapse) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sheet.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.departures), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = vm::closeStop) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
        }
        if (!collapsed) {
            when {
                sheet.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                sheet.failed -> Text(stringResource(R.string.err_network), Modifier.padding(vertical = 12.dp))
                sheet.rows.isEmpty() -> Text(stringResource(R.string.no_departures), Modifier.padding(vertical = 12.dp))
                else -> LazyColumn(Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
                    items(sheet.rows) { r ->
                        val pinned = favorites.any { it.stopId == sheet.stopId && it.matches(r) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { DepartureView(r) }
                            // ☆ pins this line in this direction at this stop ("My lines").
                            TextButton(onClick = { vm.toggleFavorite(sheet, r) }) {
                                Text(if (pinned) "★" else "☆", color = MaterialTheme.colorScheme.tertiary)
                            }
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
            Modifier.background(parseColor(r.color), MaterialTheme.shapes.extraSmall).padding(horizontal = 6.dp, vertical = 2.dp),
        ) { Text(r.line.ifBlank { kindName(r.kind) }, style = MaterialTheme.typography.labelLarge, color = parseColor(onColor(r.color))) }
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
private fun SettingsDialog(state: UiState, vm: MainActions, actions: ScreenActions) {
    var editingRoutine by remember { mutableStateOf<SavedPlace?>(null) }
    editingRoutine?.let { p ->
        RoutineDialog(p, onDismiss = { editingRoutine = null }) { r -> vm.setRoutine(p, r); editingRoutine = null }
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
        item { OfflineSection(actions) }
        if (state.savedPlaces.isNotEmpty() || state.savedTrips.isNotEmpty()) {
            item { Text(stringResource(R.string.saved), style = MaterialTheme.typography.labelLarge) }
            items(state.savedTrips) { t -> SavedRow("↗ ${t.name}") { vm.deleteTrip(t) } }
            items(state.savedPlaces) { p -> SavedPlaceRow(p, onRoutine = { onRoutine(p) }) { vm.deletePlace(p) } }
        }
    }
}

@Composable
private fun HistoryDialog(state: UiState, vm: MainActions) {
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
internal fun HistoryContent(state: UiState, vm: MainActions) {
    val st = state.historyStats
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
}

@Composable
private fun StatTile(value: String, label: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
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
    // Surface(onClick) keeps a 48dp touch target around the small pill.
    Surface(
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TRANSITOUS_SOURCES))) },
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
        shadowElevation = 2.dp,
    ) {
        Text(
            stringResource(R.string.attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
