package il.transit.core.features

import il.transit.core.api.Endpoint
import il.transit.core.api.Itinerary
import il.transit.core.api.Place
import il.transit.core.api.PlanRequest
import il.transit.core.api.Preferences
import il.transit.core.api.StreetModes
import il.transit.core.api.TransitApi
import il.transit.core.api.TransitModes
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant

/**
 * Feature 2 — "let me off on the way": you ride in a car going [a] → [b] and need to reach
 * [c]. Each option says how much the stop costs the driver and when you reach C.
 *
 * Request budget, fixed by construction (a test pins it at [DropOffPlanner.BUDGET]):
 *   1 car route A→B · 1 stop query · 2 one-to-many · [maxCandidates] plans · 2 baselines
 */
data class DropOffQuery(
    val a: LatLon,
    val b: LatLon,
    val c: LatLon,
    val departAt: Instant,
    val maxDetourMin: Int = 10,
    val corridorM: Double = 1500.0,
    val maxCandidates: Int = 4,
    /** Candidates closer together than this are the same choice; keep the better one. */
    val minSpacingM: Double = 2000.0,
    /** Stopping, getting out, getting back into traffic. */
    val stopPenaltySec: Int = 60,
    val preferences: Preferences = Preferences(),
    val language: String = "he",
)

enum class DropOffKind { STOP_ON_THE_WAY, RIDE_TO_END, TRANSIT_FROM_START }

data class DropOffOption(
    val kind: DropOffKind,
    /** The stop to get out at (null for [DropOffKind.TRANSIT_FROM_START]). */
    val stop: Place?,
    val detourSec: Int,
    /** Passenger's time in the car before getting out (traffic-adjusted). */
    val rideSec: Int,
    val transit: Itinerary,
    /** The road driven with the passenger aboard, for the map; empty for transit-from-start. */
    val carPath: List<LatLon> = emptyList(),
)

data class DropOffResult(
    val directDriveSec: Int?,
    val options: List<Option<DropOffOption>>,
    val candidatesConsidered: Int,
)

