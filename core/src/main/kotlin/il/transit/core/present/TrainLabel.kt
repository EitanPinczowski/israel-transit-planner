package il.transit.core.present

import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.api.Place

/**
 * Train number and "fewer stops" (Phase 11 E3, item 5). Israel Railways sends the train number as
 * the leg's `headsign` ("24"), an empty `tripShortName`, and a route long name
 * "נהריה-נהריה<->באר שבע מרכז-באר שבע" ("station-town<->station-town"). Built from the `plan`
 * answer alone, no request. The UI joins the words ("Train 24 to Nahariya · 3 stops on the way").
 */
data class TrainLabel(
    /** "24": 1–4 digits, a text (never parsed to a number). */
    val number: String,
    /** The town at the end of the route the train runs towards; null when the data has none. */
    val to: String?,
    /** Stops between boarding and getting off (neither counted); 0 = none on the way. */
    val stopsBetween: Int,
)

private val TRAIN_NUMBER = Regex("""\d{1,4}""")

/** A heavy-rail leg (never a tram or light rail) whose train number is in the data; else null. */
fun trainLabel(leg: Leg): TrainLabel? {
    if (legKind(leg.mode) != LegKind.TRAIN) return null
    val number = listOf(leg.tripShortName, leg.headsign)
        .map { it.orEmpty().trim() }
        .firstOrNull { TRAIN_NUMBER.matches(it) } ?: return null
    return TrainLabel(number, terminusTown(leg), leg.intermediateStops.size)
}

/**
 * The town of the route's end in the direction of travel: the side of `A<->B` whose station is
 * the trip's last stop (`tripTo`). "תל אביב מרכז-תל אביב יפו" → "תל אביב יפו". Without a match,
 * the trip's last stop itself.
 */
internal fun terminusTown(leg: Leg): String? {
    val end = leg.tripTo
        ?.name
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val sides = leg.routeLongName
        .orEmpty()
        .split("<->")
        .map { it.trim() }
    if (end != null && sides.size == 2) {
        sides.firstOrNull { side -> side.substringBefore('-').trim() == end }?.let { side ->
            return side.substringAfter('-', side).trim().ifEmpty { end }
        }
    }
    return end
}

/** Same station: the same stop id, or the same parent station (a platform and its station too). */
internal fun sameStation(a: Place, b: Place): Boolean {
    val ka = setOfNotNull(a.stopId, a.parentId)
    val kb = setOfNotNull(b.stopId, b.parentId)
    return ka.any { it in kb }
}

/** Where on the card the "Fewer stops" chip goes: option [option], leg [legIndex] of it. */
data class FewerStops(val option: Int, val legIndex: Int)

object TrainCompare {
    /** Trains leaving further apart than this are not alternatives to each other (owner decision 6). */
    const val WINDOW_SEC = 30 * 60L

    /** A train needs at least this many fewer stops than a comparable one to get the chip. */
    const val MIN_FEWER = 2
}

/**
 * Between the options of one answer: a train leg that rides between the same two stations as a
 * train leg of another option, leaving within [TrainCompare.WINDOW_SEC] of it, with at least
 * [TrainCompare.MIN_FEWER] fewer stops on the way. Never "express" or "direct": the operator's own
 * label is not in the data.
 */
fun fewerStops(options: List<Itinerary>): Set<FewerStops> {
    val trains = options.flatMapIndexed { o, itin ->
        itin.legs.mapIndexedNotNull { i, l -> if (legKind(l.mode) == LegKind.TRAIN) Triple(o, i, l) else null }
    }
    fun beats(a: Leg, b: Leg) =
        sameStation(a.from, b.from) &&
            sameStation(a.to, b.to) &&
            kotlin.math.abs(a.start.epochSecond - b.start.epochSecond) <= TrainCompare.WINDOW_SEC &&
            a.intermediateStops.size <= b.intermediateStops.size - TrainCompare.MIN_FEWER
    return trains
        .filter { (o, _, a) -> trains.any { (p, _, b) -> p != o && beats(a, b) } }
        .map { (o, i, _) -> FewerStops(o, i) }
        .toSet()
}
