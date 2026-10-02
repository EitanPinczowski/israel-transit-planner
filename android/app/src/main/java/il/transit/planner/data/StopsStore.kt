package il.transit.planner.data

import il.transit.core.api.Place
import il.transit.core.geo.BBox
import il.transit.core.plan.StopsCacheJson
import il.transit.core.plan.StopsTileCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * The stops layer on disk, so the map fills instantly after a restart and Transitous is
 * asked only for tiles we do not hold. Logic (tiles, TTL, LRU, codec) is in core's
 * StopsTileCache; one file per language, because stop names come back translated.
 */
class StopsStore(private val file: File) {
    private val cache = StopsTileCache()
    private val lock = Mutex()
    private var loaded = false

    suspend fun load(box: BBox, fetch: suspend (BBox) -> List<Place>): List<Place> = lock.withLock {
        withContext(Dispatchers.IO) {
            if (!loaded) {
                cache.restore(StopsCacheJson.decode(runCatching { file.readText() }.getOrNull()))
                loaded = true
            }
        }
        val (stops, fetched) = cache.load(box, Instant.now(), fetch)
        if (fetched) withContext(Dispatchers.IO) { runCatching { file.writeText(StopsCacheJson.encode(cache.all)) } }
        stops
    }
}
