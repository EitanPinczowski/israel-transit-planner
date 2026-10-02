package il.transit.core.geo

import il.transit.core.api.Place
import kotlinx.serialization.Serializable
import kotlin.math.floor

/** One cell of the stops grid: [row] = floor(lat / SIZE), [col] = floor(lon / SIZE). */
@Serializable
data class Tile(val row: Int, val col: Int)

/**
 * A fixed grid for caching the stops layer. Stops are fetched for whole tiles, so a later
 * view (after a pan, or the next day) can tell exactly which part it already has.
 * [SIZE_DEG] = 0.01° ≈ 1.1 km of latitude × 0.95 km of longitude in Israel.
 */
object StopTiles {
    const val SIZE_DEG = 0.01

    fun tileOf(p: LatLon) = Tile(floor(p.lat / SIZE_DEG).toInt(), floor(p.lon / SIZE_DEG).toInt())

    /** Every tile touching [box]. */
    fun tilesCovering(box: BBox): List<Tile> {
        val a = tileOf(box.min)
        val b = tileOf(box.max)
        return (a.row..b.row).flatMap { r -> (a.col..b.col).map { c -> Tile(r, c) } }
    }

    /** The rectangle spanned by [tiles], edge to edge — one request covers all of them. */
    fun bboxOf(tiles: Collection<Tile>): BBox {
        require(tiles.isNotEmpty())
        return BBox(
            LatLon(tiles.minOf { it.row } * SIZE_DEG, tiles.minOf { it.col } * SIZE_DEG),
            LatLon((tiles.maxOf { it.row } + 1) * SIZE_DEG, (tiles.maxOf { it.col } + 1) * SIZE_DEG),
        )
    }

    /** Every tile inside the rectangle of [tiles] (a fetch of [bboxOf] covers them all). */
    fun tilesIn(tiles: Collection<Tile>): List<Tile> {
        val rows = tiles.minOf { it.row }..tiles.maxOf { it.row }
        val cols = tiles.minOf { it.col }..tiles.maxOf { it.col }
        return rows.flatMap { r -> cols.map { c -> Tile(r, c) } }
    }

    /** Stops grouped by tile; every tile in [tiles] is present (empty = no stops there). */
    fun split(stops: List<Place>, tiles: Collection<Tile>): Map<Tile, List<Place>> {
        val out = tiles.associateWith { mutableListOf<Place>() }
        for (s in stops) out[tileOf(s.latLon)]?.add(s)
        return out
    }
}
