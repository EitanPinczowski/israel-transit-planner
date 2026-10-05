package il.transit.core.present

import il.transit.core.api.Alert
import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.api.Place
import il.transit.core.api.TransitApi
import il.transit.core.api.TripSegment
import il.transit.core.api.parseTime
import il.transit.core.geo.BBox
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import il.transit.core.geo.MapData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Duration
import java.time.Instant

/**
 * The trip-details sheet (Phase 8 B1): every stop of the vehicle behind one leg of the
 * selected itinerary, and where the timetable (or, rarely, real-time data) puts that vehicle.
 *
 * Transitous has no real-time and no alerts for Israeli MOT lines (only busofash, 2026-10-04),
 * so live times, ⚠ alerts and a "live" position appear only when an answer carries them; the
 * UI never draws an empty alert area or a "live" label without them.
 */
enum class StopRole { BEFORE, BOARD, RIDE, ALIGHT, AFTER }

data class TripStopRow(
    val name: String,
    val stopId: String?,
    val at: LatLon,
    /** Scheduled HH:mm — departure, or arrival at the last stop. */
    val scheduled: String,
    /** Live HH:mm, only when the answer is real-time and it differs from [scheduled]. */
    val live: String?,
    /** Minutes late (negative = early), only with real-time data. */
    val delayMin: Int?,
    /** The vehicle skips this stop: struck through. */
    val cancelled: Boolean,
    val role: StopRole,
    /** The vehicle has left this stop (by its live time when it has one). */
    val passed: Boolean,
    val isNext: Boolean,
    /** Alerts on this stop, in effect at the time it is served. */
    val alerts: List<AlertText>,
    /** When the vehicle is (expected) here. */
    val time: Instant?,
)

/** What the UI shows of an [Alert]: duplicates by text are folded. */
data class AlertText(val header: String, val description: String, val url: String? = null)

data class TripDetails(
    val tripId: String?,
    val line: String?,
    val kind: LegKind,
    val headsign: String,
    val color: String,
    val rows: List<TripStopRow>,
    /** Trip-wide alerts (on the leg), in effect now. Empty → no alert area at all. */
    val alerts: List<AlertText>,
    /** Any time in the answer is real-time. False on every MOT line as of 2026-10. */
    val realTime: Boolean,
    val tripCancelled: Boolean,
    /** Index into [rows] of the next stop the vehicle reaches, or null once it has arrived. */
    val nextIndex: Int?,
    /** The whole trip's shape, for the map; empty when the answer had none. */
    val path: List<LatLon>,
)

/** Alerts worth showing at [at]: in effect, with some text, folded by text. */
fun alertTexts(alerts: List<Alert>, at: Instant): List<AlertText> =
    alerts.filter { it.inEffectAt(at) && (it.headerText.isNotBlank() || it.descriptionText.isNotBlank()) }
        .map { AlertText(it.headerText.ifBlank { it.descriptionText }, if (it.headerText.isBlank()) "" else it.descriptionText, it.url) }
        .distinct()

/** Every alert a leg carries (the leg, both ends, the stops between) in effect at [at]. */
fun legAlerts(leg: Leg, at: Instant): List<AlertText> =
    alertTexts(leg.alerts + leg.from.alerts + leg.to.alerts + leg.intermediateStops.flatMap { it.alerts }, at)

/**
 * Builds the sheet from the `trip` answer [trip] for the user's [leg]. Without an answer
 * (offline, an error) pass `trip = null`: the leg's own stops are shown, boarding to alighting.
 */
