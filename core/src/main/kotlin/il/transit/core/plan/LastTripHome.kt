package il.transit.core.plan

import il.transit.core.api.BudgetedTransitApi
import il.transit.core.api.Endpoint
import il.transit.core.api.Itinerary
import il.transit.core.api.TransitApi
import il.transit.core.features.ISRAEL
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import il.transit.core.history.TripRecord
import il.transit.core.present.hhmm
import il.transit.core.user.UserSettings
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * "Last trip home 23:10 from Be'er Sheva Central: leave in 30 min" (Phase 9 C5). Opt-in
 * ([UserSettings.lastTripAlert]). Once per service day an inexact alarm runs the check:
 * where am I (no background location: today's history and the last fix the app saw while in
 * front), and, when away from Home, [LastRideFinder] to Home under its budget of 3. One exact
 * alarm then posts the warning [WARNING] before leaving. Owner decisions of 2026-10-05.
 */
object LastTripHome {
    /** An origin this close to Home means "at home": no request, no alert. */
    const val NEAR_HOME_M = 1000.0

    /** A foreground fix older than this says nothing about where the user is now. */
    val SEEN_MAX_AGE: Duration = Duration.ofHours(3)

    /** Notify this long before the last trip home's leave time. */
    val WARNING: Duration = Duration.ofMinutes(30)

    /** A notification this late (an inexact fallback alarm) says "leave now" instead of "leave in N min". */
    val LATE: Duration = Duration.ofMinutes(5)

    /** When the day's check runs: 19:00 Sun–Thu, 12:00 Friday (service ends in the afternoon), 20:00 Saturday. */
    fun checkTime(day: LocalDate): LocalTime = when (day.dayOfWeek) {
        DayOfWeek.FRIDAY -> LocalTime.of(12, 0)
        DayOfWeek.SATURDAY -> LocalTime.of(20, 0)
        else -> LocalTime.of(19, 0)
    }

    private fun checkAt(day: LocalDate): Instant = day.atTime(checkTime(day)).atZone(ISRAEL).toInstant()

    /**
     * When the check alarm should go off, seen from an app start or a reboot: today's check
     * time while it is ahead; [now] when it has passed and today was not checked yet (the
     * phone was off at 19:00), still inside the same service day; else the next day's.
     */
    fun nextCheck(now: Instant, checkedDay: LocalDate?): Instant {
        val day = LastRideFinder.serviceDay(now)
        val today = checkAt(day)
        return when {
            today.isAfter(now) -> today
            checkedDay != day -> now
            else -> following(now)
        }
    }

    /** The next service day's check time: what a check re-arms once it has run. */
    fun following(now: Instant): Instant = checkAt(LastRideFinder.serviceDay(now).plusDays(1))

    /** A place and when the user was there. */
    data class Seen(val at: LatLon, val time: Instant)

    /**
     * Where the user is, or null for "don't ask": the latest of
     *  - the destination of today's latest started trip (at its arrival; a trip to Home makes it Home),
     *  - the last foreground fix, today and within [SEEN_MAX_AGE];
     * null when neither exists or that place is within [NEAR_HOME_M] of Home.
     */
    fun origin(history: List<TripRecord>, seen: Seen?, home: LatLon, now: Instant): LatLon? {
        val day = LastRideFinder.serviceDay(now)
        val trip = history
            .filter { LastRideFinder.serviceDay(it.startedAt) == day && !it.startedAt.isAfter(now) }
            .maxByOrNull { it.startedAtEpoch }
            ?.let { r -> TripRecord.parseCell(r.toCell)?.let { Seen(it, r.startedAt.plusSeconds(60L * (r.totalMin ?: 0))) } }
        val fix = seen?.takeIf {
            LastRideFinder.serviceDay(it.time) == day && !it.time.isAfter(now) && Duration.between(it.time, now) <= SEEN_MAX_AGE
        }
        val latest = listOfNotNull(trip, fix).maxByOrNull { it.time } ?: return null
        return latest.at.takeIf { Geo.distanceM(it, home) > NEAR_HOME_M }
    }

