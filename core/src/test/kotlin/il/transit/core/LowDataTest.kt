package il.transit.core

import il.transit.core.api.Place
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import il.transit.core.geo.StopTiles
import il.transit.core.geo.StopsViewport
import il.transit.core.plan.NightRefresh
import il.transit.core.plan.NightRefresher
import il.transit.core.plan.StopsTileCache
import il.transit.core.user.FavoriteLine
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
        val normal = Walk(false).also { it.at(v, 15.5) }
        val low = Walk(true).also { it.at(v, 15.5) }
        assertEquals(1, normal.requests)
        assertEquals(0, low.requests)
    }

    @Test fun `stops - asks nothing while the tiles under the screen are held`() {
        val low = Walk(true)
        low.at(view(31.25, 34.79, 16.5), 16.5)
        assertEquals(1, low.requests)
        // Small pans and a zoom-in inside what was fetched: 0 more.
        low.at(view(31.2503, 34.7903, 16.5), 16.5)
        low.at(view(31.2497, 34.7897, 17.2), 17.2)
        assertEquals(1, low.requests)
    }

    /**
     * The app's two rules over the same tile cache: normal mode as `MainViewModel.onViewport` runs it (fetch the view
     * plus half a screen whenever the view leaves the box last loaded), low data through [LowData.loadStops].
     */
    private class Walk(val on: Boolean) {
        val cache = StopsTileCache()
        var loaded: BBox? = null
        var requests = 0
        var tiles = 0
        private val now = Instant.parse("2026-10-27T08:00:00Z")
        private val fetch: suspend (BBox) -> List<Place> = { b ->
            requests++
            tiles += StopTiles.tilesCovering(b).size
            emptyList()
        }

        fun at(v: BBox, zoom: Double) = runBlocking {
            if (zoom < LowData.stopsMinZoom(on)) {
                loaded = null
                return@runBlocking
            }
            if (on) {
                LowData.loadStops(cache, v, now, fetch)
            } else if (StopsViewport.needsFetch(v, zoom, loaded)) {
                val box = StopsViewport.fetchBox(v)
                cache.load(box, now, fetch)
                loaded = box
            }
        }
    }

    private fun walks(pan: Boolean): List<Pair<Walk, Walk>> {
        val rnd = Random(4)
        return (1..200).map {
            val normal = Walk(false)
            val low = Walk(true)
            var lat = 31.2 + rnd.nextDouble() * 1.5
            var lon = 34.7 + rnd.nextDouble() * 0.5
            val held = 16.5 + rnd.nextDouble() // pan-only: one zoom per walk, 16.5 to 17.5
            repeat(30) {
                val zoom = if (pan) held else 14.0 + rnd.nextDouble() * 4
                lat += (rnd.nextDouble() - 0.5) * 0.01
                lon += (rnd.nextDouble() - 0.5) * 0.01
                val v = view(lat, lon, zoom)
                normal.at(v, zoom)
                low.at(v, zoom)
            }
            normal to low
        }
    }

    private fun assertNoMore(pairs: List<Pair<Walk, Walk>>) {
        val normalReq = pairs.sumOf { it.first.requests }
        val lowReq = pairs.sumOf { it.second.requests }
        val normalTiles = pairs.sumOf { it.first.tiles }
        val lowTiles = pairs.sumOf { it.second.tiles }
        assertTrue("requests: low $lowReq vs normal $normalReq", lowReq <= normalReq)
        assertTrue("tiles: low $lowTiles vs normal $normalTiles", lowTiles <= normalTiles)
    }

    @Test fun `stops - one view never costs more in low data, at any zoom`() {
        val rnd = Random(4)
        repeat(500) {
            val zoom = 14.0 + rnd.nextDouble() * 4
            val v = view(31.2 + rnd.nextDouble() * 1.5, 34.7 + rnd.nextDouble() * 0.5, zoom)
            val normal = Walk(false).also { it.at(v, zoom) }
            val low = Walk(true).also { it.at(v, zoom) }
            assertTrue(low.requests <= normal.requests)
            assertTrue("tiles: low ${low.tiles} vs normal ${normal.tiles}", low.tiles <= normal.tiles)
        }
    }

    /** 200 walks of 30 steps, pans of up to ±0.005° and a new zoom (14 to 18) at every step. */
    @Test fun `stops - over 200 walks of pans and zooms, low data sends no more requests and no more tiles`() =
        assertNoMore(walks(pan = false))

    /** Pans only, at one zoom per walk between 16.5 and 17.5: where dropping a prefetch would cost requests. */
    @Test fun `stops - over 200 pan-only walks at zoom 16_5 to 17_5, low data sends no more requests and no more tiles`() =
        assertNoMore(walks(pan = true))

    @Test fun `night refresh - trips only, no stop boards`() = runBlocking {
        val stops = listOf("il-Israel-MOT_37314", "il-Israel-MOT_26760")
        assertEquals(stops, LowData.nightStops(stops, false))
        assertEquals(emptyList<String>(), LowData.nightStops(stops, true))
        // Through the real refresher, given My lines' stops as the worker passes them: low data sends no stoptimes.
        val now = Instant.parse("2026-10-27T21:30:00Z") // 23:30 Israel, in the window
        val favorites = listOf(FavoriteLine(stops[0], "a", "470", "x"), FavoriteLine(stops[1], "b", "369", "y"))
        fun run(on: Boolean): FakeTransitApi {
            val api = FakeTransitApi()
            runBlocking {
                NightRefresher(api).run(
                    jobs = { emptyList() },
                    lastRunDay = null,
                    now = now,
                    settings = UserSettings(lowData = on),
                    language = "he",
                    stops = NightRefresh.stops(favorites),
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
