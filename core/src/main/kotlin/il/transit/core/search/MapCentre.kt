package il.transit.core.search

import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon

/**
 * Where the user moved the map: what a generic search ("תחנה מרכזית") ranks from when there is no location fix (F10).
 * Only a move the user made counts: the map opens on a fixed place (Be'er Sheva), and the app moves it to a trip or a fix
 * by itself; ranking from those would read "where I am" from a place the user never chose (review, F10).
 */
class MapCentre {
    private var userMoved = false

    /** The centre the user moved the map to, in Israel; null until they move it. */
    var centre: LatLon? = null
        private set

    /** The user began moving the map (a finger: pan, zoom, rotate). */
    fun onUserMove() {
        userMoved = true
    }

    /** The map came to rest showing [view]: its centre counts only after a user move, and that move is then spent. */
    fun onIdle(view: BBox) {
        if (!userMoved) return
        userMoved = false
        centre = LatLon((view.min.lat + view.max.lat) / 2, (view.min.lon + view.max.lon) / 2).takeIf(PlaceSearch::inIsrael)
    }
}
