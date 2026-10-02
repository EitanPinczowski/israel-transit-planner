package il.transit.core.plan

import il.transit.core.api.Place
import il.transit.core.geo.BBox
import il.transit.core.geo.StopTiles
import il.transit.core.geo.Tile
import java.time.Duration
import java.time.Instant

/**
 * The stops layer, cached per [Tile] so it survives app restarts and pans. Stops change
 * rarely, so a tile stays fresh for [ttl]; past [capacity] tiles the least recently used
 * fall out. Pure: the app persists it with [StopsCacheJson].
 */
class StopsTileCache(
    private val ttl: Duration = Duration.ofDays(7),
    private val capacity: Int = 400,
) {
    data class Entry(val tile: Tile, val fetchedAt: Instant, val stops: List<Place>)

    // Access order: reading a tile makes it recently used.
    private val entries = LinkedHashMap<Tile, Entry>(64, 0.75f, true)

    val all: List<Entry> get() = entries.values.toList()

    /** Tiles of [tiles] that are absent or older than [ttl]. */
    fun missing(tiles: List<Tile>, now: Instant): List<Tile> =
        tiles.filter { t -> entries[t]?.let { now.isAfter(it.fetchedAt.plus(ttl)) } ?: true }

    fun put(byTile: Map<Tile, List<Place>>, now: Instant) {
        for ((t, stops) in byTile) entries[t] = Entry(t, now, stops)
        trim()
    }

    fun stopsIn(tiles: List<Tile>): List<Place> = tiles.flatMap { entries[it]?.stops.orEmpty() }

    fun restore(saved: List<Entry>) {
        entries.clear()
        saved.forEach { entries[it.tile] = it }
        trim()
    }

    /**
     * Stops for [box]: from the cache when every tile is fresh, else ONE [fetch] for the
     * rectangle of the missing tiles. Returns the stops and whether a fetch happened.
     */
    suspend fun load(box: BBox, now: Instant, fetch: suspend (BBox) -> List<Place>): Pair<List<Place>, Boolean> {
        val tiles = StopTiles.tilesCovering(box)
        val missing = missing(tiles, now)
        if (missing.isNotEmpty()) {
            val fetched = fetch(StopTiles.bboxOf(missing))
            put(StopTiles.split(fetched, StopTiles.tilesIn(missing)), now)
        }
        return stopsIn(tiles) to missing.isNotEmpty()
    }

    private fun trim() {
        val it = entries.keys.iterator()
        while (entries.size > capacity && it.hasNext()) { it.next(); it.remove() }
    }
}

/** On-disk form of [StopsTileCache]. Unreadable data restores as empty, never crashes. */
object StopsCacheJson {
    @kotlinx.serialization.Serializable
    private data class Row(val tile: Tile, val fetchedAtEpoch: Long, val stops: List<Place>)

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val rows = kotlinx.serialization.builtins.ListSerializer(Row.serializer())

    fun encode(entries: List<StopsTileCache.Entry>): String =
        json.encodeToString(rows, entries.map { Row(it.tile, it.fetchedAt.epochSecond, it.stops) })

    fun decode(s: String?): List<StopsTileCache.Entry> =
        s?.let { runCatching { json.decodeFromString(rows, it) }.getOrNull() }.orEmpty()
            .map { StopsTileCache.Entry(it.tile, Instant.ofEpochSecond(it.fetchedAtEpoch), it.stops) }
}
