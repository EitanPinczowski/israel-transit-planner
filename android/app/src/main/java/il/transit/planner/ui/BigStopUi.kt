package il.transit.planner.ui

import il.transit.core.api.TransitApi
import il.transit.core.plan.BigStopChange
import il.transit.core.plan.BigStopChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Safer change" (Phase 10 D5) for the option the user opened: [BigStopCheck] looks for it,
 * [applyBigStop] swaps the option for the re-cut one (or back). The row shows only while the
 * selected option is the one the change was found for, or its re-cut copy ([bigStopFor]).
 */
data class BigStopUi(val change: BigStopChange? = null)

/** The change to offer for the selected option, and whether it is already in use. */
fun bigStopFor(s: UiState): Pair<BigStopChange, Boolean>? {
    val c = s.bigStop.change ?: return null
    val sel = s.selectedItinerary ?: return null
    return when {
        sel === c.original -> c to false
        sel === c.changed -> c to true
        else -> null
    }
}

/**
 * Runs the check when an option is opened (Trip tab, not errands, not a saved offline copy):
 * 0 requests unless its tightest change is at a small stop, then 2 `trip` (cached by the guard,
 * shared with the trip sheet). Opening another option cancels a check still running.
 */
class BigStopCheck(private val scope: CoroutineScope, private val api: TransitApi, private val language: () -> String) {
    private var job: Job? = null

    fun run(state: MutableStateFlow<UiState>) {
        job?.cancel()
        val s = state.value
        val itin = s.selectedItinerary ?: return
        if (s.mode != AppMode.TRIP || s.chain != null || s.offlineSince != null) return
        if (bigStopFor(s) != null || BigStopChanges.toCheck(itin) == null) return
        val buffer = s.settings.transferBufferMin
        job = scope.launch {
            val c = BigStopChanges.find(itin, api, buffer, language()) ?: return@launch
            state.update { if (it.selectedItinerary === itin) it.copy(bigStop = BigStopUi(c)) else it }
        }
    }
}

/**
 * "Use this change" ([use] = true) puts the re-cut option in place of the original in the
 * results, so the trip sheet, the get-off alert and the reminder all follow it; false puts the
 * original back. The same option stays selected.
 */
fun applyBigStop(s: UiState, use: Boolean): UiState {
    val c = s.bigStop.change ?: return s
    val r = s.results ?: return s
    val (from, to) = if (use) c.original to c.changed else c.changed to c.original
    val i = r.itineraries.indexOfFirst { it === from }
    if (i < 0) return s
    val next = s.copy(results = r.copy(itineraries = r.itineraries.toMutableList().also { it[i] = to }))
    val at = next.options.indexOfFirst { it === to }
    return if (at >= 0) next.copy(selected = at) else next
}
