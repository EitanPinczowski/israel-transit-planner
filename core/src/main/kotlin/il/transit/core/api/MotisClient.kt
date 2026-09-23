package il.transit.core.api

import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * HTTP client for a MOTIS server (Transitous by default).
 *
 * Transitous usage policy (https://transitous.org/api/): open-source, non-commercial,
 * light traffic, and a User-Agent that says who we are and how to reach us.
 * [USER_AGENT] carries the repo URL as the contact; do not remove it.
 */
class MotisClient(
    baseUrl: String = TRANSITOUS,
    private val http: OkHttpClient = defaultHttp(),
    private val userAgent: String = USER_AGENT,
) : TransitApi {
    private val base: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    override suspend fun plan(req: PlanRequest): PlanResponse =
        get("api/v6/plan", req.toQuery()) { MotisJson.decodeFromString(PlanResponse.serializer(), it) }

    override suspend fun oneToMany(
        one: LatLon,
        many: List<LatLon>,
        mode: String,
        maxSeconds: Int,
        arriveBy: Boolean,
    ): List<Int?> {
        if (many.isEmpty()) return emptyList()
        val params = listOf(
            "one" to one.semicolon(),
            "many" to many.joinToString(",") { it.semicolon() },
            "mode" to mode,
            "max" to maxSeconds.toString(),
            "maxMatchingDistance" to MAX_MATCHING_M.toString(),
            "arriveBy" to arriveBy.toString(),
        )
        val entries = get("api/v1/one-to-many", params) {
            MotisJson.decodeFromString(ListSerializer(DurationEntry.serializer()), it)
        }
        check(entries.size == many.size) { "one-to-many returned ${entries.size} for ${many.size}" }
        return entries.map { e -> e.duration?.toInt() }
    }

    override suspend fun stops(box: BBox, modes: Set<String>?, language: String): List<Place> {
        val params = buildList {
            add("min" to box.min.comma())
            add("max" to box.max.comma())
            modes?.let { add("modes" to it.sorted().joinToString(",")) }
            add("language" to language)
        }
        return get("api/v6/map/stops", params) {
            MotisJson.decodeFromString(ListSerializer(Place.serializer()), it)
        }
    }

    override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
        val params = buildList {
            add("text" to text)
            add("language" to language)
            add("numResults" to max.toString())
            near?.let { add("place" to it.comma()) }
        }
        return get("api/v1/geocode", params) {
            MotisJson.decodeFromString(ListSerializer(GeocodeMatch.serializer()), it)
        }
    }

    override suspend fun reverseGeocode(at: LatLon, language: String, max: Int): List<GeocodeMatch> {
        val params = listOf("place" to at.comma(), "language" to language, "numResults" to max.toString())
        return get("api/v1/reverse-geocode", params) {
            MotisJson.decodeFromString(ListSerializer(GeocodeMatch.serializer()), it)
        }
    }

    override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String): StopTimesResponse {
        val params = buildList {
            add("stopId" to stopId)
            time?.let { add("time" to it.toString()) }
            add("n" to n.toString())
            add("language" to language)
        }
        return get("api/v6/stoptimes", params) { MotisJson.decodeFromString(StopTimesResponse.serializer(), it) }
    }

    private suspend fun <T> get(path: String, params: List<Pair<String, String>>, parse: (String) -> T): T =
        withContext(Dispatchers.IO) {
            val url = base.newBuilder().addPathSegments(path).apply {
                params.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            http.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw TransitHttpException(
                        resp.code,
                        body.take(300),
                        resp.header("Retry-After")?.toIntOrNull(),
                    )
                }
                parse(body)
            }
        }

    companion object {
        const val TRANSITOUS = "https://api.transitous.org"
        const val USER_AGENT =
            "IsraelTransitPlanner/0.1 (+https://github.com/EitanPinczowski/israel-transit-planner)"
        const val MAX_MATCHING_M = 250

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
