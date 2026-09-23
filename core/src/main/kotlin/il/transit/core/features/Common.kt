package il.transit.core.features

import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.api.StreetModes
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

val ISRAEL: ZoneId = ZoneId.of("Asia/Jerusalem")

/**
 * Free car routers assume empty roads. Israeli rush hour does not, so every drive time
 * shown or used for timing is multiplied by this factor. Editable in the app's settings.
 * Israel's work week is Sunday–Thursday.
 */
data class TrafficProfile(
    val peakFactor: Double = 1.3,
    val offPeakFactor: Double = 1.0,
    val morningPeak: IntRange = 7..9,
    val eveningPeak: IntRange = 16..18,
) {
    fun factorAt(t: Instant): Double {
        val local = t.atZone(ISRAEL)
        val workday = local.dayOfWeek !in setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
        val peak = local.hour in morningPeak || local.hour in eveningPeak
        return if (workday && peak) peakFactor else offPeakFactor
    }

    fun adjust(freeFlowSec: Int, at: Instant): Int = Math.round(freeFlowSec * factorAt(at)).toInt()
}

/**
 * One option on a car + transit result list. [driverCostSec] is what the option costs
 * the driver (a drop-off detour, a drive to the station, a pick-up round trip);
 * [arrival] is when the passenger reaches their destination.
 */
data class Option<T>(
    val payload: T,
    val driverCostSec: Int,
    val arrival: Instant,
    val transfers: Int,
)

/**
 * Options that no other option beats on all three of driver cost, arrival and transfers.
 * Output is sorted by arrival, then driver cost. Exact ties keep the first seen.
 */
fun <T> paretoFront(options: List<Option<T>>): List<Option<T>> {
    val front = ArrayList<Option<T>>()
    for (o in options) {
        val dominated = options.any { other ->
            other !== o &&
                other.driverCostSec <= o.driverCostSec &&
                !other.arrival.isAfter(o.arrival) &&
                other.transfers <= o.transfers &&
                (other.driverCostSec < o.driverCostSec || other.arrival.isBefore(o.arrival) || other.transfers < o.transfers)
        }
        val duplicate = front.any {
            it.driverCostSec == o.driverCostSec && it.arrival == o.arrival && it.transfers == o.transfers
        }
        if (!dominated && !duplicate) front += o
    }
    return front.sortedWith(compareBy<Option<T>> { it.arrival }.thenBy { it.driverCostSec })
}

/** Earliest-arriving itinerary; ties go to fewer transfers. Null if there are none. */
fun best(itineraries: List<Itinerary>): Itinerary? =
    itineraries.minWithOrNull(compareBy<Itinerary> { it.end }.thenBy { it.transfers })

/** The car leg at the start of an itinerary, if it has one. */
fun Itinerary.leadingCarLeg(): Leg? = legs.firstOrNull()?.takeIf { it.mode in StreetModes.CAR_LIKE }

/** The car leg at the end of an itinerary, if it has one. */
fun Itinerary.trailingCarLeg(): Leg? = legs.lastOrNull()?.takeIf { it.mode in StreetModes.CAR_LIKE }

/**
 * Spread of drive-time caps that turns MOTIS's (time, transfers) answer into a
 * (driver cost, time) trade-off: each cap forces a different compromise. Caps under
 * 3 minutes are not worth a car.
 */
fun capLadder(maxSec: Int): List<Int> =
    listOf(maxSec / 3, 2 * maxSec / 3, maxSec).filter { it >= 180 }.distinct().ifEmpty { listOf(maxSec) }
