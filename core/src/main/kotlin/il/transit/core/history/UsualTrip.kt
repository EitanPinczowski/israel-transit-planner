package il.transit.core.history

import il.transit.core.features.ISRAEL
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import il.transit.core.user.PlaceRoutine
import il.transit.core.user.SavedPlace
import java.time.Duration
import java.time.Instant

/** One end of a trip: where it is, and the saved-place name it had ("" for my location or a pin). */
data class TripEnd(val at: LatLon?, val name: String)

/** "Usually 52 min": the median door-to-door time of the same trip. */
data class Usual(val medianMin: Int, val trips: Int)

/** "Quickest when you leave around 07:15 (usually 41 min)". */
data class BestLeave(val leaveMin: Int, val medianMin: Int, val trips: Int)

/**
 * Insights from local history only, no requests. Medians, never means: one 3-hour Shabbat trip
 * must not move them.
 */
object UsualTrip {
    /**
     * Start and end within this of the record's rounded cells count as the same trip. Cells are
     * rounded to about 100 m, so the effective radius is up to ~380 m: "about 300 m".
     */
    const val SAME_PLACE_M = 300.0
    const val MIN_TRIPS = 3
    val WINDOW: Duration = Duration.ofDays(90)

    const val BUCKET_MIN = 15
    const val MIN_PER_BUCKET = 2

    /**
     * The usual time from [from] to [to], from at least [MIN_TRIPS] records of the last [WINDOW]; else null.
     *
     * Only records with [TripRecord.totalMin] count: older ones knew riding + walking but not the
     * waits, so they would make every new option look slower ("+N" too high).
     */
    fun usual(records: List<TripRecord>, from: TripEnd, to: TripEnd, now: Instant): Usual? {
        val since = now.minus(WINDOW)
        val same = records.filter { r ->
            r.totalMin != null && !r.startedAt.isBefore(since) && !r.startedAt.isAfter(now) &&
                same(r.fromCell, r.from, from) && same(r.toCell, r.to, to)
        }
        if (same.size < MIN_TRIPS) return null
        return Usual(median(same.map { it.totalMin!! }), same.size)
    }

    /**
     * The 15-minute leave slot with the shortest median trip to [place], on the routine's days,
     * among slots with at least [MIN_PER_BUCKET] records. Null when the data is too thin.
     *
     * The routine's time window is ignored on purpose: the point is to find a better time than
     * the routine's own. Days and slots are wall-clock Israel time (a 00:10 trip on Monday is
     * Monday, not Sunday's service day), which is fine for daytime routines. Like [usual], only
     * records with [TripRecord.totalMin] count.
     */
    fun bestLeave(records: List<TripRecord>, routine: PlaceRoutine, place: SavedPlace): BestLeave? {
        val dest = TripEnd(place.latLon, place.name)
        val slots = records
            .filter { it.totalMin != null && same(it.toCell, it.to, dest) }
            .map { it.startedAt.atZone(ISRAEL) to it.totalMin!! }
            .filter { (t, _) -> t.dayOfWeek.value in routine.days }
            .groupBy({ (t, _) -> (t.hour * 60 + t.minute) / BUCKET_MIN * BUCKET_MIN }, { it.second })
            .filterValues { it.size >= MIN_PER_BUCKET }
        val best = slots.entries.minWithOrNull(compareBy({ median(it.value) }, { it.key })) ?: return null
        return BestLeave(best.key, median(best.value), best.value.size)
    }

    /** The record's end matches [end]: by distance when both have coordinates, else by a non-blank name. */
    private fun same(cell: String?, name: String, end: TripEnd): Boolean {
        val at = TripRecord.parseCell(cell)
        return if (at != null && end.at != null) Geo.distanceM(at, end.at) <= SAME_PLACE_M
        else end.name.isNotBlank() && name == end.name
    }

    /** For an even count, the mean of the two middle values, rounded up. */
    internal fun median(xs: List<Int>): Int = xs.sorted().let { s -> if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2] + 1) / 2 }
}
