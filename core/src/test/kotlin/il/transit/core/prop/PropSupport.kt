package il.transit.core.prop

import io.kotest.property.PropTestConfig
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.api.Place
import java.time.Instant

/**
 * Phase 11 T1: property tests run kotest-property standalone from JUnit 4. Every run uses a FIXED seed and a
 * FIXED number of cases, so CI is deterministic. kotest prints the failing case and its seed; to replay or
 * explore, change [SEED] locally (see the android-build skill, "Property-test seed rule").
 */
const val SEED = 20261013L

fun cfg(iterations: Int = 500) = PropTestConfig(seed = SEED, iterations = iterations)

/** A small tuple-free helper: ints in a range. */
fun ints(range: IntRange): Arb<Int> = Arb.int(range)

fun stop(name: String, lat: Double, lon: Double) = Place(name = name, lat = lat, lon = lon)

/** A leg with only the fields the code under test reads. */
fun leg(mode: String, from: Place, to: Place, start: Instant, end: Instant, tripId: String? = null) = Leg(
    mode = mode,
    from = from,
    to = to,
    duration = (end.epochSecond - start.epochSecond).toInt(),
    startTime = start.toString(),
    endTime = end.toString(),
    tripId = tripId,
)

fun itinerary(legs: List<Leg>, transfers: Int = 0) = Itinerary(
    duration = (legs.last().end.epochSecond - legs.first().start.epochSecond).toInt(),
    startTime = legs.first().startTime,
    endTime = legs.last().endTime,
    transfers = transfers,
    legs = legs,
)
