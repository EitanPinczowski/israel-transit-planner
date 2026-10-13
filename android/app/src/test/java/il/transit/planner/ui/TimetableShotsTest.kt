package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.LayoutDirection
import com.android.resources.NightMode
import il.transit.core.api.MotisJson
import il.transit.core.api.StopTimesResponse
import il.transit.core.features.ISRAEL
import il.transit.core.present.DayTimetable
import il.transit.core.present.DepartureRow
import il.transit.core.present.LegKind
import il.transit.core.present.SavedBoard
import il.transit.core.present.TimetablePage
import il.transit.core.present.departureRow
import il.transit.core.present.hhmm
import il.transit.planner.ui.screens.StopPanel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Day timetable (Phase 10 D7) inside the stop sheet: a hub line with 150 departures (More
 * still to come), a quiet line's whole day from a real Omer recording, the same offline from
 * last night's board, and the departures board offline. English and Hebrew, light and dark,
 * Hebrew on the smallest phone and both at 1.5× text there.
 */
@RunWith(Parameterized::class)
class TimetableShotsTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    /** Line 1 every 7 minutes from 05:00, at 12:00: the grid opens at 11, 12:04 marked, More below. */
    @Test fun hub() {
        val rows = (0 until 150).map { i -> row("1", "תחנה מרכזית", il("2026-10-14T05:00").plusSeconds(i * 7L * 60)) }
        val page = TimetablePage(central, "1", "תחנה מרכזית", wednesday, rows, nextCursor = "LATER|1", requests = 2)
        shot("timetable_hub", sheet(central, timetable("1", "תחנה מרכזית", "#1E88E5", il("2026-10-14T12:00"), rows, page)))
    }

    /** Line 28 at a quiet Omer stop on Wednesday 14 Oct, the whole day in one page, at 08:30. */
    @Test fun quiet() {
        val rows = DayTimetable.departures(omerRows, "28", omerHeadsign, wednesday)
        val page = TimetablePage(omer, "28", omerHeadsign, wednesday, rows, nextCursor = null, requests = 1)
        shot("timetable_quiet", sheet(omer, timetable("28", omerHeadsign, "#F57C00", il("2026-10-14T08:30"), rows, page)))
    }

    /** No signal: the same line from the board saved at 02:14, gone departures hidden. */
    @Test fun offline() {
        val now = il("2026-10-14T08:30")
        val board = SavedBoard.of(omer, omerResp, il("2026-10-14T02:14"))
        val rows = DayTimetable.departures(board.upcoming(now), "28", omerHeadsign, wednesday)
        val t = timetable("28", omerHeadsign, "#F57C00", now, rows, null).copy(savedAt = board.savedAt, savedLastNight = true)
        shot("timetable_offline", sheet(omer, t))
    }

    /** No answer and no saved board: the note and Retry. */
    @Test fun failed() = shot(
        "timetable_failed",
        sheet(omer, timetable("28", omerHeadsign, "#F57C00", il("2026-10-14T08:30"), emptyList(), null).copy(failed = true)),
    )

    /** The line does not run that day (Saturday). */
    @Test fun none() {
        val page = TimetablePage(omer, "28", omerHeadsign, LocalDate.parse("2026-10-17"), emptyList(), nextCursor = null, requests = 1)
        shot("timetable_none", sheet(omer, timetable("28", omerHeadsign, "#F57C00", il("2026-10-17T10:00"), emptyList(), page).copy(tomorrow = true)))
    }

    /** The stop's departures with no signal: last night's board, still ahead only. */
    @Test fun stopOffline() {
        val board = SavedBoard.of(omer, omerResp, il("2026-10-14T02:14"))
        val rows = board.upcoming(il("2026-10-14T08:30")).take(8)
        shot("stop_offline", StopSheet(omer, omerName, false, rows, false, saved = SavedDepartures(board.savedAt, true, rows)))
    }

    private fun sheet(stopId: String, t: TimetableUi) = StopSheet(stopId, if (stopId == omer) omerName else "ת.מרכזית באר שבע", false, emptyList(), false, timetable = t)

    private fun timetable(line: String, headsign: String, color: String, now: Instant, rows: List<DepartureRow>, page: TimetablePage?) =
        TimetableUi(line, headsign, LegKind.BUS, color, now, loading = false, rows = rows, page = page)

    private fun shot(name: String, sheet: StopSheet) = paparazzi.snapshot(name) {
        Frame { StopPanel(sheet, emptyList(), NoActions, false, {}, Modifier, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) }
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val direction = if (v.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            AppTheme(dark = v.dark) {
                Box(Modifier.fillMaxSize().background(if (v.dark) Color(0xFF2B2E33) else Color(0xFFEDEAE4)), contentAlignment = Alignment.BottomCenter) {
                    content()
                }
            }
        }
    }

    companion object {
        private fun fixture(name: String) = TimetableShotsTest::class.java.getResource("/fixtures/$name.json")!!.readText()
        private fun il(local: String): Instant = LocalDateTime.parse(local).atZone(ISRAEL).toInstant()
        private fun row(line: String, headsign: String, at: Instant) = DepartureRow(line, headsign, LegKind.BUS, hhmm(at), null, false, at)

        private const val central = "il-Israel-MOT_48114"
        private const val omer = "il-Israel-MOT_26760"
        private val wednesday = LocalDate.parse("2026-10-14")

        private val omerResp = MotisJson.decodeFromString(StopTimesResponse.serializer(), fixture("stoptimes_day_omer_quiet"))
        private val omerRows = omerResp.stopTimes.map(::departureRow)
        private val omerHeadsign = omerRows.first { it.line == "28" }.headsign
        private val omerName = omerResp.place?.name ?: "דרך מיתר/שדרות עומרים"

        private val phone = DeviceConfig.PIXEL_5
        private val small = DeviceConfig.NEXUS_5 // 360 x 640 dp

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants() = listOf(
            Variant("en_light", phone, dark = false, rtl = false),
            Variant("en_dark", phone.copy(nightMode = NightMode.NIGHT), dark = true, rtl = false),
            Variant("he_light", phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL), dark = false, rtl = true),
            Variant("he_dark", phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, nightMode = NightMode.NIGHT), dark = true, rtl = true),
            Variant("he_small", small.copy(locale = "iw", layoutDirection = LayoutDirection.RTL), dark = false, rtl = true),
            Variant("he_large", small.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, fontScale = 1.5f), dark = false, rtl = true),
            Variant("en_large", small.copy(fontScale = 1.5f), dark = false, rtl = false),
        )
    }
}
