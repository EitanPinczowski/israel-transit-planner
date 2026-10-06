package il.transit.core.plan

import il.transit.core.api.Itinerary
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import java.time.Instant

/**
 * Reads the Trip cache with no request: the Quick Settings tile (Phase 9 C4) shows the next
 * option home from it, and a tap with no location fix plans from the cached origin.
 */
object CacheLookup {
    /** A cached destination this close to Home is "home". Keys round places to ~110 m. */
    const val HOME_RADIUS_M = 110.0

    /** The two places of a Trip-tab key ([PlanCache.key] "TRIP-<time mode>", from, to). */
    data class Ends(val from: LatLon, val to: LatLon)

    /** A Trip entry bound for Home; [next] is its first option still ahead, if any. */
    data class TripHome(val entry: PlanCache.Entry<TripResult>, val from: LatLon, val next: Itinerary?)

    /** The places of a Trip key; null for any other key (last ride, a broken key). */
    fun ends(key: String): Ends? {
        if (!key.startsWith("TRIP-")) return null
        val places = key.substringAfter(':', "").split('|')
        if (places.size != 2) return null
        val (from, to) = places.map { p ->
            val ll = p.split(',')
            val lat = ll.getOrNull(0)?.toDoubleOrNull()
            val lon = ll.getOrNull(1)?.toDoubleOrNull()
            if (ll.size != 2 || lat == null || lon == null) return null
            LatLon(lat, lon)
        }
        return Ends(from, to)
    }

    /**
     * The newest cached Trip entry to [home] that still has an option ahead at [now]; with
     * none, the newest entry to [home] anyway (its origin still helps), with [TripHome.next]
     * null. Null when nothing cached goes home.
     */
    fun tripHome(entries: List<PlanCache.Entry<TripResult>>, home: LatLon, now: Instant): TripHome? {
        val toHome = entries
            .mapNotNull { e -> ends(e.key)?.takeIf { Geo.distanceM(it.to, home) <= HOME_RADIUS_M }?.let { e to it } }
            .sortedByDescending { it.first.savedAt }
        for ((e, ends) in toHome) {
            val ahead = e.value.stillAhead(now) ?: continue
            val next = ahead.itineraries.firstOrNull() ?: continue
            return TripHome(e.copy(value = ahead), ends.from, next)
        }
        return toHome.firstOrNull()?.let { (e, ends) -> TripHome(e, ends.from, null) }
    }
}
