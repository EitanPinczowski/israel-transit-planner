package il.transit.core.plan

import il.transit.core.api.Endpoint
import il.transit.core.api.Itinerary
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import il.transit.core.remind.Reminder
import il.transit.core.user.UserSettings
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant

/**
 * "Missed it? Plan from here" (Phase 11 E2): from the ride, leave-countdown and "trip changed"
 * notifications and the trip sheet. One tap = at most [PLANS_PER_TAP] `plan`, from where the
 * phone is now to the same destination, leaving now, with the user's settings and sort. Never
 * automatic, never in the background. No reverse geocode: the start is "My location".
 *
 * One instance lives as long as the screen, so a second tap within [REUSE_SEC] repeats the
 * first tap's exact query (same start, same time) and the guard cache answers it: 0 requests.
 */
class MissedIt(private val planner: TripPlanner) {
    /** Where the phone is: [ageSec] since the fix was taken, [accuracyM] null when unknown. */
    data class Fix(val at: LatLon, val ageSec: Long, val accuracyM: Float?)

    /**
     * The trip's final destination; it travels in the notification's intent, so a tap works
     * after the app was killed. [arriveBy]: the trip was an arrive-by search (the banner says it
     * now leaves now). From a car tab it is the passenger's own destination: a plain Trip.
     */
    data class Target(val name: String?, val at: LatLon, val arriveBy: Boolean = false)

    /** Where the phone is, read on the tap only (Android: `MissedItAction`). Null without permission. */
    interface Fixes {
        /** The phone's last known fix ([pick] of the ones it has). */
        fun last(): Fix?

        /** Asks the phone for one new fix and waits for it; the tap gives up after [WAIT_MS]. */
        suspend fun fresh(): Fix?
    }

    sealed interface Outcome {
        /** Within [ARRIVED_M] of the destination: "You're already there", no request. */
        data object AlreadyThere : Outcome

        /** No usable fix (no permission, no GPS in [WAIT_MS]): the Trip tab asks "Where are you?", no request. */
        data object NeedStart : Outcome

        /** Options from [from], searched at [at]; [wasArriveBy]: the banner says it leaves now instead. */
        data class Planned(val from: LatLon, val at: Instant, val result: TripResult, val wasArriveBy: Boolean) : Outcome
    }

    private data class Last(val from: LatLon, val to: LatLon, val settings: UserSettings, val at: Instant)

    private var last: Last? = null

    /**
     * One tap. [fix] is the phone's last fix, read on the tap; when it is too old or too rough,
     * [awaitFix] is asked for a new one for at most [WAIT_MS] (it should return a usable one or
     * keep waiting). Throws what the planner throws (no signal): the caller says so.
     */
    suspend fun tap(
        target: Target,
        fix: Fix?,
        now: Instant,
        settings: UserSettings,
        language: String,
        awaitFix: suspend () -> Fix? = { null },
    ): Outcome {
        val start = fix?.takeIf(::usable)
            ?: withTimeoutOrNull(WAIT_MS) { awaitFix() }?.takeIf(::usable)
            ?: return Outcome.NeedStart
        if (Geo.distanceM(start.at, target.at) <= ARRIVED_M) return Outcome.AlreadyThere
        val again = last?.takeIf {
            val soon = !now.isBefore(it.at) && Duration.between(it.at, now).seconds <= REUSE_SEC
            val same = it.to == target.at && it.settings == settings
            same && soon && Geo.distanceM(it.from, start.at) <= SAME_START_M
        }
        val from = again?.from ?: start.at
        val at = again?.at ?: now
        // Before the plan: a second tap while this one is still waiting repeats the same query (guard cache).
        last = Last(from, target.at, settings, at)
        val result = planner.plan(query(target, from, settings, language), at)
        return Outcome.Planned(from, at, result, target.arriveBy)
    }

    companion object {
        /** One `plan` per tap; a second tap within [REUSE_SEC] costs 0 (BudgetInventoryTest). */
        const val PLANS_PER_TAP = 1

        /** The last fix is used when at most this old … */
        const val MAX_FIX_AGE_SEC = 120L

        /** … and at least this precise; otherwise a new one is awaited. */
        const val MAX_ACCURACY_M = 100f

        /** How long a tap waits for a new fix before asking "Where are you?". */
        const val WAIT_MS = 10_000L

        /** Closer than this to the destination: "You're already there". */
        const val ARRIVED_M = 150.0

        /** A second tap this soon, from about the same spot, repeats the first query. */
        const val REUSE_SEC = 60L
        const val SAME_START_M = 100.0

        fun usable(f: Fix): Boolean = f.ageSec in 0..MAX_FIX_AGE_SEC && (f.accuracyM ?: Float.MAX_VALUE) <= MAX_ACCURACY_M

        /**
         * The phone's freshest fix among [fixes] (GPS, network, fused, the map's): the newest usable
         * one, else the newest. A fresher rough network fix must not hide a GPS fix of a minute ago.
         */
        fun pick(fixes: List<Fix>): Fix? {
            val newest = fixes.sortedBy { it.ageSec }
            return newest.firstOrNull(::usable) ?: newest.firstOrNull()
        }

        /**
         * Where [itinerary] ends: the destination a ride or a countdown carries into its notification.
         * MOTIS names the street end "END"; then [name] (the reminder's place name) or none (the app
         * names it from a saved place or the trip on screen).
         */
        fun target(itinerary: Itinerary, name: String? = null, arriveBy: Boolean = false): Target? = itinerary.legs.lastOrNull()?.to?.let {
            Target(name ?: it.name.takeUnless { n -> n.isBlank() || n == "END" }, LatLon(it.lat, it.lon), arriveBy)
        }

        /** Always a plain Trip, depart now: an arrive-by or car-tab trip becomes this. */
        fun query(target: Target, from: LatLon, settings: UserSettings, language: String) =
            TripQuery(Endpoint.Coord(from), Endpoint.Coord(target.at), TimeMode.NOW, null, settings, language)

        /**
         * The reminder this tap ends (owner decision 5): the one counting down or past its leave
         * time, which is the trip just missed. A reminder still ahead (a later trip, another day)
         * is left alone.
         */
        fun endsReminder(r: Reminder?, now: Instant): Boolean = r != null && (r.counting || !r.leaveAt.isAfter(now))
    }
}
