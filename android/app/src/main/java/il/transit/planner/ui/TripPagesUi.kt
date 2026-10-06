package il.transit.planner.ui

import il.transit.core.plan.TripPages

/** The Earlier / Later buttons under the Trip options (Phase 9 C1): which page is loading,
 *  and whether the last tap failed (the list stays as it was). */
data class TripPagesUi(val loading: TripPages.Direction? = null, val failed: Boolean = false)
