package il.transit.core.history

import il.transit.core.api.Itinerary
import il.transit.core.api.StreetModes
import il.transit.core.fare.FareEstimator
import il.transit.core.geo.LatLon
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
    /** The tab it was planned in: TRIP, BETTER_START, DROP_OFF, PICK_UP, PARK_RIDE. */
    val mode: String,
    val transitMin: Int,
    val walkMin: Int,
    val transfers: Int,
    /** Minutes a car feature saved against its no-car baseline; null for a plain trip. */
    val savedMin: Int? = null,
    // Since Phase 9 C3; null on records written before it (old history.json still decodes).
    /** Door to door, first leg's start to last leg's end. */
    val totalMin: Int? = null,
    /** Full single-fare estimate in agorot, before any discount: the profile is applied when read. */
    val fareAgorot: Int? = null,
    /** The longest fare band ridden ([il.transit.core.fare.FareTable.band]). */
    val fareBand: Int? = null,
    val withTrain: Boolean? = null,
    /** Start and end rounded to [CELL_DECIMALS] decimals ("31.262,34.801"), about 100 m. */
    val fromCell: String? = null,
    val toCell: String? = null,
) {
    val startedAt: Instant get() = Instant.ofEpochSecond(startedAtEpoch)

    /** [totalMin], or for an old record the riding and walking it knew (no waits). */
    val doorToDoorMin: Int get() = totalMin ?: (transitMin + walkMin)

    companion object {
        fun from(it: Itinerary, from: String, to: String, mode: String, startedAt: Instant, savedMin: Int?): TripRecord {
            val transit = it.legs.filter { l -> l.isTransit }.sumOf { l -> l.duration }
            val walk = it.legs.filter { l -> l.mode == StreetModes.WALK }.sumOf { l -> l.duration }
            val fare = FareEstimator.estimate(it)
            return TripRecord(
                startedAtEpoch = startedAt.epochSecond,
                from = from,
                to = to,
                mode = mode,
                transitMin = Math.round(transit / 60.0).toInt(),
                walkMin = Math.round(walk / 60.0).toInt(),
                transfers = it.transfers,
                savedMin = savedMin?.takeIf { s -> s > 0 },
                totalMin = Math.round(it.duration / 60.0).toInt(),
                fareAgorot = fare?.agorot,
                fareBand = fare?.maxBand,
                withTrain = fare?.hasTrain,
                fromCell = it.legs.firstOrNull()?.from?.latLon?.let(::cellOf),
                toCell = it.legs.lastOrNull()?.to?.latLon?.let(::cellOf),
            )
        }

        const val CELL_DECIMALS = 3

        fun cellOf(p: LatLon): String = String.format(java.util.Locale.US, "%.${CELL_DECIMALS}f,%.${CELL_DECIMALS}f", p.lat, p.lon)

        fun parseCell(cell: String?): LatLon? {
            val parts = cell?.split(',') ?: return null
            val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
            val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
            return LatLon(lat, lon)
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
