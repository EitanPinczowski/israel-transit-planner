package il.transit.core.weather

import il.transit.core.api.MotisClient
import il.transit.core.api.TransitHttpException
import il.transit.core.api.retryWait
import il.transit.core.geo.LatLon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One forecast hour, [start] to [start] + 1 h: chance of more than 0.1 mm, and how much falls. */
data class RainHour(val start: Instant, val probability: Int?, val mm: Double?) {
    val end: Instant get() = start.plusSeconds(3600)
}

data class RainForecast(val hours: List<RainHour>)

/** Where rain forecasts come from; [OpenMeteoClient] in the app, a fake in tests. */
fun interface WeatherSource {
    suspend fun rain(cell: LatLon): RainForecast
}

/**
 * Open-Meteo's free forecast (`GET /v1/forecast`, no key, no card; owner approved 2026-10-07).
 * Same rules as Photon: our User-Agent, light traffic ([GuardedWeather]), and another
 * Open-Meteo (or a self-hosted one) is a base-URL change. It is told only the rounded start
 * ([GuardedWeather.cell], ~10 km), never the trip.
 *
 * Hourly values are for the **preceding** hour (Open-Meteo's docs: "sum of the preceding
 * hour"), so the value stamped 09:00 is rain between 08:00 and 09:00 ([parseOpenMeteo]).
 * `timeformat=unixtime`: UTC seconds, so the clock change never shifts an hour.
 */
class OpenMeteoClient(
    baseUrl: String = OPEN_METEO,
    private val http: OkHttpClient = fastHttp(),
    private val userAgent: String = MotisClient.USER_AGENT,
) : WeatherSource {
    private val base: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    override suspend fun rain(cell: LatLon): RainForecast {
        val url = base.newBuilder().addPathSegments("v1/forecast")
            .addQueryParameter("latitude", cell.lat.toString())
            .addQueryParameter("longitude", cell.lon.toString())
            .addQueryParameter("hourly", "precipitation_probability,precipitation")
            .addQueryParameter("timeformat", "unixtime")
            .addQueryParameter("forecast_hours", FORECAST_HOURS.toString())
            .build()
        return parseOpenMeteo(fetch(Request.Builder().url(url).header("User-Agent", userAgent).build()))
    }

    /** The body; cancelling the caller (a new search) cancels the request. */
    private suspend fun fetch(request: Request): String = suspendCancellableCoroutine { c ->
        val call = http.newCall(request)
        c.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (c.isActive) c.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use { r ->
                        val body = r.body?.string().orEmpty()
                        if (!r.isSuccessful) throw TransitHttpException(r.code, body.take(300), r.header("Retry-After")?.toIntOrNull())
                        body
                    }
                }
                if (c.isActive) result.fold({ c.resume(it) }, { c.resumeWithException(it) })
            }
        })
    }

    companion object {
        const val OPEN_METEO = "https://api.open-meteo.com"

        /** From the current hour: 48 h of trips, plus their length and the preceding-hour shift. */
        const val FORECAST_HOURS = 54

        /** 3 s for the whole call: a note that comes later is no use, and a failure shows nothing. */
        fun fastHttp(): OkHttpClient = MotisClient.defaultHttp().newBuilder()
            .callTimeout(3, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
internal data class OpenMeteoResponse(
    val error: Boolean = false,
    val reason: String? = null,
    val hourly: OpenMeteoHourly? = null,
)

@Serializable
internal data class OpenMeteoHourly(
    val time: List<Long> = emptyList(),
    val precipitation_probability: List<Int?> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
)

private val OpenMeteoJson = Json { ignoreUnknownKeys = true }

/** Open-Meteo's hourly answer as [RainHour]s, each moved one hour back (the values are for the preceding hour). */
fun parseOpenMeteo(body: String): RainForecast {
    val r = OpenMeteoJson.decodeFromString(OpenMeteoResponse.serializer(), body)
    if (r.error) throw IllegalStateException("Open-Meteo: ${r.reason}")
    val h = r.hourly ?: return RainForecast(emptyList())
    return try {
        RainForecast(
            h.time.mapIndexed { i, t ->
                RainHour(Instant.ofEpochSecond(t - 3600), h.precipitation_probability.getOrNull(i), h.precipitation.getOrNull(i))
            },
        )
    } catch (e: java.time.DateTimeException) {
        throw IllegalStateException("Open-Meteo: a time out of range", e)
    }
}

/**
 * Keeps traffic to Open-Meteo light, like `GuardedGeocoder` does for Photon: one forecast per
 * 0.1° cell (~10 km) cached for [ttl], at most [maxConcurrent] requests at once, one retry
 * after a 429/503. Asked only for a trip starting within [HORIZON]. Any failure is silent:
 * [forTrip] answers null, nothing is cached, and the app shows no note.
 */
class GuardedWeather(
    private val inner: WeatherSource,
    private val clock: Clock = Clock.systemUTC(),
    maxConcurrent: Int = 2,
    private val backoff: Duration = Duration.ofSeconds(2),
    private val ttl: Duration = Duration.ofHours(1),
) {
    private val permits = Semaphore(maxConcurrent)
    private val cache = ConcurrentHashMap<LatLon, Pair<Instant, RainForecast>>()

    /** The forecast around [from] for a trip leaving at [start], or null (too far ahead, or it failed). */
    suspend fun forTrip(start: Instant, from: LatLon): RainForecast? {
        val now = clock.instant()
        if (start.isAfter(now.plus(HORIZON))) return null
        val key = cell(from)
        cache[key]?.let { (expires, value) -> if (now.isBefore(expires)) return value }
        val value = try {
            permits.withPermit {
                try {
                    inner.rain(key)
                } catch (e: TransitHttpException) {
                    delay((retryWait(e, backoff) ?: throw e).toMillis())
                    inner.rain(key)
                }
            }
        } catch (e: CancellationException) {
            throw e // a new search: not a failure
        } catch (e: Exception) {
            return null
        }
        cache[key] = now.plus(ttl) to value
        return value
    }

    companion object {
        val HORIZON: Duration = Duration.ofHours(48)

        /** All Open-Meteo is told: the start rounded to 0.1° (about 10 km). */
        fun cell(p: LatLon): LatLon = LatLon(Math.round(p.lat * 10) / 10.0, Math.round(p.lon * 10) / 10.0)
    }
}
