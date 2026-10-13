package il.transit.core

import il.transit.core.fare.FareProfile
import il.transit.core.features.ISRAEL
import il.transit.core.history.Report
import il.transit.core.history.ReportLabels
import il.transit.core.history.TripRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZonedDateTime

/** Phase 10 D8: the monthly travel report's CSV and message. */
class ReportTest {
    private val september = YearMonth.of(2026, 9)
    private val labels = ReportLabels(
        header = listOf("Date", "Start", "End", "From", "To", "Lines", "Minutes", "Fare estimate (₪)", "Profile"),
        myLocation = "My location",
        profile = "Student",
        totalTrips = "Total trips",
        totalEstimate = "Total estimate (₪)",
        note = "Estimate from trips started in the app; not a receipt",
        summary = "%1\$s: %2\$d trips, about ₪%3\$s (%4\$s).",
        monthName = "September 2026",
    )

    private fun trip(local: String, from: String, to: String, minutes: Int, agorot: Int?, lines: String? = "480") = TripRecord(
        startedAtEpoch = ZonedDateTime.of(LocalDateTime.parse(local), ISRAEL).toEpochSecond(),
        from = from, to = to, mode = "TRIP", transitMin = minutes - 5, walkMin = 5, transfers = 0,
        totalMin = minutes, fareAgorot = agorot, lines = lines,
    )

    private fun lines(csv: String) = csv.removePrefix(Report.BOM).split("\r\n")

    @Test fun `file name, BOM and CRLF`() {
        assertEquals("transit-2026-09.csv", Report.fileName(september))
        val csv = Report.csv(listOf(trip("2026-09-10T08:00", "Home", "Work", 30, 800)), september, FareProfile.REGULAR, labels)
        assertTrue(csv.startsWith("﻿Date,Start,End,From,To,Lines,Minutes,Fare estimate (₪),Profile\r\n"))
        assertEquals("2026-09-10,08:00,08:30,Home,Work,480,30,8.00,Student", lines(csv)[1])
    }

    @Test fun `a month with no trips still has its header, totals and note`() {
        val csv = Report.csv(listOf(trip("2026-10-01T08:00", "a", "b", 20, 800)), september, FareProfile.REGULAR, labels)
        assertEquals(
            listOf(
                "Date,Start,End,From,To,Lines,Minutes,Fare estimate (₪),Profile",
                "",
                "Total trips,0",
                "Total estimate (₪),0.00",
                "Estimate from trips started in the app; not a receipt",
                "",
            ),
            lines(csv),
        )
        assertEquals(
            "September 2026: 0 trips, about ₪0.00 (Student).\nEstimate from trips started in the app; not a receipt",
            Report.text(emptyList(), september, FareProfile.REGULAR, labels),
        )
    }

    @Test fun `a trip across midnight at the month's end belongs to the month it started in`() {
        val late = trip("2026-09-30T23:40", "Tel Aviv", "Be'er Sheva", 45, 1900)
        val early = trip("2026-10-01T00:30", "Be'er Sheva", "Home", 20, 800)
        val csv = lines(Report.csv(listOf(early, late), september, FareProfile.REGULAR, labels))
        assertEquals("2026-09-30,23:40,00:25,Tel Aviv,Be'er Sheva,480,45,19.00,Student", csv[1])
        assertEquals("Total trips,1", csv[3])
        assertEquals(listOf(YearMonth.of(2026, 10), september), Report.finishedMonths(listOf(late, early), Instant.parse("2026-11-02T10:00:00Z")))
        // 1 October 00:30 Israel time is still September in UTC: the month is Israel's.
        assertEquals(listOf(september), Report.finishedMonths(listOf(late, early), Instant.parse("2026-10-15T10:00:00Z")))
    }

    @Test fun `Hebrew names with commas and quotes are escaped`() {
        val r = trip("2026-09-03T09:15", "רחוב הרצל 5, רעננה", "קניון \"עזריאלי\"", 40, 1450, lines = "R1 · 1")
        val row = lines(Report.csv(listOf(r), september, FareProfile.REGULAR, labels))[1]
        assertEquals("2026-09-03,09:15,09:55,\"רחוב הרצל 5, רעננה\",\"קניון \"\"עזריאלי\"\"\",R1 · 1,40,14.50,Student", row)
        assertEquals("'=HYPERLINK(1)", Report.escape("=HYPERLINK(1)"))
        assertEquals("\"a\nb\"", Report.escape("a\nb"))
    }

    @Test fun `totals add up the column after the profile's single share`() {
        val month = listOf(
            trip("2026-09-01T08:00", "", "Work", 30, 800),
            trip("2026-09-01T17:00", "Work", "", 30, 1450),
            trip("2026-09-02T08:00", "Home", "Work", 30, null, lines = null), // recorded before fares
        )
        val csv = lines(Report.csv(month, september, FareProfile.STUDENT, labels))
        assertEquals("2026-09-01,08:00,08:30,My location,Work,480,30,5.36,Student", csv[1])
        assertEquals("2026-09-01,17:00,17:30,Work,My location,480,30,9.72,Student", csv[2])
        assertEquals("2026-09-02,08:00,08:30,Home,Work,,30,,Student", csv[3])
        assertEquals("Total trips,3", csv[5])
        assertEquals("Total estimate (₪),15.08", csv[6])
        assertEquals(
            "September 2026: 3 trips, about ₪15.08 (Student).\nEstimate from trips started in the app; not a receipt",
            Report.text(month, september, FareProfile.STUDENT, labels),
        )
        assertEquals("0.00", Report.shekels(Report.totalAgorot(month, FareProfile.SOLDIER)))
    }
}
