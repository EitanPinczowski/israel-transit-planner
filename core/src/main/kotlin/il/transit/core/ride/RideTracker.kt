package il.transit.core.ride

import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import java.time.Duration
import java.time.Instant

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
        }
        return if (Geo.distanceM(pos, destination) <= approachM || now.isAfter(plannedEnd)) RideEvent.Finished else null
    }

    private fun passedPenultimate(leg: Leg, pos: LatLon): Boolean {
        val before = leg.intermediateStops.lastOrNull() ?: return false
        return Geo.distanceM(pos, before.latLon) <= passedStopM
    }
}
