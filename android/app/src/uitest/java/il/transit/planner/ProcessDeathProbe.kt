package il.transit.planner

import android.app.Activity
import android.app.Application
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import il.transit.core.present.LegPalette
import il.transit.core.user.SavedPlace
import il.transit.planner.ride.RideService
import il.transit.planner.ui.AppMode
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
 * Phase 11 T7 adds two more setups, driven by `tools/ui_system_run.sh` (logs under [SYSTEM_TAG]):
 *  - [EXTRA_TAB] = a tab name (`BETTER_START`, `DROP_OFF`, `PICK_UP`, `PARK_RIDE`, `ERRANDS`): the
 *    same round trip for that tab (the second option, its first ride's trip sheet); the `ready` /
 *    `restored` lines then read `mode=… from=… to=… driverTo=… stops=… options=… selected=… option=…
 *    sheet=… carPath=…`, all of it a promise since Phase 11 R1 (the tab cache brings the options
 *    back), and `requests=` counts what the restore asked, the reopened sheet's `trip` left out;
 *  - [EXTRA_RIDE]: starts a ride on the mock location, so the script can revoke the location
 *    permission under it; after the restart `ride-after …` says what is left.
 * uitest build only; tag [TAG].
 */
object ProcessDeathProbe : Application.ActivityLifecycleCallbacks {
    const val EXTRA = "uitest_process_death_setup"
    const val EXTRA_TAB = "uitest_process_death_tab"
    const val EXTRA_RIDE = "uitest_ride_setup"
    const val TAG = "ProcessDeath"
    const val SYSTEM_TAG = "SystemProbe"

    /** What onResume should do for the Activity created last: set up, or report what it shows. */
    private var pending: String? = null
    private var tab: String? = null
    private var ride = false

    /** The Activity created last came from a saved state (recreated after the kill). */
    private var fromSaved = false

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is MainActivity) return
        fromSaved = savedInstanceState != null
        tab = if (fromSaved) null else activity.intent.getStringExtra(EXTRA_TAB)
        ride = !fromSaved && activity.intent.getBooleanExtra(EXTRA_RIDE, false)
        pending = if (!fromSaved && (tab != null || ride || activity.intent.getBooleanExtra(EXTRA, false))) "setup" else "report"
    }

    override fun onActivityResumed(activity: Activity) {
        val main = activity as? MainActivity ?: return
        val what = pending ?: return
        pending = null
        val saved = fromSaved
        val app = main.application as UiTestApp
        main.lifecycleScope.launch {
            // This runs inside super.onResume(), before MainActivity creates its trip-sheet
            // ViewModel with its own factory: look both up once onResume has finished.
            delay(500)
            val vm = ViewModelProvider(main)[MainViewModel::class.java]
            val trip = ViewModelProvider(main)[TripDetailsViewModel::class.java]
            // The tab survives the kill as a marker file, so the restart reports in the same format.
            val marker = java.io.File(main.filesDir, "uitest_death_tab")
            if (what == "setup") { if (tab != null) marker.writeText(tab!!) else marker.delete() }
            if (what == "setup" && ride) {
                rideSetup(main, vm)
            } else if (what == "setup" && tab != null) {
                tabSetup(vm, trip, tab!!)
            } else if (what == "setup") {
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
                Log.i(TAG, "ready ${line(vm.state.value, trip, marker.exists())}")
            } else {
                val before = app.replay.calls.get()
                val tripsBefore = app.replay.tripCalls.get()
                val tabbed = marker.exists()
                withTimeoutOrNull(10_000) { vm.state.first { if (tabbed) it.hasResults else it.results != null } }
                delay(2_000) // the trip sheet reopens once its option is back
                // R1: the reopened sheet asks its one `trip`; anything else is the restore searching again.
                val asked = if (tabbed) (app.replay.calls.get() - before) - (app.replay.tripCalls.get() - tripsBefore) else app.replay.calls.get() - before
                // saved=false: this Activity did not come back from a saved state (the script says why).
                Log.i(TAG, "restored saved=$saved ${line(vm.state.value, trip, marker.exists())} requests=$asked")
                rideAfter(main)
                missedAfter(vm, app.replay.calls.get() - before)
            }
        }
    }

    private suspend fun place(vm: MainViewModel, field: Field, p: SavedPlace) {
        vm.startEditing(field)
        vm.pick(Suggestion(p.name, null, p.latLon, saved = true, isStop = false))
        delay(300)
    }

    /** One tab's search, as a user would set it up (places the replay's fixtures answer). */
    private suspend fun tabSetup(vm: MainViewModel, trip: TripDetailsViewModel, tab: String) {
        val a = UiTestApp
        if (tab == "ERRANDS") {
            place(vm, Field.FROM, a.BGU)
            place(vm, Field.STOP, a.MEITAR)
            place(vm, Field.TO, a.TEL_AVIV)
        } else {
            vm.setMode(AppMode.valueOf(tab))
            when (tab) {
                "DROP_OFF" -> { place(vm, Field.FROM, a.MEITAR); place(vm, Field.DRIVER_TO, a.TEL_AVIV); place(vm, Field.TO, a.REHOVOT) }
                "PICK_UP" -> { place(vm, Field.FROM, a.TEL_AVIV); place(vm, Field.TO, a.MEITAR) }
                else -> { place(vm, Field.FROM, a.MEITAR); place(vm, Field.TO, a.TEL_AVIV) }
            }
        }
        val s = withTimeoutOrNull(20_000) { vm.state.first { !it.loading && (it.hasResults || it.error != null) } }
        if (s == null || !s.hasResults) { Log.e(TAG, "setup $tab: no results"); return }
        vm.select(if (s.options.size > 1) 1 else 0)
        val itin = vm.state.value.selectedItinerary ?: return
        itin.legs.firstOrNull { it.isTransit }?.let { leg -> trip.open(leg, LegPalette.colorOf(itin.legs, leg)) }
        delay(2_000) // the Activity reports the open sheet to the ViewModel, which saves it
        Log.i(TAG, "ready ${tabLine(vm.state.value, trip)}")
    }

    /** A ride on the mock GPS, started the way the button does; the script then revokes location. */
    private suspend fun rideSetup(main: MainActivity, vm: MainViewModel) {
        val s = withTimeoutOrNull(15_000) {
            val (from, to) = UiTestApp.BGU to UiTestApp.TEL_AVIV
            vm.startEditing(Field.FROM)
            vm.pick(Suggestion(from.name, null, from.latLon, saved = true, isStop = false))
            vm.startEditing(Field.TO)
            vm.pick(Suggestion(to.name, null, to.latLon, saved = true, isStop = false))
            vm.state.first { it.results != null && !it.loading }
        }
        if (s == null) { Log.e(SYSTEM_TAG, "ride-setup: no results"); return }
        val i = s.options.indexOfFirst { it.firstTransitLeg != null }.coerceAtLeast(0)
        vm.select(i)
        val lm = main.getSystemService(LocationManager::class.java)
        val gps = LocationManager.GPS_PROVIDER
        runCatching { lm.removeTestProvider(gps) }
        @Suppress("DEPRECATION")
        lm.addTestProvider(gps, false, false, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
        lm.setTestProviderEnabled(gps, true)
        val started = vm.startRide()
        val from = s.options[i]
            .legs
            .first { it.isTransit }
            .from.latLon
        lm.setTestProviderLocation(
            gps,
            Location(gps).apply {
                latitude = from.lat
                longitude = from.lon
                accuracy = 5f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            },
        )
        val until = SystemClock.uptimeMillis() + 8_000
        while (!RideService.active.value && SystemClock.uptimeMillis() < until) delay(200)
        Log.i(SYSTEM_TAG, "ride-ready started=$started active=${RideService.active.value}")
    }

    /** After the restart: what the ride left behind (the script judges it). */
    private fun rideAfter(main: MainActivity) {
        val nm = main.getSystemService(android.app.NotificationManager::class.java)
        val ongoing = nm.activeNotifications.filter { it.notification.channelId == "ride_ongoing" }
        val text = ongoing.joinToString(" / ") {
            it.notification.extras
                .getCharSequence("android.title")
                ?.toString()
                .orEmpty()
        }
        Log.i(SYSTEM_TAG, "ride-after active=${RideService.active.value} ongoing=${ongoing.size} title=$text")
    }

    /** E2: a "Missed it" tap with no location permission asks for the start, with no request. */
    private fun missedAfter(vm: MainViewModel, requests: Int) {
        val s = vm.state.value
        Log.i(SYSTEM_TAG, "missed-it kind=${s.missedIt?.kind} editing=${s.editing} requests=$requests")
    }

    private fun tabLine(s: UiState, trip: TripDetailsViewModel): String =
        "mode=${s.mode} from=${s.from} to=${s.to} driverTo=${s.driverTo} stops=${s.chainStops.size} options=${s.options.size} " +
            "${optionLine(s, trip)} carPath=${s.selectedCarPath.size}"

    /** The selected option and the open trip sheet's leg in it. */
    private fun optionLine(s: UiState, trip: TripDetailsViewModel): String {
        val itin = s.selectedItinerary
        val leg = trip.sheet.value
            ?.leg
            ?.let { l -> itin?.legs?.indexOf(l) }
        return "selected=${s.selected} option=${MainViewModel.optionId(itin)} sheet=$leg"
    }

    private fun line(s: UiState, trip: TripDetailsViewModel, tabbed: Boolean): String =
        if (tabbed) tabLine(s, trip) else "mode=${s.mode} ${optionLine(s, trip)}"

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
