@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package il.transit.planner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import il.transit.core.calendar.DayKind
import il.transit.core.features.ISRAEL
import il.transit.core.plan.TimeMode
import il.transit.core.present.DayPick
import il.transit.planner.R
import il.transit.planner.TransitApp
import il.transit.planner.ui.UiState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/**
 * Pick a day (Phase 11 E7): Depart at / Arrive by open a day step (Today, Tomorrow, five
 * weekdays, Other date…), then the clock. A time already gone today asks "Tomorrow at …?" and
 * never moves by itself. All logic is in core [DayPick]; nothing here sends a request.
 */
internal sealed interface TimeStep {
    val mode: TimeMode

    data class Day(override val mode: TimeMode) : TimeStep
    data class Date(override val mode: TimeMode) : TimeStep
    data class Clock(override val mode: TimeMode, val date: LocalDate) : TimeStep
    data class Past(override val mode: TimeMode, val at: Instant) : TimeStep
}

/** Test tags for the journey. */
object DayTags {
    const val DAY_STEP = "day_step"
    const val CLOCK = "time_step"
    const val CLOCK_OK = "time_step_ok"
    const val CLOCK_TYPE = "time_step_type"
    const val PAST = "past_ask"
    fun day(offset: Int) = "day_$offset"
}

/** "Now" for the day step: the app's clock (fixed in the `uitest` build), or one a screenshot sets. */
internal val LocalDayClock = staticCompositionLocalOf<Clock?> { null }

@Composable
internal fun dayNow(): Instant {
    val clock = LocalDayClock.current ?: (LocalContext.current.applicationContext as? TransitApp)?.clock ?: Clock.systemUTC()
    return clock.instant()
}

/** The open step of [TimeRow], and the dialogs for it; [onSet] gets the picked time. */
@Composable
internal fun TimeSteps(step: TimeStep?, state: UiState, onStep: (TimeStep?) -> Unit, onSet: (TimeMode, Instant) -> Unit) {
    step ?: return
    val now = dayNow()
    fun go(next: TimeFlow.Next) {
        onStep(next.step)
        next.set?.let { (mode, at) -> onSet(mode, at) }
    }
    val picked = state.time?.takeIf { state.timeMode == step.mode }
    when (step) {
        is TimeStep.Day -> DayStepDialog(
            now = now,
            selected = picked?.atZone(ISRAEL)?.toLocalDate(),
            onDay = { onStep(TimeStep.Clock(step.mode, it)) },
            onOther = { onStep(TimeStep.Date(step.mode)) },
            onDismiss = { onStep(null) },
        )
        is TimeStep.Date -> DateStepDialog(
            now = now,
            selected = picked?.atZone(ISRAEL)?.toLocalDate(),
            onDate = { onStep(TimeStep.Clock(step.mode, it)) },
            onDismiss = { onStep(TimeStep.Day(step.mode)) },
        )
        is TimeStep.Clock -> ClockStepDialog(
            date = step.date,
            now = now,
            initial = (picked ?: now).atZone(ISRAEL).toLocalTime(),
            onTime = { t -> go(TimeFlow.clock(step, t, now)) },
            onBack = { onStep(TimeStep.Day(step.mode)) },
        )
        is TimeStep.Past -> PastAskDialog(
            at = step.at,
            now = now,
            onYes = { go(TimeFlow.yes(step)) },
            onChange = { go(TimeFlow.change(step)) },
        )
    }
}

/**
 * What each answer of the clock and of "Tomorrow at …?" leads to: the next step, and the time to
 * set, if any. Plain Kotlin, so the flow is a JVM test (`TimeFlowTest`); nothing is set before
 * the question is answered.
 */
internal object TimeFlow {
    data class Next(val step: TimeStep?, val set: Pair<TimeMode, Instant>? = null)

    /** OK on the clock: a time already gone today asks first; any other is set. */
    fun clock(step: TimeStep.Clock, time: LocalTime, now: Instant): Next {
        val at = DayPick.at(step.date, time)
        return if (DayPick.past(at, now)) Next(TimeStep.Past(step.mode, at)) else Next(null, step.mode to at)
    }

    /** Yes: the same clock time on the next day, in the same mode. */
    fun yes(step: TimeStep.Past) = Next(null, step.mode to DayPick.nextDay(step.at))

