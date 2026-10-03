package il.transit.planner.data

import il.transit.core.present.DepartureCache
import il.transit.core.present.DepartureCacheJson
import il.transit.core.present.DepartureRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * My lines' last departure boards, on disk, so they still show with no signal (scheduled
 * times only). Logic (pruning, codec) is in core's DepartureCache.
 */
class DepartureCacheStore(private val file: File) {
    private val cache = DepartureCache()
    private var loaded = false

    private fun loadOnce() {
        if (loaded) return
        cache.restore(DepartureCacheJson.decode(runCatching { file.readText() }.getOrNull()))
        loaded = true
    }

    suspend fun put(stopId: String, rows: List<DepartureRow>) = withContext(Dispatchers.IO) {
        synchronized(this@DepartureCacheStore) {
            loadOnce()
            cache.put(stopId, rows, Instant.now())
            runCatching { file.writeText(DepartureCacheJson.encode(cache.all)) }
        }
    }

    suspend fun upcoming(stopId: String): Pair<Instant, List<DepartureRow>>? = withContext(Dispatchers.IO) {
        synchronized(this@DepartureCacheStore) {
            loadOnce()
            cache.upcoming(stopId, Instant.now())
        }
    }
}
