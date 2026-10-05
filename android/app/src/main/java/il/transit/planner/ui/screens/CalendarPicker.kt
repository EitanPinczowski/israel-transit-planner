@file:OptIn(ExperimentalLayoutApi::class)

package il.transit.planner.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import il.transit.core.plan.CalendarEvent
import il.transit.core.plan.CalendarSuggest
import il.transit.core.present.hhmm
import il.transit.planner.R
import il.transit.planner.TransitApp
import il.transit.planner.ui.CalendarConfirm
import il.transit.planner.ui.CalendarViewModel
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.UiState
import java.time.Instant

/**
 * Where the Trip tab's search card puts the calendar chip. The Activity provides [CalendarChip]
 * (it needs the app, a ViewModel and a permission launcher); screenshots render without one.
 */
val LocalCalendarChip = compositionLocalOf<@Composable (UiState) -> Unit> { {} }

/**
 * "From my calendar" chip for the Trip tab. The calendar permission is asked only when the
 * chip is tapped, after a rationale; a refusal leaves the app exactly as before (type the place).
 */
@Composable
internal fun CalendarChip(state: UiState, vm: MainViewModel) {
    val app = LocalContext.current.applicationContext as TransitApp
    val cal: CalendarViewModel = viewModel(factory = CalendarViewModel.factory(app))
    val cs by cal.state.collectAsState()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), cal::onPermission)

    AssistChip(
        onClick = cal::onChip,
        enabled = !cs.resolving,
        label = { Text(stringResource(if (cs.resolving) R.string.calendar_finding else R.string.calendar_chip)) },
        leadingIcon = {
            if (cs.resolving) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        },
    )

    if (cs.rationale) {
        AlertDialog(
            onDismissRequest = cal::dismiss,
            title = { Text(stringResource(R.string.calendar_rationale_title)) },
            text = { Text(stringResource(R.string.calendar_rationale)) },
            confirmButton = { TextButton(onClick = { ask.launch(Manifest.permission.READ_CALENDAR) }) { Text(stringResource(R.string.calendar_allow)) } },
            dismissButton = { TextButton(onClick = cal::dismiss) { Text(stringResource(R.string.calendar_not_now)) } },
        )
    }
    if (cs.refused) {
        AlertDialog(
            onDismissRequest = cal::dismiss,
            text = { Text(stringResource(R.string.calendar_refused)) },
            confirmButton = { TextButton(onClick = cal::dismiss) { Text(stringResource(R.string.calendar_ok)) } },
        )
    }
    cs.confirm?.let { c ->
        AlertDialog(
            onDismissRequest = cal::dismiss,
            title = {
                Text(c.arriveBy?.let { stringResource(R.string.calendar_arrive_by, hhmm(it)) } ?: stringResource(R.string.calendar_leave_now))
            },
            text = { CalendarConfirmBody(c) },
            confirmButton = {
                TextButton(onClick = { cal.dismiss(); vm.fromCalendar(c.dest.name, c.dest.at, c.arriveBy) }) { Text(stringResource(R.string.calendar_plan)) }
            },
            // The normal search, pre-filled with the event's own text (no request until typing).
            dismissButton = {
                TextButton(onClick = { cal.dismiss(); vm.fromCalendar(c.dest.source, null, c.arriveBy) }) { Text(stringResource(R.string.calendar_change)) }
            },
        )
    }
    cs.events?.let { events ->
        AlertDialog(
            onDismissRequest = cal::dismiss,
            title = { Text(stringResource(R.string.calendar_list_title)) },
            text = {
                CalendarEventList(events) { e -> cal.pick(e, vm.locationProvider(), state.settings.calendarBufferMin, vm::fromCalendar) }
            },
            confirmButton = { TextButton(onClick = cal::dismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The confirm dialog's body: where the event's text matched, and a warning when only roughly. */
@Composable
internal fun CalendarConfirmBody(c: CalendarConfirm) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.calendar_matched, c.dest.name, c.dest.source), style = MaterialTheme.typography.bodyLarge)
        if (c.dest.approximate) {
            Text(stringResource(R.string.calendar_approximate), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * The list dialog's body: the next events with a location, or "nothing coming up". Its own
 * function (and [now] a parameter) so screenshot tests render it the same on any day.
 */
@Composable
internal fun CalendarEventList(events: List<CalendarEvent>, now: Instant = Instant.now(), onPick: (CalendarEvent) -> Unit) {
    if (events.isEmpty()) {
        Text(stringResource(R.string.calendar_empty))
    } else {
        LazyColumn {
            items(events) { e ->
                EventRow(e, now) { onPick(e) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EventRow(e: CalendarEvent, now: Instant, onClick: () -> Unit) {
    val row = CalendarSuggest.row(e, now)
    val title = row.title.ifBlank { stringResource(R.string.calendar_no_title) }
    val time = if (row.tomorrow) stringResource(R.string.calendar_tomorrow_at, row.time) else row.time
    Text(
        stringResource(R.string.calendar_event_row, time, title, row.location),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    )
}

/** Settings: how many minutes before a calendar event to arrive. */
@Composable
internal fun CalendarBufferSetting(current: Int, onChange: (Int) -> Unit) {
    Column {
        Text(stringResource(R.string.calendar_buffer), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CalendarSuggest.BUFFER_CHOICES.forEach { m ->
                FilterChip(current == m, { onChange(m) }, label = { Text(stringResource(R.string.minutes_short, m)) })
            }
        }
    }
}
