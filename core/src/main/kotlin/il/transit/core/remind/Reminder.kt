package il.transit.core.remind

import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.api.parseTime
import il.transit.core.geo.LatLon
import il.transit.core.present.hhmm
import il.transit.core.present.legDelayMin
import il.transit.core.present.lineLabel
import il.transit.core.user.UserSettings
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/**
 * "Tell me when to leave" for one chosen itinerary. Stored as JSON (one active reminder),
 * so every field is plain data and instants are epoch seconds.
 *
 * The boarding identity says which vehicle the reminder is about, so the re-check can find
 * the same bus in a fresh plan even after real-time data shifted it.
 */
@Serializable
data class Reminder(
    val fromLat: Double,
    val fromLon: Double,
    val toLat: Double,
    val toLon: Double,
    val settings: UserSettings,
    val tripId: String?,
    val line: String?,
    val boardStop: String,
    val scheduledBoardingEpoch: Long,
    /** When to start walking. Moves when the re-check finds a delay. */
    val leaveAtEpoch: Long,
    val boardingDelayMin: Int? = null,
    /** The leave time the user was last told (set, or notified of); alerts measure from it. */
    val alertedLeaveAtEpoch: Long? = null,
) {
    val from: LatLon get() = LatLon(fromLat, fromLon)
    val to: LatLon get() = LatLon(toLat, toLon)
    val leaveAt: Instant get() = Instant.ofEpochSecond(leaveAtEpoch)
    val scheduledBoarding: Instant get() = Instant.ofEpochSecond(scheduledBoardingEpoch)

    /** The leave time the user knows about; older stored reminders fall back to [leaveAt]. */
    val toldLeaveAt: Instant get() = Instant.ofEpochSecond(alertedLeaveAtEpoch ?: leaveAtEpoch)

    /** "line 5 at 12:14 from X" pieces for the notification. */
    val boardingTime: String get() = hhmm(scheduledBoarding.plusSeconds(60L * (boardingDelayMin ?: 0)))

    companion object {
        /** Real-time re-checks this long before leaving: often enough to catch a delay while
         *  the user can still act, at most 5 requests per reminder. */
        val RECHECKS: List<Duration> = listOf(30L, 20L, 12L, 6L, 2L).map(Duration::ofMinutes)

        /** Notify when the leave time moves by at least this much, earlier or later. */
        const val ALERT_MIN = 3L

        /** Null for an itinerary with no transit leg (walking only: nothing to remind about). */
        fun from(itinerary: Itinerary, from: LatLon, to: LatLon, settings: UserSettings): Reminder? {
            val board = itinerary.firstTransitLeg ?: return null
            return Reminder(
                fromLat = from.lat,
                fromLon = from.lon,
                toLat = to.lat,
                toLon = to.lon,
                settings = settings,
                tripId = board.tripId,
                line = lineLabel(board),
                boardStop = board.from.name,
                scheduledBoardingEpoch = scheduled(board).epochSecond,
                leaveAtEpoch = itinerary.start.epochSecond,
                boardingDelayMin = legDelayMin(board),
                alertedLeaveAtEpoch = itinerary.start.epochSecond,
            )
        }

        private fun scheduled(l: Leg): Instant = l.scheduledStartTime?.let(::parseTime) ?: l.start
    }
}

sealed interface ReminderUpdate {
    /** Same vehicle found; [reminder] carries the new leave time and delay. */
    data class Updated(val reminder: Reminder, val shiftedByMin: Long) : ReminderUpdate

    /** The vehicle is no longer offered (cancelled, or the plan changed completely). */
    data object Gone : ReminderUpdate
}

object ReminderLogic {
    /**
     * Find the reminder's vehicle among fresh [itineraries]: by trip id when both sides
     * have one, otherwise by line + boarding stop + scheduled departure (±1 min).
     */
    fun update(r: Reminder, itineraries: List<Itinerary>): ReminderUpdate {
        val match = itineraries.firstOrNull { it.boardsSameVehicle(r) } ?: return ReminderUpdate.Gone
        val board = match.firstTransitLeg!!
        if (board.cancelled) return ReminderUpdate.Gone
        val updated = r.copy(leaveAtEpoch = match.start.epochSecond, boardingDelayMin = legDelayMin(board))
        return ReminderUpdate.Updated(updated, Duration.ofSeconds(updated.leaveAtEpoch - r.leaveAtEpoch).toMinutes())
    }

    /** The next re-check: the first of leave − [Reminder.RECHECKS] still ahead of [now]; null when none is left. */
    fun nextRecheck(r: Reminder, now: Instant): Instant? =
        Reminder.RECHECKS.map { r.leaveAt.minus(it) }.firstOrNull { it.isAfter(now) }

    /**
     * Minutes the leave time moved since the user was last told (positive = later, the bus is
     * late; negative = earlier, it comes early), or null below [Reminder.ALERT_MIN]. Measured
     * from the last told time, so a delay that creeps up 2 min at a time still alerts once at 4.
     */
    fun alertMinutes(updated: Reminder): Long? {
        val moved = Duration.between(updated.toldLeaveAt, updated.leaveAt).toMinutes()
        return moved.takeIf { kotlin.math.abs(it) >= Reminder.ALERT_MIN }
    }

    private fun Itinerary.boardsSameVehicle(r: Reminder): Boolean {
        val l = firstTransitLeg ?: return false
        if (r.tripId != null && l.tripId != null) return r.tripId == l.tripId
        val sched = l.scheduledStartTime?.let(::parseTime) ?: l.start
        return lineLabel(l) == r.line && l.from.name == r.boardStop &&
            Duration.between(sched, r.scheduledBoarding).abs() <= Duration.ofMinutes(1)
    }
}
