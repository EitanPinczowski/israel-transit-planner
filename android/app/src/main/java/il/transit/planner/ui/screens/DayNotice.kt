package il.transit.planner.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.transit.core.calendar.DayKind
import il.transit.core.calendar.Holiday
import il.transit.core.calendar.HolidayDay
import il.transit.core.features.ISRAEL
import il.transit.core.present.DayNotice
import il.transit.core.present.DayNotice.Notice
import il.transit.core.plan.TimeMode
import il.transit.core.present.hhmm
import il.transit.planner.R
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.MainActions
import il.transit.planner.ui.UiError
import il.transit.planner.ui.UiState
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Holidays + dates on the Trip tab (Phase 10 D3), above the options: a holiday banner for the
 * day searched, and with 0 options "No service that day" / "The timetable may not reach <day>
 * yet" with one "Plan for <day>" button (1 `plan`, on tap). Built from the bundled calendar
 * only ([DayNotice]); nothing here sends a request. The usual "no options" row stays below.
 */
@Composable
internal fun DayNoticeBanner(state: UiState, vm: MainActions) {
    if (state.mode != AppMode.TRIP || state.loading) return
    val empty = state.error == UiError.NO_RESULTS
    if (state.results == null && !empty) return
    val now = state.resultsAt ?: Instant.now()
    val searched = state.time ?: now
    val banner = remember(searched) { DayNotice.banner(searched) }
    val notice = remember(searched, now, empty) { if (empty) DayNotice.forEmpty(searched, now) else null }
    if (banner == null && notice == null) return
    val locale = LocalConfiguration.current.locales[0]
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
    ) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = if (notice == null) 8.dp else 0.dp)) {
            banner?.let { Text(bannerText(it, locale), style = MaterialTheme.typography.bodySmall) }
            notice?.let { n ->
                Text(noticeText(n, locale), style = MaterialTheme.typography.bodyMedium)
                val (at, label) = when (n) {
                    is Notice.NoService -> n.resume to dayTime(n.resume, locale)
                    is Notice.BeyondHorizon -> n.instead to "${dayDate(n.instead.atZone(ISRAEL).toLocalDate(), locale)} ${hhmm(n.instead)}"
                }
                // Service resumes at a departure time; a week earlier keeps an arrive-by search arrive-by.
                val arriveBy = n is Notice.BeyondHorizon && state.timeMode == TimeMode.ARRIVE_BY
                // No start padding, so the label lines up with the text above it (LTR and RTL).
                TextButton(onClick = { vm.planOn(at, arriveBy) }, contentPadding = PaddingValues(start = 0.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
                    Text(stringResource(R.string.day_plan_for, label))
                }
            }
        }
    }
}

/** "Holiday table out of date: update the app", in Settings from the table's last year on. */
@Composable
internal fun HolidayTableOutOfDate() {
    Text(
        stringResource(R.string.holiday_table_old),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun bannerText(d: HolidayDay, locale: Locale): String {
    val name = stringResource(holidayName(d.holiday))
    return when (d.kind) {
        DayKind.NO_SERVICE -> stringResource(R.string.day_no_service, dayDate(d.date, locale), name)
        DayKind.EVE -> stringResource(R.string.day_eve, name)
        DayKind.YOM_KIPPUR_EVE -> stringResource(R.string.day_yom_kippur_eve)
        DayKind.REDUCED -> stringResource(R.string.day_reduced, name)
    }
}

@Composable
private fun noticeText(n: Notice, locale: Locale): String = when (n) {
    is Notice.NoService -> stringResource(
        when {
            n.holiday == null && n.evening -> R.string.day_none_shabbat_evening
            n.holiday == null -> R.string.day_none_shabbat
            n.evening -> R.string.day_none_evening
            else -> R.string.day_none
        },
    )
    is Notice.BeyondHorizon -> stringResource(R.string.day_beyond, dayDate(n.date, locale))
}

/** The holiday's name. Yom HaShoah and Yom HaZikaron change no service, so they never reach a banner or an alert. */
@StringRes
internal fun holidayName(h: Holiday): Int = when (h) {
    Holiday.ROSH_HASHANA_1, Holiday.ROSH_HASHANA_2 -> R.string.holiday_rosh_hashana
    Holiday.YOM_KIPPUR -> R.string.holiday_yom_kippur
    Holiday.SUKKOT -> R.string.holiday_sukkot
    Holiday.CHOL_HAMOED_SUKKOT -> R.string.holiday_chol_hamoed_sukkot
    Holiday.SHMINI_ATZERET -> R.string.holiday_shmini_atzeret
    Holiday.PESACH_1 -> R.string.holiday_pesach
    Holiday.PESACH_7 -> R.string.holiday_pesach_7
    Holiday.CHOL_HAMOED_PESACH -> R.string.holiday_chol_hamoed_pesach
    Holiday.SHAVUOT -> R.string.holiday_shavuot
    Holiday.YOM_HAATZMAUT, Holiday.YOM_HAZIKARON, Holiday.YOM_HASHOAH -> R.string.holiday_yom_haatzmaut
}

/** "Thu 22 Apr" / "יום ה׳ 22 באפר׳". */
private fun dayDate(d: LocalDate, locale: Locale): String = d.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))

/** "Sat 20:00": the end of Shabbat or a holiday is always within the week. */
private fun dayTime(t: Instant, locale: Locale): String =
    "${t.atZone(ISRAEL).format(DateTimeFormatter.ofPattern("EEE", locale))} ${hhmm(t)}"
