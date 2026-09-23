package il.transit.planner.data

import il.transit.core.history.History
import il.transit.core.history.TripRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Trips the user started, newest first, capped at History.MAX_RECORDS. Local only, never uploaded. */
class HistoryStore(private val file: File) {
    private val _records = MutableStateFlow<List<TripRecord>>(emptyList())
    val records: StateFlow<List<TripRecord>> = _records.asStateFlow()
    private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) {
        synchronized(this@HistoryStore) {
            if (!loaded) {
                _records.value = History.decode(runCatching { file.readText() }.getOrNull())
                loaded = true
            }
        }
    }

    suspend fun add(r: TripRecord) = update { History.add(it, r) }

    suspend fun clear() = update { emptyList() }

    private suspend fun update(change: (List<TripRecord>) -> List<TripRecord>) = withContext(Dispatchers.IO) {
        synchronized(this@HistoryStore) {
            if (!loaded) {
                _records.value = History.decode(runCatching { file.readText() }.getOrNull())
                loaded = true
            }
            _records.value = change(_records.value)
            runCatching { file.writeText(History.encode(_records.value)) }
        }
    }
}