fun tripDetails(leg: Leg, trip: Itinerary?, now: Instant): TripDetails {
    val tripLeg = trip?.legs?.firstOrNull { it.isTransit } ?: leg
    val stops = listOf(tripLeg.from) + tripLeg.intermediateStops + listOf(tripLeg.to)
    val board = matchStop(stops, leg.from, leg.start, preferFirst = true)
    val alight = matchStop(stops, leg.to, leg.end, preferFirst = false)?.takeIf { board == null || it > board }
    val lastIdx = stops.lastIndex
    val times = stops.mapIndexed { i, p -> stopTime(p, last = i == lastIdx) }
    val realTime = tripLeg.realTime || stops.any { it.isLive() }
    val next = times.indices.firstOrNull { i -> !stops[i].cancelled && times[i].live?.let { !it.isBefore(now) } == true }
    val rows = stops.mapIndexed { i, p ->
        val t = times[i]
        val role = when {
            board == null || alight == null -> StopRole.RIDE
            i < board -> StopRole.BEFORE
            i == board -> StopRole.BOARD
            i < alight -> StopRole.RIDE
            i == alight -> StopRole.ALIGHT
            else -> StopRole.AFTER
        }
        val delay = if (realTime) delayMin(t.liveStr, t.scheduledStr) else null
        TripStopRow(
            name = p.name,
            stopId = p.stopId,
            at = p.latLon,
            scheduled = t.scheduled?.let(::hhmm) ?: t.live?.let(::hhmm).orEmpty(),
            live = if (realTime && t.live != null && t.live != t.scheduled) hhmm(t.live) else null,
            delayMin = delay,
            cancelled = p.cancelled,
            role = role,
            passed = next?.let { i < it } ?: true,
            isNext = i == next,
            alerts = alertTexts(p.alerts, t.live ?: now),
            time = t.live,
        )
    }
    return TripDetails(
        tripId = tripLeg.tripId ?: leg.tripId,
        line = lineLabel(tripLeg) ?: lineLabel(leg),
        kind = legKind(tripLeg.mode),
        headsign = headsignText(tripLeg.headsign ?: leg.headsign, tripLeg.tripTo ?: leg.tripTo ?: tripLeg.to),
        color = legColor(tripLeg),
        rows = rows,
        alerts = alertTexts(tripLeg.alerts + if (tripLeg !== leg) leg.alerts else emptyList(), now),
        realTime = realTime,
        tripCancelled = tripLeg.cancelled,
        nextIndex = next,
        path = tripLeg.legGeometry?.let { Geo.decodePolyline(it.points, it.precision) }.orEmpty(),
    )
}

private class StopTimes(val live: Instant?, val scheduled: Instant?, val liveStr: String?, val scheduledStr: String?)

private fun stopTime(p: Place, last: Boolean): StopTimes {
    val live = if (last) p.arrival ?: p.departure else p.departure ?: p.arrival
    val sched = if (last) p.scheduledArrival ?: p.scheduledDeparture else p.scheduledDeparture ?: p.scheduledArrival
    fun parse(s: String?) = s?.let { runCatching { parseTime(it) }.getOrNull() }
    return StopTimes(parse(live) ?: parse(sched), parse(sched) ?: parse(live), live, sched)
}

private fun Place.isLive(): Boolean =
    (departure != null && scheduledDeparture != null && departure != scheduledDeparture) ||
        (arrival != null && scheduledArrival != null && arrival != scheduledArrival)

/**
 * Index of [target] in [stops]: by stop id, else the nearest stop within [MATCH_RADIUS_M]
 * (a plan may name a sibling platform). A trip that loops passes a stop twice; the visit
 * closest in time to [at] wins.
 */
private fun matchStop(stops: List<Place>, target: Place, at: Instant, preferFirst: Boolean): Int? {
    val byId = stops.indices.filter { target.stopId != null && stops[it].stopId == target.stopId }
    val candidates = byId.ifEmpty {
        stops.indices.filter { Geo.distanceM(stops[it].latLon, target.latLon) <= MATCH_RADIUS_M }
    }
    if (candidates.isEmpty()) return null
    return candidates.minWith(
        compareBy<Int> { i ->
            stopTime(stops[i], last = i == stops.lastIndex).scheduled?.let { Duration.between(it, at).abs().seconds } ?: Long.MAX_VALUE
        }.thenBy { if (preferFirst) it else -it },
    )
}

