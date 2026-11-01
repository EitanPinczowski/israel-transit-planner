package il.transit.planner

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import il.transit.planner.ui.Field
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.Suggestion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The set-up end of the nightly performance reports (Phase 11 T8, `tools/ui_perf_run.sh`). `am start --es uitest_perf map|ride`:
 *  - `map`: BGU -> Tel Aviv searched, the route drawn on the map (for the pan / zoom jank run);
 *  - `ride`: the same, the first option with a transit leg selected, and a REAL ride started on the emulator's own GPS
 *    (`adb emu geo fix`; no test provider, unlike ProcessDeathProbe), so the battery numbers are those of the ride service.
 * Logs `ready …` under [TAG]. uitest build only.
 */
object PerfProbe : Application.ActivityLifecycleCallbacks {
    const val EXTRA = "uitest_perf"
    const val TAG = "PerfProbe"
    private var what: String? = null

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        what = if (activity is MainActivity && savedInstanceState == null) activity.intent.getStringExtra(EXTRA) else null
    }

    override fun onActivityResumed(activity: Activity) {
        val main = activity as? MainActivity ?: return
        val kind = what ?: return
        what = null
        main.lifecycleScope.launch {
            delay(500)
            val vm = ViewModelProvider(main)[MainViewModel::class.java]
            val (from, to) = UiTestApp.BGU to UiTestApp.TEL_AVIV
            vm.startEditing(Field.FROM)
            vm.pick(Suggestion(from.name, null, from.latLon, saved = true, isStop = false))
            vm.startEditing(Field.TO)
            vm.pick(Suggestion(to.name, null, to.latLon, saved = true, isStop = false))
            val s = withTimeoutOrNull(15_000) { vm.state.first { it.results != null && !it.loading } }
            if (s == null) { Log.e(TAG, "no results"); return@launch }
            val i = s.options.indexOfFirst { it.firstTransitLeg != null }.coerceAtLeast(0)
            vm.select(i)
            if (kind == "ride") {
                val started = vm.startRide()
                Log.i(TAG, "ready kind=ride started=$started")
            } else {
                Log.i(TAG, "ready kind=map options=${s.options.size}")
            }
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
