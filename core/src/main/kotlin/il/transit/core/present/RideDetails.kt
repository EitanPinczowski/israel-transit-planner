package il.transit.core.present

import il.transit.core.api.Itinerary
import il.transit.core.api.Leg

/**
 * Ride details on every option (Phase 10 D1): per transit leg, how many stops and minutes on
 * board and where to get off ("Line 5 · 12 stops · 23 min · get off at Rager/Oren (stop 47899)").
 * Built from the `plan` answer alone, no request. The UI only picks strings, so the word order
 * of each language lives in its string resources.
 */
data class RideLeg(
    /** Index into [Itinerary.legs]. */
    val legIndex: Int,
    /** Short line name, or null (trains): the UI says the kind instead. */
    val line: String?,
    val kind: LegKind,
    /** Stops ridden: every stop after the boarding one, the get-off stop included. */
    val stops: Int,
    /** Time on board, leg end − leg start (live when the answer has it). */
    val minutes: Int,
    val getOff: String,
    /** The number on the stop's sign; null on trains and when the data has none. */
    val stopCode: String?,
)

/** The compact line of an unselected card: "17 stops · 38 min on board · off at Rager/Oren". */
data class RideTotal(val stops: Int, val minutes: Int, val getOff: String)

data class RideDetails(
    val legs: List<RideLeg>,
    /** What the selected card lists: every leg up to [MAX_LINES], else the first [MAX_LINES] − 1. */
    val shown: List<RideLeg>,
    /** Legs left out of [shown] ("+N"). */
    val more: Int,
    /** Null when the option has no transit leg (walk or drive only). */
    val total: RideTotal?,
) {
    companion object {
        const val MAX_LINES = 4
    }
}

fun rideLeg(leg: Leg, index: Int): RideLeg {
    val train = legKind(leg.mode) == LegKind.TRAIN
    return RideLeg(
        legIndex = index,
        line = lineLabel(leg),
        kind = legKind(leg.mode),
        stops = leg.intermediateStops.size + 1,
        minutes = minutes(((leg.end.epochSecond - leg.start.epochSecond).coerceAtLeast(0)).toInt()),
        getOff = leg.to.name,
        stopCode = stopPlatform(leg.to, train).stopCode,
    )
}

fun rideDetails(itin: Itinerary): RideDetails {
    val legs = itin.legs.mapIndexedNotNull { i, l -> if (l.isTransit) rideLeg(l, i) else null }
    val shown = if (legs.size <= RideDetails.MAX_LINES) legs else legs.take(RideDetails.MAX_LINES - 1)
    val total = legs.lastOrNull()?.let { last -> RideTotal(legs.sumOf { it.stops }, legs.sumOf { it.minutes }, last.getOff) }
    return RideDetails(legs, shown, legs.size - shown.size, total)
}
