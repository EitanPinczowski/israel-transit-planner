package il.transit.core.plan

import il.transit.core.geo.LatLon
import java.time.Instant
import kotlin.math.roundToLong

/**
 * The last few successful searches, so a failed search (no signal) can show what was found
 * earlier for the same trip. Pure and generic: the app decides what a value is and how to
 * persist it. Least-recently-used entries fall out past [capacity].
 */
class PlanCache<T>(private val capacity: Int = 10) {
    data class Entry<T>(val key: String, val savedAt: Instant, val value: T)

    private val entries = ArrayList<Entry<T>>()

    val all: List<Entry<T>> get() = entries.toList()

    fun put(key: String, value: T, at: Instant) {
        entries.removeAll { it.key == key }
        entries.add(0, Entry(key, at, value))
        while (entries.size > capacity) entries.removeAt(entries.size - 1)
    }

    fun get(key: String): Entry<T>? = entries.firstOrNull { it.key == key }

    fun restore(saved: List<Entry<T>>) {
        entries.clear()
        entries.addAll(saved.take(capacity))
    }

    companion object {
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
    private data class Row(val key: String, val savedAtEpoch: Long, val result: TripResult)

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val rows = kotlinx.serialization.builtins.ListSerializer(Row.serializer())

    fun encode(entries: List<PlanCache.Entry<TripResult>>): String =
        json.encodeToString(rows, entries.map { Row(it.key, it.savedAt.epochSecond, it.value) })

    fun decode(s: String?): List<PlanCache.Entry<TripResult>> =
        s?.let { runCatching { json.decodeFromString(rows, it) }.getOrNull() }.orEmpty()
            .map { PlanCache.Entry(it.key, Instant.ofEpochSecond(it.savedAtEpoch), it.result) }
}
