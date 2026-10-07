package il.transit.core.search

import il.transit.core.api.GeocodeArea
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.MotisClient
import il.transit.core.api.TransitHttpException
import il.transit.core.geo.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * A second geocoder, asked only when Transitous has no street match ([PlaceSearch]). Behind
 * an interface so the service is swappable. In the app: the phone's own Geocoder first
 * (`AndroidGeocoder`), then [PhotonClient] ([FallbackGeocoder]); owner approved both,
 * 2026-10-07. Answers come back in Transitous's shape, so the rest of the app (detail line,
 * town, suggestions) treats them alike.
 */
interface BackupGeocoder {
    suspend fun search(text: String, language: String, near: LatLon?, max: Int = PlaceSearch.BACKUP_MAX): List<GeocodeMatch>

    /** A name for the point ("טבנקין 15, רעננה"), or null when this geocoder has none / doesn't reverse. */
    suspend fun reverseName(at: LatLon, language: String): String? = null
}

/**
 * [first] (the phone's Geocoder; null when the phone has none), then [second] (Photon) only
 * when [first] errs, times out or finds nothing. So one search asks each at most once.
 * Reverse names: [first] only, then [second]; a failure is null, never an exception.
 */
class FallbackGeocoder(private val first: BackupGeocoder?, private val second: BackupGeocoder) : BackupGeocoder {
    override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
        val found = first?.let { orNull { it.search(text, language, near, max) } }
        return if (!found.isNullOrEmpty()) found else second.search(text, language, near, max)
    }

    override suspend fun reverseName(at: LatLon, language: String): String? =
        first?.let { orNull { it.reverseName(at, language) } } ?: orNull { second.reverseName(at, language) }

    private suspend fun <T> orNull(call: suspend () -> T): T? = try {
        call()
    } catch (e: kotlinx.coroutines.CancellationException) {
        // A timeout inside [first] is its own TimeoutCancellationException: a miss, not ours to rethrow.
        if (e is kotlinx.coroutines.TimeoutCancellationException) null else throw e
    } catch (e: Exception) {
        null
    }
}

/**
 * Photon (`GET /api`, OSM data, by komoot). Its public instance allows search-as-you-type at
 * fair use; Nominatim's forbids autocomplete (see `dead-ends`). Same rules as Transitous: our
 * User-Agent with the repo as contact, light traffic ([GuardedGeocoder]), and moving to
 * another Photon (or a self-hosted one) is a base-URL change.
 *
 * Bias: `lat`/`lon` (the user, else the middle of Israel) and a `bbox` around Israel, so a
 * street name shared with a town abroad never shows. Language: Photon's public instance
 * knows only default/en/de/fr; `default` is OSM's local `name`, which is Hebrew in Israel,
 * so Hebrew asks for `default` (a `he` it doesn't know would be refused).
 */
class PhotonClient(
    baseUrl: String = PHOTON,
    private val http: OkHttpClient = MotisClient.defaultHttp(),
    private val userAgent: String = MotisClient.USER_AGENT,
) : BackupGeocoder {
    private val base: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> =
        withContext(Dispatchers.IO) {
            val bias = near ?: ISRAEL_CENTRE
            val url = base.newBuilder().addPathSegment("api")
                .addQueryParameter("q", text)
                .addQueryParameter("lang", photonLanguage(language))
                .addQueryParameter("lat", bias.lat.toString())
                .addQueryParameter("lon", bias.lon.toString())
                .addQueryParameter("bbox", ISRAEL_BBOX)
                .addQueryParameter("limit", max.toString())
                .build()
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            http.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw TransitHttpException(resp.code, body.take(300), resp.header("Retry-After")?.toIntOrNull())
                parsePhoton(body).take(max)
            }
        }

    companion object {
        const val PHOTON = "https://photon.komoot.io"

        /** minLon,minLat,maxLon,maxLat: Israel, the Golan and Eilat, a little sea. */
        const val ISRAEL_BBOX = "34.2,29.45,35.95,33.35"
        val ISRAEL_CENTRE = LatLon(31.8, 34.95)

        fun photonLanguage(language: String): String = if (language == "en") "en" else "default"
    }
}

