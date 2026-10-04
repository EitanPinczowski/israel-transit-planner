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

internal const val TRANSITOUS_SOURCES = "https://transitous.org/sources/"

/** 5–[max] minutes in steps of 5. Local while dragging; [onDone] (a search) runs once, on release. */
@Composable
internal fun MinutesSlider(labelRes: Int, current: Int, onDone: (Int) -> Unit, max: Int = 30) {
    var value by remember(current) { mutableStateOf(current.toFloat()) }
    Column {
        Text(stringResource(labelRes, value.toInt()), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value,
            onValueChange = { value = (Math.round(it / 5f) * 5).toFloat() },
            onValueChangeFinished = { onDone(value.toInt()) },
            valueRange = 5f..max.toFloat(),
            steps = (max - 5) / 5 - 1,
        )
    }
}

@Composable
internal fun PlaceRow(
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

/**
 * The bottom sheet's top: a handle that folds the sheet to its title row (drag down or tap)
 * and unfolds it (drag up or tap), so a small phone can see the route on the map.
 */
@Composable
internal fun SheetHeader(collapsed: Boolean, onCollapse: (Boolean) -> Unit, title: @Composable () -> Unit) {
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
internal fun Sheet(modifier: Modifier, shape: Shape, content: @Composable ColumnScope.() -> Unit) {
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

/** A selectable option in a results list: tinted and outlined when it is the one on the map. */
@Composable
internal fun Modifier.option(selected: Boolean, onClick: () -> Unit): Modifier {
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

/** The selected card's action chips: "send to driver" (car tabs) and [LocalTrackActions]. */
@Composable
internal fun OptionActions(onSend: (() -> Unit)?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
        if (onSend != null) ActionChip(R.string.send_to_driver, Icons.AutoMirrored.Filled.Send, onSend)
        LocalTrackActions.current()
    }
}

@Composable
internal fun ActionChip(labelRes: Int, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize), tint = MaterialTheme.colorScheme.primary) },
    )
}

/** "≈ ₪8", isolated so Hebrew text around it cannot reorder the amount. */
@Composable
internal fun FareText(f: FareEstimate?) {
    if (f == null) return
    val text = if (f.agorot == 0) stringResource(R.string.fare_free_label) else "\u2068${fareLabel(f)}\u2069"
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
}

@Composable
internal fun LegChipView(c: LegChip) {
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
internal fun kindName(k: LegKind): String = stringResource(
    when (k) {
        LegKind.WALK -> R.string.kind_walk
        LegKind.BUS -> R.string.kind_bus
        LegKind.TRAIN -> R.string.kind_train
        LegKind.LIGHT_RAIL -> R.string.kind_light_rail
        LegKind.CAR -> R.string.kind_car
        LegKind.OTHER -> R.string.kind_other
    },
)

internal fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

@Composable
internal fun NameDialog(titleRes: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
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
internal fun AttributionChip(modifier: Modifier) {
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
