package il.transit.core.plan

import il.transit.core.geo.LatLon
import java.time.Instant
import kotlin.math.roundToLong

/**
 * The last few successful searches, so a failed search (no signal) can show what was found
 * earlier for the same trip. Pure and generic: the app decides what a value is and how to
 * persist it. Least-recently-used entries fall out past [capacity].
 *
 * The default capacity is [CAPACITY]: the night refresh adds up to
 * [NightRefresh.MAX_TRIPS] entries a night, and they must not push out the user's own
 * ten most recent searches.
 */
class PlanCache<T>(private val capacity: Int = CAPACITY) {
    /** [night]: planned by the night refresh, not by a search the user made. */
    data class Entry<T>(val key: String, val savedAt: Instant, val value: T, val night: Boolean = false)

    private val entries = ArrayList<Entry<T>>()

    val all: List<Entry<T>> get() = entries.toList()

    fun put(key: String, value: T, at: Instant, night: Boolean = false) {
        entries.removeAll { it.key == key }
        entries.add(0, Entry(key, at, value, night))
        while (entries.size > capacity) entries.removeAt(entries.size - 1)
    }

    fun get(key: String): Entry<T>? = entries.firstOrNull { it.key == key }

    fun restore(saved: List<Entry<T>>) {
        entries.clear()
        entries.addAll(saved.take(capacity))
    }

    companion object {
        const val CAPACITY = 16

        /**
         * Cache key: the tab plus every place rounded to ~100 m (3 decimals ≈ 110 m of
         * latitude), so "my location" a few steps away still finds yesterday's plan.
         */
        fun key(mode: String, vararg places: LatLon?): String =
            mode + ":" + places.joinToString("|") { p -> p?.let { "${r3(it.lat)},${r3(it.lon)}" } ?: "-" }

        private fun r3(d: Double): String = ((d * 1000).roundToLong() / 1000.0).toString()
    }
}

/** On-disk form of the Trip tab's cache. Unreadable data restores as empty, never crashes. */
object TripCacheJson {
    @kotlinx.serialization.Serializable
    private data class Row(val key: String, val savedAtEpoch: Long, val result: TripResult, val night: Boolean? = null)

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val rows = kotlinx.serialization.builtins.ListSerializer(Row.serializer())

    fun encode(entries: List<PlanCache.Entry<TripResult>>): String =
        json.encodeToString(rows, entries.map { Row(it.key, it.savedAt.epochSecond, it.value, it.night.takeIf { n -> n }) })

    fun decode(s: String?): List<PlanCache.Entry<TripResult>> =
        s?.let { runCatching { json.decodeFromString(rows, it) }.getOrNull() }.orEmpty()
            .map { PlanCache.Entry(it.key, Instant.ofEpochSecond(it.savedAtEpoch), it.result, it.night == true) }
}

/**
 * A saved result as the offline Trip view shows it at [now]: options whose vehicle has
 * already left are dropped (the first transit leg's departure; a walk-only option stays).
 * Null when nothing is left to show.
 */
fun TripResult.stillAhead(now: Instant): TripResult? {
    val ahead = itineraries.filter { (it.firstTransitLeg?.let { l -> il.transit.core.api.parseTime(l.startTime) } ?: it.start) >= now }
    return if (ahead.isEmpty() && walkOnly == null) null else TripResult(ahead, walkOnly)
}
