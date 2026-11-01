package il.transit.core.user

import il.transit.core.api.Place
import il.transit.core.geo.BBox
import il.transit.core.geo.StopTiles
import il.transit.core.geo.StopsViewport
import il.transit.core.plan.StopsTileCache
import java.time.Instant

/**
 * Low-data mode (Phase 11 E4, owner decision 2): one switch, off by default
 * ([UserSettings.lowData]). It only removes traffic:
 *  - the stops layer loads from zoom [STOPS_MIN_ZOOM] instead of 15, and asks only when a tile under the
 *    screen is missing ([loadStops]): never more requests than normal (boss, review of PR #105);
 *  - the night refresh saves no stop boards (trips only, still Wi-Fi + charging);
 *  - the offline map area download asks first unless the phone is on Wi-Fi;
 *  - planning, search, reminders and the rain note are unchanged.
 */
object LowData {
    const val STOPS_MIN_ZOOM = 16.0

    fun stopsMinZoom(on: Boolean): Double = if (on) STOPS_MIN_ZOOM else StopsViewport.MIN_ZOOM

    /**
     * The stops for [view] in low-data mode, from [cache] (Phase 11 E4; boss decision on the review: low data never
     * sends more requests than normal). It asks Transitous only when a tile under the screen itself is missing, then
     * fetches the view plus half a screen (normal mode's box), so the next pans are covered too. Normal mode asks
     * whenever the view leaves the box it last loaded. Returns the stops and whether a request went out.
     */
    suspend fun loadStops(cache: StopsTileCache, view: BBox, now: Instant, fetch: suspend (BBox) -> List<Place>): Pair<List<Place>, Boolean> {
        val box = StopsViewport.fetchBox(view)
        if (cache.missing(StopTiles.tilesCovering(view), now).isEmpty()) return cache.stopsIn(StopTiles.tilesCovering(box)) to false
        return cache.load(box, now, fetch)
    }

    /** The stops whose board the night refresh saves: none in low-data mode. */
    fun nightStops(stops: List<String>, on: Boolean): List<String> = if (on) emptyList() else stops

    /** Ask before downloading the offline map area: only in low-data mode and off Wi-Fi. */
    fun askBeforeOfflineDownload(on: Boolean, wifi: Boolean): Boolean = on && !wifi
}
