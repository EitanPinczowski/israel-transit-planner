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
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasSetTextAction
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
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import il.transit.core.features.BetterStartPlanner
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import il.transit.core.update.LatestRelease
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
        compose.onNode(hasText(d.str(R.string.remind_me))).performClick()
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
                d.open()
                compose.waitForIdle()
                Espresso.pressBackUnconditionally()
                SystemClock.sleep(500)
                val alive = d.scenarioState?.isAtLeast(Lifecycle.State.RESUMED) == true
                Findings.expect("J4", alive && d.closed(), "Back with $what open " + if (alive) "did not close it" else "left the app")
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
        compose.onNode(hasText(d.str(R.string.mode_better_start)) and hasClickAction()).performClick()
        d.place(Field.FROM, MEITAR)
        var before = d.app.replay.calls.get()
        d.place(Field.TO, TEL_AVIV)
        d.awaitSearch()
        assertTrue("first search: ${d.app.replay.calls.get() - before} requests", d.app.replay.calls.get() - before <= BetterStartPlanner.BUDGET)
        before = d.app.replay.calls.get()
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
        compose.onNode(hasText(d.str(R.string.save_trip))).performClick()
        val field = compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
        field.performClick()
        field.performTextInput("עבודה")
        compose.onNode(hasText(d.str(R.string.save)) and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5_000) { d.vm.state.value.savedTrips.any { it.name == "עבודה" } }
        compose.onNode(hasContentDescription(d.str(R.string.close)) and hasAnyAncestor(hasTestTag(UiTags.BOTTOM))).performClick()
        compose.waitForIdle()
        compose.onNode(hasText("↗ עבודה") and hasClickAction()).performClick()
        d.awaitSearch()
        assertTrue(d.vm.state.value.options.isNotEmpty())
        compose.onNode(hasContentDescription(d.str(R.string.settings))).performClick()
        compose.onNode(hasContentDescription(d.str(R.string.delete)) and hasParent(hasAnyChild(hasText("↗ עבודה")))).performClick()
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
            compose.onNode(hasText(d.str(R.string.ride_start))).performClick()
            compose.waitUntil(8_000) { RideService.active.value }
            val from = leg.from.latLon
            val to = leg.to.latLon
            val total = Geo.distanceM(from, to)
            // Mid-ride, then 300 m out: inside the 400 m alert radius, not yet at the stop.
            for (f in listOf(0.5, 1.0 - 300.0 / total, 1.0 - 250.0 / total)) {
                val p = LatLon(from.lat + (to.lat - from.lat) * f, from.lon + (to.lon - from.lon) * f)
                lm.setTestProviderLocation(gps, fix(gps, p))
                SystemClock.sleep(5_500) // the service asks for a fix every 5 s
            }
            val title = d.str(R.string.ride_get_off_title)
            val nm = d.app.getSystemService(android.app.NotificationManager::class.java)
            val deadline = SystemClock.uptimeMillis() + 15_000
            var seen = false
            while (!seen && SystemClock.uptimeMillis() < deadline) {
                seen = nm.activeNotifications.any { it.notification.extras.getCharSequence("android.title")?.toString() == title }
                if (!seen) SystemClock.sleep(500)
            }
            assertTrue("no \"$title\" notification near ${leg.to.name}", seen)
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
