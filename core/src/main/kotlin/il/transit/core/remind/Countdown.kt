package il.transit.core.remind

import il.transit.core.api.Itinerary
import il.transit.core.api.StreetModes
import java.time.Duration
import java.time.Instant

/**
 * The ongoing "leave now" notification (Phase 9 C5): it counts down to boarding and says
 * "Line 5 · board at Rager/Oren · walk 6 min". The Android side only turns these pieces
 * into strings; 0 requests.
 */
data class Countdown(
    val line: String?,
    /** A train: "Train 406", not "Line 406". */
    val rail: Boolean,
    val boardStop: String,
    /** Walking before the first vehicle, whole minutes; null when unknown or none. */
    val walkMin: Int?,
    /** Scheduled boarding plus the known delay: what the chronometer counts down to. */
    val boardAt: Instant,
    /** The notification removes itself then ([LINGER] after boarding). */
    val endAt: Instant,
) {
    /** For `setTimeoutAfter`: how long the notification may stay from [now]. */
    fun timeoutMs(now: Instant): Long = Duration.between(now, endAt).toMillis().coerceAtLeast(0)

    companion object {
        /** The countdown stays this long after boarding, then goes away by itself. */
        val LINGER: Duration = Duration.ofMinutes(2)

        /** Between the text's pieces, in both languages. */
        const val SEP = " · "

        /** Null once [now] is past [endAt]: a leave alarm that Doze held back too long posts nothing. */
        fun of(r: Reminder, now: Instant): Countdown? {
            val boardAt = r.scheduledBoarding.plusSeconds(60L * (r.boardingDelayMin ?: 0))
            val end = boardAt.plus(LINGER)
            if (!now.isBefore(end)) return null
            val board = r.itinerary?.firstTransitLeg
            return Countdown(
                line = r.line,
                rail = board?.mode == "RAIL",
                boardStop = r.boardStop,
                walkMin = r.itinerary?.let(::walkMin),
                boardAt = boardAt,
                endAt = end,
            )
        }

        /** Minutes walked before the first transit leg, rounded; null when there is no walk. */
        fun walkMin(itinerary: Itinerary): Int? {
            val before = itinerary.legs.takeWhile { !it.isTransit }
            val sec = before.filter { it.mode == StreetModes.WALK }.sumOf { it.duration }
            return Math.round(sec / 60.0).toInt().takeIf { it > 0 }
        }

        /** "Line 5 · board at X · walk 6 min" from the already translated pieces. */
        fun text(line: String, board: String, walk: String?): String = listOfNotNull(line, board, walk).joinToString(SEP)
    }
}
