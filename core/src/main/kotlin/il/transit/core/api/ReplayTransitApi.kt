package il.transit.core.api

import il.transit.core.features.ISRAEL
import il.transit.core.geo.BBox
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import il.transit.core.geo.RailStations
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [TransitApi] that answers from answers recorded on Transitous
 * (`core/src/test/resources/fixtures`), never the network. For UI tests, the on-device
 * test build (`uitest`), monkey runs and Test Lab crawlers, so they cost Transitous nothing.
 *
 * Recorded trips are moved in time to the request (first departure 5 min after a
 * depart-at, last arrival 5 min before an arrive-by), so screens read like a live answer.
 * A plain transit search to or from somewhere remote (outside Be'er Sheva, no station
 * within 2 km — Meitar, say) gets the Be'er Sheva answer made [SLOWER_ELSEWHERE_MIN]
 * slower, as the real trip is: the car features compare against that baseline, so every
 * feature has options to show.
 *
 * [failing] makes every call throw, as with no signal. [load] returns a fixture's text by
 * name (without `.json`); [clock] is "now" for a departures board asked without a time.
 */
class ReplayTransitApi(
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
    private val load: (String) -> String,
) : TransitApi {
    @Volatile var failing: Boolean = false

    /** Calls answered, all kinds. Tests pin request counts with it. */
    val calls = AtomicInteger()

    override suspend fun plan(req: PlanRequest): PlanResponse {
        hit()
        val car = StreetModes.CAR_LIKE
        val name = when {
            req.directOnly -> "plan_direct_car"
            req.preTransitModes.any { it in car } -> "plan_car_pre_meitar"
            req.postTransitModes.any { it in car } -> "plan_car_post_pickup"
            req.time.atZone(ISRAEL).dayOfWeek == DayOfWeek.FRIDAY -> "plan_friday_bs_telaviv"
            else -> "plan_bgu_telaviv"
        }
        val raw = MotisJson.parseToJsonElement(load(name))
        if (name == "plan_direct_car") return MotisJson.decodeFromJsonElement(PlanResponse.serializer(), raw)
        val recorded = MotisJson.decodeFromJsonElement(PlanResponse.serializer(), raw).let { r ->
            // Like the server: no car leg longer than the cap the feature asked for.
            r.copy(itineraries = r.itineraries.filter { it.fitsCaps(req) })
        }
        val its = recorded.itineraries.ifEmpty { return recorded }
        val slower = if (!name.startsWith("plan_car") && listOf(req.from, req.to).any(::remote)) SLOWER_ELSEWHERE_MIN else 0L
        val delta = if (req.arriveBy) {
            Duration.between(its.maxOf { it.end }, req.time.minus(Duration.ofMinutes(5)))
        } else {
            Duration.between(its.minOf { it.start }, req.time.plus(Duration.ofMinutes(5 + slower)))
        }.truncatedTo(ChronoUnit.MINUTES)
        return MotisJson.decodeFromJsonElement(PlanResponse.serializer(), shift(raw, delta))
    }

    /** No recording fits every candidate list, so drives are estimated: straight line at 72 km/h plus
     *  2 min, which puts
     *  stations on the way a few minutes off the route, as real ones are. */
    override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean): List<Int?> {
        hit()
        return many.map { m -> (Geo.distanceM(one, m) / 20.0 + 120).toInt().takeIf { it <= maxSeconds } }
    }

    override suspend fun stops(box: BBox, modes: Set<String>?, language: String): List<Place> {
        hit()
        return MotisJson.decodeFromString(ListSerializer(Place.serializer()), load("map_stops_beersheva_north"))
    }

    override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
        hit()
        return MotisJson.decodeFromString(ListSerializer(GeocodeMatch.serializer()), load("geocode_rager")).take(max)
    }

    override suspend fun reverseGeocode(at: LatLon, language: String, max: Int): List<GeocodeMatch> {
        hit()
        return MotisJson.decodeFromString(ListSerializer(GeocodeMatch.serializer()), load("reverse_geocode_bgu")).take(max)
    }

    override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String): StopTimesResponse {
        hit()
        val raw = MotisJson.parseToJsonElement(load("stoptimes_beersheva_north"))
        val recorded = MotisJson.decodeFromJsonElement(StopTimesResponse.serializer(), raw)
        val first = recorded.stopTimes.mapNotNull { st -> (st.place.departure ?: st.place.arrival)?.let(::parseTime) }.minOrNull()
            ?: return recorded
        val delta = Duration.between(first, (time ?: clock.instant()).plus(Duration.ofMinutes(2))).truncatedTo(ChronoUnit.MINUTES)
        val shifted = MotisJson.decodeFromJsonElement(StopTimesResponse.serializer(), shift(raw, delta))
        return shifted.copy(stopTimes = shifted.stopTimes.take(n))
    }

    private fun hit() {
        calls.incrementAndGet()
        if (failing) throw java.io.IOException("replay: no signal (failing = true)")
    }

    /** Outside Be'er Sheva and over 2 km from any station: the recording is too fast for it. */
    private fun remote(e: Endpoint): Boolean {
        val at = (e as? Endpoint.Coord)?.at ?: return false
        return Geo.distanceM(at, BEER_SHEVA) > NEAR_M && RailStations.ALL.none { Geo.distanceM(at, it.latLon) <= STATION_M }
    }

    private fun Itinerary.fitsCaps(req: PlanRequest): Boolean {
        val firstTransit = legs.indexOfFirst { it.isTransit }.takeIf { it >= 0 } ?: return true
        val lastTransit = legs.indexOfLast { it.isTransit }
        val pre = legs.take(firstTransit).filter { it.mode in StreetModes.CAR_LIKE }.sumOf { it.duration }
        val post = legs.drop(lastTransit + 1).filter { it.mode in StreetModes.CAR_LIKE }.sumOf { it.duration }
        return (req.maxPreTransitSec == null || pre <= req.maxPreTransitSec) &&
            (req.maxPostTransitSec == null || post <= req.maxPostTransitSec)
    }

    companion object {
        /** Be'er Sheva centre: plain searches starting within [NEAR_M] get the recording as is. */
        val BEER_SHEVA = LatLon(31.2520, 34.7915)
        const val NEAR_M = 10_000.0
        const val STATION_M = 2_000.0
        const val SLOWER_ELSEWHERE_MIN = 75L

        /** Every fixture this replays. A test checks they all exist and decode. */
        val FIXTURES = listOf(
            "plan_direct_car", "plan_car_pre_meitar", "plan_car_post_pickup", "plan_friday_bs_telaviv",
            "plan_bgu_telaviv", "map_stops_beersheva_north", "geocode_rager",
            "reverse_geocode_bgu", "stoptimes_beersheva_north",
        )

        private val INSTANT = Regex("""\d{4}-\d\d-\d\dT\d\d:\d\d(:\d\d(\.\d+)?)?Z""")

        /** Moves every ISO-8601 UTC timestamp in [e] by [delta]. */
        fun shift(e: JsonElement, delta: Duration): JsonElement = when (e) {
            is JsonObject -> JsonObject(e.mapValues { (_, v) -> shift(v, delta) })
            is JsonArray -> JsonArray(e.map { shift(it, delta) })
            is JsonPrimitive ->
                if (e.isString && INSTANT.matches(e.content)) JsonPrimitive(Instant.parse(e.content).plus(delta).toString()) else e
        }
    }
}
