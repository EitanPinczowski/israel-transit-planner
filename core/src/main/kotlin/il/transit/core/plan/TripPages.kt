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
     * fastest and over [TripPlanner.MAX_EXTRA_SEC] longer.
     */
    fun sane(options: List<Itinerary>): List<Itinerary> {
        val fastest = options.minOfOrNull { it.duration } ?: 0
        return options.filter { it.duration <= maxOf(2 * fastest, fastest + TripPlanner.MAX_EXTRA_SEC) }
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
