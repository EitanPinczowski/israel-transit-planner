package il.transit.planner.data

import il.transit.core.plan.CacheJson
import il.transit.core.plan.PlanCache
import il.transit.core.plan.TabCacheJson
import il.transit.core.plan.TabResult
import il.transit.core.plan.TripCacheJson
import il.transit.core.plan.TripResult
import il.transit.core.plan.stillAhead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.Instant

/**
 * The Trip tab's last successful searches, on disk, so a search with no signal can show
 * what was found before. Logic (LRU, keys, codec) is in core's PlanCache / TripCacheJson.
 * The night refresh writes here too ([putNight]), under the key of a NOW search.
 *
 * Generic since Phase 11 R1: [tabs] keeps what the car tabs and errands showed, read back only
 * after process death ([TabResult]). [ahead] is what is still worth showing at a time.
 */
class PlanCacheStore<T>(
    private val file: File,
    private val clock: Clock,
    private val codec: CacheJson<T>,
    private val ahead: (T, Instant) -> T?,
    capacity: Int = PlanCache.CAPACITY,
) {
    private val cache = PlanCache<T>(capacity)
    private var loaded = false

    private fun loadOnce() {
        if (loaded) return
        cache.restore(codec.decode(runCatching { file.readText() }.getOrNull()))
        loaded = true
    }

    suspend fun put(key: String, value: T) = save(key, value, night = false)

    /** A trip planned by the night refresh, shown as "planned last night at 02:14". */
    suspend fun putNight(key: String, value: T) = save(key, value, night = true)

    private suspend fun save(key: String, value: T, night: Boolean) = withContext(Dispatchers.IO) {
        synchronized(this@PlanCacheStore) {
            loadOnce()
            cache.put(key, value, clock.instant(), night)
            runCatching { file.writeText(codec.encode(cache.all)) }
        }
    }

    /**
     * The saved entry as the offline Trip view shows it at [now]: options already gone are
     * dropped, and an entry with nothing left is no entry.
     */
    suspend fun get(key: String, now: Instant = clock.instant()): PlanCache.Entry<T>? = withContext(Dispatchers.IO) {
        synchronized(this@PlanCacheStore) {
            loadOnce()
            cache.get(key)?.let { e -> ahead(e.value, now)?.let { e.copy(value = it) } }
        }
    }

    /** Every saved entry as stored, newest first: the Quick Settings tile looks for one home (C4). */
    suspend fun entries(): List<PlanCache.Entry<T>> = withContext(Dispatchers.IO) {
        synchronized(this@PlanCacheStore) {
            loadOnce()
            cache.all
        }
    }

    /** Forgets every saved search (UI tests start each case from an empty cache). */
    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(this@PlanCacheStore) {
            cache.restore(emptyList())
            loaded = true
            file.delete()
        }
    }
}

    companion object {
        /** The Trip tab's searches (offline view, night refresh, the Quick Settings tile). */
        fun trips(file: File, clock: Clock = Clock.systemUTC()) = PlanCacheStore(file, clock, TripCacheJson, TripResult::stillAhead)

        /** One entry per car tab and errands is all a restore needs; a few spare for switching back and forth. */
        const val TABS_CAPACITY = 6

        /** What the car tabs and errands showed, for a restore after process death (R1). */
        fun tabs(file: File, clock: Clock = Clock.systemUTC()) = PlanCacheStore(file, clock, TabCacheJson, TabResult::stillAhead, TABS_CAPACITY)
    }
}
