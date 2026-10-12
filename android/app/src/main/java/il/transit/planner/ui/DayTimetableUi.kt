package il.transit.planner.ui

import il.transit.core.api.TransitApi
import il.transit.core.present.DayTimetable
import il.transit.core.present.DepartureRow
import il.transit.core.present.LegKind
import il.transit.core.present.TimetableLoader
import il.transit.core.present.TimetablePage
import il.transit.planner.data.TimetableStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** What the stop sheet asks of the day timetable (Phase 10 D7): one entry point, [MainActions.timetable]. */
sealed interface TimetableAction {
    /** A departure row was tapped: that line, that direction, today. */
    data class Open(val row: DepartureRow) : TimetableAction

    /** Today (false) or the next service day (true). */
    data class Day(val tomorrow: Boolean) : TimetableAction

    /** One more page: one request. */
    data object More : TimetableAction

    /** Back to the departures. */
    data object Close : TimetableAction
}

/** The day timetable inside the stop sheet. [rows] are the line's departures that day, earliest first. */
data class TimetableUi(
    val line: String,
    val headsign: String,
    val kind: LegKind,
    val color: String,
    val now: Instant,
    val tomorrow: Boolean = false,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val failed: Boolean = false,
    val rows: List<DepartureRow> = emptyList(),
    /** Online: the pages so far; "More" shows while it is not complete. */
    val page: TimetablePage? = null,
    /** Offline: when the night refresh saved the board shown; departures already gone are hidden. */
    val savedAt: Instant? = null,
    val savedLastNight: Boolean = false,
) {
    val canLoadMore: Boolean get() = page != null && !page.complete && savedAt == null
}

/** The stop sheet's board with no signal: saved at [at] ("last night" when [lastNight]). */
data class SavedDepartures(val at: Instant, val lastNight: Boolean, val rows: List<DepartureRow>)

/**
 * Loads the day timetable for the stop sheet: 2 `stoptimes` pages on open (core
 * [TimetableLoader]), 1 per "More". With no signal, the board the night refresh saved for this
 * stop ([TimetableStore]), departures still ahead only. Switching line, day or stop cancels a
 * load still running.
 */
class DayTimetableHolder(
    private val scope: CoroutineScope,
    api: TransitApi,
    private val saved: TimetableStore?,
    private val language: () -> String,
    private val clock: Clock,
) {
    private val loader = TimetableLoader(api)
    private var job: Job? = null

    fun handle(state: MutableStateFlow<UiState>, action: TimetableAction) {
        val sheet = state.value.stopSheet ?: return
        val open = sheet.timetable
        when (action) {
            is TimetableAction.Open -> {
                val r = action.row
                load(state, sheet.stopId, TimetableUi(r.line, r.headsign, r.kind, r.color, clock.instant()))
            }
            is TimetableAction.Day -> if (open != null && open.tomorrow != action.tomorrow) {
                load(state, sheet.stopId, open.copy(tomorrow = action.tomorrow, now = clock.instant(), loading = true, loadingMore = false, failed = false, rows = emptyList(), page = null, savedAt = null, savedLastNight = false))
            }
            TimetableAction.More -> more(state, sheet.stopId, open ?: return)
            TimetableAction.Close -> {
                job?.cancel()
                state.update { it.copy(stopSheet = it.stopSheet?.copy(timetable = null)) }
            }
        }
    }

    /** The night's saved board for [stopId], departures still ahead only; null with none left. */
    suspend fun savedBoard(stopId: String): SavedDepartures? {
        val board = saved?.get(stopId) ?: return null
        val now = clock.instant()
        val rows = board.upcoming(now)
        return if (rows.isEmpty()) null else SavedDepartures(board.savedAt, board.isLastNight(now), rows)
    }

    private fun day(ui: TimetableUi): LocalDate = DayTimetable.today(ui.now).let { if (ui.tomorrow) it.plusDays(1) else it }

    private fun load(state: MutableStateFlow<UiState>, stopId: String, ui: TimetableUi) {
        job?.cancel()
        state.update { it.copy(stopSheet = it.stopSheet?.copy(timetable = ui)) }
        job = scope.launch {
            val day = day(ui)
            val next = try {
                val page = loader.open(stopId, ui.line, ui.headsign, day, language())
                ui.copy(loading = false, rows = page.rows, page = page)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                offline(stopId, ui, day)
            }
            put(state, stopId, ui, next)
        }
    }

    private fun more(state: MutableStateFlow<UiState>, stopId: String, ui: TimetableUi) {
        val page = ui.page ?: return
        if (!ui.canLoadMore || ui.loadingMore) return
        state.update { it.copy(stopSheet = it.stopSheet?.copy(timetable = ui.copy(loadingMore = true))) }
        job = scope.launch {
            val next = try {
                val more = loader.more(page, language())
                ui.copy(loadingMore = false, rows = more.rows, page = more)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ui.copy(loadingMore = false) // the tap can be tried again
            }
            put(state, stopId, ui, next)
        }
    }

    /** No signal: this line's departures from the saved board, still ahead, or "failed". */
    private suspend fun offline(stopId: String, ui: TimetableUi, day: LocalDate): TimetableUi {
        val board = saved?.get(stopId)
        val rows = board?.let { b -> DayTimetable.departures(b.upcoming(clock.instant()), ui.line, ui.headsign, day) }.orEmpty()
        return if (board == null || rows.isEmpty()) ui.copy(loading = false, failed = true)
        else ui.copy(loading = false, rows = rows, savedAt = board.savedAt, savedLastNight = board.isLastNight(clock.instant()))
    }

    /** Only into the same stop's sheet, the same line and day: a late answer for another one is dropped. */
    private fun put(state: MutableStateFlow<UiState>, stopId: String, was: TimetableUi, next: TimetableUi) = state.update { s ->
        val sheet = s.stopSheet
        val t = sheet?.timetable
        if (sheet == null || sheet.stopId != stopId || t == null || t.line != was.line || t.headsign != was.headsign || t.tomorrow != was.tomorrow) s
        else s.copy(stopSheet = sheet.copy(timetable = next))
    }
}
