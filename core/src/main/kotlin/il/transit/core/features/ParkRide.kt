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
import il.transit.core.geo.RailStations
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant

/**
 * Feature 4 — "park & ride": drive your own car to a train station, park, and go on by train.
 *
 * Not `preTransitModes=CAR_PARKING`: on Transitous it parks at unnamed OSM lots by bus stops,
 * never at a station (see the `dead-ends` skill). So the stations are picked here:
 *   0 requests — bundled [RailStations] (trains only), straight-line pre-filter
 *   1 request  — one-to-many CAR from the origin to all of them
 *   ≤ [ParkRideQuery.maxPlans] — a `plan` from each of the best stations' stop ids
 *   1 request  — the transit-only baseline
 * Total [ParkRidePlanner.BUDGET], pinned by a test.
 */
data class ParkRideQuery(
    val origin: LatLon,
    val dest: LatLon,
    val departAt: Instant,
    /** The longest drive to the station the user accepts, traffic included. */
    val maxDriveMin: Int = 20,
    /** An option must arrive this much earlier than the baseline, or need fewer transfers. */
    val minGainMin: Int = 5,
    /** Stations planned on to the destination — one request each. */
    val maxPlans: Int = 3,
    /** Parking, walking to the platform, buying nothing (Rav-Kav): before the train can be caught. */
    val parkSec: Int = 300,
    val preferences: Preferences = Preferences(),
    val language: String = "he",
)

data class ParkRideOption(
    val station: Place,
    /** Drive to the station with the traffic factor applied. */
    val driveSec: Int,
    /** The same drive at free flow (one-to-many). Kept for "drive home from there". */
    val freeFlowSec: Int,
    /** From the station's stop id to the destination. */
    val itinerary: Itinerary,
    /** Leave home: the drive and [ParkRideQuery.parkSec] before [parkedBy]. */
    val leaveAt: Instant,
    /** Parked and leaving the station on foot or by train: the itinerary's start. */
    val parkedBy: Instant,
) {
    /** Where the car is: what "navigate to the station" and the way back aim at. */
    val stationAt: LatLon get() = station.latLon

    /** The car part for the map: no route was requested (budget), so origin → station. */
    fun carPath(origin: LatLon): List<LatLon> = listOf(origin, station.latLon)

    /**
     * Drive home from the station, leaving at [at]. An estimate from the outbound drive —
     * no extra request — with the traffic factor of the return time.
     */
    fun driveHomeSec(at: Instant, traffic: TrafficProfile = TrafficProfile()): Int = traffic.adjust(freeFlowSec, at)
}

data class ParkRideResult(
    val baseline: Itinerary?,
    val options: List<Option<ParkRideOption>>,
    /** Stations whose drive came back within the limit. */
    val stationsInReach: Int,
)

