package il.transit.planner

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import il.transit.core.present.LegPalette
import il.transit.planner.ui.Field
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.Suggestion
import il.transit.planner.ui.TripDetailsViewModel
import il.transit.planner.ui.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Both ends of the process-death journey (`tools/ui_behaviour_run.sh`, Phase 10 D11). An
 * instrumented test can't outlive its own process, so the shell drives it:
 *  1. `am start -S … --ez [EXTRA] true`: the probe searches BGU → Tel Aviv, picks the second
 *     option, opens its first ride's trip sheet and logs `ready …`;
 *  2. Home, `am kill`, `am start`: Android recreates the Activity from its saved state; the probe
 *     logs `restored …` with what came back. The script compares the two lines.
 * uitest build only; tag [TAG].
 */
object ProcessDeathProbe : Application.ActivityLifecycleCallbacks {
    const val EXTRA = "uitest_process_death_setup"
    const val TAG = "ProcessDeath"

    /** What onResume should do for the Activity created last: set up, report, or nothing. */
    private var pending: String? = null

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is MainActivity) return
        pending = when {
            savedInstanceState != null -> "report"
            activity.intent.getBooleanExtra(EXTRA, false) -> "setup"
            else -> null
        }
    }

    override fun onActivityResumed(activity: Activity) {
        val main = activity as? MainActivity ?: return
        val what = pending ?: return
        pending = null
        // The Activity created both ViewModels in onCreate; these find the same ones.
        val vm = ViewModelProvider(main)[MainViewModel::class.java]
        val trip = ViewModelProvider(main)[TripDetailsViewModel::class.java]
        val app = main.application as UiTestApp
        main.lifecycleScope.launch {
            if (what == "setup") {
                val (from, to) = UiTestApp.BGU to UiTestApp.TEL_AVIV
                vm.startEditing(Field.FROM)
                vm.pick(Suggestion(from.name, null, from.latLon, saved = true, isStop = false))
                vm.startEditing(Field.TO)
                vm.pick(Suggestion(to.name, null, to.latLon, saved = true, isStop = false))
                val s = withTimeoutOrNull(15_000) { vm.state.first { it.results != null && !it.loading } }
                if (s == null) { Log.e(TAG, "setup: no results"); return@launch }
                vm.select(if (s.options.size > 1) 1 else 0)
                val itin = vm.state.value.selectedItinerary ?: return@launch
                val leg = itin.legs.first { it.isTransit }
                trip.open(leg, LegPalette.colorOf(itin.legs, leg))
                delay(2_000) // the Activity reports the open sheet to the ViewModel, which saves it
                Log.i(TAG, "ready ${line(vm.state.value, trip)}")
            } else {
                val before = app.replay.calls.get()
                withTimeoutOrNull(10_000) { vm.state.first { it.results != null } }
                delay(2_000) // the trip sheet reopens once its option is back
                Log.i(TAG, "restored ${line(vm.state.value, trip)} requests=${app.replay.calls.get() - before}")
            }
        }
    }

    private fun line(s: UiState, trip: TripDetailsViewModel): String {
        val itin = s.selectedItinerary
        val leg = trip.sheet.value?.leg?.let { l -> itin?.legs?.indexOf(l) }
        return "mode=${s.mode} selected=${s.selected} option=${MainViewModel.optionId(itin)} sheet=$leg"
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
