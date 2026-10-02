package il.transit.core

import il.transit.core.api.Place
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import il.transit.core.geo.StopTiles
import il.transit.core.geo.Tile
import il.transit.core.plan.StopsCacheJson
import il.transit.core.plan.StopsTileCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class StopsCacheTest {
    private val now = Instant.parse("2026-10-02T08:00:00Z")

    private fun stop(name: String, lat: Double, lon: Double) = place(name, LatLon(lat, lon), stopId = name)

    /** A fake map/stops endpoint over a fixed set of stops; counts and records requests. */
    private class FakeStops(val all: List<Place>) {
        val requests = mutableListOf<BBox>()
        suspend fun fetch(b: BBox): List<Place> {
            requests += b
            return all.filter { it.lat >= b.min.lat && it.lat <= b.max.lat && it.lon >= b.min.lon && it.lon <= b.max.lon }
        }
    }

    @Test fun `tiles cover a box and snap to a 0_01 degree grid`() {
        assertEquals(Tile(3125, 3479), StopTiles.tileOf(LatLon(31.2525, 34.7915)))
        val tiles = StopTiles.tilesCovering(BBox(LatLon(31.245, 34.785), LatLon(31.262, 34.801)))
        assertEquals(setOf(3124, 3125, 3126), tiles.map { it.row }.toSet())
        assertEquals(setOf(3478, 3479, 3480), tiles.map { it.col }.toSet())
        assertEquals(9, tiles.size)
        val b = StopTiles.bboxOf(listOf(Tile(3125, 3479), Tile(3126, 3480)))
        assertEquals(31.25, b.min.lat, 1e-9)
        assertEquals(34.81, b.max.lon, 1e-9)
    }

    @Test fun `split puts each stop in exactly one requested tile`() {
        val tiles = listOf(Tile(3125, 3479), Tile(3125, 3480))
        val byTile = StopTiles.split(
            listOf(stop("a", 31.2525, 34.7915), stop("b", 31.2525, 34.8005), stop("far", 31.30, 34.79)),
            tiles,
        )
        assertEquals(listOf("a"), byTile.getValue(Tile(3125, 3479)).map { it.name })
        assertEquals(listOf("b"), byTile.getValue(Tile(3125, 3480)).map { it.name })
        assertEquals(2, byTile.size) // "far" is in no requested tile: dropped, not misfiled
    }

    @Test fun `a view inside cached tiles costs no request, a pan fetches only new tiles once`() = runBlocking {
        val api = FakeStops(listOf(stop("a", 31.2525, 34.7915), stop("b", 31.2525, 34.8125)))
        val cache = StopsTileCache()
        val view = BBox(LatLon(31.251, 34.791), LatLon(31.254, 34.794))

        val (first, fetched1) = cache.load(view, now) { api.fetch(it) }
        assertTrue(fetched1)
        assertEquals(listOf("a"), first.map { it.name })

        val (again, fetched2) = cache.load(view, now.plusSeconds(3600)) { api.fetch(it) }
        assertFalse(fetched2)
        assertEquals(listOf("a"), again.map { it.name })
        assertEquals(1, api.requests.size)

        // Pan east two tiles: one request, for the tiles not yet held.
        val panned = BBox(LatLon(31.251, 34.791), LatLon(31.254, 34.813))
        val (wide, fetched3) = cache.load(panned, now) { api.fetch(it) }
        assertTrue(fetched3)
        assertEquals(2, api.requests.size)
        assertEquals(34.80, api.requests.last().min.lon, 1e-9) // the cached tile was not asked again
        assertEquals(setOf("a", "b"), wide.map { it.name }.toSet())
    }

    @Test fun `tiles go stale after the ttl`() {
        val cache = StopsTileCache(ttl = Duration.ofDays(7))
        val t = Tile(1, 1)
        cache.put(mapOf(t to emptyList()), now)
        assertTrue(cache.missing(listOf(t), now.plus(Duration.ofDays(6))).isEmpty())
        assertEquals(listOf(t), cache.missing(listOf(t), now.plus(Duration.ofDays(8))))
    }

    @Test fun `least recently used tiles fall out past capacity`() {
        val cache = StopsTileCache(capacity = 2)
        cache.put(mapOf(Tile(0, 0) to emptyList()), now)
        cache.put(mapOf(Tile(0, 1) to emptyList()), now)
        cache.stopsIn(listOf(Tile(0, 0))) // touch: (0,1) is now the oldest
        cache.put(mapOf(Tile(0, 2) to emptyList()), now)
        assertEquals(setOf(Tile(0, 0), Tile(0, 2)), cache.all.map { it.tile }.toSet())
    }

    @Test fun `codec round-trips and garbage restores as empty`() {
        val cache = StopsTileCache()
        cache.put(mapOf(Tile(3125, 3479) to listOf(stop("a", 31.2525, 34.7915))), now)
        val back = StopsCacheJson.decode(StopsCacheJson.encode(cache.all))
        assertEquals(1, back.size)
        assertEquals("a", back[0].stops.single().name)
        assertEquals(now, back[0].fetchedAt)
        val restored = StopsTileCache().apply { restore(back) }
        assertTrue(restored.missing(listOf(Tile(3125, 3479)), now).isEmpty())
        assertTrue(StopsCacheJson.decode("{not json").isEmpty())
        assertTrue(StopsCacheJson.decode(null).isEmpty())
    }
}
