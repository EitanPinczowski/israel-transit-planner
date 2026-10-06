package il.transit.planner.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import il.transit.core.present.AlertText
import il.transit.core.present.StopRole
import il.transit.core.present.TripDetails
import il.transit.core.present.TripStopRow
import il.transit.core.present.onColor
import il.transit.planner.R
import il.transit.planner.ui.TripSheet

/**
 * Every stop of the vehicle behind one leg (Phase 8 B1). Live times, ⚠ alerts and "live
 * position" only appear when the answer carries them — on Israeli MOT lines it never does
 * (2026-10), so the usual sheet is the timetable with a "scheduled position" note.
 */
@Composable
internal fun TripDetailsSheet(sheet: TripSheet, onClose: () -> Unit, modifier: Modifier, shape: Shape) {
    // No dispatcher in screenshot tests; on a phone there always is one.
    if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler(onBack = onClose)
    val d = sheet.details
    var collapsed by rememberSaveable { mutableStateOf(false) }
    Sheet(modifier, shape) {
        SheetHeader(collapsed, { collapsed = it }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { if (d != null) Header(d) }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
        }
        if (collapsed) return@Sheet
        if (d == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Sheet
        }
        Column(Modifier.weight(1f, fill = false).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (d.tripCancelled) Text(stringResource(R.string.trip_cancelled), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
            if (d.alerts.isNotEmpty()) Alerts(d.alerts)
            if (sheet.legOnly) Note(stringResource(R.string.trip_leg_only))
            sheet.vehicle?.let { v ->
                Note(stringResource(if (v.realTime) R.string.trip_live_position else R.string.trip_scheduled_position))
            }
            val list = rememberLazyListState()
            // Open on where it matters: the boarding stop, or the next stop once on board.
            LaunchedEffect(d.rows.size) {
                val board = d.rows.indexOfFirst { it.role == StopRole.BOARD }
                val target = listOfNotNull(board.takeIf { it >= 0 }, d.nextIndex).maxOrNull() ?: 0
                list.scrollToItem((target - 1).coerceAtLeast(0))
            }
            LazyColumn(Modifier.weight(1f, fill = false), state = list) {
                itemsIndexed(d.rows) { _, r -> StopRowView(r, parseColor(d.color)) }
            }
        }
    }
}

@Composable
private fun Header(d: TripDetails) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.background(parseColor(d.color), MaterialTheme.shapes.extraSmall).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(d.line ?: kindName(d.kind), style = MaterialTheme.typography.labelLarge, color = parseColor(onColor(d.color)))
        }
        Spacer(Modifier.width(8.dp))
        Text(d.headsign, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Alerts(alerts: List<AlertText>) {
    // A service alert warns, it is not an app error: tertiary, like the offline banner.
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            alerts.forEach { a ->
                Text("⚠ ${a.header}", style = MaterialTheme.typography.titleSmall)
                if (a.description.isNotBlank()) Text(a.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StopRowView(r: TripStopRow, lineColor: Color) {
    val mine = r.role == StopRole.BOARD || r.role == StopRole.ALIGHT
    val onRide = mine || r.role == StopRole.RIDE
    val strike = if (r.cancelled) TextDecoration.LineThrough else null
    Row(
        Modifier.fillMaxWidth()
            .background(if (mine) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, MaterialTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .alpha(if (r.passed && !mine) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(r.scheduled, style = MaterialTheme.typography.titleSmall, textDecoration = strike, modifier = Modifier.width(48.dp))
        // The line itself: a dot per stop, filled on the user's part of the ride.
        Box(
            Modifier.size(if (mine) 14.dp else 10.dp)
                .background(if (onRide) lineColor else MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                r.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                textDecoration = strike,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val tags = listOfNotNull(
                stringResource(R.string.trip_board).takeIf { r.role == StopRole.BOARD },
                stringResource(R.string.trip_alight).takeIf { r.role == StopRole.ALIGHT },
                stringResource(R.string.trip_next).takeIf { r.isNext },
                stringResource(R.string.trip_skipped).takeIf { r.cancelled },
            )
            if (tags.isNotEmpty()) Text(tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (mine) PlatformLine(r.platform)
            r.alerts.forEach { a -> Text("⚠ ${a.header}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
        }
        // Live time: only with real-time data, which MOT lines never have (2026-10).
        val delay = r.delayMin
        r.live?.let { live ->
            Column(horizontalAlignment = Alignment.End) {
                val late = delay != null && delay > 0
                val color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Text(stringResource(R.string.trip_live_time, live), style = MaterialTheme.typography.labelMedium, color = color)
                if (late) Text(stringResource(R.string.late_paren, delay!!), style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}
