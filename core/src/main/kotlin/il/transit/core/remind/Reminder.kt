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
) {
    val from: LatLon get() = LatLon(fromLat, fromLon)
    val to: LatLon get() = LatLon(toLat, toLon)
    val leaveAt: Instant get() = Instant.ofEpochSecond(leaveAtEpoch)
    val scheduledBoarding: Instant get() = Instant.ofEpochSecond(scheduledBoardingEpoch)

    /** Re-check real-time data this long before leaving. */
    val recheckAt: Instant get() = leaveAt.minus(RECHECK_BEFORE)

    /** "line 5 at 12:14 from X" pieces for the notification. */
    val boardingTime: String get() = hhmm(scheduledBoarding.plusSeconds(60L * (boardingDelayMin ?: 0)))

    companion object {
        val RECHECK_BEFORE: Duration = Duration.ofMinutes(15)

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

    /** When the re-check should run: [Reminder.recheckAt], or right away if that has passed. */
    fun recheckTime(r: Reminder, now: Instant): Instant = maxOf(r.recheckAt, now)

    private fun Itinerary.boardsSameVehicle(r: Reminder): Boolean {
        val l = firstTransitLeg ?: return false
        if (r.tripId != null && l.tripId != null) return r.tripId == l.tripId
        val sched = l.scheduledStartTime?.let(::parseTime) ?: l.start
        return lineLabel(l) == r.line && l.from.name == r.boardStop &&
            Duration.between(sched, r.scheduledBoarding).abs() <= Duration.ofMinutes(1)
    }
}
