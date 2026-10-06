package il.transit.core.fare

import il.transit.core.features.ISRAEL
import il.transit.core.history.TripRecord
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** A finished month's single fares against the cheapest monthly pass that covers it. */
data class PassAdvice(
    val month: YearMonth,
    /** Trips started in the app with a fare, the "based on N trips" count. */
    val trips: Int,
    /** Single fares, each day capped at its daily cap, after the profile's discount. */
    val singleAgorot: Int,
    val passAgorot: Int,
    val withTrain: Boolean,
) {
    /** Positive: the pass would have saved this much. Negative: paying per ride was cheaper. */
    val passSavesAgorot: Int get() = singleAgorot - passAgorot
}

/**
 * "A monthly pass would have saved ₪74 in September". An estimate from the trips the user
 * started in the app, which is all history knows. No requests.
 *
 * Days and months are service days (04:00 to 03:59 Israel time, as the daily cap counts them),
 * so a trip at 00:30 on 1 October belongs to 30 September. Records store the full fare; the
 * current [FareProfile] is applied to the whole month, so a month that spans a profile change
 * is priced at today's profile. Free transfers between two separate records are not
 * modelled, so single fares can come out a little high.
 */
object PassAdvisor {
    const val MIN_TRIPS = 10
    /** The service day starts at 04:00. */
    private const val DAY_START_H = 4L

    /** Advice for the last finished month before [now], or null (too few trips, FREE profile). */
    fun advise(records: List<TripRecord>, profile: FareProfile, now: Instant): PassAdvice? {
        if (profile == FareProfile.FREE) return null
        val month = YearMonth.from(serviceDay(now)).minusMonths(1)
        return forMonth(records, profile, month)
    }

    fun forMonth(records: List<TripRecord>, profile: FareProfile, month: YearMonth): PassAdvice? {
        if (profile == FareProfile.FREE) return null
        val rides = records.filter { it.fareAgorot != null && YearMonth.from(serviceDay(it.startedAt)) == month }
        if (rides.size < MIN_TRIPS) return null

        val single = rides.groupBy { serviceDay(it.startedAt) }.values.sumOf { day ->
            val train = day.any { it.withTrain == true }
            minOf(day.sumOf { it.fareAgorot!! }, FareTable.dailyCap(day.maxOf { it.fareBand ?: FareTable.YELLOW }, train))
        }
        val train = rides.any { it.withTrain == true }
        val pass = FareTable.monthlyPass(rides.maxOf { it.fareBand ?: FareTable.YELLOW }, train)
        return PassAdvice(month, rides.size, discount(single, profile), discount(pass, profile), train)
    }

    fun serviceDay(t: Instant): LocalDate = t.atZone(ISRAEL).minusHours(DAY_START_H).toLocalDate()

    private fun discount(agorot: Int, profile: FareProfile) = (agorot * profile.percentPaid + 50) / 100
}
