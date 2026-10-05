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

/** My lines: each pinned line's next departures at its stop, with live delays. */
@Composable
internal fun FavoritesDialog(state: UiState, vm: MainActions) {
    AlertDialog(
        onDismissRequest = { vm.showFavorites(false) },
        confirmButton = { TextButton(onClick = { vm.showFavorites(false) }) { Text(stringResource(R.string.done)) } },
        title = { Text(stringResource(R.string.my_lines)) },
        text = { FavoritesContent(state, vm) },
    )
}

/** The dialog's body on its own, so screenshot tests can render it (Paparazzi draws no dialog windows). */
@Composable
internal fun FavoritesContent(state: UiState, vm: MainActions) {
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
}

@Composable
internal fun StopPanel(
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
internal fun DepartureView(r: DepartureRow) {
    Column {
        DepartureLine(r)
        // ⚠ only when the answer carries alerts; Israel sent none in 2026-10.
        r.alerts.forEach { a ->
            Text("⚠ ${a.header}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DepartureLine(r: DepartureRow) {
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
