package il.transit.core.plan

import il.transit.core.api.Itinerary
import il.transit.core.api.PlanResponse
import java.time.Instant

/**
 * "Earlier" / "Later" on the Trip tab: pages of the same search merged into one list, and
 * "if I miss this" from what is already loaded. Pure; the request is [TripPlanner.page].
 */
object TripPages {
    enum class Direction { EARLIER, LATER }

    /** Most options the list keeps; more pages push out the ones at the far end. */
    const val MAX_OPTIONS = 20

    /**
     * MOTIS also returns trips that wait out a night or Shabbat (Friday 16:19, arrive Saturday
     * 17:25: 25 h). Nobody wants those next to a 91-min option: drop anything over twice the
     * fastest and over [TripPlanner.MAX_EXTRA_SEC] longer. Then the unreasonable chains go
     * ([reasonable]).
     */
    fun sane(options: List<Itinerary>): List<Itinerary> {
        val fastest = options.minOfOrNull { it.duration } ?: 0
        val short = options.filter { it.duration <= maxOf(2 * fastest, fastest + TripPlanner.MAX_EXTRA_SEC) }
        return reasonable(short, { it.transfers }, { it.end }, { it.start })
    }

    /**
     * [options] without the unreasonable chains (D12). The window bounds both ends, whatever
     * the time mode: X goes when some Y has at least [TripPlanner.CHAIN_FEWER_CHANGES] fewer
     * changes, arrives at most [TripPlanner.CHAIN_WINDOW_SEC] after X **and** leaves at most
     * that much before X. The departure bound is the arrive-by reading; on depart-after lists it
     * also keeps a direct bus at 07:00 from wiping out every 2-change option that leaves at
     * 10:00 (someone who takes X could not have taken that Y).
     * X with [TripPlanner.LONG_CHAIN_CHANGES]+ changes goes when an option with at most
     * [TripPlanner.SHORT_CHAIN_CHANGES] is within the same window (owner, 2026-10-12; with
     * today's constants that Y also has 2+ fewer changes, so the first rule already covers it).
     * [mayReplace] (y, x) limits which Y can drop X in both rules; the car tabs pass "costs the
     * driver no more than X", so a chain that saves the driver time stays. Each Y is from
     * [options] as given, and Y has fewer changes than X, so the fewest-change option is never
     * dropped and a non-empty list stays non-empty. Order kept.
     */
    fun <T> reasonable(
        options: List<T>,
        transfers: (T) -> Int,
        arrival: (T) -> Instant,
        departure: (T) -> Instant,
        mayReplace: (y: T, x: T) -> Boolean = { _, _ -> true },
    ): List<T> {
        if (options.size < 2) return options
        val window = TripPlanner.CHAIN_WINDOW_SEC.toLong()
        fun alternative(y: T, x: T): Boolean =
            !arrival(y).isAfter(arrival(x).plusSeconds(window)) && !departure(y).isBefore(departure(x).minusSeconds(window))
        val kept = options.filter { x ->
            val changes = transfers(x)
            val longChain = changes >= TripPlanner.LONG_CHAIN_CHANGES &&
                options.any { y -> transfers(y) <= TripPlanner.SHORT_CHAIN_CHANGES && alternative(y, x) && mayReplace(y, x) }
            val beaten = options.any { y -> transfers(y) <= changes - TripPlanner.CHAIN_FEWER_CHANGES && alternative(y, x) && mayReplace(y, x) }
            !longChain && !beaten
        }
        return kept.ifEmpty { options }
    }

    /** The planner's order: earliest arrival first, or latest departure first for arrive-by. */
    fun order(options: List<Itinerary>, arriveBy: Boolean): List<Itinerary> = if (arriveBy) {
        options.sortedWith(compareByDescending<Itinerary> { it.start }.thenBy { it.transfers })
    } else {
        options.sortedWith(compareBy<Itinerary> { it.end }.thenBy { it.transfers })
    }

    /** Same vehicles, same start: one option, whichever page it came from. The start is the
     *  first boarding's timetable time, so a live delay does not make a second copy. */
    fun key(it: Itinerary): String {
        val transit = it.legs.filter { l -> l.isTransit }
        val start = transit.firstOrNull()?.let { l -> l.scheduledStartTime ?: l.startTime } ?: it.startTime
        return transit.joinToString("+") { l -> l.tripId ?: l.startTime } + "@" + start
    }

