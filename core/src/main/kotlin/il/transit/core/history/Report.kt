package il.transit.core.history

import il.transit.core.fare.FareProfile
import il.transit.core.features.ISRAEL
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The words of the report in the user's language; the app fills them from string resources. */
data class ReportLabels(
    /** The 9 column titles: date, start, end, from, to, lines, minutes, fare estimate (₪), profile. */
    val header: List<String>,
    /** A trip end with no name (the user's location at the time). */
    val myLocation: String,
    /** The fare profile's name, written on every row. */
    val profile: String,
    val totalTrips: String,
    val totalEstimate: String,
    /** "Estimate from trips started in the app; not a receipt." */
    val note: String,
    /** "Prices use your current fare profile, so changing it mid-month recomputes the whole month." */
    val profileNote: String,
    /** The message body: %1$s month name and year, %2$d trips, %3$s shekels, %4$s profile. */
    val summary: String,
    /** "September 2026". */
    val monthName: String,
)

/**
 * The monthly travel report (Phase 10 D8): a finished month of history as a CSV file for an
 * expense claim, plus the same summary as text. Pure; the app writes the file and opens the
 * share sheet. Nothing leaves the phone without that tap.
 *
 * A trip belongs to the calendar month (Israel time) it started in, so a ride from 23:40 on
 * 30 September to 00:20 belongs to September. The fare is each trip's full estimate after the
 * profile's single-ride discount, and the total is the sum of the column: daily and monthly
 * caps are left out so the column adds up.
 */
object Report {
    /** Excel reads a UTF-8 file as the local code page unless it starts with this. */
    const val BOM = "﻿"
    private const val EOL = "\r\n"
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun fileName(month: YearMonth): String = String.format(Locale.US, "transit-%04d-%02d.csv", month.year, month.monthValue)

    fun monthOf(t: Instant): YearMonth = YearMonth.from(t.atZone(ISRAEL))

    /** Months before the current one with at least one trip, newest first. */
    fun finishedMonths(records: List<TripRecord>, now: Instant): List<YearMonth> {
        val current = monthOf(now)
        return records.map { monthOf(it.startedAt) }.filter { it < current }.distinct().sortedDescending()
    }

    fun trips(records: List<TripRecord>, month: YearMonth): List<TripRecord> =
        records.filter { monthOf(it.startedAt) == month }.sortedBy { it.startedAtEpoch }

    /** One trip's estimate after the profile's single-ride share; null when history has no fare. */
    fun fareAgorot(r: TripRecord, profile: FareProfile): Int? = r.fareAgorot?.let { FareProfile.share(it, profile.singlePaid) }

    fun totalAgorot(trips: List<TripRecord>, profile: FareProfile): Int = trips.sumOf { fareAgorot(it, profile) ?: 0 }

    fun csv(records: List<TripRecord>, month: YearMonth, profile: FareProfile, labels: ReportLabels): String {
        val trips = trips(records, month)
        val sb = StringBuilder(BOM)
        fun row(vararg cells: String) {
            cells.joinTo(sb, ",") { escape(it) }
            sb.append(EOL)
        }
        row(*labels.header.toTypedArray())
        for (r in trips) {
            val start = r.startedAt.atZone(ISRAEL)
            val minutes = r.totalMin ?: (r.transitMin + r.walkMin)
            row(
                DATE.format(start),
                TIME.format(start),
                TIME.format(start.plusMinutes(minutes.toLong())),
                r.from.ifBlank { labels.myLocation },
                r.to.ifBlank { labels.myLocation },
                r.lines.orEmpty(),
                minutes.toString(),
                fareAgorot(r, profile)?.let(::shekels).orEmpty(),
                labels.profile,
            )
        }
        row()
        row(labels.totalTrips, trips.size.toString())
        row(labels.totalEstimate, shekels(totalAgorot(trips, profile)))
        row(labels.note)
        return sb.toString()
    }

    /** The share message: "September 2026: 23 trips, about ₪184 (Student).", the profile note and the note. */
    fun text(records: List<TripRecord>, month: YearMonth, profile: FareProfile, labels: ReportLabels): String {
        val trips = trips(records, month)
        val total = shekels(totalAgorot(trips, profile))
        return String.format(Locale.US, labels.summary, labels.monthName, trips.size, total, labels.profile) + "\n" + labels.profileNote + "\n" + labels.note
    }

    /** "5.36": two decimals and a dot, which Sheets and Excel read as a number in any locale. */
    fun shekels(agorot: Int): String = String.format(Locale.US, "%d.%02d", agorot / 100, agorot % 100)

    /**
     * RFC 4180: a cell with a comma, quote or line break goes in quotes, its quotes doubled. A
     * cell that starts like a formula (=, +, -, @) gets a leading apostrophe so a spreadsheet
     * shows it as text instead of running it.
     */
    fun escape(cell: String): String {
        val safe = if (cell.isNotEmpty() && cell[0] in "=+-@" && cell.toDoubleOrNull() == null) "'$cell" else cell
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
