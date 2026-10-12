package il.transit.core.plan

import il.transit.core.api.BudgetedTransitApi
import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.api.Place
import il.transit.core.api.StreetModes
import il.transit.core.api.TransitApi
import il.transit.core.api.TransitModes
import il.transit.core.api.parseTime
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import il.transit.core.geo.RailStations
import il.transit.core.present.Transfer
import il.transit.core.present.parseStopDescription
import il.transit.core.present.transfers
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * "Safer change" (Phase 10 D5, item 27): the option changes lines at a small street stop, and
 * both vehicles also pass a big stop; change there instead. **The same two vehicles**, so the
 * departure and the arrival are the ones of the option, and the trip is never longer.
 */
data class BigStopChange(
    /** The option as planned. */
    val original: Itinerary,
    /** The same option with the two legs re-cut at [stop]. Same start, same end. */
    val changed: Itinerary,
    /** The change it replaces (the one with the least slack). */
    val transfer: Transfer,
    /** Where to change instead. */
    val stop: String,
    /** Leg indices in [original] (and [changed]) of the vehicle left and the vehicle boarded. */
    val fromLeg: Int,
    val toLeg: Int,
    /** Stops more on the first vehicle (positive: stay on; negative: get off that many earlier). */
    val extraStops: Int,
    /** Whole minutes to change at [stop], and at the original stop. */
    val slackMin: Int,
    val originalSlackMin: Int,
)

/**
 * Finds the safer change for the option the user opened. 0 requests for an option without a
 * change, or whose tightest change is already at a big stop; otherwise one `trip` for each of
 * the two vehicles around that change ([BUDGET], cached by the guard: the trip sheet asks the
 * same two). The search itself ([pick]) and the re-cut are pure.
 */
object BigStopChanges {
    /** `trip` for the vehicle before the change and the one after it. */
    const val BUDGET = 2

    /** A candidate must leave at least this much more time than the original change. */
    const val MORE_SLACK_SEC = 180L

    /** Stops matched by distance: within this, with the same name. */
    const val MATCH_M = 150.0

    /** Walking pace for the walk between two platforms (straight line; slow, so slack is not overstated). */
    const val WALK_MPS = 1.0

    /**
     * The change to look at, or null when nothing needs a request: no change, a vehicle without
     * a trip id, or the tightest change is already at a big stop.
     */
    fun toCheck(itin: Itinerary): Transfer? {
        val t = transfers(itin).minByOrNull { it.slackSec } ?: return null
        val a = itin.legs[t.fromLeg]
        val b = itin.legs[t.toLeg]
        if (a.tripId.isNullOrBlank() || b.tripId.isNullOrBlank()) return null
        if (isBig(a.to, a.mode) || isBig(b.from, b.mode)) return null
        return t
    }

