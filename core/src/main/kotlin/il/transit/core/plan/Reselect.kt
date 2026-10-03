package il.transit.core.plan

import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.api.parseTime
import il.transit.core.present.lineLabel
import java.time.Duration

/**
 * Where the option the user picked went after a background refresh. A refresh drops buses
 * that left and adds new ones, and a sort can reorder them, so the old index may now point at
 * another bus; "Start ride" or "Remind me" would then follow the wrong one.
 *
 * Matches every transit leg first, then only the first boarding (the rest may have been
 * re-planned), then walk-only with walk-only. Falls back to [previousIndex], clamped.
 */
fun reselect(previous: Itinerary?, options: List<Itinerary>, previousIndex: Int): Int {
    if (options.isEmpty()) return 0
    if (previous != null) {
        val transit = previous.legs.filter { it.isTransit }
        val exact = options.indexOfFirst { o ->
            val legs = o.legs.filter { it.isTransit }
            legs.size == transit.size && legs.zip(transit).all { (a, b) -> a.sameVehicle(b) }
        }
        if (exact >= 0) return exact
        val first = previous.firstTransitLeg
        val boarding = if (first == null) -1 else options.indexOfFirst { it.firstTransitLeg?.sameVehicle(first) == true }
        if (boarding >= 0) return boarding
    }
    return previousIndex.coerceIn(0, options.size - 1)
}

/** By trip id when both have one, otherwise line + boarding stop + scheduled departure (±1 min). */
private fun Leg.sameVehicle(other: Leg): Boolean {
    if (tripId != null && other.tripId != null) return tripId == other.tripId
    fun scheduled(l: Leg) = l.scheduledStartTime?.let(::parseTime) ?: l.start
    return lineLabel(this) == lineLabel(other) && from.name == other.from.name &&
        Duration.between(scheduled(this), scheduled(other)).abs() <= Duration.ofMinutes(1)
}
