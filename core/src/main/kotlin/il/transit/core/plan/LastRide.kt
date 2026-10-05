package il.transit.core.plan

import il.transit.core.api.Endpoint
import il.transit.core.api.Itinerary
import il.transit.core.api.PlanRequest
import il.transit.core.api.Preferences
import il.transit.core.api.TransitApi
import il.transit.core.features.ISRAEL
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * The evening's last trip and the first one after it (null when none was found).
 * [runsAllNight]: trips kept coming until the request budget ran out (a night line such
 * as 469 Be'er Sheva → Tel Aviv, hourly all night) — there is no "last trip" to warn about.
 */
data class LastRide(val last: Itinerary?, val next: Itinerary?, val runsAllNight: Boolean = false) {
    /** No service for a long stretch after [last]: Shabbat, a holiday, or a route that stops early. */
    val longGap: Boolean
        get() = last != null && !runsAllNight && (next == null || Duration.between(last.start, next.start) > LONG_GAP)

    companion object {
        val LONG_GAP: Duration = Duration.ofHours(6)
    }
}

/**
 * "Is this the last bus?", with no holiday calendar: the timetable already knows.
 *  1. arrive-by 03:00 the next morning → the latest trips of [day];
 *  2. depart one minute after the last of them → the first trip after it. A gap of hours
 *     means Shabbat or a holiday, and its day and time say when service resumes.
 * Two traps, both seen live (2026-10-03): a trip that leaves late but arrives after 03:00
 * (a 00:35 night bus) is invisible to step 1, so if step 2 finds one still inside the
 * service day it becomes the last and step 2 runs again; and MOTIS offers trips that wait
 * out Shabbat (Fri 16:20 → Sat night), which are not "the next trip" and are skipped.
 * At most [BUDGET] requests.
 */
class LastRideFinder(private val api: TransitApi) {
    suspend fun find(from: Endpoint, to: Endpoint, day: LocalDate, preferences: Preferences, language: String): LastRide {
        val dayStart = day.atTime(DAY_STARTS).atZone(ISRAEL).toInstant()
        val cutoff = day.plusDays(1).atTime(DAY_STARTS.minusHours(1)).atZone(ISRAEL).toInstant()
        val dayEnd = day.plusDays(1).atTime(DAY_STARTS).atZone(ISRAEL).toInstant()
        val evening = api.plan(PlanRequest(from, to, cutoff, arriveBy = true, preferences = preferences, language = language))
        var last = evening.itineraries.filter { it.start >= dayStart && it.start < dayEnd }.maxByOrNull { it.start }
            ?: return LastRide(null, null)
        repeat(BUDGET - 1) {
            val ref = last
            val after = api.plan(PlanRequest(from, to, ref.start.plusSeconds(60), preferences = preferences, language = language))
                .itineraries.filter { it.start > ref.start && it.duration <= maxOf(2 * ref.duration, ref.duration + 3600) }
                .minByOrNull { it.start }
            if (after == null || after.start >= dayEnd) return LastRide(ref, after)
            last = after // later than anything the arrive-by search could see
        }
        return LastRide(last, null, runsAllNight = true)
    }

    companion object {
        const val BUDGET = 3

        /** Service days run 04:00 to 03:59, so a 00:30 night bus still counts as "tonight". */
        val DAY_STARTS: LocalTime = LocalTime.of(4, 0)

        /** Look automatically only when the answer matters: evenings, Friday and Saturday. */
        fun worthAsking(departure: Instant): Boolean {
            val t = departure.atZone(ISRAEL)
            return t.hour >= 17 || t.hour < 4 || t.dayOfWeek == java.time.DayOfWeek.FRIDAY || t.dayOfWeek == java.time.DayOfWeek.SATURDAY
        }

        /** The service day a departure belongs to (before 04:00 counts as the previous day). */
        fun serviceDay(t: Instant): LocalDate = t.atZone(ISRAEL).minusHours(DAY_STARTS.hour.toLong()).toLocalDate()
    }
}