    /**
     * The alert for a found last trip, or null: none found, the line [LastRide.runsAllNight],
     * or its leave time has already gone.
     */
    fun alert(lr: LastRide, now: Instant): LastTripAlert? {
        if (lr.runsAllNight) return null
        val last = lr.last ?: return null
        if (!now.isBefore(last.start)) return null
        val board = last.firstTransitLeg ?: return null
        val next = lr.next?.takeIf { lr.longGap }
        val nextAt = next?.let(::boardingAt)?.atZone(ISRAEL)
        return LastTripAlert(
            serviceDay = LastRideFinder.serviceDay(last.start).toString(),
            leaveAtEpoch = last.start.epochSecond,
            boardTime = hhmm(board.start),
            boardStop = board.from.name,
            nextDay = nextAt?.dayOfWeek?.value,
            nextTime = nextAt?.let { hhmm(it.toInstant()) },
        )
    }

    private fun boardingAt(i: Itinerary): Instant = i.firstTransitLeg?.start ?: i.start

    /** What the notification says at [now]; null once the leave time has gone (post nothing). */
    fun message(a: LastTripAlert, now: Instant): Message? = when {
        !now.isBefore(a.leaveAt) -> null
        now.isBefore(a.notifyAt.plus(LATE)) -> Message.LeaveIn(maxOf(1, Math.round(Duration.between(now, a.leaveAt).seconds / 60.0).toInt()))
        else -> Message.LeaveNow
    }

    sealed interface Message {
        /** "Last trip home 23:10 from X: leave in 30 min". */
        data class LeaveIn(val minutes: Int) : Message

        /** "Last trip home leaves at 23:10, leave now": the warning time had already passed. */
        data object LeaveNow : Message
    }
}

/** What the evening's notification needs, stored until it is posted (it survives a reboot). */
@Serializable
data class LastTripAlert(
    /** The service day it is about ("2026-10-09"): at most one notification per day. */
    val serviceDay: String,
    /** When to start walking for the last trip home. */
    val leaveAtEpoch: Long,
    /** "23:10": boarding the first vehicle. */
    val boardTime: String,
    val boardStop: String,
    /** [LastRide.longGap]: when service resumes ("(next: Saturday 19:30)"); ISO day of week. */
    val nextDay: Int? = null,
    val nextTime: String? = null,
) {
    val leaveAt: Instant get() = Instant.ofEpochSecond(leaveAtEpoch)
    val notifyAt: Instant get() = leaveAt.minus(LastTripHome.WARNING)
    val day: LocalDate get() = LocalDate.parse(serviceDay)
}

/**
 * Runs one evening check. 0 requests when the day was already checked or there is no
 * origin; otherwise [markChecked] is called first (so even a failure leaves the day checked:
 * never more than [LastRideFinder.BUDGET] requests a day) and [LastRideFinder] runs under a
 * [BudgetedTransitApi] of that size. Network errors propagate; the day stays checked.
 */
class LastTripChecker(private val api: TransitApi) {
    data class Outcome(val alert: LastTripAlert?, val requests: Int)

    suspend fun check(
        checkedDay: LocalDate?,
        origin: LatLon?,
        home: LatLon,
        now: Instant,
        settings: UserSettings,
        language: String,
        markChecked: suspend (LocalDate) -> Unit,
    ): Outcome? {
        val day = LastRideFinder.serviceDay(now)
        if (day == checkedDay || origin == null) return null
        markChecked(day)
        val budgeted = BudgetedTransitApi(api, LastRideFinder.BUDGET)
        val lr = LastRideFinder(budgeted).find(Endpoint.Coord(origin), Endpoint.Coord(home), day, settings.preferences(), language)
        return Outcome(LastTripHome.alert(lr, now), budgeted.used)
    }
}
