package il.transit.planner.uitest

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.ActivityInfo
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import il.transit.core.features.BetterStartPlanner
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import il.transit.core.update.LatestRelease
import il.transit.core.update.UpdateState
import il.transit.planner.R
import il.transit.planner.UiTestApp.Companion.BGU
import il.transit.planner.UiTestApp.Companion.MEITAR
import il.transit.planner.UiTestApp.Companion.TEL_AVIV
import il.transit.planner.ride.RideService
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.Field
import il.transit.planner.ui.UiTags
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a user does, tap by tap, and what they must see. A broken journey fails the test;
 * a known UX gap is a [Findings] entry (report first) until it is fixed.
 * Every request goes to the replay, so call counts double as the Transitous budget check.
 */
@RunWith(AndroidJUnit4::class)
class JourneysTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val permissions = Permissions.grantAll()

    private fun AppDriver.tapRow(labelRes: Int) =
        compose.onNode(hasText(str(labelRes)) and hasClickAction() and hasAnyAncestor(hasTestTag(UiTags.TOP))).performClick()

    private fun AppDriver.tapSuggestion(name: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(name) and hasClickAction() and hasAnyAncestor(hasTestTag(UiTags.SUGGESTIONS))).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(hasText(name) and hasClickAction() and hasAnyAncestor(hasTestTag(UiTags.SUGGESTIONS))).onFirst().performClick()
    }

    private fun AppDriver.tripByTaps() {
        tapRow(R.string.from)
        tapSuggestion(BGU.name)
        tapRow(R.string.to)
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        field.performTextInput("תל")
        tapSuggestion(TEL_AVIV.name)
        awaitSearch()
    }

    /** The option with a train, which has a stop to get off at. */
    private fun AppDriver.selectTrain(): Int {
        val i = vm.state.value.options.indexOfFirst { it.legs.any { l -> l.mode == "RAIL" || l.mode.endsWith("_RAIL") || l.mode == "LONG_DISTANCE" } }
            .takeIf { it >= 0 } ?: vm.state.value.options.indexOfFirst { it.firstTransitLeg != null }
        onMain { it.select(i) }
        compose.waitForIdle()
        return i
    }

    @Test fun j02_search_by_taps_shows_results() = AppDriver(compose).launch().use { d ->
        val before = d.app.replay.calls.get()
        d.tripByTaps()
        val s = d.vm.state.value
        assertEquals(null, s.error)
        assertTrue("no options after picking from and to", s.options.isNotEmpty())
        compose.onAllNodes(hasText(":", substring = true) and hasAnyAncestor(hasTestTag(UiTags.BOTTOM))).onFirst().assertExists()
        // One plan, plus the geocoding of what was typed.
        assertTrue("requests: ${d.app.replay.calls.get() - before}", d.app.replay.calls.get() - before <= 3)
    }

    @Test fun j03_remind_me_arms_a_reminder() = AppDriver(compose).launch().use { d ->
        d.tripByTaps()
        d.selectTrain()
        d.tapInResults(d.str(R.string.remind_me))
        compose.waitUntil(5_000) { d.vm.state.value.reminder != null }
        val r = d.vm.state.value.reminder!!
        if (r.leaveAt.toEpochMilli() > System.currentTimeMillis()) {
            Findings.expect("J3", d.shell("dumpsys alarm").contains(d.app.packageName), "reminder set but no alarm registered for ${d.app.packageName}")
        }
        compose.onNode(hasText(d.str(R.string.cancel)) and hasAnyAncestor(hasTestTag(UiTags.BOTTOM))).performClick()
        compose.waitUntil(5_000) { d.vm.state.value.reminder == null }
    }

    /** Back should close what is open — the search, the results, a stop — before leaving. */
    @Test fun j04_back_closes_before_leaving() {
        fun backKeepsApp(open: AppDriver.() -> Unit, closed: AppDriver.() -> Boolean, what: String) {
            AppDriver(compose).launch().use { d ->
                // API 26: a crashed SystemUI's dialog may hold the key focus; clear it while
                // nothing is open, so the one Back below is the app's (every API, blocking).
                d.focusApp()
                d.open()
                compose.waitForIdle()
                SystemClock.sleep(700) // as a person would: the keyboard is up before Back
                val focus = d.focusedWindow()
                // A shell key event: it may go to the keyboard's window (another app), which the
                // instrumentation's own injection may not (INJECT_EVENTS, API 26). Not UiAutomator
                // (its accessibility connection stays up for later tests) nor Espresso (it waits
                // for window focus, which API 26 may not give).
                d.shell("input keyevent 4")
                // The keyboard (if up) takes a Back and slides away first: give it 2 s.
                val until = SystemClock.uptimeMillis() + 2_000
                do SystemClock.sleep(100) while (SystemClock.uptimeMillis() < until &&
                    d.scenarioState?.isAtLeast(Lifecycle.State.RESUMED) == true && !d.closed())
                val alive = d.scenarioState?.isAtLeast(Lifecycle.State.RESUMED) == true
                assertTrue("J4: one Back with $what open " + (if (alive) "did not close it" else "left the app") +
                    " (${d.inputState()}; key focus at the press: $focus)", alive && d.closed())
            }
        }
        backKeepsApp({ onMain { it.startEditing(Field.TO) } }, { vm.state.value.editing == null }, "the search")
        backKeepsApp({ place(Field.FROM, BGU); place(Field.TO, TEL_AVIV); awaitSearch() }, { !vm.state.value.hasResults }, "results")
        backKeepsApp({ onMain { it.openStop("il-Israel-MOT_37314", "באר שבע צפון") } }, { vm.state.value.stopSheet == null }, "a stop's departures")
    }

    @Test fun j05_rotation_and_dark_mode_keep_the_results() = AppDriver(compose).launch().use { d ->
        d.tripByTaps()
        val count = d.vm.state.value.options.size
        d.inst.runOnMainSync { d.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        SystemClock.sleep(1500)
        d.refreshActivity()
        compose.waitForIdle()
        assertEquals("results lost on rotation", count, d.vm.state.value.options.size)
        d.inst.runOnMainSync { d.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        SystemClock.sleep(1500)
        d.refreshActivity()
        d.shell("cmd uimode night yes")
        SystemClock.sleep(1500)
        d.refreshActivity()
        compose.waitForIdle()
        assertEquals("results lost on a dark-mode switch", count, d.vm.state.value.options.size)
        d.shell("cmd uimode night ${if (Run.theme == "dark") "yes" else "no"}")
        SystemClock.sleep(1000)
    }

    @Test fun j07_better_start_slider_searches_once_within_budget() = AppDriver(compose).launch().use { d ->
        // The tab is an icon: found by the name TalkBack reads, so a missing description fails here.
        compose.onNode(hasContentDescription(d.str(R.string.tab_better_start)) and hasClickAction()).performClick()
        compose.waitUntil(5_000) { d.vm.state.value.mode == AppMode.BETTER_START }
        d.place(Field.FROM, MEITAR)
        var before = d.app.replay.calls.get()
        d.place(Field.TO, TEL_AVIV)
        d.awaitSearch()
        assertTrue("first search: ${d.app.replay.calls.get() - before} requests", d.app.replay.calls.get() - before <= BetterStartPlanner.BUDGET)
        before = d.app.replay.calls.get()
        d.unfoldSearch()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(10f, 5f..30f, 4))).performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        compose.waitUntil(5_000) { d.vm.state.value.maxDriveMin == 20 }
        d.awaitSearch()
        val used = d.app.replay.calls.get() - before
        assertTrue("slider release: $used requests (one search is ≤ ${BetterStartPlanner.BUDGET})", used in 1..BetterStartPlanner.BUDGET)
        assertTrue("no options at 20 minutes", d.vm.state.value.betterStart?.options?.isNotEmpty() == true)
    }

    @Test fun j08_no_signal_shows_saved_results_then_retry_works() {
        AppDriver(compose).launch().use { d ->
            d.tripByTaps()
            d.app.replay.failing = true
            compose.onNode(hasContentDescription(d.str(R.string.refresh))).performClick()
            compose.waitUntil(8_000) { d.vm.state.value.offlineSince != null }
            assertTrue(d.vm.state.value.options.isNotEmpty())
        }
        AppDriver(compose).launch().use { d ->
            d.app.replay.failing = true
            d.place(Field.FROM, BGU)
            d.place(Field.TO, TEL_AVIV)
            d.awaitSearch()
            assertNotNull("no error without signal", d.vm.state.value.error)
            d.app.replay.failing = false
            compose.onNode(hasText(d.str(R.string.retry))).performClick()
            d.awaitSearch()
            assertTrue("retry did not bring results", d.vm.state.value.options.isNotEmpty())
        }
    }

    @Test fun j10_save_trip_then_one_tap_then_delete() = AppDriver(compose).launch().use { d ->
        d.tripByTaps()
        d.tapSaveTrip()
        val nameField = hasSetTextAction() and hasAnyAncestor(isDialog())
        d.awaitNode(nameField)
        val field = compose.onNode(nameField)
        field.performClick()
        field.performTextInput("עבודה")
        compose.onNode(hasText(d.str(R.string.save)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5_000) { d.vm.state.value.savedTrips.any { it.name == "עבודה" } }
        compose.onNode(hasContentDescription(d.str(R.string.close)) and hasAnyAncestor(hasTestTag(UiTags.BOTTOM))).performClick()
        compose.waitForIdle()
        // The one-tap chip: "↗" is its icon, the name its label (two texts in the tree). The
        // name dialog's keyboard may still be leaving and cover it: its click action, not a touch.
        val chip = compose.onNode(hasText("עבודה") and hasClickAction() and hasAnyAncestor(hasTestTag(UiTags.TOP)))
        runCatching { chip.performScrollTo() }
        chip.performSemanticsAction(SemanticsActions.OnClick)
        d.awaitSearch()
        assertTrue(d.vm.state.value.options.isNotEmpty())
        // J10: with results open, Settings can still be reached from the ⋮ menu (the panel once covered it).
        d.tapMenuItem(R.string.settings)
        compose.waitForIdle()
        assertTrue("J10: Settings can't be tapped while results are open", d.vm.state.value.showSettings)
        // The saved rows sit at the end of the settings list: scroll there first (a lazy list
        // only composes what is on screen). Trips come before places, so the first Delete is the trip's.
        d.awaitNode(hasScrollToNodeAction() and hasAnyAncestor(isDialog()))
        compose.onNode(hasScrollToNodeAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText("↗ עבודה"))
        compose.onAllNodes(hasContentDescription(d.str(R.string.delete)) and hasAnyAncestor(isDialog())).onFirst().performClick()
        compose.waitUntil(5_000) { d.vm.state.value.savedTrips.isEmpty() }
    }

    @Test fun j11_send_to_driver_shares_waze_and_google_links() = AppDriver(compose).launch().use { d ->
        d.onMain { it.setMode(AppMode.BETTER_START) }
        d.place(Field.FROM, MEITAR)
        d.place(Field.TO, TEL_AVIV)
        d.onMain { it.setMaxDrive(20) }
        d.awaitSearch()
        d.onMain { it.select(0) }
        Intents.init()
        try {
            Intents.intending(not(IntentMatchers.isInternal())).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
            compose.onNode(hasText(d.str(R.string.send_to_driver))).performClick()
            compose.waitForIdle()
            val chooser = Intents.getIntents().last { it.action == Intent.ACTION_CHOOSER }
            @Suppress("DEPRECATION")
            val text = (chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            assertTrue("share text: $text", "waze.com" in text && "google.com/maps" in text)
        } finally {
            Intents.release()
        }
    }

    /** Walks a fake GPS along the train ride; the "get off" alert must fire before the stop. */
    @Test fun j12_ride_alert_fires_near_the_stop() = AppDriver(compose).launch().use { d ->
        d.place(Field.FROM, BGU)
        d.place(Field.TO, TEL_AVIV)
        d.awaitSearch()
        val i = d.selectTrain()
        val leg = d.vm.state.value.options[i].legs.last { it.isTransit }
        val pkg = d.app.packageName
        d.shell("appops set $pkg android:mock_location allow")
        val lm = d.app.getSystemService(LocationManager::class.java)
        val gps = LocationManager.GPS_PROVIDER
        @Suppress("DEPRECATION")
        lm.addTestProvider(gps, false, false, false, false, true, true, true, android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_FINE)
        lm.setTestProviderEnabled(gps, true)
        try {
            d.tapInResults(d.str(R.string.ride_start))
            val started = SystemClock.uptimeMillis() + 8_000
            while (!RideService.active.value && SystemClock.uptimeMillis() < started) SystemClock.sleep(200)
            if (!RideService.active.value) {
                fun granted(p: String) = d.app.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val services = d.shell("dumpsys activity services $pkg").take(3_000)
                val log = d.shell("logcat -d -s RideService ActivityManager LocationManagerService").takeLast(4_000)
                throw AssertionError(
                    "J12: the ride did not start in 8 s (${d.inputState()}, fineLocation=${granted(android.Manifest.permission.ACCESS_FINE_LOCATION)}, " +
                        "notifications=${AppDriver.sdk < 33 || granted("android.permission.POST_NOTIFICATIONS")})\n" +
                        "--- dumpsys activity services ---\n$services\n--- logcat ---\n$log",
                )
            }
            val from = leg.from.latLon
            val to = leg.to.latLon
            val total = Geo.distanceM(from, to)
            fun at(f: Double) = LatLon(from.lat + (to.lat - from.lat) * f, from.lon + (to.lon - from.lon) * f)
            val title = d.str(R.string.ride_get_off_title)
            val nm = d.app.getSystemService(android.app.NotificationManager::class.java)
            fun alerted() = nm.activeNotifications.any { it.notification.extras.getCharSequence("android.title")?.toString() == title }
            // What the service made of each fix (stops left; "-" = no progress yet), for the failure message.
            val seenByService = mutableListOf<String>()
            // Mid-ride, then 300 m and 250 m out (inside the 400 m alert radius), then 20 m closer
            // every 5.5 s while waiting, down to 170 m (not yet at the stop), as a real GPS keeps reporting: the
            // system may drop one mock fix on a slow emulator (API 29 once lost the alert that way).
            // 20 m apart, as the service ignores moves under 15 m.
            val fixes = listOf(0.5) + listOf(300.0, 250.0, 230.0, 210.0, 190.0, 170.0).map { 1.0 - it / total }
            var seen = false
            for (f in fixes) {
                lm.setTestProviderLocation(gps, fix(gps, at(f)))
                val next = SystemClock.uptimeMillis() + 5_500 // the service asks for a fix every 5 s
                while (!seen && SystemClock.uptimeMillis() < next) { seen = alerted(); if (!seen) SystemClock.sleep(250) }
                seenByService += RideService.progress.value?.stopsLeft?.toString() ?: "-"
                if (seen) break
            }
            if (!seen) {
                val log = d.shell("logcat -d -s RideService LocationManagerService NotificationService").takeLast(3_000)
                throw AssertionError(
                    "no \"$title\" notification near ${leg.to.name} (ride active=${RideService.active.value}, " +
                        "stops left after each fix: $seenByService)\n--- logcat ---\n$log",
                )
            }
        } finally {
            d.onMain { it.stopRide() }
            runCatching { lm.removeTestProvider(gps) }
        }
    }

    @Test fun j13_update_banner_shows_and_dismisses() {
        val d = AppDriver(compose)
        d.app.latestRelease = LatestRelease("9.9.9", "https://example.invalid/release", null)
        d.launch().use {
            compose.waitUntil(5_000) { d.vm.state.value.update != null }
            compose.onNode(hasText("9.9.9", substring = true)).assertExists()
            compose.onNode(hasContentDescription(d.str(R.string.close)) and hasAnyAncestor(hasTestTag(UiTags.TOP))).performClick()
            compose.waitUntil(5_000) { d.vm.state.value.update == null }
            // Settings → Check for updates asks again (the fake checker) and brings the banner back.
            d.onMain { it.checkForUpdates() }
            compose.waitUntil(5_000) { d.vm.state.value.updateState is UpdateState.Available && d.vm.state.value.update != null }
        }
    }

    private fun fix(provider: String, p: LatLon) = Location(provider).apply {
        latitude = p.lat
        longitude = p.lon
        accuracy = 5f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }
}
