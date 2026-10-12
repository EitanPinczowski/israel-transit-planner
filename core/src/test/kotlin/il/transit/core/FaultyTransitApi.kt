package il.transit.core

import il.transit.core.api.GeocodeMatch
import il.transit.core.api.Itinerary
import il.transit.core.api.Place
import il.transit.core.api.PlanRequest
import il.transit.core.api.PlanResponse
import il.transit.core.api.StopTimesResponse
import il.transit.core.api.TransitApi
import il.transit.core.api.TransitHttpException
import il.transit.core.api.TripSegment
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import il.transit.core.search.BackupGeocoder
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/** What the next call to a [FaultyTransitApi] / [FaultyGeocoder] does instead of answering. */
sealed interface Fault {
    /** The server refuses: a [TransitHttpException] with this code (and `Retry-After`). */
    data class Http(val code: Int, val retryAfterSec: Int? = null) : Fault

    /** The answer takes [ms] (virtual time in `runTest`), then comes normally. */
    data class Slow(val ms: Long) : Fault

    /** Never answers; only cancellation ends the call. */
    data object Hang : Fault

    /** The network gives up: OkHttp's read timeout is a [SocketTimeoutException]. */
    data object Timeout : Fault
}

/** Counts calls and how many run at once; the faults come off [script] one per call, then it answers. */
class CallLog {
    val script = ConcurrentLinkedQueue<Fault>()
    private val started = AtomicInteger()
    private val inFlightNow = AtomicInteger()
    private val maxInFlightSeen = AtomicInteger()
    val calls: Int get() = started.get()
    val inFlight: Int get() = inFlightNow.get()
    val maxInFlight: Int get() = maxInFlightSeen.get()

    suspend fun <T> run(answer: () -> T): T {
        started.incrementAndGet()
        val now = inFlightNow.incrementAndGet()
        maxInFlightSeen.accumulateAndGet(now) { a, b -> maxOf(a, b) }
        try {
            when (val fault = script.poll()) {
                is Fault.Http -> throw TransitHttpException(fault.code, "scripted", fault.retryAfterSec)
                is Fault.Slow -> delay(fault.ms)
                Fault.Hang -> awaitCancellation()
                Fault.Timeout -> throw SocketTimeoutException("scripted read timeout")
                null -> {}
            }
            return answer()
        } finally {
            inFlightNow.decrementAndGet()
        }
    }
}

/** A [TransitApi] that fails the way a volunteer-run server does. No socket involved. */
class FaultyTransitApi(vararg faults: Fault) : TransitApi {
    private val log = CallLog().also { it.script.addAll(faults) }
    val script get() = log.script
    val calls get() = log.calls
    val inFlight get() = log.inFlight
    val maxInFlight get() = log.maxInFlight

    override suspend fun plan(req: PlanRequest) = log.run { PlanResponse() }

    override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean) =
        log.run { many.map { 60 } }

    override suspend fun stops(box: BBox, modes: Set<String>?, language: String) = log.run { emptyList<Place>() }

    override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int) =
        log.run { listOf(GeocodeMatch("PLACE", text, "id:$text", 32.0, 34.8)) }

    override suspend fun reverseGeocode(at: LatLon, language: String, max: Int) =
        log.run { listOf(GeocodeMatch("ADDRESS", "here", "rev", at.lat, at.lon)) }

    override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String) =
        log.run { StopTimesResponse() }

    override suspend fun trip(tripId: String, language: String): Itinerary =
        log.run { itinerary(leg("BUS", place("a", LatLon(32.0, 34.8)), place("b", LatLon(32.1, 34.8)), NOON, NOON.plusSeconds(600))) }

    override suspend fun mapTrips(box: BBox, start: Instant, end: Instant, zoom: Double, language: String) =
        log.run { emptyList<TripSegment>() }
}

/** The same for the backup geocoders (the phone's Geocoder, Photon). */
class FaultyGeocoder(vararg faults: Fault) : BackupGeocoder {
    private val log = CallLog().also { it.script.addAll(faults) }
    val calls get() = log.calls
    val inFlight get() = log.inFlight
    val maxInFlight get() = log.maxInFlight

    override suspend fun search(text: String, language: String, near: LatLon?, max: Int) =
        log.run { listOf(GeocodeMatch("PLACE", text, "id:$text", 32.0, 34.8)) }

    override suspend fun reverseName(at: LatLon, language: String): String? = log.run { "here" }
}

/** One canned HTTP answer (or a network failure) for [scriptedHttp]. */
data class Reply(val code: Int = 200, val body: String = "", val headers: Map<String, String> = emptyMap(), val fail: java.io.IOException? = null)

/** An [OkHttpClient] whose interceptor answers from [replies] in order (the last one repeats). It never
 *  proceeds down the chain, so nothing is opened: not a socket, not even localhost. */
class ScriptedHttp(vararg replies: Reply) {
    private val queue = ConcurrentLinkedQueue(replies.toList())
    private var last: Reply = replies.last()
    val requests = ConcurrentLinkedQueue<Request>()
    val count: Int get() = requests.size

    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        requests += chain.request()
        val reply = queue.poll()?.also { last = it } ?: last
        reply.fail?.let { throw it }
        Response.Builder()
            .request(chain.request()).protocol(Protocol.HTTP_1_1).code(reply.code).message("scripted")
            .apply { reply.headers.forEach { (k, v) -> header(k, v) } }
            .body(reply.body.toResponseBody("application/json".toMediaType()))
            .build()
    }).build()
}
