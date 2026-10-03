package il.transit.core.api

import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import kotlinx.serialization.Serializable
import java.time.Instant

// Phase 8 models: service alerts, one vehicle's whole trip (`/api/v6/trip` answers with an
// [Itinerary]) and the moving vehicles of `/api/v6/map/trips`. Recorded 2026-10-03; what was
// learned is pinned in ApiPhase8Test and written in the transitous-api skill.

/** A GTFS-RT service alert as MOTIS passes it on. No Israeli answer carried one (2026-10-03). */
@Serializable
data class Alert(
    val headerText: String = "",
    val descriptionText: String = "",
    /** GTFS-RT cause, e.g. `CONSTRUCTION`, `STRIKE`, `UNKNOWN_CAUSE`. */
    val cause: String? = null,
    /** GTFS-RT effect, e.g. `NO_SERVICE`, `DETOUR`, `SIGNIFICANT_DELAYS`. */
    val effect: String? = null,
    val url: String? = null,
    val severityLevel: String? = null,
    /** When the alert should be shown. */
    val communicationPeriod: List<TimeRange> = emptyList(),
    /** When the disruption is in effect — the alert's validity. Empty = always. */
    val impactPeriod: List<TimeRange> = emptyList(),
) {
    /** True when [at] falls in one of the impact periods, or the alert has none. */
    fun inEffectAt(at: Instant): Boolean = impactPeriod.isEmpty() || impactPeriod.any { it.contains(at) }
}

/** An interval; a missing end means open-ended. */
@Serializable
data class TimeRange(val start: String? = null, val end: String? = null) {
    fun contains(at: Instant): Boolean =
        (start == null || !at.isBefore(parseTime(start))) && (end == null || at.isBefore(parseTime(end)))
}

/** One trip running over a [TripSegment]. */
@Serializable
data class TripRef(val tripId: String, val displayName: String = "")

/**
 * One stop-to-stop hop of a vehicle, from `GET /api/v6/map/trips`: the vehicle is between
 * [from] and [to] somewhere in [departure, arrival]. The server answers every hop that
 * overlaps the time window and crosses the box, so a long train shows up as several hops —
 * and a foreign trip whose straight-line hop happens to cross Israel can appear too (a Rome →
 * Naples train with a bad stop at 10.1,40.1 did). Select by [tripId], don't trust the box.
 */
@Serializable
data class TripSegment(
    val trips: List<TripRef>,
    val mode: String,
    val from: Place,
    val to: Place,
    val departure: String,
    val arrival: String,
    val scheduledDeparture: String? = null,
    val scheduledArrival: String? = null,
    val realTime: Boolean = false,
    /** Not this hop's length (a 7-min hop said 94 km, Rome → Naples 4,900 km); unused. */
    val distance: Double? = null,
    /** Google polyline at precision [POLYLINE_PRECISION] — NOT 6 like leg geometry. */
    val polyline: String = "",
) {
    val tripId: String get() = trips.firstOrNull()?.tripId.orEmpty()

    /** Line number for buses ("470"). Israel Railways sends the long "A<->B" name here, not the
     *  train number (that is the `trip` leg's headsign). */
    val routeName: String get() = trips.firstOrNull()?.displayName.orEmpty()
    val depart: Instant get() = parseTime(departure)
    val arrive: Instant get() = parseTime(arrival)

    fun path(): List<LatLon> = Geo.decodePolyline(polyline, POLYLINE_PRECISION)

    companion object {
        /** `map/trips` encodes at precision 5 (openapi: "with precision 5"); decoding with 6
         *  puts Be'er Sheva at 3.1,3.5. Recorded 2026-10-03. */
        const val POLYLINE_PRECISION = 5
    }
}
