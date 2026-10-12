package il.transit.core.search

import il.transit.core.api.GeocodeMatch
import il.transit.core.api.Place
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon

/**
 * Search by the number on the stop's sign (Phase 10 D6): "47899", "תחנה 47899", "stop 47899".
 *
 * Transitous's `geocode` does not index stop codes (2026-10-12, `geocode_stopcode_13907*`: "13907",
 * Be'er Sheva central's intercity platforms, answers names containing "1390" in Iraq, Spain and
 * Australia, with `type=STOP` too), so the code is looked up on the phone, 0 requests: in the stops
 * the map has already loaded (the stops tile cache, which also holds My lines' stops once the map has
 * shown them). Not there: "Stop 47899 is not in this area yet: open the map near it".
 */
object StopCode {
    /** Marks a [GeocodeMatch] made from a stop-code hit: its `id` is the stop id, and a tap opens the stop sheet. */
    const val CATEGORY = "stop_code"

    private val CODE = Regex("""^(?:(?:תחנה|תחנת|stop|stop\s*(?:no\.?|number|#))\s*:?\s*)?#?(\d{3,6})$""", RegexOption.IGNORE_CASE)

    /**
     * The stop code [query] is, or null: 3–6 digits alone, or after "תחנה" / "stop". "15" (too short)
     * and "הרצל 15" (a house on a street) are not.
     */
    fun of(query: String): String? = CODE.find(query.trim().replace(Regex("\\s+"), " "))?.groupValues?.get(1)

    /**
     * The stops among [known] whose sign says [code] (leading zeros aside). A station and its
     * platforms share one code: one row per name, the parent first, then the nearest to [near].
     */
    fun find(code: String, known: List<Place>, near: LatLon?): List<Place> {
        val want = code.trimStart('0')
        return known.filter { it.stopId != null && it.stopCode?.trim()?.trimStart('0') == want }
            .distinctBy { it.stopId }
            .sortedWith(compareBy<Place> { it.parentId != null }.thenBy { p -> near?.let { Geo.distanceM(it, p.latLon) } ?: 0.0 })
            .distinctBy { PlaceSearch.normalize(it.name) }
    }

    /** A hit as a search answer: a STOP whose `id` is the stop id ([CATEGORY]). */
    fun asMatch(p: Place): GeocodeMatch =
        GeocodeMatch(type = "STOP", name = p.name, id = p.stopId.orEmpty(), lat = p.lat, lon = p.lon, modes = p.modes, category = CATEGORY)
}
