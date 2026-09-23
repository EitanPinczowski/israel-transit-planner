package il.transit.planner.data

import il.transit.core.plan.PlanCache
import il.transit.core.plan.TripCacheJson
import il.transit.core.plan.TripResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * The Trip tab's last successful searches, on disk, so a search with no signal can show
 * what was found before. Logic (LRU, keys, codec) is in core's PlanCache / TripCacheJson.
 */
class PlanCacheStore(private val file: File) {
    private val cache = PlanCache<TripResult>()
    private var loaded = false

    private fun loadOnce() {
        if (loaded) return
        cache.restore(TripCacheJson.decode(runCatching { file.readText() }.getOrNull()))
        loaded = true
    }

    suspend fun put(key: String, value: TripResult) = withContext(Dispatchers.IO) {
        synchronized(this@PlanCacheStore) {
            loadOnce()
            cache.put(key, value, Instant.now())
            runCatching { file.writeText(TripCacheJson.encode(cache.all)) }
        }
    }

    suspend fun get(key: String): PlanCache.Entry<TripResult>? = withContext(Dispatchers.IO) {
        synchronized(this@PlanCacheStore) {
            loadOnce()
            cache.get(key)
        }
    }
}