private const val MATCH_RADIUS_M = 150.0

/** Where the vehicle is on the map, and whether that comes from real-time data. */
data class VehicleMark(
    val at: LatLon,
    /** False: the timetable position ("scheduled position" in the UI). */
    val realTime: Boolean,
    /** Stopped at a stop (between arriving and leaving), rather than moving. */
    val atStop: Boolean,
)

/**
 * Where [tripId]'s vehicle is at [now], from the `map/trips` hops [segments]. Hops of other
 * trips are ignored (a foreign hop can cross the box). Moving: interpolated by time along
 * the hop's polyline, so the mark glides between refreshes. Between two hops: at the stop.
 * Null when no hop of the trip covers [now].
 */
fun vehicleAt(segments: List<TripSegment>, tripId: String, now: Instant): VehicleMark? {
    val mine = segments.filter { it.tripId == tripId }.sortedBy { it.depart }
    if (mine.isEmpty()) return null
    mine.firstOrNull { !now.isBefore(it.depart) && !now.isAfter(it.arrive) }?.let { s ->
        val span = Duration.between(s.depart, s.arrive).toMillis()
        val f = if (span <= 0) 1.0 else Duration.between(s.depart, now).toMillis().toDouble() / span
        val path = s.path().ifEmpty { listOf(s.from.latLon, s.to.latLon) }
        return VehicleMark(pointAlong(path, f), s.realTime, atStop = false)
    }
    // Dwelling: arrived at a hop's end, the next hop leaves later.
    mine.zipWithNext().firstOrNull { (a, b) -> !now.isBefore(a.arrive) && now.isBefore(b.depart) }?.let { (a, _) ->
        return VehicleMark(a.to.latLon, a.realTime, atStop = true)
    }
    // Waiting at the first stop of the window (a hop that hasn't left yet).
    mine.first().takeIf { now.isBefore(it.depart) && Duration.between(now, it.depart) <= DWELL_MAX }?.let { s ->
        return VehicleMark(s.from.latLon, s.realTime, atStop = true)
    }
    return null
}

private val DWELL_MAX: Duration = Duration.ofMinutes(2)

/** The point [fraction] (0..1) of the way along [path], by length. */
fun pointAlong(path: List<LatLon>, fraction: Double): LatLon {
    require(path.isNotEmpty()) { "empty path" }
    if (path.size == 1) return path[0]
    val f = fraction.coerceIn(0.0, 1.0)
    val total = Geo.lengthM(path)
    if (total == 0.0) return path[0]
    var left = total * f
    for (i in 0 until path.size - 1) {
        val seg = Geo.distanceM(path[i], path[i + 1])
        if (left <= seg && seg > 0) {
            val t = left / seg
            return LatLon(path[i].lat + (path[i + 1].lat - path[i].lat) * t, path[i].lon + (path[i + 1].lon - path[i].lon) * t)
        }
        left -= seg
    }
    return path.last()
}

/**
 * The box to ask `map/trips` for at [now]: the stretch of the trip between the last stop
 * served and the next one, padded by [padM]. Not the user's leg alone: the user usually opens
 * the sheet while waiting, when the vehicle is still before the boarding stop. Null when the
 * timetable puts the vehicle nowhere (not left yet by more than [lead], or arrived): then
 * nothing is asked.
 */
fun vehicleBox(details: TripDetails, now: Instant, lead: Duration = Duration.ofMinutes(2), padM: Double = 1_000.0): BBox? {
    val timed = details.rows.filter { !it.cancelled && it.time != null }
    if (timed.isEmpty()) return null
    val first = timed.first().time!!
    val last = timed.last().time!!
    if (now.isBefore(first.minus(lead)) || now.isAfter(last)) return null
    val nextI = timed.indexOfFirst { !it.time!!.isBefore(now) }.let { if (it < 0) timed.lastIndex else it }
    val prevI = (nextI - 1).coerceAtLeast(0)
    val a = timed[prevI].at
    val b = timed[nextI].at
    // The shape between the two stops can bow away from the straight line; include it.
    val shape = details.path.filter { p -> Geo.distanceM(p, a) + Geo.distanceM(p, b) <= Geo.distanceM(a, b) * 1.5 + 200 }
    return Geo.bbox(listOf(a, b) + shape, padM)
}

