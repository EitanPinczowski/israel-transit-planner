package il.transit.core

import il.transit.core.api.Place
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import il.transit.core.geo.StopTiles
import il.transit.core.geo.StopsViewport
import il.transit.core.plan.NightRefresh
import il.transit.core.plan.NightRefresher
import il.transit.core.plan.StopsTileCache
import il.transit.core.user.LowData
import il.transit.core.user.UserSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Random

/** Phase 11 E4: low-data mode only removes traffic (owner decision 2). */
class LowDataTest {
    private fun view(lat: Double, lon: Double, zoom: Double): BBox {
        // A phone screen: about 360 × 640 dp; at zoom z one dp is 156543 * cos(lat) / 2^z m.
        val mPerDp = 156_543.0 * Math.cos(Math.toRadians(lat)) / Math.pow(2.0, zoom)
        val dLat = 640 * mPerDp / 111_320.0 / 2
        val dLon = 360 * mPerDp / (111_320.0 * Math.cos(Math.toRadians(lat))) / 2
        return BBox(LatLon(lat - dLat, lon - dLon), LatLon(lat + dLat, lon + dLon))
    }

    @Test fun `stops - from zoom 16 instead of 15`() {
        assertEquals(15.0, LowData.stopsMinZoom(false), 0.0)
        assertEquals(16.0, LowData.stopsMinZoom(true), 0.0)
        val v = view(31.25, 34.79, 15.5)
        assertTrue(LowData.stopsNeedFetch(v, 15.5, null, on = false))
        assertFalse(LowData.stopsNeedFetch(v, 15.5, null, on = true))
        val v16 = view(31.25, 34.79, 16.2)
        assertTrue(LowData.stopsNeedFetch(v16, 16.2, null, on = true))
        assertFalse("already loaded", LowData.stopsNeedFetch(v16, 16.2, LowData.stopsFetchBox(v16, true), on = true))
    }

    @Test fun `stops - only the tiles under the view, never the half-screen around it`() {
        val v = view(32.08, 34.78, 16.0)
        val normal = LowData.stopsFetchBox(v, false)
        val low = LowData.stopsFetchBox(v, true)
        assertEquals(StopsViewport.fetchBox(v), normal)
        assertEquals(StopTiles.tilesCovering(v).toSet(), StopTiles.tilesCovering(low).filter { t ->
            // bboxOf's far edges touch the next tile: count only tiles the box really covers
            StopTiles.tilesCovering(v).contains(t)
        }.toSet())
        assertTrue(StopTiles.tilesIn(StopTiles.tilesCovering(v)).size < StopTiles.tilesCovering(normal).size)
        assertTrue(low.min.lat <= v.min.lat && low.max.lat >= v.max.lat && low.min.lon <= v.min.lon && low.max.lon >= v.max.lon)
    }

    /** Replays a viewport walk through the app's own rule and tile cache, counting what is fetched. */
    private class Walk(val on: Boolean) {
        val cache = StopsTileCache()
        var loaded: BBox? = null
        var requests = 0
        var tiles = 0
        fun at(v: BBox, zoom: Double, now: Instant) = runBlocking {
            if (zoom < LowData.stopsMinZoom(on)) {
                loaded = null
                return@runBlocking
            }
            if (!LowData.stopsNeedFetch(v, zoom, loaded, on)) return@runBlocking
            val box = LowData.stopsFetchBox(v, on)
            cache.load(box, now) { b ->
                requests++
                tiles += StopTiles.tilesCovering(b).size
                emptyList<Place>()
            }
            loaded = box
        }
    }

    @Test fun `stops - one view never costs more in low data, at any zoom`() {
        val rnd = Random(4)
        val now = Instant.parse("2026-10-27T08:00:00Z")
        repeat(500) {
            val zoom = 14.0 + rnd.nextDouble() * 4
            val v = view(31.2 + rnd.nextDouble() * 1.5, 34.7 + rnd.nextDouble() * 0.5, zoom)
            val normal = Walk(false).also { it.at(v, zoom, now) }
            val low = Walk(true).also { it.at(v, zoom, now) }
            assertTrue(low.requests <= normal.requests)
            assertTrue("tiles: low ${low.tiles} vs normal ${normal.tiles}", low.tiles <= normal.tiles)
        }
    }

