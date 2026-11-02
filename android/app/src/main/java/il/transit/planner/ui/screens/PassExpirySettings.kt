@file:OptIn(ExperimentalMaterial3Api::class)

package il.transit.planner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.transit.core.remind.PassExpiry
import il.transit.core.user.UserSettings
import il.transit.planner.R
import il.transit.planner.remind.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

/**
 * Settings → "Rav-Kav pass ends on" (Phase 11 E1): a date or Off. Setting or changing it
 * re-arms the notice (3 days before and on the last day, 09:00 Israel time); Off cancels it;
 * a date that has passed goes back to Off. 0 requests.
 */
@Composable
internal fun PassExpirySetting(s: UserSettings, set: (UserSettings) -> Unit) {
    val context = LocalContext.current
    val today = PassExpiry.today(Instant.now())
    val endsOn = PassExpiry.parse(s.passEndsOn)
    var picking by rememberSaveable { mutableStateOf(false) }
    var refused by rememberSaveable { mutableStateOf<PassExpiry.Validity?>(null) }
    // A pass that has ended (or a broken value) is Off.
    LaunchedEffect(s.passEndsOn) {
        if (s.passEndsOn != null && (endsOn == null || PassExpiry.isPast(endsOn, Instant.now()))) {
            set(s.copy(passEndsOn = null))
            ReminderScheduler.syncPass(context, null)
        }
    }
    PassExpirySection(
        endsOn = endsOn?.takeIf { !it.isBefore(today) },
        refused = refused,
        onPick = { picking = true },
        onOff = {
            refused = null
            set(s.copy(passEndsOn = null))
            ReminderScheduler.syncPass(context, null)
        },
    )
    if (picking) {
        PassDatePicker(initial = endsOn ?: today, today = today, onDismiss = { picking = false }) { date ->
            picking = false
            val v = PassExpiry.check(date, today)
            refused = v.takeIf { it != PassExpiry.Validity.OK }
            if (v == PassExpiry.Validity.OK) {
                set(s.copy(passEndsOn = date.toString()))
                ReminderScheduler.syncPass(context, date)
            }
        }
    }
}

/** The row: the date ("Sun 1 Nov") or Off, Set / Change, Turn off, what it does, and why a date was refused. */
@Composable
internal fun PassExpirySection(endsOn: LocalDate?, refused: PassExpiry.Validity?, onPick: () -> Unit, onOff: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.pass_row), style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                endsOn?.let { stringResource(R.string.pass_date, PassExpiry.dateLabel(it, locale)) } ?: stringResource(R.string.pass_off),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = onPick) { Text(stringResource(if (endsOn == null) R.string.pass_set else R.string.pass_change)) }
            if (endsOn != null) TextButton(onClick = onOff) { Text(stringResource(R.string.pass_turn_off)) }
        }
        Text(stringResource(R.string.pass_help), style = MaterialTheme.typography.bodySmall)
        val why = when (refused) {
            PassExpiry.Validity.PAST -> R.string.pass_refused_past
            PassExpiry.Validity.TOO_FAR -> R.string.pass_refused_far
            PassExpiry.Validity.OK, null -> null
        }
        why?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

/** Material's date picker (typing allowed), limited to today … today + 400 days. Its millis are UTC midnights. */
@Composable
private fun PassDatePicker(initial: LocalDate, today: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    fun millis(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun date(m: Long) = Instant.ofEpochMilli(m).atZone(ZoneOffset.UTC).toLocalDate()
    val last = today.plusDays(PassExpiry.MAX_DAYS_AHEAD)
    val allowed = remember(today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = date(utcTimeMillis).let { !it.isBefore(today) && !it.isAfter(last) }
            override fun isSelectableYear(year: Int) = year in today.year..last.year
        }
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = millis(initial), selectableDates = allowed)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = state.selectedDateMillis != null, onClick = { state.selectedDateMillis?.let { onPick(date(it)) } }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}
