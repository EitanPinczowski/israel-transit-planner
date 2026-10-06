@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package il.transit.planner.ui

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
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
import il.transit.planner.ui.screens.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Wider than this (landscape, foldables, tablets), the panels move into one side column. */
internal val WIDE = 600.dp

/** Shorter than this (or narrower than [WIDE]), the search card folds to one line while results are shown. */
internal val SHORT = 700.dp

/** Test tags of the screen's regions: the UI tests' layout audit checks them against each other. */
object UiTags {
    /** The search card column (phones), or the whole side column (wide windows). */
    const val TOP = "top"
    /** The results sheet with the attribution chip above it (phones only). */
    const val BOTTOM = "bottom"
    const val SUGGESTIONS = "suggestions"
}

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
    val crashLog: il.transit.planner.ui.screens.CrashLogUi = il.transit.planner.ui.screens.CrashLogUi(),
    /** Opens the trip sheet (all stops of the vehicle) for a transit leg of the selected option. */
    val openLeg: (il.transit.core.api.Leg) -> Unit = {},
)

/** Pixels of the map hidden on each side (absolute left/right, not start/end). */
data class MapPadding(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)

/** System bars and the camera cutout, without the keyboard (that one only matters while typing). */
internal val barsAndCutout: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

/**
 * The whole screen over the map. Phones: search card on top, results as a bottom sheet.
 * Wide windows: both in one side column at the start edge. Every edge respects the status
 * and navigation bars, the camera cutout (which is on a side in landscape) and the keyboard.
 */