    /**
     * [page] merged into [current]: deduped by [key] (the copy already shown stays), filtered
     * by [sane] over the whole list (so a Friday Later page that waits out Shabbat adds
     * nothing), capped at [MAX_OPTIONS] by dropping the options furthest from the new page, and
     * in [order]. Only the cursor on [direction]'s side moves; null there = no more pages.
     * The sort chips apply on top, as for any result.
     */
    fun merge(current: TripResult, page: PlanResponse, direction: Direction, arriveBy: Boolean): TripResult {
        val seen = current.itineraries.mapTo(HashSet(), ::key)
        val merged = sane(current.itineraries + page.itineraries.filter { seen.add(key(it)) })
        val byDeparture = merged.sortedBy { it.start }
        val kept = (if (direction == Direction.LATER) byDeparture.takeLast(MAX_OPTIONS) else byDeparture.take(MAX_OPTIONS)).toSet()
        return current.copy(
            itineraries = order(merged.filter { it in kept }, arriveBy),
            earlierCursor = if (direction == Direction.EARLIER) page.previousPageCursor else current.earlierCursor,
            laterCursor = if (direction == Direction.LATER) page.nextPageCursor else current.laterCursor,
            paged = true,
        )
    }

    /**
     * A background refresh of a paged list. [fresh] is the first page planned again: its copy
     * of an option replaces the old one (newer times), the other loaded pages stay unless their
     * bus has left by [now], and the cursors stay [current]'s. An unpaged list is just [fresh].
     */
    fun refresh(current: TripResult?, fresh: TripResult, now: Instant, arriveBy: Boolean): TripResult {
        if (current == null || !current.paged) return fresh
        val freshKeys = fresh.itineraries.mapTo(HashSet(), ::key)
        val kept = current.itineraries.filter { key(it) !in freshKeys && departure(it) >= now }
        return current.copy(itineraries = order(sane(fresh.itineraries + kept), arriveBy).take(MAX_OPTIONS), walkOnly = fresh.walkOnly)
    }

    /** What an Earlier / Later tap does to the shown list once its answer is back. */
    sealed interface PageOutcome {
        /** The page merged: show [result]. */
        data class Merged(val result: TripResult) : PageOutcome

        /** The request failed (offline): keep the list as it is and say so under the buttons. */
        data object Failed : PageOutcome

        /** A new search replaced the list while the page loaded: drop the page quietly. */
        data object Stale : PageOutcome
    }

    /**
     * [requested] is the list the tap paged, [shown] the list on screen now, [merged] the
     * answer (null = the request failed). Never touches the shown list on failure.
     */
    fun outcome(requested: TripResult, shown: TripResult?, merged: TripResult?): PageOutcome = when {
        shown !== requested -> PageOutcome.Stale
        merged == null -> PageOutcome.Failed
        else -> PageOutcome.Merged(merged)
    }

    /** What "if I miss this" says about [selected]. */
    sealed interface Miss {
        /** [next] is the first loaded option whose bus or train leaves after [selected]'s;
         *  it arrives [laterMin] minutes later (0 when it is not later). */
        data class Next(val next: Itinerary, val laterMin: Int) : Miss

        /** Nothing later is loaded: offer the Later page instead (no request until tapped). */
        data object ShowLater : Miss
    }

    /** Null when [selected] has no transit leg (walking all the way): nothing to miss. */
    fun ifIMissIt(selected: Itinerary, options: List<Itinerary>): Miss? {
        val leaves = selected.firstTransitLeg?.start ?: return null
        val next = options
            .filter { o -> o.firstTransitLeg?.start?.isAfter(leaves) == true }
            .minWithOrNull(compareBy<Itinerary> { it.firstTransitLeg!!.start }.thenBy { it.end })
            ?: return Miss.ShowLater
        val later = ((next.end.epochSecond - selected.end.epochSecond) / 60).toInt().coerceAtLeast(0)
        return Miss.Next(next, later)
    }

    private fun departure(it: Itinerary): Instant = it.firstTransitLeg?.start ?: it.start
}
