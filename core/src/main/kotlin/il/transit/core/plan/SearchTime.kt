package il.transit.core.plan

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The departure a "Now" search sends: the clock rounded down to the minute (Phase 11 F6). Every request is
 * cached by its query, time included, so with seconds in it two "Now" searches a few seconds apart never met
 * the guard's cache. MOTIS plans in whole minutes anyway. Explicit times (Depart at / Arrive by) stay exact.
 */
object SearchTime {
    fun of(now: Instant): Instant = now.truncatedTo(ChronoUnit.MINUTES)
}