@Composable
fun MainScreen(
    state: UiState,
    vm: MainActions,
    actions: ScreenActions,
    /** The open trip sheet (TripDetailsSheet), shown in the results' place; null when closed. */
    tripSheet: (@Composable (Modifier, Shape) -> Unit)? = null,
    map: @Composable () -> Unit,
) {
    var savingPlace by remember { mutableStateOf<LatLon?>(null) }
    var savingTrip by remember { mutableStateOf(false) }
    var collapsed by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    val showPanel = state.editing == null &&
        (tripSheet != null || state.stopSheet != null || state.loading || state.hasResults || state.error != null)
    // A new search always shows its answer (and folds the search again); closing the results
    // unfolds the search card.
    LaunchedEffect(state.loading) { if (state.loading) { collapsed = false; searchOpen = false } }
    LaunchedEffect(showPanel) { if (!showPanel) searchOpen = false }

    // Back closes what is open, innermost first; with nothing open it leaves the app as usual.
    // (The trip sheet has its own handler, composed later, so it closes first.) Screenshot
    // tests have no back dispatcher, hence the check.
    if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler(enabled = state.editing != null || showPanel) {
        when {
            state.editing != null -> vm.cancelEditing()
            state.stopSheet != null -> vm.closeStop()
            searchOpen -> searchOpen = false
            else -> vm.clearResults()
        }
    }

    val panel: @Composable (Modifier, Shape) -> Unit = { modifier, shape ->
        if (tripSheet != null) {
            tripSheet(modifier, shape)
        } else if (state.stopSheet != null) {
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
        // Phones (any height: a Fold cover screen is tall but narrow, so its tabs take two
        // lines) and short windows can't fit the whole search card and the results: the
        // search folds to one line; unfolded, it gets the screen until a new search or Back
        // brings the results back.
        val foldsForResults = maxWidth < WIDE || maxHeight < SHORT
        val compactSearch = showPanel && foldsForResults && !searchOpen
        val searchOnly = showPanel && foldsForResults && searchOpen
        val panelShown = showPanel && !searchOnly
        val sheetMaxHeight = maxHeight * 0.5f
        val screenHeight = maxHeight // read inside nested layout scopes below

        map()
        StatusBarScrim()

        val search: @Composable () -> Unit = {
            // On a short screen with results the banner waits until they close: the map needs
            // the room (R7), and the update is not going anywhere.
            if (!compactSearch) state.update?.let { UpdateBanner(it, state.updateState, vm) }
            if (state.editing != null) {
                // The tapped row turns into the text field, in place; the suggestions follow the card.
                CompositionLocalProvider(
                    LocalPlaceEditor provides { m -> PlaceEditor(state, vm, m) },
                    LocalCompactEditing provides (maxHeight < SHORT),
                ) { SearchCard(state, vm, onSavePlace = { savingPlace = it }) }
            } else if (compactSearch) {
                CompactSearch(state) { searchOpen = true }
            } else {
                SearchCard(state, vm, onSavePlace = { savingPlace = it })
            }
        }

        if (maxWidth >= WIDE) {
            var side by remember { mutableStateOf(0 to 0) } // left and right edge, px
            Column(
                Modifier.fillMaxHeight().testTag(UiTags.TOP)
                    .onGloballyPositioned { c -> c.boundsInRoot().let { side = it.left.roundToInt() to it.right.roundToInt() } }
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                    .width(minOf(420.dp, maxWidth * 0.5f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    state.editing != null -> {
                        search()
                        SuggestionList(state, vm, Modifier.weight(1f, fill = false).testTag(UiTags.SUGGESTIONS))
                    }
                    panelShown -> {
                        search()
                        panel(Modifier.weight(1f, fill = false), MaterialTheme.shapes.extraLarge)
                    }
                    else -> ScrollingSearch(Modifier.weight(1f, fill = false), search) { if (!showPanel) SavedChips(state, vm) }
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
            // The search column ends where the bottom part (credit, results) begins, so a long
            // suggestion list or a tall card can never run under it.
            val belowSearch = WindowInsets(bottom = (rootHeight - bottomEdge).coerceAtLeast(0))
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(belowSearch)).padding(12.dp)) {
                Column(
                    Modifier.fillMaxWidth().testTag(UiTags.TOP).onGloballyPositioned { topEdge = it.boundsInRoot().bottom.roundToInt() },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        state.editing != null -> {
                            search()
                            SuggestionList(state, vm, Modifier.weight(1f, fill = false).testTag(UiTags.SUGGESTIONS))
                        }
                        panelShown -> search()
                        else -> ScrollingSearch(Modifier.weight(1f, fill = false), search) { if (!showPanel) SavedChips(state, vm) }
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().testTag(UiTags.BOTTOM)
                    .onGloballyPositioned { bottomEdge = it.boundsInRoot().top.roundToInt() },
            ) {
                AttributionChip(Modifier.windowInsetsPadding(bars.only(WindowInsetsSides.Horizontal)).padding(8.dp))
                if (panelShown) {
                    val top = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
                    // At most half the screen, and never so tall that less than [MAP_SHARE] of it
                    // stays map between the search and the sheet (R7): on a 640 dp phone, or at
                    // the largest font, the folded search and the credit take their part first.
                    val searchBottom = with(density) { topEdge.toDp() }
                    val cap = minOf(sheetMaxHeight, screenHeight * (1f - MAP_SHARE) - searchBottom - CREDIT_ROOM)
                    panel(Modifier.heightIn(max = cap.coerceAtLeast(MIN_SHEET)), top)
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

/** The search card (and what follows it) scrolling when it is taller than the window, as in
 *  landscape with the drop-off slider or at the largest font. Only used with no results beside it. */
@Composable
private fun ScrollingSearch(modifier: Modifier, search: @Composable () -> Unit, below: @Composable () -> Unit) {
    // A scrolling column clips at its edges: room for the card's shadow inside, drawn where it was.
    Column(
        modifier.offset(y = -SHADOW_ROOM).verticalScroll(rememberScrollState()).padding(vertical = SHADOW_ROOM),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        search()
        below()
    }
}

private val SHADOW_ROOM = 4.dp

/** Of a phone's height, at least this much stays map while a sheet is open (layout rule R7: 25%). */
private const val MAP_SHARE = 0.3f

/** The Transitous credit chip above the sheet, with its padding. */
private val CREDIT_ROOM = 48.dp

/** A sheet is never squeezed below its header and one row. */
private val MIN_SHEET = 160.dp

/** Keeps the clock and battery icons readable over a busy map. */
@Composable
internal fun StatusBarScrim() {
    val c = MaterialTheme.colorScheme.surface
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.85f), c.copy(alpha = 0f))))
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(12.dp),
    )
}

/** The search card folded to one line, so results get the room on short phones. Tap to unfold. */
@Composable
internal fun CompactSearch(state: UiState, onOpen: () -> Unit) {
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