/**
 * What the open sheet asks Transitous, and how often — the request pattern pinned by
 * `TripDetailsTest`: one `trip` per [open] (the guard caches it 30 s), and at most one
 * `map/trips` per [REFRESH] from [tick], only while [visible], only while the timetable puts
 * the vehicle on the road. Between refreshes [tick] just moves the mark along the polyline.
 */
class TripDetailsSession(
    private val api: TransitApi,
    val leg: Leg,
    private val language: String = "he",
) {
    var details: TripDetails? = null
        private set

    /** No `trip` answer (no trip id, offline, an error): only the leg's own stops are known. */
    var legOnly: Boolean = false
        private set

    /** Hops of the last refresh, kept to interpolate between refreshes. */
    private var segments: List<TripSegment> = emptyList()
    private var lastRefresh: Instant? = null

    /** One `trip` request. On failure the sheet falls back to the leg's own stops. */
    suspend fun open(now: Instant): TripDetails {
        val trip = leg.tripId?.let { id -> orNull { api.trip(id, language) } }
        legOnly = trip == null
        return tripDetails(leg, trip, now).also { details = it }
    }

    /** Re-derives rows for [now] (next stop, passed) without asking anything. */
    fun at(now: Instant): TripDetails? = details?.let { d ->
        val next = d.rows.indices.firstOrNull { i -> !d.rows[i].cancelled && d.rows[i].time?.let { !it.isBefore(now) } == true }
        d.copy(nextIndex = next, rows = d.rows.mapIndexed { i, r -> r.copy(isNext = i == next, passed = next?.let { i < it } ?: true) })
    }

    /**
     * Call every few seconds while the sheet is open. Returns the vehicle mark for [now], or
     * null when it is unknown. Asks `map/trips` only when [visible] and [REFRESH] has passed.
     */
    suspend fun tick(now: Instant, visible: Boolean): VehicleMark? {
        val d = details ?: return null
        val id = d.tripId ?: return null
        val due = lastRefresh?.let { Duration.between(it, now) >= REFRESH } ?: true
        if (visible && due) {
            val box = vehicleBox(d, now)
            if (box != null) {
                lastRefresh = now
                segments = orNull { api.mapTrips(box, now, now.plus(WINDOW), ZOOM, language) } ?: segments
            }
        }
        return vehicleAt(segments, id, now)
    }

    /** A failed call is a missing answer, but cancellation (sheet closed) must propagate. */
    private suspend fun <T> orNull(call: suspend () -> T): T? = try {
        call()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        val REFRESH: Duration = Duration.ofSeconds(30)

        /** Covers the next refresh, so the mark keeps moving until the next answer. */
        val WINDOW: Duration = Duration.ofSeconds(60)

        /** As recorded (2026-10-03, zoom 14); MOTIS thins out vehicles at low zoom. */
        const val ZOOM = 14.0
    }
}

/** GeoJSON for the map's vehicle layer: one Point with `realTime` and `color`, or empty. */
fun vehicleGeoJson(mark: VehicleMark?, color: String): String {
    if (mark == null) return MapData.EMPTY
    val point = buildJsonObject {
        put("type", JsonPrimitive("Feature"))
        put("geometry", buildJsonObject {
            put("type", JsonPrimitive("Point"))
            put("coordinates", JsonArray(listOf(mark.at.lon, mark.at.lat).map { JsonPrimitive(it) }))
        })
        put("properties", buildJsonObject {
            put("realTime", JsonPrimitive(mark.realTime))
            put("color", JsonPrimitive(color))
        })
    }
    return """{"type":"FeatureCollection","features":[$point]}"""
}
