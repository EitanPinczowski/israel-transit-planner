package il.transit.planner.ui

import il.transit.core.plan.TripSort
import il.transit.core.plan.arrangeFront
import il.transit.core.plan.reselect

/**
 * Phase 10 D12: the car + transit tabs' options without unreasonable chains and in [sort]
 * order. They are stored that way, so the card list, [UiState.options], the map line,
 * [UiState.selectedCarPath], "send to driver" and the parked station all index one list.
 * The Trip tab sorts in [UiState.options] instead: its stored list stays in the planner's
 * order for Earlier / Later and the background refresh.
 */
internal fun UiState.arranged(sort: TripSort = settings.tripSort): UiState = copy(
    betterStart = betterStart?.let { r -> r.copy(options = arrangeFront(r.options, sort) { it.itinerary }) },
    dropOff = dropOff?.let { r -> r.copy(options = arrangeFront(r.options, sort) { it.transit }) },
    pickUp = pickUp?.let { r -> r.copy(options = arrangeFront(r.options, sort) { it.itinerary }) },
    parkRide = parkRide.result?.let { r -> parkRide.copy(result = r.copy(options = arrangeFront(r.options, sort) { it.itinerary })) } ?: parkRide,
)

/**
 * [sort] chosen: every tab re-ordered, and the option that was opened stays opened, so its
 * card, its car path and its map line move together (and D5's "safer change", which follows
 * the selected itinerary, stays with it). An errand chain keeps its leg order.
 */
internal fun UiState.resorted(sort: TripSort): UiState {
    val was = selectedItinerary
    val next = copy(settings = settings.copy(tripSort = sort)).arranged(sort)
    if (chain != null) return next
    val same = next.options.indexOfFirst { it === was }
    return next.copy(selected = if (same >= 0) same else reselect(was, next.options, 0))
}
