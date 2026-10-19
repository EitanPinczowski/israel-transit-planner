package il.transit.core.plan

import il.transit.core.api.Endpoint
import il.transit.core.api.Itinerary
import il.transit.core.api.PlanRequest
import il.transit.core.api.TransitApi
import il.transit.core.api.parseTime
import il.transit.core.features.Option
import il.transit.core.user.UserSettings
import java.time.Instant

enum class TimeMode { NOW, DEPART_AT, ARRIVE_BY }

/** How every tab orders its options (D12); a re-ordering of the same answers, never a new search. */
@kotlinx.serialization.Serializable
enum class TripSort { FASTEST, FEWEST_TRANSFERS, LEAST_WALKING }

/**
 * [options] in [sort] order, whatever the tab's payload [T]:
 * - FASTEST: earliest [arrival] (the car part included), or latest [departure] for [arriveBy];
 * - FEWEST_TRANSFERS: fewest transit transfers;
 * - LEAST_WALKING: least [walkSec] over the whole option.
 * The sort is stable: ties keep [options]' order, which callers pass in the planner's order.
 */
fun <T> sortOptions(
    options: List<T>,
    sort: TripSort,
    arriveBy: Boolean = false,
    itinerary: (T) -> Itinerary,
    arrival: (T) -> Instant = { itinerary(it).end },
    departure: (T) -> Instant = { itinerary(it).start },
): List<T> = when (sort) {
    TripSort.FASTEST -> if (arriveBy) options.sortedByDescending(departure) else options.sortedBy(arrival)
    TripSort.FEWEST_TRANSFERS -> options.sortedBy { itinerary(it).transfers }
    TripSort.LEAST_WALKING -> options.sortedBy { itinerary(it).walkSec }
}

/** The Trip tab: [TripResult.itineraries] (planner order) and the walk-only option, sorted together. */
fun sortOptions(result: TripResult, sort: TripSort, arriveBy: Boolean): List<Itinerary> =
    sortOptions(result.itineraries + listOfNotNull(result.walkOnly), sort, arriveBy, { it })

/** Plain itineraries, depart-after. */
fun sortOptions(options: List<Itinerary>, sort: TripSort): List<Itinerary> = sortOptions(options, sort, false, { it })

/**
 * A car + transit tab's options (a Pareto front, baselines included) as the list shows them:
 * the unreasonable chains dropped ([TripPages.reasonable], arrival = [Option.arrival], car part
 * included), then sorted. Ties fall back to the front's own order (arrival, then driver
 * cost, then transfers), whatever order [options] come in, so re-sorting a re-sorted list
 * gives the same answer as sorting the planner's.
 */
fun <T> arrangeFront(options: List<Option<T>>, sort: TripSort, itinerary: (T) -> Itinerary): List<Option<T>> {
    val planned = options.sortedWith(compareBy<Option<T>> { it.arrival }.thenBy { it.driverCostSec }.thenBy { it.transfers })
    // A chain that saves the driver time is a real trade-off, not an unreasonable chain: only
    // an option that costs the driver no more may replace it (review, D12).
    val kept = TripPages.reasonable(planned, { it.transfers }, { it.arrival }, { itinerary(it.payload).start }) { y, x -> y.driverCostSec <= x.driverCostSec }
    return sortOptions(kept, sort, false, { itinerary(it.payload) }, { it.arrival })
}

/** Seconds spent walking, all legs. */
val Itinerary.walkSec: Int get() = legs.filter { it.mode == il.transit.core.api.StreetModes.WALK }.sumOf { it.duration }

data class TripQuery(
    val from: Endpoint,
    val to: Endpoint,
    val timeMode: TimeMode = TimeMode.NOW,
    /** Ignored for [TimeMode.NOW]. */
    val time: Instant? = null,
    val settings: UserSettings = UserSettings(),
    val language: String = "he",
)

@kotlinx.serialization.Serializable
data class TripResult(
    /** Transit options, earliest arrival first (latest departure first for arrive-by). */
    val itineraries: List<Itinerary>,
    /** Walking all the way, when MOTIS offers it and it is not absurdly long. */
    val walkOnly: Itinerary?,
    /** Cursors for the windows before and after everything loaded so far ("Earlier" /
     *  "Later"); null = none, or a result saved before paging existed. */
    val earlierCursor: String? = null,
    val laterCursor: String? = null,
    /** The search's own time (epoch seconds), sent again with every page so a repeated cursor
     *  is the same query and the guard cache answers it. */
    val searchEpoch: Long? = null,
    /** An Earlier or Later page has been merged in. */
    val paged: Boolean = false,
)

