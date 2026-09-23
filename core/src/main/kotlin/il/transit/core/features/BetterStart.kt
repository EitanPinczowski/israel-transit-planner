package il.transit.core.features

import il.transit.core.api.Endpoint
import il.transit.core.api.Itinerary
import il.transit.core.api.PlanRequest
import il.transit.core.api.Preferences
import il.transit.core.api.StreetModes
import il.transit.core.api.TransitApi
import il.transit.core.geo.LatLon
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant

/**
 * Feature 1 — "better starting point": someone drives you (and leaves) to a stop within
 * [BetterStartQuery.maxDriveMin], from which the trip is faster or has fewer transfers.
 *
 * MOTIS searches the drop-off stops itself (`preTransitModes=CAR_DROPOFF`), so this costs
 * one baseline request plus one per rung of [capLadder] — at most 4.
 */
data class BetterStartQuery(
    val origin: LatLon,
    val dest: LatLon,
    val departAt: Instant,
    val maxDriveMin: Int = 10,
    /** An option must arrive this much earlier than the baseline, or need fewer transfers. */
    val minGainMin: Int = 5,
    val preferences: Preferences = Preferences(),
    /** Verified in the phase-0 spike; `CAR` is the fallback if Transitous rejects CAR_DROPOFF. */
    val carMode: String = StreetModes.CAR_DROPOFF,
)

data class BetterStartOption(
    val itinerary: Itinerary,
    /** Drive time with the traffic factor applied. */
    val driveSec: Int,
    val dropOffStopName: String,
    /** The traffic-adjusted drive eats all the slack before the first departure. */
    val tight: Boolean,
)

data class BetterStartResult(
    val baseline: Itinerary?,
    val options: List<Option<BetterStartOption>>,
)

class BetterStartPlanner(
    private val api: TransitApi,
    private val traffic: TrafficProfile = TrafficProfile(),
) {
    suspend fun plan(q: BetterStartQuery): BetterStartResult = coroutineScope {
        val factor = traffic.factorAt(q.departAt)
        val baseReq = PlanRequest(
            from = Endpoint.Coord(q.origin),
            to = Endpoint.Coord(q.dest),
            time = q.departAt,
            preferences = q.preferences,
        )
        val baselineJob = async { best(api.plan(baseReq).itineraries) }
        // MOTIS measures the car leg in free-flow seconds, so the cap is shrunk by the
        // traffic factor: a 10-min limit at peak allows ~7.7 free-flow minutes.
        val caps = capLadder((q.maxDriveMin * 60 / factor).toInt())
        val carJobs = caps.map { cap ->
            async {
                api.plan(baseReq.copy(preTransitModes = listOf(q.carMode), maxPreTransitSec = cap)).itineraries
            }
        }
        val baseline = baselineJob.await()
        val candidates = carJobs.awaitAll().flatten().mapNotNull { it.toOption(q.departAt) }

        val gainSec = q.minGainMin * 60L
        val worthIt = candidates.filter { o ->
            baseline == null ||
                o.arrival.plusSeconds(gainSec) <= baseline.end ||
                (o.transfers < baseline.transfers && !o.arrival.isAfter(baseline.end))
        }
        BetterStartResult(baseline, paretoFront(worthIt))
    }

    private fun Itinerary.toOption(departAt: Instant): Option<BetterStartOption>? {
        val car = leadingCarLeg() ?: return null
        val firstTransit = firstTransitLeg ?: return null
        val drive = traffic.adjust(car.duration, departAt)
        val slackSec = firstTransit.start.epochSecond - car.end.epochSecond
        val tight = drive - car.duration > slackSec
        return Option(
            payload = BetterStartOption(this, drive, car.to.name, tight),
            driverCostSec = drive,
            arrival = end,
            transfers = transfers,
        )
    }
}
