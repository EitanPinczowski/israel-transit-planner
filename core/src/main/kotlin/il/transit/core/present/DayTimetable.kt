package il.transit.core.present

import il.transit.core.api.BudgetExceededException
import il.transit.core.api.BudgetedTransitApi
import il.transit.core.api.StopTime
import il.transit.core.api.StopTimesResponse
import il.transit.core.api.TransitApi
import il.transit.core.api.parseTime
import il.transit.core.features.ISRAEL
import il.transit.core.plan.LastRideFinder
import java.time.Instant
import java.time.LocalDate

/**
 * A line's day timetable at one stop (Phase 10 D7): every departure of that line in that
 * direction in one service day (04:00 to 04:00, [LastRideFinder.DAY_STARTS]), grouped by hour.
 * Times are scheduled: MOT lines carry no real-time on Transitous.
 */
object DayTimetable {
    /** Departures per `stoptimes` page. */
    const val PAGE_N = 60

    /** Pages fetched when the timetable opens; then "More", one request per tap. */
    const val PAGES_PER_OPEN = 2

    fun dayStart(day: LocalDate): Instant = day.atTime(LastRideFinder.DAY_STARTS).atZone(ISRAEL).toInstant()

    fun dayEnd(day: LocalDate): Instant = dayStart(day.plusDays(1))

    /** The service day [now] is in: 03:30 still belongs to yesterday. */
    fun today(now: Instant): LocalDate = LastRideFinder.serviceDay(now)

    /**
     * [line] towards [headsign] in [day], earliest first. A Saturday-night trip listed once per
     * service day (same line, same minute, two trip ids) shows once.
     */
    fun departures(rows: List<DepartureRow>, line: String, headsign: String, day: LocalDate): List<DepartureRow> {
        val start = dayStart(day)
        val end = dayEnd(day)
        return rows.asSequence()
            .filter { it.line == line && it.headsign == headsign }
            .filter { r -> r.instant != null && r.instant >= start && r.instant < end }
            .sortedBy { it.instant }
            .distinctBy { it.line to it.instant!!.epochSecond / 60 }
            .toList()
    }

    /** One minute in the grid: "05", when, and whether it is the next one or already gone. */
    data class Cell(val minute: String, val at: Instant, val next: Boolean, val gone: Boolean)

    /** One row of the grid: "07 │ 05 25 45". */
    data class Hour(val hour: String, val cells: List<Cell>)

    /** [rows] by hour of the day, in service-day order (…22, 23, 00, 01); the first one at or after [now] is `next`. */
    fun grid(rows: List<DepartureRow>, now: Instant): List<Hour> {
        val timed = rows.filter { it.instant != null }.sortedBy { it.instant }
        val next = timed.firstOrNull { it.instant!! >= now }
        val byHour = LinkedHashMap<Int, MutableList<Cell>>()
        for (r in timed) {
            val at = r.instant!!
            val local = at.atZone(ISRAEL)
            byHour.getOrPut(local.hour) { ArrayList() } += Cell("%02d".format(local.minute), at, r === next, at < now)
        }
        return byHour.map { (h, cells) -> Hour("%02d".format(h), cells) }
    }
}

/** What the day timetable shows: [rows] of the line so far, and where "More" goes on. */
data class TimetablePage(
    val stopId: String,
    val line: String,
    val headsign: String,
    val day: LocalDate,
    val rows: List<DepartureRow>,
    /** The next page to ask for on "More"; null once the day is complete. */
    val nextCursor: String?,
    /** Requests this page cost so far (open + More taps). */
    val requests: Int,
) {
    val complete: Boolean get() = nextCursor == null
}

/**
 * Loads a [TimetablePage]: at most [DayTimetable.PAGES_PER_OPEN] `stoptimes` pages of
 * [DayTimetable.PAGE_N] on open (under a [BudgetedTransitApi]), then one per [more]. Every page
 * starts the same query at the day's 04:00, so the guard caches each one. Paging stops when a page
 * reaches past the day's end (a Friday whose next departure is Saturday night ends there), comes
 * back empty, or has no next cursor.
 */