/** The ordinary A→B search: one `plan` request. */
class TripPlanner(private val api: TransitApi) {
    /**
     * One search. [now] is the caller's clock: the live Trip tab passes it, and only then are options
     * that already left dropped ([notDeparted]); errands and the reminder re-check pass none (the
     * phone's time is used for a Now search) and keep MOTIS's answer as it is.
     */
    suspend fun plan(q: TripQuery, now: Instant? = null): TripResult {
        val time = if (q.timeMode == TimeMode.NOW) now ?: Instant.now() else requireNotNull(q.time) { "time needed for ${q.timeMode}" }
        val resp = api.plan(request(q, time))
        val shown = now?.let { notDeparted(resp.itineraries, q, time, it) } ?: resp.itineraries
        val sorted = TripPages.order(TripPages.sane(shown), q.timeMode == TimeMode.ARRIVE_BY)
        val walk = resp.direct.minByOrNull { it.duration }?.takeIf { it.duration <= MAX_WALK_ONLY_SEC }
        return TripResult(sorted, walk, resp.previousPageCursor, resp.nextPageCursor, time.epochSecond)
    }

    /**
     * "Earlier" / "Later": one `plan` request for the window next to [current], merged into it
     * ([TripPages.merge]). The request repeats [current]'s search time, so the same cursor
     * twice is the same query (the guard cache answers it). [current] unchanged when there is
     * no cursor that way. Not filtered for departures ([notDeparted]): "Earlier" is asked for
     * on purpose, and on a Now search it shows what just left.
     */
    suspend fun page(q: TripQuery, current: TripResult, direction: TripPages.Direction): TripResult {
        val cursor = when (direction) {
            TripPages.Direction.EARLIER -> current.earlierCursor
            TripPages.Direction.LATER -> current.laterCursor
        } ?: return current
        val time = current.searchEpoch?.let(Instant::ofEpochSecond) ?: q.time ?: Instant.now()
        val resp = api.plan(request(q, time).copy(pageCursor = cursor))
        return TripPages.merge(current, resp, direction, q.timeMode == TimeMode.ARRIVE_BY)
    }

    /**
     * Phase 11 E7 (owner's "wrong trip results"): for Now, and for a Depart at that is not
     * more than [LIVE_WINDOW_SEC] ahead, an option whose first boarding (not the walk before
     * it) is more than [DEPARTED_GRACE_SEC] before [now] has left without us: dropped. Arrive by
     * is left alone (a past arrival is asked about in the app, never searched). 0 requests.
     */
    private fun notDeparted(options: List<Itinerary>, q: TripQuery, time: Instant, now: Instant): List<Itinerary> {
        val live = q.timeMode == TimeMode.NOW || (q.timeMode == TimeMode.DEPART_AT && time < now.plusSeconds(LIVE_WINDOW_SEC))
        if (!live) return options
        val cutoff = now.minusSeconds(DEPARTED_GRACE_SEC)
        return options.filter { o -> o.firstTransitLeg?.let { parseTime(it.startTime) >= cutoff } ?: true }
    }

    private fun request(q: TripQuery, time: Instant) = PlanRequest(
        from = q.from,
        to = q.to,
        time = time,
        arriveBy = q.timeMode == TimeMode.ARRIVE_BY,
        preferences = q.settings.preferences(),
        language = q.language,
    )

    companion object {
        const val MAX_WALK_ONLY_SEC = 45 * 60

        /** A boarding this long ago may still be at the stop (clocks, a late bus): kept. */
        const val DEPARTED_GRACE_SEC = 60L

        /** A Depart at up to this far ahead is "about now": departed options are dropped as for Now. */
        const val LIVE_WINDOW_SEC = 15 * 60L

        /** An option is dropped when it takes more than twice the fastest and over an hour longer. */
        const val MAX_EXTRA_SEC = 60 * 60

        /**
         * No unreasonable chains (owner, D12, 2026-10-12; Golan → Tel Aviv came out as 7 buses,
         * 6 changes, listed first): an option is dropped when another has at least
         * [CHAIN_FEWER_CHANGES] fewer changes, arrives at most [CHAIN_WINDOW_SEC] later and
         * leaves at most that much earlier ([TripPages.reasonable]).
         */
        const val CHAIN_FEWER_CHANGES = 2
        const val CHAIN_WINDOW_SEC = 10 * 60

        /** An option with [LONG_CHAIN_CHANGES] or more changes goes when one with at most
         *  [SHORT_CHAIN_CHANGES] is within [CHAIN_WINDOW_SEC] (owner, 2026-10-12). */
        const val LONG_CHAIN_CHANGES = 5
        const val SHORT_CHAIN_CHANGES = 3
    }
}
