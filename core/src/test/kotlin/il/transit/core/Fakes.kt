package il.transit.core

import il.transit.core.api.EncodedPolyline
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.api.Place
import il.transit.core.api.PlanRequest
import il.transit.core.api.PlanResponse
import il.transit.core.api.StopTimesResponse
import il.transit.core.api.TransitApi
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import java.time.Instant
import java.util.Collections
import kotlin.math.roundToLong

/** Wednesday 2026-09-23 12:00 Israel time (UTC+3): off-peak, factor 1.0. */
val NOON: Instant = Instant.parse("2026-09-23T09:00:00Z")

/** Sunday 2026-09-20 08:00 Israel time: morning peak, factor 1.3. */
val SUNDAY_8AM: Instant = Instant.parse("2026-09-20T05:00:00Z")

fun place(name: String, at: LatLon, stopId: String? = null, modes: List<String>? = null, importance: Double? = null) =
    Place(name = name, lat = at.lat, lon = at.lon, stopId = stopId, modes = modes, importance = importance)

fun leg(mode: String, from: Place, to: Place, start: Instant, end: Instant, geometry: EncodedPolyline? = null) = Leg(
    mode = mode,
    from = from,
    to = to,
    duration = (end.epochSecond - start.epochSecond).toInt(),
    startTime = start.toString(),
    endTime = end.toString(),
    legGeometry = geometry,
)

fun itinerary(vararg legs: Leg, transfers: Int = 0) = Itinerary(
    duration = (legs.last().end.epochSecond - legs.first().start.epochSecond).toInt(),
    startTime = legs.first().startTime,
    endTime = legs.last().endTime,
    transfers = transfers,
    legs = legs.toList(),
)

/** Encodes a polyline the way MOTIS does, so tests exercise the real decoder. */
fun encodePolyline(points: List<LatLon>, precision: Int = 6): EncodedPolyline {
    val factor = Math.pow(10.0, precision.toDouble())
    val sb = StringBuilder()
    var pLat = 0L
    var pLon = 0L
    fun enc(v: Long) {
        var x = if (v < 0) (v shl 1).inv() else v shl 1
        while (x >= 0x20) {
            sb.append(((0x20 or (x and 0x1f).toInt()) + 63).toChar())
            x = x shr 5
        }
        sb.append((x + 63).toInt().toChar())
    }
    for (p in points) {
        val lat = (p.lat * factor).roundToLong()
        val lon = (p.lon * factor).roundToLong()
        enc(lat - pLat)
        enc(lon - pLon)
        pLat = lat
        pLon = lon
    }
    return EncodedPolyline(sb.toString(), precision, points.size)
}

/** A scriptable [TransitApi] that records every call. Unscripted calls fail loudly. */
class FakeTransitApi : TransitApi {
    val calls: MutableList<String> = Collections.synchronizedList(ArrayList())
    val planRequests: MutableList<PlanRequest> = Collections.synchronizedList(ArrayList())

    var onPlan: (PlanRequest) -> PlanResponse = { error("unscripted plan: $it") }
    var onOneToMany: (one: LatLon, many: List<LatLon>, arriveBy: Boolean) -> List<Int?> =
        { _, _, _ -> error("unscripted one-to-many") }
    var onStops: (BBox, Set<String>?) -> List<Place> = { _, _ -> error("unscripted stops") }

    override suspend fun plan(req: PlanRequest): PlanResponse {
        calls += "plan"
        planRequests += req
        return onPlan(req)
    }

    override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean): List<Int?> {
        calls += "oneToMany"
        return onOneToMany(one, many, arriveBy)
    }

    override suspend fun stops(box: BBox, modes: Set<String>?, language: String): List<Place> {
        calls += "stops"
        return onStops(box, modes)
    }

    override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
        calls += "geocode"
        return onGeocode(text)
    }

    var onGeocode: (String) -> List<GeocodeMatch> = { emptyList() }

    override suspend fun reverseGeocode(at: LatLon, language: String, max: Int): List<GeocodeMatch> {
        calls += "reverseGeocode"
        return emptyList()
    }

    override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String): StopTimesResponse {
        calls += "stopTimes"
        return StopTimesResponse()
    }
}
