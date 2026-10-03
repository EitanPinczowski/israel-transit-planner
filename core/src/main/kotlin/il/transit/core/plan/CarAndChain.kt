package il.transit.core.plan

import il.transit.core.api.Endpoint
import il.transit.core.api.Itinerary
import il.transit.core.api.PlanRequest
import il.transit.core.api.StreetModes
import il.transit.core.api.TransitApi
import il.transit.core.features.TrafficProfile
import il.transit.core.geo.LatLon
import il.transit.core.user.UserSettings
import java.time.Instant
import kotlin.math.ceil

/** The same trip by car: minutes with the traffic factor at that hour, and the distance. */
data class CarTime(val minutes: Int, val factor: Double, val km: Double)

/**
 * "🚗 By car?": one street-only request (MOTIS skips the transit search), the drive scaled by
 * [TrafficProfile] like every other drive in the app. No taxi price: the official tariff could
 * not be confirmed from a reachable source (2026-10), so the app links to MOT's calculator.
 */
class CarCompare(private val api: TransitApi) {
    suspend fun drive(from: LatLon, to: LatLon, at: Instant, traffic: TrafficProfile): CarTime? {
        val car = api.plan(
            PlanRequest(
                from = Endpoint.Coord(from),
                to = Endpoint.Coord(to),
                time = at,
                directModes = listOf(StreetModes.CAR),
                directOnly = true,
                maxDirectSec = MAX_DRIVE_SEC,
            ),
        ).direct.firstOrNull { it.legs.any { l -> l.mode == StreetModes.CAR } } ?: return null
        return CarTime(
            minutes = ceil(traffic.adjust(car.duration, at) / 60.0).toInt(),
            factor = traffic.factorAt(at),
            km = car.legs.sumOf { it.distance ?: 0.0 } / 1000,
        )
    }

    companion object {
        const val BUDGET = 1
        const val MAX_DRIVE_SEC = 4 * 3600
        const val TAXI_CALCULATOR_URL = "https://bus.gov.il/taxicalculator"
    }
}

/** A stop on an errand chain: where, and how long to stay there before moving on. */
data class ChainStop(val at: LatLon, val name: String?, val stayMin: Int = 0)

/**
 * The chain's legs in order. [failedAt] is the index of the first leg with no way to make it
 * (the rest are not planned); null when every leg was found.
 */
data class ChainResult(val legs: List<Itinerary>, val failedAt: Int?) {
    val leaveAt: Instant? get() = legs.firstOrNull()?.start
    val arriveAt: Instant? get() = legs.lastOrNull()?.end?.takeIf { failedAt == null }
}

/**
 * Errands: A → B → C …, each leg leaving once the previous one arrives plus the stay there.
 * One ordinary plan per leg (at most [BUDGET]); each leg takes its earliest-arriving option,
 * or walking all the way when that is all there is.
 */
class ChainPlanner(api: TransitApi) {
    private val planner = TripPlanner(api)

    suspend fun plan(from: LatLon, stops: List<ChainStop>, to: LatLon, departAt: Instant, settings: UserSettings, language: String): ChainResult {
        require(stops.size <= MAX_STOPS) { "at most $MAX_STOPS stops on the way" }
        val points = listOf(ChainStop(from, null)) + stops + listOf(ChainStop(to, null))
        val legs = mutableListOf<Itinerary>()
        var leaveAt = departAt
        for (i in 0 until points.size - 1) {
            val r = planner.plan(
                TripQuery(Endpoint.Coord(points[i].at), Endpoint.Coord(points[i + 1].at), TimeMode.DEPART_AT, leaveAt, settings, language),
            )
            val leg = r.itineraries.firstOrNull() ?: r.walkOnly ?: return ChainResult(legs, failedAt = i)
            legs += leg
            leaveAt = leg.end.plusSeconds(points[i + 1].stayMin * 60L)
        }
        return ChainResult(legs, failedAt = null)
    }

    companion object {
        const val MAX_STOPS = 3
        const val BUDGET = MAX_STOPS + 1
        val STAY_CHOICES = listOf(0, 15, 30, 60)
    }
}