    /**
     * Over a walk, low data can now and then ask again for tiles normal had fetched ahead (no
     * prefetch), so a single walk is not always smaller; over many walks it downloads less.
     */
    @Test fun `stops - over 200 walks of pans and zooms, low data downloads fewer tiles`() {
        val rnd = Random(4)
        val now = Instant.parse("2026-10-27T08:00:00Z")
        var lowTiles = 0
        var normalTiles = 0
        repeat(200) {
            val normal = Walk(false)
            val low = Walk(true)
            var lat = 31.2 + rnd.nextDouble() * 1.5
            var lon = 34.7 + rnd.nextDouble() * 0.5
            repeat(30) {
                val zoom = 14.0 + rnd.nextDouble() * 4
                lat += (rnd.nextDouble() - 0.5) * 0.01
                lon += (rnd.nextDouble() - 0.5) * 0.01
                val v = view(lat, lon, zoom)
                normal.at(v, zoom, now)
                low.at(v, zoom, now)
            }
            lowTiles += low.tiles
            normalTiles += normal.tiles
        }
        assertTrue("tiles: low $lowTiles vs normal $normalTiles", lowTiles < normalTiles)
    }

    @Test fun `stops - one view at zoom 15 to 16 is 0 requests in low data, 1 in normal`() {
        val now = Instant.parse("2026-10-27T08:00:00Z")
        val normal = Walk(false)
        val low = Walk(true)
        val v = view(31.25, 34.79, 15.4)
        normal.at(v, 15.4, now)
        low.at(v, 15.4, now)
        assertEquals(1, normal.requests)
        assertEquals(0, low.requests)
    }

    @Test fun `night refresh - trips only, no stop boards`() = runBlocking {
        val stops = listOf("il-Israel-MOT_37314", "il-Israel-MOT_26760")
        assertEquals(stops, LowData.nightStops(stops, false))
        assertEquals(emptyList<String>(), LowData.nightStops(stops, true))
        // Through the real refresher: low data sends no stoptimes.
        val now = Instant.parse("2026-10-27T21:30:00Z") // 23:30 Israel, in the window
        fun run(on: Boolean): FakeTransitApi {
            val api = FakeTransitApi()
            runBlocking {
                NightRefresher(api).run(
                    jobs = { emptyList() }, lastRunDay = null, now = now, settings = UserSettings(lowData = on), language = "he",
                    stops = LowData.nightStops(NightRefresh.stops(listOf(
                        il.transit.core.user.FavoriteLine(stops[0], "a", "470", "x"),
                        il.transit.core.user.FavoriteLine(stops[1], "b", "369", "y"),
                    )), on),
                    save = { _, _ -> },
                )
            }
            return api
        }
        assertEquals(2, run(false).calls.count { it == "stopTimes" })
        assertEquals(0, run(true).calls.count { it == "stopTimes" })
    }

    @Test fun `offline map - asks only in low data and off Wi-Fi`() {
        assertFalse(LowData.askBeforeOfflineDownload(on = false, wifi = false))
        assertFalse(LowData.askBeforeOfflineDownload(on = false, wifi = true))
        assertFalse(LowData.askBeforeOfflineDownload(on = true, wifi = true))
        assertTrue(LowData.askBeforeOfflineDownload(on = true, wifi = false))
    }

    @Test fun `planning, search, reminders and rain are unchanged`() {
        val base = UserSettings(maxWalkMin = 20, rainAware = true)
        val low = base.copy(lowData = true)
        assertEquals(base.preferences(), low.preferences())
        assertEquals(base.traffic(), low.traffic())
        assertEquals(base.rainAware, low.rainAware)
        assertFalse("off by default", UserSettings().lowData)
    }
}
