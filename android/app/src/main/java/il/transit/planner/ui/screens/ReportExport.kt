package il.transit.planner.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import il.transit.core.fare.FareProfile
import il.transit.core.history.Report
import il.transit.core.history.ReportLabels
import il.transit.core.history.TripRecord
import il.transit.planner.R
import java.io.File
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// The monthly travel report (Phase 10 D8): History → "Monthly report" → a finished month →
// the share sheet with transit-2026-09.csv and the summary as text. No requests; nothing
// leaves the phone until the user picks an app in the share sheet.

/** "Monthly report" and its month list, under the history stats. Hidden until a month has finished. */
@Composable
internal fun MonthlyReportButton(history: List<TripRecord>, profile: FareProfile, now: Instant) {
    val months = remember(history, now) { Report.finishedMonths(history, now) }
    if (months.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val labels = reportLabels(profile)
    Column {
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.report_button)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ReportMonthList(history, months, locale) { m, name ->
                open = false
                share(context, history, m, profile, labels(name))
            }
        }
    }
}

/** The menu's body on its own, so screenshot tests can draw it (Paparazzi draws no popups). */
@Composable
internal fun ReportMonthList(history: List<TripRecord>, months: List<YearMonth>, locale: Locale, onPick: (YearMonth, String) -> Unit) {
    Column {
        Text(
            stringResource(R.string.report_pick),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        months.take(MAX_MONTHS).forEach { m ->
            val name = monthName(m, locale)
            val count = remember(history, m) { Report.trips(history, m).size }
            DropdownMenuItem(
                text = { Text(pluralStringResource(R.plurals.report_month_trips, count, name, count)) },
                onClick = { onPick(m, name) },
            )
        }
    }
}

/** A year of months is plenty for an expense claim, and keeps the menu short. */
private const val MAX_MONTHS = 12

private fun monthName(m: YearMonth, locale: Locale): String = DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(m)

/** The labels with everything but the month name filled in. */
@Composable
private fun reportLabels(profile: FareProfile): (String) -> ReportLabels {
    val header = listOf(
        R.string.report_col_date, R.string.report_col_start, R.string.report_col_end, R.string.report_col_from,
        R.string.report_col_to, R.string.report_col_lines, R.string.report_col_minutes, R.string.report_col_fare,
        R.string.report_col_profile,
    ).map { stringResource(it) }
    val myLocation = stringResource(R.string.my_location)
    val profileName = stringResource(fareProfileLabel(profile))
    val totalTrips = stringResource(R.string.report_total_trips)
    val totalEstimate = stringResource(R.string.report_total_estimate)
    val note = stringResource(R.string.report_note)
    val profileNote = stringResource(R.string.report_profile_note)
    val summary = stringResource(R.string.report_summary)
    return { month -> ReportLabels(header, myLocation, profileName, totalTrips, totalEstimate, note, profileNote, summary, month) }
}

private fun share(context: Context, history: List<TripRecord>, month: YearMonth, profile: FareProfile, labels: ReportLabels) {
    val file = try {
        ReportFiles.write(context.cacheDir, Report.fileName(month), Report.csv(history, month, profile, labels))
    } catch (e: java.io.IOException) {
        Toast.makeText(context, R.string.report_failed, Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.report_subject, labels.monthName))
        putExtra(Intent.EXTRA_TEXT, Report.text(history, month, profile, labels))
        // The ClipData carries the read grant to the app the user picks in the chooser.
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.report_share)))
}

/**
 * The report files in `cacheDir/reports/`, shared through the app's FileProvider
 * (`res/xml/update_paths.xml`). A file lives a day: long enough for the app the user shared
 * it with to read it, then [prune] deletes it.
 */
internal object ReportFiles {
    const val DIR = "reports"
    private const val KEEP_MS = 24 * 60 * 60 * 1000L

    fun write(cacheDir: File, name: String, csv: String): File {
        val dir = File(cacheDir, DIR).apply { mkdirs() }
        prune(cacheDir)
        return File(dir, name).apply { writeText(csv, Charsets.UTF_8) }
    }

    /** Deletes reports older than a day; called when the app starts and before each new one. */
    fun prune(cacheDir: File, nowMs: Long = System.currentTimeMillis()) {
        File(cacheDir, DIR).listFiles()?.forEach { if (nowMs - it.lastModified() > KEEP_MS) it.delete() }
    }
}
