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
 * "Best pick-up point": the trip home, mirror image of [BetterStartPlanner]. You ride
 * transit to a stop; someone drives from [PickUpQuery.home] to collect you there.
 * The driver's cost is the round trip home → stop → home.
 *
 * Uses `postTransitModes` with [PickUpQuery.carMode]; whether Transitous accepts
 * CAR_DROPOFF on the arrival side is a phase-0 spike question, so `CAR` is the default.
 */
data class PickUpQuery(
    val me: LatLon,
    val home: LatLon,
    val departAt: Instant,
    /** Max one-way drive for the person collecting you. */
    val maxDriveMin: Int = 15,
    val preferences: Preferences = Preferences(),
    val carMode: String = StreetModes.CAR,
    /** An option must get you home this much earlier than transit alone, or with fewer transfers. */
    val minGainMin: Int = 5,
    val language: String = "he",
)

data class PickUpOption(
    val itinerary: Itinerary,
    val pickUpStopName: String,
    /** Where the driver waits — what "send to driver" links to. */
    val pickUpAt: LatLon,
    /** When you get there by transit, i.e. when the driver must be there. */
    val pickUpTime: Instant,
    /** One-way, traffic-adjusted. The driver's cost is twice this. */
    val driveSec: Int,
    /** When the driver should leave home to be there as you arrive. */
    val driverLeavesAt: Instant,
)

data class PickUpResult(
    /** All the way home by transit — what you'd do with nobody to collect you. */
    val baseline: Itinerary?,
    val options: List<Option<PickUpOption>>,
)

class PickUpPlanner(
    private val api: TransitApi,
    private val traffic: TrafficProfile = TrafficProfile(),
) {
    suspend fun plan(q: PickUpQuery): PickUpResult = coroutineScope {
        val baseReq = PlanRequest(
            Endpoint.Coord(q.me),
            Endpoint.Coord(q.home),
            q.departAt,
            preferences = q.preferences,
            language = q.language,
        )
        val baselineJob = async { best(api.plan(baseReq).itineraries) }
        val caps = capLadder((q.maxDriveMin * 60 / traffic.factorAt(q.departAt)).toInt())
        val jobs = caps.map { cap ->
            async { api.plan(baseReq.copy(postTransitModes = listOf(q.carMode), maxPostTransitSec = cap)).itineraries }
        }
        val options = jobs.awaitAll().flatten().mapNotNull { it.toOption() }
        val baseline = baselineJob.await()
        val gainSec = q.minGainMin * 60L
        val worthIt = options.filter { o ->
            baseline == null ||
                o.arrival.plusSeconds(gainSec) <= baseline.end ||
                (o.transfers < baseline.transfers && !o.arrival.isAfter(baseline.end))
        }
        PickUpResult(baseline, paretoFront(worthIt))
    }

    private fun Itinerary.toOption(): Option<PickUpOption>? {
        val car = trailingCarLeg() ?: return null
        if (lastTransitLeg == null) return null
        val drive = traffic.adjust(car.duration, car.start)
        return Option(
            PickUpOption(this, car.from.name, car.from.latLon, car.start, drive, car.start.minusSeconds(drive.toLong())),
            driverCostSec = 2 * drive,
            // MOTIS times the drive home at free flow; the traffic-adjusted time is the honest one.
            arrival = car.start.plusSeconds(drive.toLong()),
            transfers = transfers,
        )
    }

    companion object {
        /** 1 baseline + 3 rungs. */
        const val BUDGET = 4
    }
}
