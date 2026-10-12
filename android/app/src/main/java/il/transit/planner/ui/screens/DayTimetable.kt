@file:OptIn(ExperimentalLayoutApi::class)

package il.transit.planner.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import il.transit.core.features.ISRAEL
import il.transit.core.present.DayTimetable
import il.transit.core.present.hhmm
import il.transit.core.present.onColor
import il.transit.planner.R
import il.transit.planner.ui.MainActions
import il.transit.planner.ui.StopSheet
import il.transit.planner.ui.TimetableAction
import il.transit.planner.ui.TimetableUi
import java.time.Instant
import java.time.format.DateTimeFormatter

/** The header of the stop sheet while a line's day timetable is open: back, line chip, direction, stop. */
@Composable
internal fun TimetableHeader(sheet: StopSheet, t: TimetableUi, vm: MainActions) {
    // Back returns to the departures first; MainScreen's handler then closes the sheet.
    if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler { vm.timetable(TimetableAction.Close) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.timetable(TimetableAction.Close) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.timetable_back))
        }
        Box(Modifier.background(parseColor(t.color), MaterialTheme.shapes.extraSmall).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(t.line.ifBlank { kindName(t.kind) }, style = MaterialTheme.typography.labelLarge, color = parseColor(onColor(t.color)))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.headsign, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sheet.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The day timetable's body: Today / Tomorrow, the offline note, the hour grid ("07 │ 05 25 45",
 * the next one marked, gone ones faded), More, and once at the foot: the times are scheduled.
 */
@Composable
internal fun ColumnScope.TimetableBody(t: TimetableUi, vm: MainActions) {
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !t.tomorrow, onClick = { vm.timetable(TimetableAction.Day(false)) }, label = { Text(stringResource(R.string.timetable_today)) })
        FilterChip(selected = t.tomorrow, onClick = { vm.timetable(TimetableAction.Day(true)) }, label = { Text(stringResource(R.string.timetable_tomorrow)) })
    }
    t.savedAt?.let { at -> SavedNote(at, t.savedLastNight) }
    when {
        t.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        t.failed -> EmptyNote(stringResource(R.string.err_network)) {
            OutlinedButton(onClick = { vm.timetable(TimetableAction.Retry) }) { Text(stringResource(R.string.retry)) }
        }
        t.rows.isEmpty() && !t.canLoadMore -> EmptyNote(stringResource(if (t.savedAt != null) R.string.timetable_none_left else R.string.timetable_none))
        else -> {
            val grid = DayTimetable.grid(t.rows, t.now)
            val list = rememberLazyListState()
            // Open at the hour of the next departure, with the hour before it in view.
            val nextHour = grid.indexOfFirst { h -> h.cells.any { it.next } }
            LaunchedEffect(t.line, t.headsign, t.tomorrow, nextHour >= 0) { if (nextHour > 0) list.scrollToItem(nextHour - 1) }
            LazyColumn(Modifier.weight(1f, fill = false), state = list) {
                items(grid, key = { it.hour }) { h -> HourRow(h) }
                if (t.canLoadMore || t.loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            if (t.loadingMore) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            else OutlinedButton(onClick = { vm.timetable(TimetableAction.More) }) { Text(stringResource(R.string.timetable_more)) }
                        }
                    }
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        stringResource(R.string.timetable_scheduled),
        Modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One hour: the hour at the start (the right in Hebrew), a rule, then its minutes in reading order. */
@Composable
private fun HourRow(h: DayTimetable.Hour) {
    val nextLabel = stringResource(R.string.timetable_next)
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
            Text(
                h.hour,
                Modifier.widthIn(min = 32.dp).padding(top = 2.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            VerticalDivider(Modifier.padding(horizontal = 8.dp).size(width = 1.dp, height = 22.dp))
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                h.cells.forEach { c ->
                    val time = "${h.hour}:${c.minute}"
                    when {
                        c.next -> Text(
                            c.minute,
                            Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraSmall)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .semantics { contentDescription = "$time $nextLabel" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        else -> Text(
                            c.minute,
                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp).semantics { contentDescription = time },
                            style = MaterialTheme.typography.bodyLarge,
                            // onSurfaceVariant, not outline: gone times still read (≥ 4.5:1) in both themes.
                            color = if (c.gone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (c.gone) FontWeight.Light else null,
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** "Offline · saved last night at 02:14" (or "saved 12/10 at 02:14" for an older board). */
@Composable
internal fun SavedNote(at: Instant, lastNight: Boolean) {
    val text = if (lastNight) stringResource(R.string.timetable_saved_last_night, hhmm(at))
    else stringResource(R.string.timetable_saved_at, DAY.format(at.atZone(ISRAEL)), hhmm(at))
    // Not the error red: a saved board is a normal offline state, not a failure.
    Text(text, Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
}

/** No departures, or no answer: a centred note, with an action below it when there is one. */
@Composable
private fun EmptyNote(text: String, action: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action()
    }
}

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M")
