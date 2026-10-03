package il.transit.core.api

import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps our traffic to Transitous light, as its usage policy asks:
 *  - caches answers (plans briefly, stops for a day),
 *  - runs at most [maxConcurrent] requests at once,
 *  - on 429/503 waits (Retry-After, else [backoff]) and retries ONCE; a second refusal propagates.
 */
class GuardedTransitApi(
    private val inner: TransitApi,
    private val clock: Clock = Clock.systemUTC(),
    maxConcurrent: Int = 2,
    private val backoff: Duration = Duration.ofSeconds(2),
    private val planTtl: Duration = Duration.ofSeconds(60),
    private val staticTtl: Duration = Duration.ofDays(1),
) : TransitApi {
    private val permits = Semaphore(maxConcurrent)
    private val cache = ConcurrentHashMap<String, Pair<Instant, Any>>()

    override suspend fun plan(req: PlanRequest) =
        cached("plan:" + req.toQuery(), planTtl) { inner.plan(req) }

    override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean) =
        cached("otm:$one:$many:$mode:$maxSeconds:$arriveBy", planTtl) {
            inner.oneToMany(one, many, mode, maxSeconds, arriveBy)
        }

    override suspend fun stops(box: BBox, modes: Set<String>?, language: String) =
        cached("stops:$box:$modes:$language", staticTtl) { inner.stops(box, modes, language) }

    override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int) =
        cached("geo:$text:$language:$near:$max", staticTtl) { inner.geocode(text, language, near, max) }

    override suspend fun reverseGeocode(at: LatLon, language: String, max: Int) =
        cached("rgeo:$at:$language:$max", staticTtl) { inner.reverseGeocode(at, language, max) }

    // Real-time departures: never cached longer than the plan TTL.
    override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String) =
        cached("st:$stopId:$time:$n:$language", Duration.ofSeconds(30)) { inner.stopTimes(stopId, time, n, language) }

    // A trip's live times move; 30 s like departures.
    override suspend fun trip(tripId: String, language: String) =
        cached("trip:$tripId:$language", TRIP_TTL) { inner.trip(tripId, language) }

    // Vehicle positions: the trip sheet refreshes every 30 s, so 20 s keeps a re-open cheap
    // without ever showing a position from two refreshes ago.
    override suspend fun mapTrips(box: BBox, start: Instant, end: Instant, zoom: Double, language: String) =
        cached("mt:$box:$start:$end:$zoom:$language", MAP_TRIPS_TTL) { inner.mapTrips(box, start, end, zoom, language) }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, ttl: Duration, call: suspend () -> T): T {
        val now = clock.instant()
        cache[key]?.let { (expires, value) -> if (now.isBefore(expires)) return value as T }
        val value = permits.withPermit { withRetry(call) }
        cache[key] = now.plus(ttl) to value
        return value
    }

    private suspend fun <T> withRetry(call: suspend () -> T): T = try {
        call()
    } catch (e: TransitHttpException) {
        if (e.code != 429 && e.code != 503) throw e
        delay(e.retryAfterSec?.let { it * 1000L } ?: backoff.toMillis())
        call()
    }

    companion object {
        val TRIP_TTL: Duration = Duration.ofSeconds(30)
        val MAP_TRIPS_TTL: Duration = Duration.ofSeconds(20)
    }
}

class BudgetExceededException(val budget: Int) :
    RuntimeException("search exceeded its budget of $budget requests")

/**
 * Hard cap on requests for ONE feature search. Every special feature runs through one
 * of these, so a bug in candidate pruning shows up as an exception in a test rather
 * than as a flood of requests to a volunteer-run server. Cache hits still count:
 * the cap is on what the feature *asks for*, which is what a test can pin down.
 */
class BudgetedTransitApi(private val inner: TransitApi, val budget: Int) : TransitApi {
    @Volatile var used = 0
        private set

    @Synchronized private fun take() {
        if (used >= budget) throw BudgetExceededException(budget)
        used++
    }

    override suspend fun plan(req: PlanRequest): PlanResponse { take(); return inner.plan(req) }

    override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean): List<Int?> {
        take(); return inner.oneToMany(one, many, mode, maxSeconds, arriveBy)
    }

    override suspend fun stops(box: BBox, modes: Set<String>?, language: String): List<Place> {
        take(); return inner.stops(box, modes, language)
    }

    override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
        take(); return inner.geocode(text, language, near, max)
    }

    override suspend fun reverseGeocode(at: LatLon, language: String, max: Int): List<GeocodeMatch> {
        take(); return inner.reverseGeocode(at, language, max)
    }

    override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String): StopTimesResponse {
        take(); return inner.stopTimes(stopId, time, n, language)
    }

    override suspend fun trip(tripId: String, language: String): Itinerary {
        take(); return inner.trip(tripId, language)
    }

    override suspend fun mapTrips(box: BBox, start: Instant, end: Instant, zoom: Double, language: String): List<TripSegment> {
        take(); return inner.mapTrips(box, start, end, zoom, language)
    }
}
