package il.transit.core.features

import il.transit.core.api.Endpoint
import il.transit.core.api.Itinerary
import il.transit.core.api.PlanRequest
import il.transit.core.api.Preferences
import il.transit.core.api.StreetModes
import il.transit.core.api.TransitApi
import il.transit.core.api.TransitHttpException
import il.transit.core.geo.LatLon
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant

/**
 * Feature 1 — "better starting point": someone drives you (and leaves) to a stop within
 * [BetterStartQuery.maxDriveMin], from which the trip is faster or has fewer transfers.
 *
 * MOTIS searches the drop-off stops itself (`preTransitModes=CAR`), so this costs one
 * baseline request plus one per rung of [capLadder]. `CAR`, not `CAR_DROPOFF`: Transitous
 * accepts `CAR_DROPOFF` but its answers barely use the car (Meitar → Tel Aviv with a 20-min
 * cap: a 4-min drive arriving 07:35, against 15 min to Be'er Sheva North arriving 06:46 with
 * `CAR`; fixtures `plan_car_dropoff_meitar` / `plan_car_pre_meitar`). A query may still ask
 * for `CAR_DROPOFF` (e.g. a self-hosted MOTIS); if the server refuses it (HTTP 400) the
 * search falls back to `CAR` once, which is why [BetterStartPlanner.BUDGET] is 5 and not 4.
 */
data class BetterStartQuery(
    val origin: LatLon,
    val dest: LatLon,
    val departAt: Instant,
    val maxDriveMin: Int = 10,
    /** An option must arrive this much earlier than the baseline, or need fewer transfers. */
    val minGainMin: Int = 5,
    val preferences: Preferences = Preferences(),
    /** `CAR_DROPOFF` falls back to `CAR` automatically if the server rejects it. */
    val carMode: String = StreetModes.CAR,
    val language: String = "he",
)

data class BetterStartOption(
    val itinerary: Itinerary,
    /** Drive time with the traffic factor applied. */
    val driveSec: Int,
    val dropOffStopName: String,
    /** Where the driver stops — what "send to driver" links to. */
    val dropOffAt: LatLon,
    /** The traffic-adjusted drive eats all the slack before the first departure. */
    val tight: Boolean,
)

data class BetterStartResult(
    val baseline: Itinerary?,
    val options: List<Option<BetterStartOption>>,
    /** The car mode the server accepted: the query's, or `CAR` after a refused `CAR_DROPOFF`. */
    val usedMode: String,
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
            language = q.language,
        )
        val baselineJob = async { best(api.plan(baseReq).itineraries) }
        // MOTIS measures the car leg in free-flow seconds, so the cap is shrunk by the
        // traffic factor: a 10-min limit at peak allows ~7.7 free-flow minutes.
        val caps = capLadder((q.maxDriveMin * 60 / factor).toInt())
        fun carReq(cap: Int, mode: String) = baseReq.copy(preTransitModes = listOf(mode), maxPreTransitSec = cap)

        // The widest rung goes first, alone: it doubles as the probe for CAR_DROPOFF support.
        var mode = q.carMode
        val widest = try {
            api.plan(carReq(caps.last(), mode)).itineraries
        } catch (e: TransitHttpException) {
            if (e.code != 400 || mode == StreetModes.CAR) throw e
            mode = StreetModes.CAR
            api.plan(carReq(caps.last(), mode)).itineraries
        }
        val usedMode = mode
        val narrower = caps.dropLast(1).map { cap -> async { api.plan(carReq(cap, usedMode)).itineraries } }
        val baseline = baselineJob.await()
        val candidates = (widest + narrower.awaitAll().flatten()).mapNotNull { it.toOption(q.departAt) }

        val gainSec = q.minGainMin * 60L
        val worthIt = candidates.filter { o ->
            baseline == null ||
                o.arrival.plusSeconds(gainSec) <= baseline.end ||
                (o.transfers < baseline.transfers && !o.arrival.isAfter(baseline.end))
        }
        BetterStartResult(baseline, paretoFront(worthIt), usedMode)
    }

    private fun Itinerary.toOption(departAt: Instant): Option<BetterStartOption>? {
        val cars = leadingCarLegs().ifEmpty { return null }
        val boardAt = legs.indexOfFirst { it.isTransit }.takeIf { it > 0 } ?: return null
        val firstTransit = legs[boardAt]
        val freeFlow = cars.sumOf { it.duration }
        val drive = traffic.adjust(freeFlow, departAt)
        // Waiting time at the stop: the traffic delay comes out of it.
        val slackSec = firstTransit.start.epochSecond - legs[boardAt - 1].end.epochSecond
        val tight = drive - freeFlow > slackSec
        // CAR_DROPOFF leaves you on an unnamed street point; name it after the stop you walk to.
        val dropOff = cars.last().to
        val name = dropOff.name.ifBlank { firstTransit.from.name }
        return Option(
            payload = BetterStartOption(this, drive, name, dropOff.latLon, tight),
            driverCostSec = drive,
            arrival = end,
            transfers = transfers,
        )
    }

    companion object {
        /** 1 baseline + 1 refused CAR_DROPOFF probe + 3 rungs. */
        const val BUDGET = 5
    }
}
