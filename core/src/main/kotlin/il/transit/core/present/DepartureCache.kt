package il.transit.core.present

import java.time.Instant

/** One departure kept on the phone for "My lines" with no signal: scheduled data only. */
@kotlinx.serialization.Serializable
data class CachedDeparture(val line: String, val headsign: String, val kind: String, val epochSec: Long)

/**
 * The last departure boards fetched for My lines, per stop, so they still show without
 * signal. Only times still ahead are shown, never a delay or a cancellation (that would be
 * stale). The [maxStops] least recently saved stops fall out.
 */
class DepartureCache(private val maxStops: Int = 20) {
    data class Entry(val stopId: String, val savedAt: Instant, val rows: List<CachedDeparture>)

    private val entries = LinkedHashMap<String, Entry>()

    val all: List<Entry> get() = entries.values.toList()

    fun put(stopId: String, rows: List<DepartureRow>, now: Instant) {
        entries.remove(stopId)
        entries[stopId] = Entry(
            stopId,
            now,
            rows.mapNotNull { r -> r.instant?.let { CachedDeparture(r.line, r.headsign, r.kind.name, it.epochSecond) } },
        )
        while (entries.size > maxStops) entries.remove(entries.keys.first())
    }

    /** The saved board for [stopId] with only the departures not yet gone; null when none are left. */
    fun upcoming(stopId: String, now: Instant): Pair<Instant, List<DepartureRow>>? {
        val e = entries[stopId] ?: return null
        val rows = e.rows.filter { it.epochSec >= now.epochSecond - 60 }.map { c ->
            val at = Instant.ofEpochSecond(c.epochSec)
            DepartureRow(
                line = c.line,
                headsign = c.headsign,
                kind = runCatching { LegKind.valueOf(c.kind) }.getOrDefault(LegKind.OTHER),
                time = hhmm(at),
                delayMin = null,
                cancelled = false,
                instant = at,
            )
        }
        return if (rows.isEmpty()) null else e.savedAt to rows
    }

    fun restore(saved: List<Entry>) {
        entries.clear()
        saved.forEach { entries[it.stopId] = it }
        while (entries.size > maxStops) entries.remove(entries.keys.first())
    }
}

/** On-disk form of [DepartureCache]. Unreadable data restores as empty, never crashes. */
object DepartureCacheJson {
    @kotlinx.serialization.Serializable
    private data class Row(val stopId: String, val savedAtEpoch: Long, val rows: List<CachedDeparture>)

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val rows = kotlinx.serialization.builtins.ListSerializer(Row.serializer())

    fun encode(entries: List<DepartureCache.Entry>): String =
        json.encodeToString(rows, entries.map { Row(it.stopId, it.savedAt.epochSecond, it.rows) })

    fun decode(s: String?): List<DepartureCache.Entry> =
        s?.let { runCatching { json.decodeFromString(rows, it) }.getOrNull() }.orEmpty()
            .map { DepartureCache.Entry(it.stopId, Instant.ofEpochSecond(it.savedAtEpoch), it.rows) }
}