    /**
     * The safer change for [itin], or null. Network errors give null (the card just shows no
     * suggestion); cancellation propagates.
     */
    suspend fun find(itin: Itinerary, api: TransitApi, bufferMin: Int?, language: String = "he"): BigStopChange? {
        val t = toCheck(itin) ?: return null
        val budgeted = BudgetedTransitApi(api, BUDGET)
        val (tripA, tripB) = try {
            budgeted.trip(itin.legs[t.fromLeg].tripId!!, language) to budgeted.trip(itin.legs[t.toLeg].tripId!!, language)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return pick(itin, t, tripA, tripB, bufferMin)
    }

    /**
     * The best candidate from the two `trip` answers: a stop the first vehicle reaches (after
     * the user boards it) and the second leaves from (before the user gets off it), matched by
     * stop id, parent station, or the same name within [MATCH_M]. Kept only if it is big, the
     * first vehicle gets there before the second leaves with ≥ the original slack +
     * [MORE_SLACK_SEC] and ≥ the user's buffer, and the walk is no longer than the original's.
     * The most slack wins (then the fewest extra stops). A loop line that passes a stop twice
     * is matched by index and time, so a visit on the wrong lap never qualifies.
     */
    fun pick(itin: Itinerary, t: Transfer, tripA: Itinerary, tripB: Itinerary, bufferMin: Int?): BigStopChange? {
        val legA = itin.legs[t.fromLeg]
        val legB = itin.legs[t.toLeg]
        val a = tripStops(tripA) ?: return null
        val b = tripStops(tripB) ?: return null
        val boardA = matchStop(a.stops, legA.from, legA.start) ?: return null
        val alightA = matchStop(a.stops, legA.to, legA.end, after = boardA) ?: return null
        val boardB = matchStop(b.stops, legB.from, legB.start) ?: return null
        val alightB = matchStop(b.stops, legB.to, legB.end, after = boardB) ?: return null
        val originalWalkM = if (legA.to.stopId != null && legA.to.stopId == legB.from.stopId) 0.0 else Geo.distanceM(legA.to.latLon, legB.from.latLon)
        val minSlack = maxOf(t.slackSec + MORE_SLACK_SEC, (bufferMin ?: 0) * 60L)

        class Cand(val j: Int, val k: Int, val slack: Long, val walkM: Double)
        val cands = ArrayList<Cand>()
        for (j in boardA + 1..a.stops.lastIndex) {
            val p = a.stops[j]
            if (p.cancelled) continue
            val arrive = arrival(p) ?: continue
            for (k in 0 until alightB) {
                if (j == alightA && k == boardB) continue
                val q = b.stops[k]
                if (q.cancelled || !sameStop(p, q)) continue
                if (!isBig(p, a.mode) && !isBig(q, b.mode)) continue
                val leave = departure(q) ?: continue
                // The user boards the first vehicle before this stop, and is on the second one
                // before the final stop (index order), and in time order too (a loop's other lap).
                if (arrive.isBefore(legA.start) || leave.isAfter(legB.end)) continue
                val walkM = if (p.stopId != null && p.stopId == q.stopId) 0.0 else Geo.distanceM(p.latLon, q.latLon)
                if (walkM > originalWalkM) continue
                val slack = Duration.between(arrive, leave).seconds - walkSec(walkM)
                if (slack < minSlack) continue
                cands += Cand(j, k, slack, walkM)
            }
        }
        val best = cands.maxWithOrNull(compareBy<Cand> { it.slack }.thenBy { -kotlin.math.abs(it.j - alightA) }) ?: return null
        val changed = recut(itin, t, tripA, a, boardA, best.j, tripB, b, best.k, alightB, best.walkM) ?: return null
        return BigStopChange(
            original = itin,
            changed = changed,
            transfer = t,
            stop = a.stops[best.j].name,
            fromLeg = t.fromLeg,
            toLeg = changed.legs.indexOfLast { it.isTransit && it.tripId == legB.tripId }.takeIf { it >= 0 } ?: t.toLeg,
            extraStops = best.j - alightA,
            slackMin = Math.floorDiv(best.slack, 60L).toInt(),
            originalSlackMin = t.slackMin,
        )
    }

    /**
     * A big stop (owner decision 3): a rail or light-rail station (the bundled list, the stop's
     * modes, or the vehicle is a train), a central station or terminal by name, or a stop whose
     * description gives a platform.
     */
    fun isBig(p: Place, vehicleMode: String? = null): Boolean {
        if (vehicleMode != null && vehicleMode in TransitModes.RAIL_LIKE) return true
        if (p.modes.orEmpty().any { it in TransitModes.RAIL_LIKE }) return true
        if (railIds.contains(p.stopId) || (p.parentId != null && railIds.contains(p.parentId))) return true
        if (BIG_NAME.containsMatchIn(p.name)) return true
        return parseStopDescription(p.description).platform != null
    }

    private val railIds: Set<String> by lazy { RailStations.ALL.mapNotNullTo(HashSet()) { it.stopId } }

    /** תחנה מרכזית (also written ת.מרכזית / ת' מרכזית), מסוף, Central Station, Terminal. */
    private val BIG_NAME = Regex("""ת(?:חנה|\.|')\s*מרכזית|מסוף|central\s+(?:bus\s+)?station|terminal""", RegexOption.IGNORE_CASE)

    private fun walkSec(m: Double): Long = if (m <= 0.0) 0L else ceil(m / WALK_MPS).toLong()

    private fun sameStop(p: Place, q: Place): Boolean = when {
        p.stopId != null && p.stopId == q.stopId -> true
        p.parentId != null && p.parentId == q.parentId -> true
        else -> p.name.trim() == q.name.trim() && Geo.distanceM(p.latLon, q.latLon) <= MATCH_M
    }

    private class TripStops(val leg: Leg, val stops: List<Place>) {
        val mode: String get() = leg.mode
    }

    private fun tripStops(trip: Itinerary): TripStops? {
        val leg = trip.legs.firstOrNull { it.isTransit } ?: return null
        return TripStops(leg, listOf(leg.from) + leg.intermediateStops + leg.to)
    }

    private fun time(s: String?): Instant? = s?.let { runCatching { parseTime(it) }.getOrNull() }
    private fun arrival(p: Place): Instant? = time(p.arrival) ?: time(p.departure)
    private fun departure(p: Place): Instant? = time(p.departure) ?: time(p.arrival)

    /**
     * Index of [target] among [stops] after index [after]: by stop id, else the nearest stop
     * within [MATCH_M]; a loop's two visits are told apart by the time closest to [at].
     */
    private fun matchStop(stops: List<Place>, target: Place, at: Instant, after: Int = -1): Int? {
        val range = (after + 1)..stops.lastIndex
        val byId = range.filter { target.stopId != null && stops[it].stopId == target.stopId }
        val cands = byId.ifEmpty { range.filter { Geo.distanceM(stops[it].latLon, target.latLon) <= MATCH_M } }
        return cands.minByOrNull { i ->
            (departure(stops[i]) ?: arrival(stops[i]))?.let { Duration.between(it, at).abs().seconds } ?: Long.MAX_VALUE
        }
    }

    /**
     * The option with the first vehicle ridden to stop [j] of its trip and the second boarded at
     * stop [k] of its own: the two transit legs are cut from the `trip` answers, the street legs
     * between them become one walk (none at the same stop). Everything before and after is
     * untouched, so the start, the end and the number of changes stay as planned. Null if the
     * result would not end exactly when the original does.
     */
    private fun recut(
        itin: Itinerary, t: Transfer,
        tripA: Itinerary, a: TripStops, boardA: Int, j: Int,
        tripB: Itinerary, b: TripStops, k: Int, alightB: Int,
        walkM: Double,
    ): Itinerary? {
        val legA = itin.legs[t.fromLeg]
        val legB = itin.legs[t.toLeg]
        val stopA = a.stops[j]
        val stopB = b.stops[k]
        val arrive = arrival(stopA) ?: return null
        val leave = departure(stopB) ?: return null
        val newA = legA.copy(
            to = stopA,
            endTime = stopA.arrival ?: stopA.departure!!,
            scheduledEndTime = stopA.scheduledArrival ?: stopA.scheduledDeparture ?: legA.scheduledEndTime,
            duration = Duration.between(legA.start, arrive).seconds.toInt(),
            intermediateStops = a.stops.subList(boardA + 1, j),
            legGeometry = slice(tripA, a.leg, legA.from.latLon, stopA.latLon) ?: legA.legGeometry,
            distance = null,
        )
        val newB = legB.copy(
            from = stopB,
            startTime = stopB.departure ?: stopB.arrival!!,
            scheduledStartTime = stopB.scheduledDeparture ?: stopB.scheduledArrival ?: legB.scheduledStartTime,
            duration = Duration.between(leave, legB.end).seconds.toInt(),
            intermediateStops = b.stops.subList(k + 1, alightB),
            legGeometry = slice(tripB, b.leg, stopB.latLon, legB.to.latLon) ?: legB.legGeometry,
            distance = null,
        )
        val walk = if (walkM <= 0.0) null else {
            val sec = walkSec(walkM)
            Leg(
                mode = StreetModes.WALK,
                from = stopA,
                to = stopB,
                duration = sec.toInt(),
                startTime = newA.endTime,
                endTime = shift(newA.endTime, sec),
                distance = walkM.roundToLong().toDouble(),
            )
        }
        val legs = itin.legs.subList(0, t.fromLeg) + newA + listOfNotNull(walk) + newB + itin.legs.subList(t.toLeg + 1, itin.legs.size)
        val out = itin.copy(legs = legs)
        if (out.legs.last().end != itin.legs.last().end || out.legs.first().start != itin.legs.first().start) return null
        return out
    }

    /** [iso] plus [sec] seconds, keeping its offset (Israel time stays Israel time). */
    private fun shift(iso: String, sec: Long): String =
        java.time.OffsetDateTime.parse(iso).plusSeconds(sec).toString()

    /** The part of the trip's shape between two stops, or null without a shape. */
    private fun slice(trip: Itinerary, leg: Leg, from: LatLon, to: LatLon): il.transit.core.api.EncodedPolyline? {
        val g = leg.legGeometry ?: return null
        val pts = Geo.decodePolyline(g.points, g.precision)
        if (pts.size < 2) return null
        val i = pts.indices.minBy { Geo.distanceM(pts[it], from) }
        val j = (i until pts.size).minBy { Geo.distanceM(pts[it], to) }
        val part = pts.subList(i, j + 1).ifEmpty { return null }
        return il.transit.core.api.EncodedPolyline(encodePolyline(part, g.precision), g.precision, part.size)
    }
}

/** Google's polyline encoding at [precision] digits (MOTIS uses 6), the inverse of `Geo.decodePolyline`. */
internal fun encodePolyline(points: List<LatLon>, precision: Int): String {
    val factor = Math.pow(10.0, precision.toDouble())
    val sb = StringBuilder()
    var lastLat = 0L
    var lastLon = 0L
    fun put(v: Long) {
        var x = if (v < 0) (v shl 1).inv() else v shl 1
        while (x >= 0x20) {
            sb.append(((0x20 or (x and 0x1f).toInt()) + 63).toChar())
            x = x shr 5
        }
        sb.append((x.toInt() + 63).toChar())
    }
    for (p in points) {
        val lat = Math.round(p.lat * factor)
        val lon = Math.round(p.lon * factor)
        put(lat - lastLat)
        put(lon - lastLon)
        lastLat = lat
        lastLon = lon
    }
    return sb.toString()
}