    /** Change: the clock again, on the day picked. */
    fun change(step: TimeStep.Past) = Next(TimeStep.Clock(step.mode, step.at.atZone(ISRAEL).toLocalDate()))
}

@Composable
internal fun DayStepDialog(now: Instant, selected: LocalDate?, onDay: (LocalDate) -> Unit, onOther: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(DayTags.DAY_STEP),
        title = { Text(stringResource(R.string.day_step_title)) },
        text = { DayStepContent(now, selected, onDay, onOther) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The chips: in a FlowRow, so they wrap and mirror in Hebrew (Today at the right). */
@Composable
internal fun DayStepContent(now: Instant, selected: LocalDate?, onDay: (LocalDate) -> Unit, onOther: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val days = remember(now) { DayPick.days(now) }
    val night = DayPick.night(now)
    val inChips = selected != null && days.any { it.date == selected }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { d ->
                FilterChip(
                    selected = d.date == selected,
                    onClick = { onDay(d.date) },
                    modifier = Modifier.testTag(DayTags.day(d.offset)),
                    label = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(dayName(d, night, locale), maxLines = 1)
                            d.mark?.let { markText(it, locale) }?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    },
                )
            }
            AssistChip(
                onClick = onOther,
                leadingIcon = { Icon(Icons.Default.DateRange, null) },
                label = { Text(if (selected != null && !inChips) DayPick.dateText(selected, locale) else stringResource(R.string.day_other)) },
            )
        }
    }
}

@Composable
private fun dayName(d: DayPick.Day, night: Boolean, locale: Locale): String {
    val word = when (d.offset) {
        0 -> stringResource(R.string.day_today)
        1 -> stringResource(R.string.day_tomorrow)
        else -> return DayPick.chipText(d.date, locale)
    }
    return if (night) stringResource(R.string.day_with_date, word, DayPick.chipText(d.date, locale)) else word
}

/** The line under the day's name; none for Shabbat in Hebrew, where the name already is "שבת". */
@Composable
private fun markText(m: DayPick.Mark, locale: Locale): String? = when (m) {
    DayPick.Mark.Shabbat -> if (locale.language == "iw" || locale.language == "he") null else stringResource(R.string.day_mark_shabbat)
    DayPick.Mark.Friday -> stringResource(R.string.day_mark_friday)
    is DayPick.Mark.Holiday -> when (m.day.kind) {
        DayKind.EVE, DayKind.YOM_KIPPUR_EVE -> stringResource(R.string.day_mark_eve, stringResource(holidayName(m.day.holiday)))
        DayKind.NO_SERVICE, DayKind.REDUCED -> stringResource(holidayName(m.day.holiday))
    }
}

/**
 * Other date…: Material's picker, from today to [DayPick.MAX_DAYS] ahead. Past the timetable's reach
 * the D3 notice speaks. Our own dialog, up to 16 dp from the screen's sides ([dateDialogMargin]).
 */
@Composable
internal fun DateStepDialog(now: Instant, selected: LocalDate?, onDate: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberDayPickerState(now, selected)
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(horizontal = dateDialogMargin(LocalConfiguration.current.screenWidthDp)),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
            DateStepContent(picker, onDate, onDismiss)
        }
    }
}

/**
 * The date dialog's side margin: 16 dp where the screen has room, less down to none on a 360 dp
 * phone. The picker's 7-column grid needs its 360 dp there: at font ×1.3 a 328 dp dialog cut the
 * Saturday column.
 */
internal fun dateDialogMargin(screenWidthDp: Int): Dp = ((screenWidthDp - 360) / 2).coerceIn(0, 16).dp

/** The picker, then Cancel and OK at the end. */
@Composable
internal fun DateStepContent(picker: DatePickerState, onDate: (LocalDate) -> Unit, onCancel: () -> Unit) {
    Column {
        DatePicker(picker, showModeToggle = false)
        Row(Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            TextButton(
                enabled = picker.selectedDateMillis != null,
                onClick = { picker.selectedDateMillis?.let { onDate(DayPick.fromPicker(it)) } },
            ) { Text(stringResource(R.string.time_step_ok)) }
        }
    }
}