class ParkRidePlanner(
    private val api: TransitApi,
    private val traffic: TrafficProfile = TrafficProfile(),
    private val stations: List<Place> = RailStations.ALL,
) {
    suspend fun plan(q: ParkRideQuery): ParkRideResult = coroutineScope {
        val baselineJob = async {
            best(
                api.plan(
                    PlanRequest(Endpoint.Coord(q.origin), Endpoint.Coord(q.dest), q.departAt, preferences = q.preferences, language = q.language),
                ).itineraries,
            )
        }
        // MOTIS measures free flow, so the cap is shrunk by the traffic factor (like better start).
        val factor = traffic.factorAt(q.departAt)
        val capSec = (q.maxDriveMin * 60 / factor).toInt()
        val nearby = preFilter(stations, q.origin, capSec)

        val reached = if (nearby.isEmpty()) {
            emptyList()
        } else {
            val secs = api.oneToMany(q.origin, nearby.map { it.latLon }, StreetModes.CAR, capSec, arriveBy = false)
            nearby.indices.mapNotNull { i -> secs.getOrNull(i)?.takeIf { it <= capSec }?.let { nearby[i] to it } }
        }
        val chosen = choose(reached, q)

        val jobs = chosen.map { (station, freeFlow) ->
            async {
                val drive = traffic.adjust(freeFlow, q.departAt)
                val earliest = q.departAt.plusSeconds((drive + q.parkSec).toLong())
                api.plan(
                    PlanRequest(
                        from = Endpoint.Stop(requireNotNull(station.stopId)),
                        to = Endpoint.Coord(q.dest),
                        time = earliest,
                        preferences = q.preferences,
                        language = q.language,
                    ),
                ).itineraries.filter { it.startsByTrain() }.map { itin ->
                    // Leave home just in time for this train, not at the search time: the
                    // 08:34 from Be'er Sheva North means leaving Meitar at 08:10, not 07:30.
                    val parkedBy = itin.start
                    val leaveAt = parkedBy.minusSeconds((drive + q.parkSec).toLong())
                    Option(
                        ParkRideOption(station, drive, freeFlow, itin, leaveAt, parkedBy),
                        driverCostSec = drive,
                        arrival = itin.end,
                        transfers = itin.transfers,
                    )
                }
            }
        }
        val baseline = baselineJob.await()
        val gainSec = q.minGainMin * 60L
        val worthIt = jobs.awaitAll().flatten().filter { o ->
            baseline == null ||
                o.arrival.plusSeconds(gainSec) <= baseline.end ||
                (o.transfers < baseline.transfers && !o.arrival.isAfter(baseline.end))
        }
        ParkRideResult(baseline, paretoFront(worthIt), reached.size)
    }

    companion object {
        /** 1 baseline + 1 one-to-many + [ParkRideQuery.maxPlans] (3) plans. */
        const val BUDGET = 5

        /** Faster than any free-flow average on Israeli roads: the straight-line pre-filter
         *  must never drop a station the car could actually reach in time. */
        const val MAX_SPEED_MPS = 30.0

        /** Closer than this, you walk to the station; the baseline already covers it. */
        const val MIN_DISTANCE_M = 1500.0

        /** Stations sent to one-to-many: one request holds them all, but keep the URL short. */
        const val MAX_CANDIDATES = 12

        /** Two stations closer than this serve the same trains (Tel Aviv HaShalom and Center
         *  are 1.3 km apart): planning both would spend a request on one answer. */
        const val MIN_SPACING_M = 2000.0

        /** Rough door-to-door train speed, only to rank stations by where they lead. */
        const val RAIL_MPS = 20.0

        /**
         * Train stations (light rail has no parking) that a car could reach within [capSec]
         * free-flow seconds as the crow flies, and that are not a walk away. Nearest first,
         * at most [MAX_CANDIDATES]. No "towards the destination" cut: Meitar's best station,
         * Be'er Sheva North, is farther from Tel Aviv than Meitar itself; [choose] ranks instead.
         */
        fun preFilter(stations: List<Place>, origin: LatLon, capSec: Int): List<Place> {
            val reachM = capSec * MAX_SPEED_MPS
            return stations.asSequence()
                .filter { it.stopId != null && it.modes.orEmpty().any { m -> m in TransitModes.HEAVY_RAIL } }
                .map { it to Geo.distanceM(origin, it.latLon) }
                .filter { (_, d) -> d in MIN_DISTANCE_M..reachM }
                .sortedBy { it.second }
                .take(MAX_CANDIDATES)
                .map { it.first }
                .toList()
        }

        /**
         * The best [ParkRideQuery.maxPlans] reached stations, by an estimate of the whole trip:
         * the drive plus the crow-flies remainder at [RAIL_MPS]. A close station that leads
         * nowhere loses to a slightly farther one on the line to the destination. Picks are at
         * least [MIN_SPACING_M] apart.
         */
        fun choose(reached: List<Pair<Place, Int>>, q: ParkRideQuery): List<Pair<Place, Int>> {
            val picked = ArrayList<Pair<Place, Int>>()
            for (r in reached.sortedBy { (s, freeFlow) -> freeFlow + Geo.distanceM(s.latLon, q.dest) / RAIL_MPS }) {
                if (picked.size == q.maxPlans) break
                if (picked.none { Geo.distanceM(it.first.latLon, r.first.latLon) < MIN_SPACING_M }) picked += r
            }
            return picked
        }
    }
}

/** The first vehicle is a train: buses from the station's forecourt are not park & ride. */
private fun Itinerary.startsByTrain(): Boolean =
    legs.firstOrNull { it.isTransit }?.mode in TransitModes.HEAVY_RAIL
