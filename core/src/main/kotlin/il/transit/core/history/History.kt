package il.transit.core.history

import il.transit.core.api.Itinerary
import il.transit.core.api.StreetModes
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

/** One trip the user actually started ("Start trip"), stored locally only. */
@Serializable
data class TripRecord(
    val startedAtEpoch: Long,
    val from: String,
    val to: String,
    /** The tab it was planned in: TRIP, BETTER_START, DROP_OFF, PICK_UP. */
    val mode: String,
    val transitMin: Int,
    val walkMin: Int,
    val transfers: Int,
    /** Minutes a car feature saved against its no-car baseline; null for a plain trip. */
    val savedMin: Int? = null,
) {
    val startedAt: Instant get() = Instant.ofEpochSecond(startedAtEpoch)

    companion object {
        fun from(it: Itinerary, from: String, to: String, mode: String, startedAt: Instant, savedMin: Int?): TripRecord {
            val transit = it.legs.filter { l -> l.isTransit }.sumOf { l -> l.duration }
            val walk = it.legs.filter { l -> l.mode == StreetModes.WALK }.sumOf { l -> l.duration }
            return TripRecord(
                startedAtEpoch = startedAt.epochSecond,
                from = from,
                to = to,
                mode = mode,
                transitMin = Math.round(transit / 60.0).toInt(),
                walkMin = Math.round(walk / 60.0).toInt(),
                transfers = it.transfers,
                savedMin = savedMin?.takeIf { s -> s > 0 },
            )
        }
    }
}

data class HistoryStats(
    val trips: Int,
    val tripsThisWeek: Int,
    val transitHours: Double,
    val walkHours: Double,
    /** Total minutes the car + transit features saved. */
    val minutesSaved: Int,
    val featureTrips: Int,
    val topDestination: String?,
)

object History {
    /** History is capped so the JSON file stays small; oldest trips fall off. */
    const val MAX_RECORDS = 500

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val list = ListSerializer(TripRecord.serializer())

    /** Newest first; capped. */
    fun add(records: List<TripRecord>, r: TripRecord): List<TripRecord> = (listOf(r) + records).take(MAX_RECORDS)

    fun stats(records: List<TripRecord>, now: Instant): HistoryStats {
        val weekAgo = now.minus(Duration.ofDays(7))
        return HistoryStats(
            trips = records.size,
            tripsThisWeek = records.count { it.startedAt.isAfter(weekAgo) },
            transitHours = records.sumOf { it.transitMin } / 60.0,
            walkHours = records.sumOf { it.walkMin } / 60.0,
            minutesSaved = records.sumOf { it.savedMin ?: 0 },
            featureTrips = records.count { it.mode != "TRIP" },
            topDestination = records.groupingBy { it.to }.eachCount().maxByOrNull { it.value }?.key,
        )
    }

    fun encode(records: List<TripRecord>): String = json.encodeToString(list, records)

    fun decode(s: String?): List<TripRecord> = s?.let { runCatching { json.decodeFromString(list, it) }.getOrNull() }.orEmpty()
}
