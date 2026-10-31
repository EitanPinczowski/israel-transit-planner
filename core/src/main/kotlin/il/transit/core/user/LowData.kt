package il.transit.core.user

import il.transit.core.geo.BBox
import il.transit.core.geo.StopTiles
import il.transit.core.geo.StopsViewport

/**
 * Low-data mode (Phase 11 E4, owner decision 2): one switch, off by default
 * ([UserSettings.lowData]). It only removes traffic:
 *  - the stops layer loads from zoom [STOPS_MIN_ZOOM] instead of 15 and fetches only the tiles on
 *    screen, not the half-screen around them;
 *  - the night refresh saves no stop boards (trips only, still Wi-Fi + charging);
 *  - the offline map area download asks first unless the phone is on Wi-Fi;
 *  - planning, search, reminders and the rain note are unchanged.
 */
object LowData {
    const val STOPS_MIN_ZOOM = 16.0

    fun stopsMinZoom(on: Boolean): Double = if (on) STOPS_MIN_ZOOM else StopsViewport.MIN_ZOOM

    /** Whether the stops layer needs a load for [view]; the same rule as normal, from the higher zoom. */
    fun stopsNeedFetch(view: BBox, zoom: Double, loaded: BBox?, on: Boolean): Boolean =
        zoom >= stopsMinZoom(on) && StopsViewport.needsFetch(view, zoom, loaded)

    /**
     * The box the stops layer asks for. Normal: the view plus half a screen around it. Low data: the
     * whole tiles under the view only (a tile is what the cache holds, so this asks for no stop the
     * screen does not touch).
     */
    fun stopsFetchBox(view: BBox, on: Boolean): BBox =
        if (on) StopTiles.bboxOf(StopTiles.tilesCovering(view)) else StopsViewport.fetchBox(view)

    /** The stops whose board the night refresh saves: none in low-data mode. */
    fun nightStops(stops: List<String>, on: Boolean): List<String> = if (on) emptyList() else stops

    /** Ask before downloading the offline map area: only in low-data mode and off Wi-Fi. */
    fun askBeforeOfflineDownload(on: Boolean, wifi: Boolean): Boolean = on && !wifi
}