@Serializable
internal data class PhotonResponse(val features: List<PhotonFeature> = emptyList())

@Serializable
internal data class PhotonFeature(val geometry: PhotonGeometry, val properties: PhotonProperties = PhotonProperties())

@Serializable
internal data class PhotonGeometry(val coordinates: List<Double> = emptyList())

@Serializable
internal data class PhotonProperties(
    val osm_type: String? = null,
    val osm_id: Long? = null,
    val osm_value: String? = null,
    val type: String? = null,
    val name: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val city: String? = null,
    val district: String? = null,
    val state: String? = null,
    val country: String? = null,
)

private val PhotonJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/**
 * Photon's GeoJSON as [GeocodeMatch]es: an unnamed house is an ADDRESS named "street number", a
 * street an ADDRESS, anything else a PLACE (category = its OSM value). The city is the admin-level-8
 * area, so the suggestion's grey line names the town, as for Transitous answers.
 */
fun parsePhoton(body: String): List<GeocodeMatch> =
    PhotonJson.decodeFromString(PhotonResponse.serializer(), body).features.mapNotNull { f ->
        val p = f.properties
        val c = f.geometry.coordinates
        if (c.size < 2) return@mapNotNull null
        // A house with no name of its own is an address; a named one (a hospital at no. 151) is a place.
        val address = p.type == "street" || (p.name == null && (p.type == "house" || p.housenumber != null))
        val name = when {
            p.name != null -> p.name
            p.street != null -> listOfNotNull(p.street, p.housenumber).joinToString(" ")
            else -> return@mapNotNull null
        }
        val areas = buildList {
            p.country?.let { add(GeocodeArea(it, 2.0)) }
            p.state?.let { add(GeocodeArea(it, 4.0)) }
            p.city?.let { add(GeocodeArea(it, 8.0, default = true)) }
            p.district?.let { add(GeocodeArea(it, 10.0)) }
        }
        GeocodeMatch(
            type = if (address) "ADDRESS" else "PLACE",
            name = name,
            id = "photon:${p.osm_type.orEmpty()}${p.osm_id ?: ""}",
            lat = c[1],
            lon = c[0],
            street = p.street ?: if (p.type == "street") p.name else null,
            houseNumber = p.housenumber,
            category = if (address) null else p.osm_value,
            areas = areas,
        )
    }

/**
 * Keeps traffic to the backup geocoder light, like [il.transit.core.api.GuardedTransitApi]
 * does for Transitous: answers cached for a day, at most [maxConcurrent] requests at once,
 * one retry after a 429/503 (Retry-After, else [backoff]); a second refusal propagates.
 */
class GuardedGeocoder(
    private val inner: BackupGeocoder,
    private val clock: Clock = Clock.systemUTC(),
    maxConcurrent: Int = 2,
    private val backoff: Duration = Duration.ofSeconds(2),
    private val ttl: Duration = Duration.ofDays(1),
) : BackupGeocoder {
    private val permits = Semaphore(maxConcurrent)
    private val cache = ConcurrentHashMap<String, Pair<Instant, List<GeocodeMatch>>>()

    override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
        val key = "$text:$language:$near:$max"
        val now = clock.instant()
        cache[key]?.let { (expires, value) -> if (now.isBefore(expires)) return value }
        val value = permits.withPermit {
            try {
                inner.search(text, language, near, max)
            } catch (e: TransitHttpException) {
                if (e.code != 429 && e.code != 503) throw e
                delay(e.retryAfterSec?.let { it * 1000L } ?: backoff.toMillis())
                inner.search(text, language, near, max)
            }
        }
        cache[key] = now.plus(ttl) to value
        return value
    }

    private val names = ConcurrentHashMap<String, Pair<Instant, String?>>()

    override suspend fun reverseName(at: LatLon, language: String): String? {
        val key = "$at:$language"
        val now = clock.instant()
        names[key]?.let { (expires, value) -> if (now.isBefore(expires)) return value }
        val value = permits.withPermit { inner.reverseName(at, language) }
        if (value != null) names[key] = now.plus(ttl) to value // a miss may be offline: ask again next time
        return value
    }
}
