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
internal fun DropOffList(state: UiState, result: DropOffResult, vm: MainActions, modifier: Modifier) {
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
internal fun DropOffCard(row: DropOffRow, selected: Boolean, onClick: () -> Unit, onSend: (() -> Unit)?) {
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
