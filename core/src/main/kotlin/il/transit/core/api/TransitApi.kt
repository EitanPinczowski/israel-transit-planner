package il.transit.core.api

import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import java.time.Instant

/**
 * Everything the app asks a MOTIS server. Implemented by [MotisClient] (HTTP) and
 * wrapped by [GuardedTransitApi] (cache, concurrency, backoff) and [BudgetedTransitApi]
 * (per-search request cap). Switching from Transitous to a self-hosted MOTIS is a
 * base-URL change, nothing more.
 */
interface TransitApi {
    suspend fun plan(req: PlanRequest): PlanResponse

    /**
     * Street travel times between [one] and each of [many], in the order given.
     * `arriveBy = false`: one → many. `arriveBy = true`: many → one.
     * An entry is null when no path was found within [maxSeconds].
     */
    suspend fun oneToMany(
        one: LatLon,
        many: List<LatLon>,
        mode: String,
        maxSeconds: Int,
        arriveBy: Boolean,
    ): List<Int?>

    suspend fun stops(box: BBox, modes: Set<String>? = null, language: String = "he"): List<Place>

    suspend fun geocode(text: String, language: String = "he", near: LatLon? = null, max: Int = 8): List<GeocodeMatch>

    /** Nearest addresses/places/stops to [at], best first. Used to name a long-pressed point. */
    suspend fun reverseGeocode(at: LatLon, language: String = "he", max: Int = 3): List<GeocodeMatch>

    suspend fun stopTimes(stopId: String, time: Instant? = null, n: Int = 10, language: String = "he"): StopTimesResponse
}

/** A place the planner can route from/to: a coordinate, or a MOTIS stop id. */
sealed interface Endpoint {
    fun param(): String

    data class Coord(val at: LatLon) : Endpoint {
        override fun param() = at.comma()
    }

    data class Stop(val stopId: String) : Endpoint {
        override fun param() = stopId
    }
}

data class PlanRequest(
    val from: Endpoint,
    val to: Endpoint,
    val time: Instant,
    val arriveBy: Boolean = false,
    val preTransitModes: List<String> = listOf(StreetModes.WALK),
    val postTransitModes: List<String> = listOf(StreetModes.WALK),
    val directModes: List<String> = listOf(StreetModes.WALK),
    val maxPreTransitSec: Int? = null,
    val maxPostTransitSec: Int? = null,
    val preferences: Preferences = Preferences(),
    val language: String = "he",
    val withFares: Boolean = false,
    /** Street-only request (e.g. just the car route): MOTIS skips the transit search when
     *  `transitModes` is empty, which is much cheaper for the server. */
    val directOnly: Boolean = false,
) {
    /** Query parameters for `GET /api/v6/plan`. Stable ordering, so it doubles as a cache key. */
    fun toQuery(): List<Pair<String, String>> = buildList {
        add("fromPlace" to from.param())
        add("toPlace" to to.param())
        add("time" to time.toString())
        add("arriveBy" to arriveBy.toString())
        add("preTransitModes" to preTransitModes.joinToString(","))
        add("postTransitModes" to postTransitModes.joinToString(","))
        add("directModes" to directModes.joinToString(","))
        // A walk-only access/egress leg honours the user's max-walk preference; a car leg
        // (the special features) carries its own explicit cap instead.
        val pre = maxPreTransitSec ?: preferences.maxWalkSec?.takeIf { preTransitModes == listOf(StreetModes.WALK) }
        val post = maxPostTransitSec ?: preferences.maxWalkSec?.takeIf { postTransitModes == listOf(StreetModes.WALK) }
        pre?.let { add("maxPreTransitTime" to it.toString()) }
        post?.let { add("maxPostTransitTime" to it.toString()) }
        when {
            directOnly -> add("transitModes" to "")
            preferences.transitModes != null -> add("transitModes" to preferences.transitModes.joinToString(","))
        }
        preferences.maxTransfers?.let { add("maxTransfers" to it.toString()) }
        preferences.pedestrianSpeedMps?.let { add("pedestrianSpeed" to it.toString()) }
        preferences.additionalTransferSec?.let { add("additionalTransferTime" to (it / 60).toString()) }
        add("language" to language)
        if (withFares) add("withFares" to "true")
    }
}

/** User preferences, sent with every plan request (including the special features). */
data class Preferences(
    /** null = all modes. e.g. setOf("RAIL") for trains only, or everything but BUS. */
    val transitModes: Set<String>? = null,
    val maxTransfers: Int? = null,
    val pedestrianSpeedMps: Double? = null,
    /** Extra buffer added at every transfer (MOTIS takes minutes). */
    val additionalTransferSec: Int? = null,
    /** Longest walk to the first stop / from the last stop. null = server default (15 min). */
    val maxWalkSec: Int? = null,
)

class TransitHttpException(val code: Int, message: String, val retryAfterSec: Int? = null) :
    RuntimeException("HTTP $code: $message")
