package il.transit.core

import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.MotisClient
import il.transit.core.api.MotisJson
import il.transit.core.api.StopTimesResponse
import il.transit.core.features.ISRAEL
import il.transit.core.present.DayTimetable
import il.transit.core.present.DepartureRow
import il.transit.core.present.LegKind
import il.transit.core.present.SavedBoard
import il.transit.core.present.SavedBoardsJson
import il.transit.core.present.TimetableLoader
import il.transit.core.present.departureRow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Day timetable for a line at a stop (Phase 10 D7). Recorded 2026-10-12 from Transitous:
 * Be'er Sheva central's intercity platforms on Wed 14 Oct from 04:00 (two pages of 60), a quiet
 * stop in Omer the same day, and the same stop on Friday 16 Oct from 12:00.
 */
class DayTimetableTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name.json")!!.readText()
    private fun response(name: String) = MotisJson.decodeFromString(StopTimesResponse.serializer(), fixture(name))
    private fun il(local: String): Instant = LocalDateTime.parse(local).atZone(ISRAEL).toInstant()

    private val central = "il-Israel-MOT_48114"
    private val omer = "il-Israel-MOT_26760"
    private val wednesday = LocalDate.parse("2026-10-14")
    private val friday = LocalDate.parse("2026-10-16")

    private val p1 = response("stoptimes_day_beersheva_central_p1")
    private val p2 = response("stoptimes_day_beersheva_central_p2")

    /** Answers like Transitous: the first page without a cursor, the second with p1's next cursor. */
    private fun centralApi(third: StopTimesResponse = p2.copy(nextPageCursor = "LATER|3")) = FakeTransitApi().apply {
        onStopTimes = { c ->
            when (c.pageCursor) {
                null -> p1
                p1.nextPageCursor -> p2
                p2.nextPageCursor -> third
                else -> error("unexpected cursor ${c.pageCursor}")
            }
        }
    }

    private fun row(line: String, at: Instant, headsign: String = "Centre") =
        DepartureRow(line, headsign, LegKind.BUS, "", null, false, at)

    @Test fun `the paging fields parse, and an answer without them still decodes`() {
        assertEquals("LATER|1791949560", p1.nextPageCursor)
        assertEquals("EARLIER|1791942240", p1.previousPageCursor)
        val old = MotisJson.decodeFromString(StopTimesResponse.serializer(), """{"stopTimes":[]}""")
        assertNull(old.nextPageCursor)
        assertNull(old.previousPageCursor)
    }

    @Test fun `the page cursor is sent and is part of the guard's cache key`() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            repeat(2) { server.enqueue(MockResponse().setBody(fixture("stoptimes_day_beersheva_central_p1"))) }
            val api = GuardedTransitApi(MotisClient(server.url("/").toString()))
            val at = DayTimetable.dayStart(wednesday)
            api.stopTimes(central, at, 60, "he")
            api.stopTimes(central, at, 60, "he", "LATER|1791949560")
            api.stopTimes(central, at, 60, "he", "LATER|1791949560") // cached: no third request
            assertEquals(2, server.requestCount)
            assertNull(server.takeRequest().requestUrl!!.queryParameter("pageCursor"))
            val second = server.takeRequest().requestUrl!!
            assertEquals("LATER|1791949560", second.queryParameter("pageCursor"))
            assertEquals("2026-10-14T01:00:00Z", second.queryParameter("time"))
            assertEquals("60", second.queryParameter("n"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun `opening a busy stop sends two pages from the day's 04-00, then More sends one`() = runBlocking {
        val api = centralApi()
        val loader = TimetableLoader(api)
        val page = loader.open(central, "470", "ירושלים_תחנה מרכזית", wednesday, "he")
        assertEquals(2, api.stopTimesRequests.size)
        assertEquals(2, page.requests)
        api.stopTimesRequests.forEach {
            assertEquals(il("2026-10-14T04:00"), it.time)
            assertEquals(DayTimetable.PAGE_N, it.n)
        }
        assertFalse("the day goes on past 07:50", page.complete)
        assertEquals(p2.nextPageCursor, page.nextCursor)
        // Only line 470 to Jerusalem, in order, from both pages.
        assertTrue(page.rows.all { it.line == "470" && it.headsign == "ירושלים_תחנה מרכזית" })
        assertEquals(page.rows.sortedBy { it.instant }, page.rows)
        val inP1 = p1.stopTimes.map(::departureRow).count { it.line == "470" && it.headsign == "ירושלים_תחנה מרכזית" }
        assertTrue(page.rows.size > inP1)

        val more = loader.more(page, "he")
        assertEquals(3, api.stopTimesRequests.size)
        assertEquals(p2.nextPageCursor, api.stopTimesRequests.last().pageCursor)
        assertEquals("LATER|3", more.nextCursor)
        assertEquals(3, more.requests)
    }

    @Test fun `a quiet stop's whole day fits one page, and nothing more is asked`() = runBlocking {
        val api = FakeTransitApi().apply { onStopTimes = { response("stoptimes_day_omer_quiet") } }
        val page = TimetableLoader(api).open(omer, "28", headsignOf("stoptimes_day_omer_quiet", "28"), wednesday, "he")
        assertEquals(1, api.stopTimesRequests.size)
        assertTrue(page.complete)
        // Thursday's 05:10 (02:10Z) and later are in the page but not in Wednesday's timetable.
        assertTrue(page.rows.all { it.instant!! < il("2026-10-15T04:00") })
        assertEquals("05:10", page.rows.first().time)
        assertEquals(TimetableLoader(api).more(page, "he"), page)
        assertEquals(1, api.stopTimesRequests.size)
    }

    @Test fun `Friday ends early, the page reaches Saturday night, so the day is complete`() = runBlocking {
        val api = FakeTransitApi().apply { onStopTimes = { response("stoptimes_day_omer_friday") } }
        val page = TimetableLoader(api).open(omer, "28א", headsignOf("stoptimes_day_omer_friday", "28א"), friday, "he")
        assertEquals(1, api.stopTimesRequests.size)
        assertTrue(page.complete)
        assertEquals("15:43", page.rows.last().time)
        assertTrue(page.rows.none { it.instant!! >= il("2026-10-17T04:00") })
    }

    @Test fun `the 04-00 edge, 03-59 is yesterday's, 04-00 today's`() {
        val rows = listOf(row("5", il("2026-10-14T03:59")), row("5", il("2026-10-14T04:00")), row("5", il("2026-10-15T03:59")), row("5", il("2026-10-15T04:00")))
        val day = DayTimetable.departures(rows, "5", "Centre", wednesday)
        assertEquals(listOf(il("2026-10-14T04:00"), il("2026-10-15T03:59")), day.map { it.instant })
        assertEquals(LocalDate.parse("2026-10-13"), DayTimetable.today(il("2026-10-14T03:59")))
        assertEquals(wednesday, DayTimetable.today(il("2026-10-14T04:00")))
    }

    @Test fun `the line filter keeps one line in one direction`() {
        val at = il("2026-10-14T08:00")
        val rows = listOf(row("5", at), row("5", at.plusSeconds(600), "Station"), row("50", at.plusSeconds(60)), row("5א", at.plusSeconds(120)))
        assertEquals(listOf(rows[0]), DayTimetable.departures(rows, "5", "Centre", wednesday))
    }

    @Test fun `a Saturday-night trip listed twice shows once`() {
        val at = il("2026-10-17T23:20")
        val rows = listOf(row("470", at).copy(tripId = "a_161026"), row("470", at).copy(tripId = "a_171026"), row("470", at.plusSeconds(1800)))
        assertEquals(2, DayTimetable.departures(rows, "470", "Centre", LocalDate.parse("2026-10-17")).size)
    }

    @Test fun `the grid groups by hour, past midnight after 23, the next one marked`() {
        val rows = listOf("06:10", "06:40", "07:05", "07:25", "07:45", "23:50", "00:20").map { t ->
            val day = if (t.startsWith("00")) "2026-10-15" else "2026-10-14"
            row("5", il("${day}T$t"))
        }
        val grid = DayTimetable.grid(rows, il("2026-10-14T07:10"))
        assertEquals(listOf("06", "07", "23", "00"), grid.map { it.hour })
        assertEquals(listOf("05", "25", "45"), grid[1].cells.map { it.minute })
        assertEquals(listOf("07:25"), grid.flatMap { it.cells }.filter { it.next }.map { il.transit.core.present.hhmm(it.at) })
        assertTrue(grid[0].cells.all { it.gone })
        assertFalse(grid[2].cells.single().gone)
        // Nothing left today: nothing is marked.
        assertTrue(DayTimetable.grid(rows, il("2026-10-15T01:00")).flatMap { it.cells }.none { it.next })
    }

    @Test fun `a hub line with 150 departures makes a full grid`() {
        val rows = (0 until 150).map { i -> row("1", il("2026-10-14T05:00").plusSeconds(i * 7L * 60)) }
        val grid = DayTimetable.grid(DayTimetable.departures(rows, "1", "Centre", wednesday), il("2026-10-14T12:00"))
        assertEquals(150, grid.sumOf { it.cells.size })
        assertEquals(1, grid.flatMap { it.cells }.count { it.next })
    }

    @Test fun `a saved board shows only what is still ahead, and survives a round trip`() {
        val board = SavedBoard.of(omer, response("stoptimes_day_omer_quiet"), il("2026-10-14T02:14"))
        assertEquals(60, board.rows.size)
        val ahead = board.upcoming(il("2026-10-14T12:00"))
        assertTrue(ahead.all { it.instant!! >= il("2026-10-14T11:59") })
        assertTrue(ahead.none { it.delayMin != null || it.cancelled })
        assertTrue(board.isLastNight(il("2026-10-14T19:00")))
        assertFalse(board.isLastNight(il("2026-10-14T21:00")))
        val back = SavedBoardsJson.decode(SavedBoardsJson.encode(listOf(board)))
        assertEquals(listOf(board), back)
        assertEquals(emptyList<SavedBoard>(), SavedBoardsJson.decode("not json"))
        assertEquals(emptyList<SavedBoard>(), SavedBoardsJson.decode(null))
    }

    private fun headsignOf(fixture: String, line: String) =
        response(fixture).stopTimes.map(::departureRow).first { it.line == line }.headsign
}
