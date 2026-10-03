package il.transit.core.ride

import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.api.Place
import il.transit.core.api.parseTime
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import java.time.Duration
import java.time.Instant

/** Where the ride stands, for the "on the bus" notification: "Line 5 · 3 stops left · 08:47". */
data class RideProgress(
    /** Line of the vehicle being ridden (or waited for). */
    val line: String?,
    val alightStop: String,
    /** Stops still to reach on this ride, the alighting stop included. */
    val stopsLeft: Int,
    /** Estimated arrival at [alightStop]: scheduled, shifted by how late the ride runs. */
    val arriveAt: Instant,
    /** Minutes behind schedule where the rider is now (negative = early). */
    val delayMin: Int,
    /** Estimated arrival at the destination, shifted the same way. */
    val tripArriveAt: Instant,
)

sealed interface RideEvent {
    /** Get off at the next stop. Fired once per transit leg. */
    data class Approaching(val legIndex: Int, val stopName: String, val isLastLeg: Boolean) : RideEvent

    /** The trip is over (at the destination, or long past the planned arrival): stop tracking. */
    data object Finished : RideEvent
}

/**
 * "Get off at the next stop", from GPS alone. Pure logic, fed one position at a time by the
 * foreground service, so it can be tested with a synthetic track.
 *
 * For each transit leg, the alert fires once, at the first of:
 *  - within [approachM] of the alighting stop, or
 *  - within [passedStopM] of the stop before it (when MOTIS listed intermediate stops) —
 *    "your stop is next" is what people on Israeli buses actually need.
 * A leg is done once you are at its stop, or its planned end is [legGrace] behind you.
 */
class RideTracker(
    itinerary: Itinerary,
    private val approachM: Double = 400.0,
    private val passedStopM: Double = 120.0,
    private val atStopM: Double = 80.0,
    private val legGrace: Duration = Duration.ofMinutes(3),
    private val tripGrace: Duration = Duration.ofMinutes(30),
) {
    private val legs: List<Leg> = itinerary.legs.filter { it.isTransit }
    private val destination: LatLon = itinerary.legs.last().to.latLon
    private val plannedEnd: Instant = itinerary.end
    private var index = 0
    private var alerted = false

    /** Furthest stop-to-stop segment reached on the current leg; never moves backwards. */
    private var segment = 0

    /** Index of the transit leg being ridden, or legs.size when all are done. */
    val currentLeg: Int get() = index

    fun update(pos: LatLon, now: Instant): RideEvent? {
        if (now.isAfter(plannedEnd.plus(tripGrace))) return RideEvent.Finished
        while (index < legs.size) {
            val leg = legs[index]
            val toStop = Geo.distanceM(pos, leg.to.latLon)
            if (!alerted && (toStop <= approachM || passedPenultimate(leg, pos))) {
                alerted = true
                return RideEvent.Approaching(index, leg.to.name, isLastLeg = index == legs.size - 1)
            }
            val legOver = toStop <= atStopM || now.isAfter(leg.end.plus(legGrace))
            if (!legOver) return null
            index++
            alerted = false
            segment = 0
        }
        return if (Geo.distanceM(pos, destination) <= approachM || now.isAfter(plannedEnd)) RideEvent.Finished else null
    }

    /**
     * Progress on the current transit leg, from the nearest stop-to-stop segment of its route:
     * how many stops are left, and how late it runs (now vs the schedule interpolated to this
     * point). Null once every transit leg is done. Call after [update] with the same fix.
     */
    fun progress(pos: LatLon, now: Instant): RideProgress? {
        val leg = legs.getOrNull(index) ?: return null
        val stops = listOf(leg.from) + leg.intermediateStops + listOf(leg.to)
        var best = 0
        var bestD = Double.MAX_VALUE
        var bestF = 0.0
        for (i in 0 until stops.size - 1) {
            val (d, f) = project(pos, stops[i].latLon, stops[i + 1].latLon)
            if (d < bestD) { bestD = d; best = i; bestF = f }
        }
        if (best < segment) { best = segment; bestF = 0.0 } else segment = best
        val t0 = timeAt(leg, best, stops)
        val t1 = timeAt(leg, best + 1, stops)
        val planned = t0.plusMillis((Duration.between(t0, t1).toMillis() * bestF).toLong())
        val delay = Duration.between(planned, now)
        val legEnd = timeAt(leg, stops.size - 1, stops)
        return RideProgress(
            line = il.transit.core.present.lineLabel(leg),
            alightStop = leg.to.name,
            stopsLeft = stops.size - 1 - best,
            arriveAt = maxOf(legEnd.plus(delay), now),
            delayMin = delay.toMinutes().toInt(),
            tripArriveAt = maxOf(plannedEnd.plus(delay), now),
        )
    }

    /** Scheduled time at stop [i] of [stops] (0 = boarding, last = alighting). */
    private fun timeAt(leg: Leg, i: Int, stops: List<Place>): Instant {
        val p = stops[i]
        val s = when (i) {
            0 -> leg.scheduledStartTime ?: leg.startTime
            stops.size - 1 -> leg.scheduledEndTime ?: leg.endTime
            else -> p.scheduledDeparture ?: p.scheduledArrival ?: p.departure ?: p.arrival
        }
        return s?.let(::parseTime) ?: leg.start
    }

    /** Distance from [p] to segment a→b in metres, and how far along it (0..1) the closest point is. */
    private fun project(p: LatLon, a: LatLon, b: LatLon): Pair<Double, Double> {
        val k = kotlin.math.cos(Math.toRadians(a.lat))
        val bx = (b.lon - a.lon) * k
        val by = b.lat - a.lat
        val px = (p.lon - a.lon) * k
        val py = p.lat - a.lat
        val len2 = bx * bx + by * by
        val f = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val closest = LatLon(a.lat + by * f, a.lon + (bx * f) / k)
        return Geo.distanceM(p, closest) to f
    }

    private fun passedPenultimate(leg: Leg, pos: LatLon): Boolean {
        val before = leg.intermediateStops.lastOrNull() ?: return false
        return Geo.distanceM(pos, before.latLon) <= passedStopM
    }
}