class DropOffPlanner(
    private val api: TransitApi,
    private val traffic: TrafficProfile = TrafficProfile(),
) {
    suspend fun plan(q: DropOffQuery): DropOffResult = coroutineScope {
        // 1. The drive A→B: its geometry defines the corridor, its duration the baseline.
        val route = api.plan(
            PlanRequest(
                from = Endpoint.Coord(q.a),
                to = Endpoint.Coord(q.b),
                time = q.departAt,
                directModes = listOf(StreetModes.CAR),
                directOnly = true,
            ),
        ).direct.firstOrNull { it.legs.any { l -> l.mode == StreetModes.CAR } }
            ?: return@coroutineScope DropOffResult(null, baselinesOnly(q, null), 0)

        val routeSec = route.duration
        val line = route.legs.flatMap { leg ->
            leg.legGeometry?.let { Geo.decodePolyline(it.points, it.precision) } ?: listOf(leg.from.latLon, leg.to.latLon)
        }

        // 2. Candidate stops in the corridor. Long drives only consider rail/light-rail:
        //    a bus stop 40 km away is never a better place to get out than a station.
        val longDrive = Geo.distanceM(q.a, q.b) > LONG_DRIVE_M
        val stops = api.stops(Geo.bbox(line, q.corridorM), if (longDrive) TransitModes.RAIL_LIKE else null)
        val candidates = pickCandidates(stops, line, q)

        // 3. Detour for each candidate: A→s + s→B − (A→B), from two one-to-many calls.
        val maxSec = routeSec + q.maxDetourMin * 60
        val coords = candidates.map { it.latLon }
        val toStop = async { api.oneToMany(q.a, coords, StreetModes.CAR, maxSec, arriveBy = false) }
        val fromStop = async { api.oneToMany(q.b, coords, StreetModes.CAR, maxSec, arriveBy = true) }
        val aToS = toStop.await()
        val sToB = fromStop.await()

        val factor = traffic.factorAt(q.departAt)
        val viable = candidates.indices.mapNotNull { i ->
            val ride = aToS[i] ?: return@mapNotNull null
            val onward = sToB[i] ?: return@mapNotNull null
            val detour = ((ride + onward - routeSec) * factor).toInt() + q.stopPenaltySec
            if (detour > q.maxDetourMin * 60) null else Triple(candidates[i], detour.coerceAtLeast(0), (ride * factor).toInt())
        }

        // 4. Transit from each surviving stop to C, leaving when the car gets there.
        val stopJobs = viable.map { (stop, detour, ride) ->
            async {
                val transit = best(
                    api.plan(
                        PlanRequest(
                            from = Endpoint.Coord(stop.latLon),
                            to = Endpoint.Coord(q.c),
                            time = q.departAt.plusSeconds((ride + q.stopPenaltySec).toLong()),
                            preferences = q.preferences,
                            language = q.language,
                        ),
                    ).itineraries,
                ) ?: return@async null
                Option(
                    DropOffOption(DropOffKind.STOP_ON_THE_WAY, stop, detour, ride, transit, Geo.pathTo(line, stop.latLon)),
                    driverCostSec = detour,
                    arrival = transit.end,
                    transfers = transit.transfers,
                )
            }
        }
        val baselineJob = async { baselinesOnly(q, (routeSec * factor).toInt(), line) }
        val all = stopJobs.awaitAll().filterNotNull() + baselineJob.await()
        DropOffResult((routeSec * factor).toInt(), paretoFront(all), candidates.size)
    }

    /**
     * The two options that need no stop: ride to B then take transit, and take transit
     * from A. Two requests. [routeSec] is null when the car route itself failed.
     */
    private suspend fun baselinesOnly(
        q: DropOffQuery,
        routeSec: Int?,
        line: List<LatLon> = emptyList(),
    ): List<Option<DropOffOption>> = coroutineScope {
        val rideToEnd = routeSec?.let { sec ->
            async {
                best(api.plan(PlanRequest(Endpoint.Coord(q.b), Endpoint.Coord(q.c), q.departAt.plusSeconds(sec.toLong()), preferences = q.preferences, language = q.language)).itineraries)
                    ?.let { Option(DropOffOption(DropOffKind.RIDE_TO_END, null, 0, sec, it, line), 0, it.end, it.transfers) }
            }
        }
        val fromStart = async {
            best(api.plan(PlanRequest(Endpoint.Coord(q.a), Endpoint.Coord(q.c), q.departAt, preferences = q.preferences, language = q.language)).itineraries)
                ?.let { Option(DropOffOption(DropOffKind.TRANSIT_FROM_START, null, 0, 0, it), 0, it.end, it.transfers) }
        }
        listOfNotNull(rideToEnd?.await(), fromStart.await())
    }

    companion object {
        const val LONG_DRIVE_M = 15_000.0

        /** 1 route + 1 stops + 2 one-to-many + 4 candidates + 2 baselines. */
        const val BUDGET = 10

        /**
         * Keep stops inside the corridor, rank them (rail first, then MOTIS importance),
         * and take the best [DropOffQuery.maxCandidates] that are at least
         * [DropOffQuery.minSpacingM] apart, so the few requests we spend cover the whole route.
         */
        fun pickCandidates(stops: List<Place>, line: List<LatLon>, q: DropOffQuery): List<Place> {
            val inCorridor = stops.filter { Geo.distanceToLineM(it.latLon, line) <= q.corridorM }
            val ranked = inCorridor.sortedWith(
                compareByDescending<Place> { s -> s.modes.orEmpty().any { it in TransitModes.RAIL_LIKE } }
                    .thenByDescending { it.importance ?: 0.0 }
                    .thenBy { it.name },
            )
            val picked = ArrayList<Place>()
            for (s in ranked) {
                if (picked.size >= q.maxCandidates) break
                if (picked.none { Geo.distanceM(it.latLon, s.latLon) < q.minSpacingM }) picked += s
            }
            return picked
        }
    }
}
