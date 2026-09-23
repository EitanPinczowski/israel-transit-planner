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
)

data class PickUpOption(
    val itinerary: Itinerary,
    val pickUpStopName: String,
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
        val baseReq = PlanRequest(Endpoint.Coord(q.me), Endpoint.Coord(q.home), q.departAt, preferences = q.preferences)
        val baselineJob = async { best(api.plan(baseReq).itineraries) }
        val caps = capLadder((q.maxDriveMin * 60 / traffic.factorAt(q.departAt)).toInt())
        val jobs = caps.map { cap ->
            async { api.plan(baseReq.copy(postTransitModes = listOf(q.carMode), maxPostTransitSec = cap)).itineraries }
        }
        val options = jobs.awaitAll().flatten().mapNotNull { it.toOption() }
        PickUpResult(baselineJob.await(), paretoFront(options))
    }

    private fun Itinerary.toOption(): Option<PickUpOption>? {
        val car = trailingCarLeg() ?: return null
        if (lastTransitLeg == null) return null
        val drive = traffic.adjust(car.duration, car.start)
        return Option(
            PickUpOption(this, car.from.name, drive, car.start.minusSeconds(drive.toLong())),
            driverCostSec = 2 * drive,
            arrival = end,
            transfers = transfers,
        )
    }
}
