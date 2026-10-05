package il.transit.planner.data

import il.transit.core.plan.PlanCache
import il.transit.core.plan.TripCacheJson
import il.transit.core.plan.TripResult
import il.transit.core.plan.stillAhead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * The Trip tab's last successful searches, on disk, so a search with no signal can show
 * what was found before. Logic (LRU, keys, codec) is in core's PlanCache / TripCacheJson.
 * The night refresh writes here too ([putNight]), under the key of a NOW search.
 */
class PlanCacheStore(private val file: File) {
    private val cache = PlanCache<TripResult>()
    private var loaded = false

    private fun loadOnce() {
        if (loaded) return
        cache.restore(TripCacheJson.decode(runCatching { file.readText() }.getOrNull()))
        loaded = true
    }

    suspend fun put(key: String, value: TripResult) = save(key, value, night = false)

    /** A trip planned by the night refresh, shown as "planned last night at 02:14". */
    suspend fun putNight(key: String, value: TripResult) = save(key, value, night = true)

    private suspend fun save(key: String, value: TripResult, night: Boolean) = withContext(Dispatchers.IO) {
        synchronized(this@PlanCacheStore) {
            loadOnce()
            cache.put(key, value, Instant.now(), night)
            runCatching { file.writeText(TripCacheJson.encode(cache.all)) }
        }
    }

    /**
     * The saved entry as the offline Trip view shows it at [now]: options already gone are
     * dropped, and an entry with nothing left is no entry.
     */
    suspend fun get(key: String, now: Instant = Instant.now()): PlanCache.Entry<TripResult>? = withContext(Dispatchers.IO) {
        synchronized(this@PlanCacheStore) {
            loadOnce()
            cache.get(key)?.let { e -> e.value.stillAhead(now)?.let { e.copy(value = it) } }
        }
    }
}