class TimetableLoader(private val api: TransitApi) {
    suspend fun open(stopId: String, line: String, headsign: String, day: LocalDate, language: String): TimetablePage {
        val budgeted = BudgetedTransitApi(api, DayTimetable.PAGES_PER_OPEN)
        var page = TimetablePage(stopId, line, headsign, day, emptyList(), nextCursor = null, requests = 0)
        var cursor: String? = null
        while (true) {
            page = try {
                fetch(budgeted, page, cursor, language)
            } catch (e: BudgetExceededException) {
                break
            }
            cursor = page.nextCursor ?: break
        }
        return page
    }

    /** One more page (one request); a complete page is returned as it is. */
    suspend fun more(page: TimetablePage, language: String): TimetablePage {
        val cursor = page.nextCursor ?: return page
        return fetch(api, page, cursor, language)
    }

    private suspend fun fetch(api: TransitApi, page: TimetablePage, cursor: String?, language: String): TimetablePage {
        val resp = api.stopTimes(page.stopId, DayTimetable.dayStart(page.day), DayTimetable.PAGE_N, language, cursor)
        val rows = DayTimetable.departures(page.rows + resp.stopTimes.map(::departureRow), page.line, page.headsign, page.day)
        val last = resp.stopTimes.mapNotNull(::departureAt).maxOrNull()
        val done = resp.stopTimes.isEmpty() || last == null || last >= DayTimetable.dayEnd(page.day)
        return page.copy(rows = rows, nextCursor = if (done) null else resp.nextPageCursor, requests = page.requests + 1)
    }

    private fun departureAt(st: StopTime): Instant? =
        (st.place.departure ?: st.place.arrival ?: st.place.scheduledDeparture)?.let { runCatching { parseTime(it) }.getOrNull() }
}

/**
 * A stop's board saved by the night refresh, for offline use: scheduled times only, never a
 * delay or a cancellation. [savedAt] is when it was saved ("Saved last night at 02:14").
 */
data class SavedBoard(val stopId: String, val savedAt: Instant, val rows: List<CachedDeparture>) {
    /** Departures not yet gone (a minute's grace), as rows. */
    fun upcoming(now: Instant): List<DepartureRow> = all().filter { it.instant!!.epochSecond >= now.epochSecond - 60 }

    fun all(): List<DepartureRow> = rows.map { c ->
        val at = Instant.ofEpochSecond(c.epochSec)
        DepartureRow(
            line = c.line,
            headsign = c.headsign,
            kind = runCatching { LegKind.valueOf(c.kind) }.getOrDefault(LegKind.OTHER),
            time = hhmm(at),
            delayMin = null,
            cancelled = false,
            instant = at,
        )
    }

    /** Saved within the last 18 hours: "last night" still reads true the next evening. */
    fun isLastNight(now: Instant): Boolean = !savedAt.isAfter(now) && savedAt.isAfter(now.minusSeconds(18 * 3600))

    companion object {
        fun of(stopId: String, resp: StopTimesResponse, savedAt: Instant): SavedBoard = SavedBoard(
            stopId,
            savedAt,
            resp.stopTimes.map(::departureRow).mapNotNull { r -> r.instant?.let { CachedDeparture(r.line, r.headsign, r.kind.name, it.epochSecond) } },
        )
    }
}

/** On-disk form of the saved boards. Unreadable data restores as empty, never crashes. */
object SavedBoardsJson {
    @kotlinx.serialization.Serializable
    private data class Row(val stopId: String, val savedAtEpoch: Long, val rows: List<CachedDeparture>)

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val rows = kotlinx.serialization.builtins.ListSerializer(Row.serializer())

    fun encode(boards: List<SavedBoard>): String =
        json.encodeToString(rows, boards.map { Row(it.stopId, it.savedAt.epochSecond, it.rows) })

    fun decode(s: String?): List<SavedBoard> =
        s?.let { runCatching { json.decodeFromString(rows, it) }.getOrNull() }.orEmpty()
            .map { SavedBoard(it.stopId, Instant.ofEpochSecond(it.savedAtEpoch), it.rows) }
}
