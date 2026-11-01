package il.transit.core.api

import il.transit.core.features.ISRAEL
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** The outside services the app talks to, as the data card names them (Phase 11 E4). */
enum class UsageService(val key: String) {
    TRANSITOUS("transitous"),
    WEATHER("weather"),

    /** The phone's Geocoder and Photon: only asked after a Transitous miss. */
    ADDRESS("address"),
}

/** What became of one request a guard was asked for. */
enum class UsageOutcome(val key: String) {
    /** Sent over the network. */
    NETWORK("net"),

    /** Answered from the guard's cache: nothing sent. */
    CACHE("cache"),

    /** Sent again after a 429/503: a second network request. */
    RETRY("retry"),
}

/**
 * Where the guards report each request (Phase 11 E4). Only counts: the service, the kind of call
 * ("plan", "stops", "search"...) and what became of it, never a place or a query text.
 */
fun interface UsageMeter {
    fun count(service: UsageService, kind: String, outcome: UsageOutcome)

    companion object {
        val NONE = UsageMeter { _, _, _ -> }
    }
}

/**
 * One Israel day of counts. [day] is the epoch day of the Israel date; [counts] keys are
 * `service.kind.outcome` ("transitous.plan.net"); [nightAt] is when that day's night refresh ran
 * (epoch seconds) and [nightRequests] what it sent; [rideGpsSec] the seconds of ride tracking.
 * Numbers and fixed keys only: the JSON holds no place, no name and no query.
 */
@Serializable
data class UsageDay(
    val day: Long,
    val counts: Map<String, Int> = emptyMap(),
    val nightAt: Long? = null,
    val nightRequests: Int = 0,
    val rideGpsSec: Long = 0,
) {
    fun sum(service: UsageService, vararg outcomes: UsageOutcome): Int = counts.entries.sumOf { (k, v) ->
        val parts = k.split('.')
        if (parts.size == 3 && parts[0] == service.key && outcomes.any { it.key == parts[2] }) v else 0
    }
}

/** What the data card shows (Phase 11 E4). */
data class UsageSummary(
    /** Requests asked of Transitous today, answered from the network or the cache. */
    val transitous: Int,
    /** Of [transitous], those answered from the cache (nothing sent). */
    val transitousCache: Int,
    /** Of [transitous], second tries after a 429/503. */
    val transitousRetries: Int,
    /** Requests sent to Open-Meteo today (cache hits not counted: nothing left the phone). */
    val weather: Int,
    /** Requests sent to the backup geocoders today. */
    val address: Int,
    /** The last night refresh within [UsageLog.NIGHT_RECENT], or null. */
    val nightAt: Instant?,
    val nightRequests: Int,
    /** Minutes of ride GPS today, rounded up. */
    val rideGpsMin: Int,
) {
    val empty: Boolean get() = transitous == 0 && weather == 0 && address == 0 && nightAt == null && rideGpsMin == 0
}

/**
 * Counts per day per service and per kind of call (network vs cache, retries counted), the day
 * ending at midnight Israel time, [KEEP_DAYS] days kept. Thread-safe; [onChange] tells the app
 * to save (the app debounces). Only counts and times are stored.
 */
class UsageLog(private val clock: Clock = Clock.systemUTC(), restored: List<UsageDay> = emptyList()) : UsageMeter {
    private val days = sortedMapOf<Long, UsageDay>()

    @Volatile var onChange: (() -> Unit)? = null

    init {
        restored.forEach { days[it.day] = it }
        trim(clock.instant())
    }

    override fun count(service: UsageService, kind: String, outcome: UsageOutcome) {
        val key = "${service.key}.${keyPart(kind)}.${outcome.key}"
        update(clock.instant()) { it.copy(counts = it.counts + (key to (it.counts[key] ?: 0) + 1)) }
    }

    /** The night refresh ran at [at] and sent [requests] (plans and boards). */
    fun nightRefresh(at: Instant, requests: Int) =
        update(at) { it.copy(nightAt = at.epochSecond, nightRequests = requests) }

    /** [duration] of ride GPS, ending now. */
    fun rideGps(duration: Duration) {
        if (duration.isNegative || duration.isZero) return
        update(clock.instant()) { it.copy(rideGpsSec = it.rideGpsSec + duration.seconds) }
    }

    @Synchronized fun days(): List<UsageDay> = days.values.toList()

    @Synchronized fun summary(now: Instant = clock.instant()): UsageSummary {
        val today = days[dayOf(now)] ?: UsageDay(dayOf(now))
        val night = days.values
            .mapNotNull { d -> d.nightAt?.let { Instant.ofEpochSecond(it) to d.nightRequests } }
            .filter { (at, _) -> !at.isAfter(now) && Duration.between(at, now) <= NIGHT_RECENT }
            .maxByOrNull { it.first }
        return UsageSummary(
            transitous = today.sum(UsageService.TRANSITOUS, *UsageOutcome.entries.toTypedArray()),
            transitousCache = today.sum(UsageService.TRANSITOUS, UsageOutcome.CACHE),
            transitousRetries = today.sum(UsageService.TRANSITOUS, UsageOutcome.RETRY),
            weather = today.sum(UsageService.WEATHER, UsageOutcome.NETWORK, UsageOutcome.RETRY),
            address = today.sum(UsageService.ADDRESS, UsageOutcome.NETWORK, UsageOutcome.RETRY),
            nightAt = night?.first,
            nightRequests = night?.second ?: 0,
            rideGpsMin = ((today.rideGpsSec + 59) / 60).toInt(),
        )
    }

    private fun update(at: Instant, change: (UsageDay) -> UsageDay) {
        synchronized(this) {
            val d = dayOf(at)
            days[d] = change(days[d] ?: UsageDay(d))
            trim(clock.instant())
        }
        onChange?.invoke()
    }

    @Synchronized private fun trim(now: Instant) {
        val oldest = dayOf(now) - (KEEP_DAYS - 1)
        days.keys.filter { it < oldest }.forEach { days.remove(it) }
    }

    companion object {
        const val KEEP_DAYS = 7

        /** The card names a night refresh this recent ("night refresh 02:14 (4 requests)"). */
        val NIGHT_RECENT: Duration = Duration.ofHours(24)

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        private val list = ListSerializer(UsageDay.serializer())

        /** The Israel day [at] falls in: a day ends at midnight Israel time, whatever the phone's zone. */
        fun dayOf(at: Instant): Long = at.atZone(ISRAEL).toLocalDate().toEpochDay()

        fun date(day: Long): LocalDate = LocalDate.ofEpochDay(day)

        fun encode(days: List<UsageDay>): String = json.encodeToString(list, days)

        /** Unreadable data restores as empty, never crashes. */
        fun decode(s: String?): List<UsageDay> =
            s?.let { runCatching { json.decodeFromString(list, it) }.getOrNull() }.orEmpty()

        /**
         * The kinds of call the guards report: `GuardedTransitApi`'s cache prefixes, `GuardedGeocoder`'s and
         * `GuardedWeather`'s. Anything else is stored as "other", so no text (a query, a place) can reach the file.
         */
        val KINDS: Set<String> = setOf("plan", "otm", "stops", "geo", "rgeo", "st", "trip", "mt", "search", "reverse", "forecast")

        private fun keyPart(kind: String): String = if (kind in KINDS) kind else "other"
    }
}
