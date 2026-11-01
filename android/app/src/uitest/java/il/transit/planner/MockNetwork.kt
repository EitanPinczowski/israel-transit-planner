package il.transit.planner

import il.transit.core.api.GeocodeMatch
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.Itinerary
import il.transit.core.api.MotisClient
import il.transit.core.api.MotisJson
import il.transit.core.api.Place
import il.transit.core.api.PlanRequest
import il.transit.core.api.PlanResponse
import il.transit.core.api.ReplayTransitApi
import il.transit.core.api.StopTimesResponse
import il.transit.core.api.TransitApi
import il.transit.core.api.TripSegment
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import il.transit.core.search.BackupGeocoder
import il.transit.core.search.FallbackGeocoder
import il.transit.core.search.GuardedGeocoder
import il.transit.core.search.PhotonClient
import il.transit.core.weather.GuardedWeather
import il.transit.core.weather.OpenMeteoClient
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * The network-fault mode of the `uitest` build (Phase 11 T4). The app talks real HTTP, through its
 * real clients and guards, to a `MockWebServer` the fault-matrix test runs on the emulator's
 * localhost ([base]); nothing reaches the internet. Set as [UiTestApp.network] before the Activity
 * starts; every other test keeps the replay.
 *
 *  - Transitous: [GuardedTransitApi] over [MotisClient] at `<base>/motis/<id>`. What the server sends
 *    when it answers normally is the replay's answer to the same call, as MOTIS JSON, kept under
 *    `<id>` ([answer]): the server only decides whether it answers, and how it fails.
 *  - Photon at `<base>/photon` (the phone's own Geocoder is left out: Google would answer it).
 *  - Open-Meteo at `<base>/meteo`.
 *
 * Read timeouts are [TIMEOUT_S] (live: 20 s), so "the server never answers" costs the matrix seconds,
 * not minutes. [clearCaches] starts fresh guards, so each fault case sends its own requests.
 */
class MockNetwork(private val replay: ReplayTransitApi, private val clock: Clock, base: String) {
    /** The server's root URL; moved when the test restarts the server after "offline". */
    @Volatile var base: String = base.trimEnd('/')

    private val http = OkHttpClient
        .Builder()
        .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    private val ids = AtomicLong()
    private val answers = ConcurrentHashMap<Long, String>()

    /** The normal answer for the call numbered [id] (the path segment after `/motis/`), or null. */
    fun answer(id: Long): String? = answers[id]

    private suspend fun <T> motis(json: String, call: suspend (MotisClient) -> T): T {
        val id = ids.incrementAndGet()
        answers[id] = json
        return try {
            call(MotisClient("$base/motis/$id", http))
        } finally {
            answers.remove(id)
        }
    }

    private val wire: TransitApi = object : TransitApi {
        override suspend fun plan(req: PlanRequest): PlanResponse =
            motis(MotisJson.encodeToString(PlanResponse.serializer(), replay.plan(req))) { it.plan(req) }

        override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean): List<Int?> {
            val durations = replay.oneToMany(one, many, mode, maxSeconds, arriveBy)
            val json = durations.joinToString(",", "[", "]") { d -> if (d == null) "{}" else "{\"duration\":$d}" }
            return motis(json) { it.oneToMany(one, many, mode, maxSeconds, arriveBy) }
        }

        override suspend fun stops(box: BBox, modes: Set<String>?, language: String): List<Place> =
            motis(MotisJson.encodeToString(ListSerializer(Place.serializer()), replay.stops(box, modes, language))) { it.stops(box, modes, language) }

        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> =
            motis(MotisJson.encodeToString(ListSerializer(GeocodeMatch.serializer()), replay.geocode(text, language, near, max))) {
                it.geocode(text, language, near, max)
            }

        override suspend fun reverseGeocode(at: LatLon, language: String, max: Int): List<GeocodeMatch> =
            motis(MotisJson.encodeToString(ListSerializer(GeocodeMatch.serializer()), replay.reverseGeocode(at, language, max))) {
                it.reverseGeocode(at, language, max)
            }

        override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String, pageCursor: String?): StopTimesResponse =
            motis(MotisJson.encodeToString(StopTimesResponse.serializer(), replay.stopTimes(stopId, time, n, language, pageCursor))) {
                it.stopTimes(stopId, time, n, language, pageCursor)
            }

        override suspend fun trip(tripId: String, language: String): Itinerary =
            motis(MotisJson.encodeToString(Itinerary.serializer(), replay.trip(tripId, language))) { it.trip(tripId, language) }

        override suspend fun mapTrips(box: BBox, start: Instant, end: Instant, zoom: Double, language: String): List<TripSegment> =
            motis(MotisJson.encodeToString(ListSerializer(TripSegment.serializer()), replay.mapTrips(box, start, end, zoom, language))) {
                it.mapTrips(box, start, end, zoom, language)
            }
    }

    @Volatile private var guard: TransitApi = GuardedTransitApi(wire, clock)

    @Volatile private var geocoder: BackupGeocoder = photon()

    /**
     * One weather guard for the whole run: the ViewModel keeps the one it was built with. Its source
     * reads [base] on every call, so it follows the server across "offline"; each case of the rain
     * screen asks for another cell, so its 1 h cache never hides a request.
     */
    val weather: GuardedWeather = GuardedWeather({ cell -> OpenMeteoClient("$base/meteo", http).rain(cell) }, clock)

    private fun photon(): BackupGeocoder = FallbackGeocoder(null, GuardedGeocoder(PhotonClient("$base/photon"), clock))

    /** Fresh Transitous and Photon guards (empty caches) on the current [base]; see [weather] for Open-Meteo. */
    fun clearCaches() {
        guard = GuardedTransitApi(wire, clock)
        geocoder = photon()
    }

    /** What the app's [TransitApp.api] is in this mode. */
    val api: TransitApi = object : TransitApi {
        override suspend fun plan(req: PlanRequest) = guard.plan(req)
        override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean) =
            guard.oneToMany(one, many, mode, maxSeconds, arriveBy)
        override suspend fun stops(box: BBox, modes: Set<String>?, language: String) = guard.stops(box, modes, language)
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int) = guard.geocode(text, language, near, max)
        override suspend fun reverseGeocode(at: LatLon, language: String, max: Int) = guard.reverseGeocode(at, language, max)
        override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String, pageCursor: String?) =
            guard.stopTimes(stopId, time, n, language, pageCursor)
        override suspend fun trip(tripId: String, language: String) = guard.trip(tripId, language)
        override suspend fun mapTrips(box: BBox, start: Instant, end: Instant, zoom: Double, language: String) =
            guard.mapTrips(box, start, end, zoom, language)
    }

    /** The backup geocoder: Photon only (no phone Geocoder), behind its own guard. */
    val backup: BackupGeocoder = object : BackupGeocoder {
        override suspend fun search(text: String, language: String, near: LatLon?, max: Int) = geocoder.search(text, language, near, max)
        override suspend fun reverseName(at: LatLon, language: String) = geocoder.reverseName(at, language)
    }

    companion object {
        const val TIMEOUT_S = 3L
    }
}