@Composable
internal fun rememberDayPickerState(now: Instant, selected: LocalDate?): DatePickerState {
    val today = DayPick.today(now)
    val last = DayPick.lastDay(now)
    val start = selected?.takeIf { DayPick.selectable(it, now) } ?: today
    return rememberDatePickerState(
        initialSelectedDateMillis = DayPick.toPicker(start),
        initialDisplayedMonthMillis = DayPick.toPicker(start.withDayOfMonth(1)),
        yearRange = today.year..last.year,
        selectableDates = remember(now) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = DayPick.selectable(DayPick.fromPicker(utcTimeMillis), now)
                override fun isSelectableYear(year: Int) = year in today.year..last.year
            }
        },
    )
}

/** The clock for [date]: a dial, or two fields to type in. The title names the day. */
@Composable
internal fun ClockStepDialog(date: LocalDate, now: Instant, initial: LocalTime, onTime: (LocalTime) -> Unit, onBack: () -> Unit) {
    val clock = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    var typing by rememberSaveable { mutableStateOf(false) }
    BasicAlertDialog(onDismissRequest = onBack, modifier = Modifier.testTag(DayTags.CLOCK)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
            ClockStepContent(date, now, clock, typing, { typing = !typing }, { onTime(LocalTime.of(clock.hour, clock.minute)) }, onBack)
        }
    }
}

@Composable
internal fun ClockStepContent(
    date: LocalDate,
    now: Instant,
    clock: TimePickerState,
    typing: Boolean,
    onToggle: () -> Unit,
    onOk: () -> Unit,
    onBack: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val today = DayPick.today(now)
    val title = when (date) {
        today -> stringResource(R.string.day_today)
        today.plusDays(1) -> if (DayPick.night(now)) DayPick.dateText(date, locale) else stringResource(R.string.day_tomorrow)
        else -> DayPick.dateText(date, locale)
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 20.dp, start = 12.dp, end = 12.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp, bottom = 16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (typing) TimeInput(clock) else TimePicker(clock)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggle, modifier = Modifier.testTag(DayTags.CLOCK_TYPE)) {
                Icon(
                    painterResource(if (typing) R.drawable.ic_schedule else R.drawable.ic_keyboard),
                    stringResource(if (typing) R.string.time_step_dial else R.string.time_step_type),
                )
            }
            Box(Modifier.weight(1f))
            TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = onOk, modifier = Modifier.testTag(DayTags.CLOCK_OK)) { Text(stringResource(R.string.time_step_ok)) }
        }
    }
}

/** "Tomorrow at 06:10?": a past time today is never moved without asking. */
@Composable
internal fun PastAskDialog(at: Instant, now: Instant, onYes: () -> Unit, onChange: () -> Unit) {
    AlertDialog(
        onDismissRequest = onChange,
        modifier = Modifier.testTag(DayTags.PAST),
        title = { Text(stringResource(R.string.past_title)) },
        text = { Text(pastAskText(at, now)) },
        confirmButton = { TextButton(onClick = onYes) { Text(stringResource(R.string.past_yes)) } },
        dismissButton = { TextButton(onClick = onChange) { Text(stringResource(R.string.past_change)) } },
    )
}

/** "Tomorrow at 06:10?", or between midnight and 04:00 the date, as the chip will say it ([DayPick.label]). */
@Composable
internal fun pastAskText(at: Instant, now: Instant): String {
    val l = DayPick.label(DayPick.nextDay(at), now)
    return if (l.word == DayPick.Word.TOMORROW) {
        stringResource(R.string.past_ask, l.time)
    } else {
        stringResource(R.string.past_ask_on, DayPick.dateText(l.date, LocalConfiguration.current.locales[0]), l.time)
    }
}

/** The time chip's text: "Depart 08:15" today, "Depart tomorrow 08:15", "Arrive by Fri 23 Oct 17:30". */
@Composable
internal fun timeChipText(mode: TimeMode, at: Instant, now: Instant): String {
    val l = DayPick.label(at, now)
    val arrive = mode == TimeMode.ARRIVE_BY
    return when (l.word) {
        DayPick.Word.TODAY -> "${stringResource(if (arrive) R.string.arrive_by else R.string.depart_at)} ${l.time}"
        DayPick.Word.TOMORROW -> stringResource(if (arrive) R.string.arrive_tomorrow else R.string.depart_tomorrow, l.time)
        DayPick.Word.DATE -> stringResource(
            if (arrive) R.string.arrive_on else R.string.depart_on,
            DayPick.dateText(l.date, LocalConfiguration.current.locales[0]),
            l.time,
        )
    }
}
