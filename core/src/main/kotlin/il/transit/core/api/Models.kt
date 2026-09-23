package il.transit.core.api

import il.transit.core.geo.LatLon
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime

// Subset of the MOTIS v6 API schema (https://github.com/motis-project/motis/blob/master/openapi.yaml).
// Only the fields the app uses are declared; `ignoreUnknownKeys` keeps us working as
// Transitous adds fields. Everything MOTIS marks optional is nullable or defaulted here.

val MotisJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

@Serializable
data class Place(
    val name: String,
    val lat: Double,
    val lon: Double,
    val stopId: String? = null,
    val parentId: String? = null,
    val importance: Double? = null,
    val arrival: String? = null,
    val departure: String? = null,
    val scheduledArrival: String? = null,
    val scheduledDeparture: String? = null,
    val track: String? = null,
    val modes: List<String>? = null,
) {
    val latLon: LatLon get() = LatLon(lat, lon)
}

@Serializable
data class EncodedPolyline(val points: String, val precision: Int, val length: Int = 0)

@Serializable
data class Leg(
    val mode: String,
    val from: Place,
    val to: Place,
    val duration: Int,
    val startTime: String,
    val endTime: String,
    val scheduledStartTime: String? = null,
    val scheduledEndTime: String? = null,
    val realTime: Boolean = false,
    val distance: Double? = null,
    val headsign: String? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val displayName: String? = null,
    val routeColor: String? = null,
    val agencyName: String? = null,
    val tripId: String? = null,
    val cancelled: Boolean = false,
    val intermediateStops: List<Place> = emptyList(),
    val legGeometry: EncodedPolyline? = null,
) {
    val start: Instant get() = parseTime(startTime)
    val end: Instant get() = parseTime(endTime)
    val isTransit: Boolean get() = mode !in StreetModes.ALL
}

@Serializable
data class Itinerary(
    val duration: Int,
    val startTime: String,
    val endTime: String,
    val transfers: Int,
    val legs: List<Leg>,
    val id: String? = null,
) {
    val start: Instant get() = parseTime(startTime)
    val end: Instant get() = parseTime(endTime)
    val firstTransitLeg: Leg? get() = legs.firstOrNull { it.isTransit }
    val lastTransitLeg: Leg? get() = legs.lastOrNull { it.isTransit }
}

@Serializable
data class PlanResponse(
    val itineraries: List<Itinerary> = emptyList(),
    val direct: List<Itinerary> = emptyList(),
)

/** One entry of a one-to-many answer. `duration` is absent when no path was found. */
@Serializable
data class DurationEntry(val duration: Double? = null, val distance: Double? = null)

@Serializable
data class GeocodeMatch(
    val type: String,
    val name: String,
    val id: String,
    val lat: Double,
    val lon: Double,
    val score: Double = 0.0,
    val street: String? = null,
    val houseNumber: String? = null,
    val modes: List<String>? = null,
)

@Serializable
data class StopTime(
    val place: Place,
    val mode: String,
    val realTime: Boolean = false,
    val headsign: String = "",
    val routeShortName: String = "",
    val displayName: String? = null,
    val cancelled: Boolean = false,
    val tripCancelled: Boolean = false,
)

@Serializable
data class StopTimesResponse(val stopTimes: List<StopTime> = emptyList(), val place: Place? = null)

object StreetModes {
    const val WALK = "WALK"
    const val BIKE = "BIKE"
    const val CAR = "CAR"
    const val CAR_PARKING = "CAR_PARKING"
    const val CAR_DROPOFF = "CAR_DROPOFF"
    val ALL = setOf(WALK, BIKE, CAR, CAR_PARKING, CAR_DROPOFF, "RENTAL", "HGV", "FLEX", "ODM", "RIDE_SHARING")
    val CAR_LIKE = setOf(CAR, CAR_PARKING, CAR_DROPOFF)
}

object TransitModes {
    /** Stop modes worth a drop-off detour on a long drive: trains and light rail. */
    val RAIL_LIKE = setOf(
        "RAIL", "HIGHSPEED_RAIL", "LONG_DISTANCE", "REGIONAL_RAIL", "SUBURBAN", "SUBWAY", "TRAM", "METRO",
    )
}

fun parseTime(s: String): Instant = OffsetDateTime.parse(s).toInstant()
