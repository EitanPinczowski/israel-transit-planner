package il.transit.planner.data

import il.transit.core.api.StopTimesResponse
import il.transit.core.present.SavedBoard
import il.transit.core.present.SavedBoardsJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * The last board the night refresh saved per stop of My lines (Phase 10 D7), on disk, so the
 * stop sheet and the day timetable still show with no signal. Scheduled times only; the codec
 * and the "still ahead" rule are in core's [SavedBoard].
 */
class TimetableStore(private val file: File) {
    private val boards = LinkedHashMap<String, SavedBoard>()
    private var loaded = false

    private fun loadOnce() {
        if (loaded) return
        SavedBoardsJson.decode(runCatching { file.readText() }.getOrNull()).forEach { boards[it.stopId] = it }
        loaded = true
    }

    suspend fun put(stopId: String, board: StopTimesResponse, now: Instant = Instant.now()) = withContext(Dispatchers.IO) {
        synchronized(this@TimetableStore) {
            loadOnce()
            boards.remove(stopId)
            boards[stopId] = SavedBoard.of(stopId, board, now)
            while (boards.size > MAX_BOARDS) boards.remove(boards.keys.first())
            runCatching { file.writeText(SavedBoardsJson.encode(boards.values.toList())) }
        }
    }

    suspend fun get(stopId: String): SavedBoard? = withContext(Dispatchers.IO) {
        synchronized(this@TimetableStore) {
            loadOnce()
            boards[stopId]
        }
    }

    private companion object {
        /** Starred stops change; a few old boards are kept, the oldest saved falls out. */
        const val MAX_BOARDS = 6
    }
}
