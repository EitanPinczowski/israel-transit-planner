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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import il.transit.core.api.ReplayTransitApi
import il.transit.core.features.BetterStartPlanner
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import il.transit.core.present.DayPick
import il.transit.core.present.hhmm
import il.transit.core.update.LatestRelease
import il.transit.core.update.UpdateState
import il.transit.core.user.UserSettings
import il.transit.planner.R
import il.transit.planner.ui.PlaceRef
import il.transit.planner.ui.Shortcuts
import il.transit.planner.OwnIntents
import android.content.ComponentName
import android.content.pm.ShortcutManager
import il.transit.planner.remind.Notifications
import il.transit.planner.remind.MissedItAction
import il.transit.planner.MainActivity
import il.transit.core.plan.MissedIt
import android.os.ParcelFileDescriptor
import il.transit.planner.UiTestApp
import il.transit.planner.UiTestApp.Companion.BGU
import il.transit.planner.UiTestApp.Companion.MEITAR
import il.transit.planner.UiTestApp.Companion.TEL_AVIV
import il.transit.planner.ride.RideService
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.Field
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.UiTags
import il.transit.planner.ui.screens.DayTags
import kotlinx.coroutines.flow.first
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * What a user does, tap by tap, and what they must see. A broken journey fails the test;
 * a known UX gap is a [Findings] entry (report first) until it is fixed.
 * Every request goes to the replay, so call counts double as the Transitous budget check.
 */
@RunWith(AndroidJUnit4::class)
class JourneysTest {
    val compose = createEmptyComposeRule()

    @get:Rule val leaks = Leaks.around(compose)
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
        d.tapInResults(d.str(R.string.action_remind))
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
        // The saved rows sit at the end of the settings list: swipe there first (a lazy list
        // only composes what is on screen). One swipe at a time, the list found again and the UI
        // idle before each: the list is longer since "Save my current location", and a long
        // performScrollToNode raced its layout ("multithreaded access to SnapshotStateObserver",
        // API 33). Trips come before places, so the first Delete is the trip's.
        val list = hasScrollAction() and hasAnyAncestor(isDialog())
        val trip = hasText("↗ עבודה") and hasAnyAncestor(isDialog())
        repeat(8) {
            if (compose.onAllNodes(trip).fetchSemanticsNodes().isNotEmpty()) return@repeat
            d.awaitNode(list)
            compose.onAllNodes(list).onFirst().performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        d.awaitNode(trip)
        // Its click action, not a touch: the row may be only partly on screen after the last swipe.
        compose.onAllNodes(hasContentDescription(d.str(R.string.delete)) and hasAnyAncestor(isDialog())).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
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

    /**
     * E7 pick a day: Depart → Tomorrow → 08:00 (typed) before the places, then the trip by taps. Picking
     * sends nothing; the search is one plan, for tomorrow 08:00, every option is on that day, Earlier and
     * Later stay on it, and the chip says "Depart tomorrow 08:00".
     */
    @Test fun j16_pick_tomorrow_searches_that_day() = AppDriver(compose).launch().use { d ->
        val vm = d.vm
        val calls = d.app.replay.calls
        val clock = d.app.clock
        fun state() = vm.state.value
        val tomorrow = DayPick.days(clock.instant())[1].date
        val at = DayPick.at(tomorrow, LocalTime.of(8, 0))
        val before = calls.get()
        compose.onNode(hasText(d.str(R.string.depart_at)) and hasClickAction()).performClick()
        d.awaitNode(hasTestTag(DayTags.day(1)))
        compose.onNode(hasTestTag(DayTags.day(1))).performClick()
        d.awaitNode(hasTestTag(DayTags.CLOCK_TYPE))
        compose.onNode(hasTestTag(DayTags.CLOCK_TYPE)).performClick()
        val fields = hasSetTextAction() and hasAnyAncestor(hasTestTag(DayTags.CLOCK))
        compose.waitUntil(5_000) { compose.onAllNodes(fields).fetchSemanticsNodes().size == 2 }
        compose.onAllNodes(fields)[0].performTextReplacement("08")
        compose.onAllNodes(fields)[1].performTextReplacement("00")
        compose.onNode(hasTestTag(DayTags.CLOCK_OK)).performClick()
        compose.waitUntil(5_000) { state().time != null }
        assertEquals("the picked time", at, state().time)
        assertEquals("picking a day sent a request", before, calls.get())

        d.tripByTaps()
        val s = state()
        assertEquals(null, s.error)
        assertTrue("no options", s.options.isNotEmpty())
        fun days(): Set<LocalDate> = state().options.mapTo(HashSet()) { DayPick.today(it.start) }
        assertEquals("options on the searched day", setOf(tomorrow), days())
        assertTrue("an option leaves before 08:00", s.options.minOf { it.start } >= at)
        // One plan, plus the geocoding of what was typed (as j02).
        val sent = calls.get() - before
        assertTrue("requests: $sent", sent <= 3)

        // Earlier / Later through the ViewModel: the buttons sit at the end of a lazy list and are not
        // always composed on a 320 dp screen (API 35 run 39141187371); what matters here is the day they keep.
        for ((name, page) in listOf<Pair<String, (MainViewModel) -> Unit>>("Earlier" to { it.earlier() }, "Later" to { it.later() })) {
            val shown = state().results
            d.onMain(page)
            compose.waitUntil(5_000) { state().pages.loading == null && state().results !== shown }
            assertEquals("$name: options on the searched day", setOf(tomorrow), days())
            assertEquals(at, state().time)
        }

        d.unfoldSearch()
        d.awaitNode(hasText(d.str(R.string.depart_tomorrow, "08:00")))
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
            d.tapInResults(d.str(R.string.action_start_trip))
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
            // The alert is armed only on the ride's last 20 min (R2): ride at 5 min before the train is due.
            d.app.clockShift = java.time.Duration.between(UiTestApp.NOW, leg.end.minusSeconds(300))
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
            // Phase 10 D10: the ongoing ride notification is a Live Update on Android 16+, a plain bar below.
            liveUpdateCheck("J12", nm.activeNotifications.firstOrNull { it.notification.channelId == "ride_ongoing" })
            if (!seen) {
                val log = d.shell("logcat -d -s RideService LocationManagerService NotificationService").takeLast(3_000)
                throw AssertionError(
                    "no \"$title\" notification near ${leg.to.name} (ride active=${RideService.active.value}, " +
                        "stops left after each fix: $seenByService)\n--- logcat ---\n$log",
                )
            }
        } finally {
            d.onMain { it.stopRide() }
            d.app.clockShift = java.time.Duration.ZERO
            runCatching { lm.removeTestProvider(gps) }
        }
    }

    /**
     * Phase 11 E2: on a ride, the ongoing notification's "Missed it? Plan from here" opens the Trip tab
     * from the phone's fix to the ride's destination with options, for exactly 1 more request, and the
     * old ride stops.
     */
    @Test fun j16_missed_it_from_ride_notification() = AppDriver(compose).launch().use { d ->
        d.place(Field.FROM, BGU)
        d.place(Field.TO, TEL_AVIV)
        d.awaitSearch()
        val i = d.selectTrain()
        val options = d.vm.state.value.options
        val leg = options[i].legs.first { it.isTransit }
        val pkg = d.app.packageName
        d.shell("appops set $pkg android:mock_location allow")
        val lm = d.app.getSystemService(LocationManager::class.java)
        val gps = LocationManager.GPS_PROVIDER
        @Suppress("DEPRECATION")
        lm.addTestProvider(gps, false, false, false, false, true, true, true, android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_FINE)
        lm.setTestProviderEnabled(gps, true)
        try {
            d.tapInResults(d.str(R.string.action_start_trip))
            val started = SystemClock.uptimeMillis() + 8_000
            while (!RideService.active.value && SystemClock.uptimeMillis() < started) SystemClock.sleep(200)
            assertTrue("J16: the ride did not start in 8 s (${d.inputState()})", RideService.active.value)
            // On the train, halfway along its first ride: well away from the destination.
            val here = LatLon((leg.from.lat + leg.to.lat) / 2, (leg.from.lon + leg.to.lon) / 2)
            lm.setTestProviderLocation(gps, fix(gps, here))
            val nm = d.app.getSystemService(android.app.NotificationManager::class.java)
            val label = d.str(R.string.missed_it_action)
            fun action(): android.app.Notification.Action? {
                val ride = nm.activeNotifications.firstOrNull { it.notification.channelId == "ride_ongoing" } ?: return null
                val actions = ride.notification.actions ?: return null
                return actions.firstOrNull { "${it.title}" == label }
            }
            val until = SystemClock.uptimeMillis() + 5_000
            while (action() == null && SystemClock.uptimeMillis() < until) SystemClock.sleep(200)
            val tap = action() ?: throw AssertionError("J16: no \"$label\" on the ride notification: ${nm.activeNotifications.map { it.notification.channelId }}")
            val calls = d.app.replay.calls
            val before = calls.get()
            tap.actionIntent.send()
            compose.waitUntil(15_000) {
                val s = d.vm.state.value
                s.missedIt?.kind == il.transit.planner.ui.screens.MissedItUi.Kind.PLANNED && !s.loading && s.options.isNotEmpty()
            }
            assertEquals("one plan for the tap", 1, calls.get() - before)
            val s = d.vm.state.value
            assertEquals(il.transit.planner.ui.PlaceRef.MyLocation, s.from)
            assertEquals(il.transit.core.plan.TimeMode.NOW, s.timeMode)
            compose.waitUntil(5_000) { !RideService.active.value } // the old ride's alerts stop
            val at = hhmm(s.missedIt!!.at!!)
            d.awaitNode(hasText(d.str(R.string.missed_it_from, at)))
        } finally {
            d.onMain { it.stopRide() }
            runCatching { lm.removeTestProvider(gps) }
        }
    }

    /**
     * Phase 11 E2, the headline case: "Missed it?" tapped while the app is not running. A fresh
     * Activity (and ViewModel) starts from the action's intent alone, with no map fix yet: the
     * platform fix answers, the Trip tab shows options to the intent's destination for exactly 1 more
     * request, and the arrive-by flag in the intent reaches the banner. Broken intents plan nothing.
     */
    @Test fun j17_missed_it_from_a_killed_app() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val target = MissedIt.Target("Tel Aviv", LatLon(TEL_AVIV.lat, TEL_AVIV.lon), arriveBy = true)
        val good = MissedItAction.intent(ctx, target)
        assertEquals(target, MissedItAction.target(good))
        assertEquals(null, MissedItAction.target(Intent(good).putExtra("missed_it_lat", Double.NaN)))
        assertEquals(null, MissedItAction.target(Intent(good).apply { removeExtra("missed_it_lon") }))
        assertEquals(null, MissedItAction.target(Intent(good).putExtra("missed_it", false)))
        assertEquals(null, MissedItAction.target(Intent(ctx, MainActivity::class.java)))
        assertEquals(null, MissedItAction.target(null))
        val other = MissedItAction.intent(ctx, target.copy(at = LatLon(BGU.lat, BGU.lon)))
        assertTrue("each destination its own PendingIntent", !good.filterEquals(other))

        val pkg = ctx.packageName
        val lm = ctx.getSystemService(LocationManager::class.java)
        val gps = LocationManager.GPS_PROVIDER
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        // Read to the end: the command runs only as its output is drained (AppDriver.shell does the same).
        val appops = shell.executeShellCommand("appops set $pkg android:mock_location allow")
        ParcelFileDescriptor.AutoCloseInputStream(appops).use { it.readBytes() }
        @Suppress("DEPRECATION")
        lm.addTestProvider(gps, false, false, false, false, true, true, true, android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_FINE)
        lm.setTestProviderEnabled(gps, true)
        try {
            lm.setTestProviderLocation(gps, fix(gps, LatLon(BGU.lat, BGU.lon)))
            val calls = (ctx.applicationContext as il.transit.planner.UiTestApp).replay.calls
            val before = calls.get()
            AppDriver(compose).launch(good).use { d ->
                compose.waitUntil(15_000) {
                    val s = d.vm.state.value
                    s.missedIt?.kind == il.transit.planner.ui.screens.MissedItUi.Kind.PLANNED && !s.loading && s.options.isNotEmpty()
                }
                assertEquals("one plan for the tap", 1, calls.get() - before)
                val s = d.vm.state.value
                assertEquals(AppMode.TRIP, s.mode)
                assertEquals(il.transit.planner.ui.PlaceRef.MyLocation, s.from)
                assertEquals(PlaceRef.Point("Tel Aviv", target.at), s.to)
                assertTrue("arrive-by reached the banner", s.missedIt!!.wasArriveBy)
                d.awaitNode(hasText(d.str(R.string.missed_it_from, hhmm(s.missedIt!!.at!!))))
                d.awaitNode(hasText(d.str(R.string.missed_it_arrive_by)))
            }
        } finally {
            runCatching { lm.removeTestProvider(gps) }
        }
    }

    /**
     * Phase 11 F5: the launcher shortcuts the app really publishes point at the non-exported alias,
     * and each opens its screen. A launcher starts a shortcut as the app that published it
     * (`LauncherApps.startShortcut` → `startActivitiesAsPackage`; AOSP: "the target activity doesn't
     * have to be exported"), so the test, which runs as the app, starts the published intent the same way.
     */
    @Test fun j18_shortcuts_open_through_the_alias() = AppDriver(compose).launch().use { d ->
        val ctx = d.app
        val vm = d.vm

        fun s() = vm.state.value

        compose.waitUntil(10_000) { s().savedPlaces.isNotEmpty() }
        Shortcuts.update(ctx, s().savedPlaces, UiTestApp.NOW)
        val sm = ctx.getSystemService(ShortcutManager::class.java)
        val published = sm.dynamicShortcuts
        val alias = ComponentName(ctx, OwnIntents.ALIAS)
        assertTrue("shortcuts published: ${published.map { it.id }}", published.size >= 2)
        for (sc in published) assertEquals("${sc.id} opens through the alias", alias, sc.intent?.component)
        val lines = published.first { it.id == "lines" }
        ctx.startActivity(Intent(lines.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        compose.waitUntil(15_000) { s().showFavorites }
        d.onMain { it.showFavorites(false) }
        val place = published.first { it.id.startsWith("place-") }
        val name = place.intent?.getStringExtra(Shortcuts.EXTRA_PLACE)
        assertNotNull(name)
        ctx.startActivity(Intent(place.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        compose.waitUntil(15_000) { (s().to as? PlaceRef.Point)?.name == name }
    }

    /**
     * Phase 10 D10: the leave-now countdown posts on the reminder's id, as a Live Update on
     * Android 16+ and with a plain progress bar below; cancelling it leaves nothing behind.
     */
    @Test fun j14_leave_countdown_is_a_live_update() = AppDriver(compose).launch().use { d ->
        d.tripByTaps()
        val i = d.selectTrain()
        // The replay is dated at the uitest clock (2026-10-05); Android 16 drops a notification whose
        // `when` (here: boarding) is over 14 days older than the phone's clock, so move it to today.
        val chosen = d.vm.state.value.options[i]
        val itinerary = ReplayTransitApi.moved(chosen, Instant.now())
        val r = il.transit.core.remind.Reminder.from(
            itinerary,
            itinerary.legs.first().from.latLon,
            itinerary.legs.last().to.latLon,
            il.transit.core.user.UserSettings(),
        )!!
        val nm = d.app.getSystemService(android.app.NotificationManager::class.java)
        try {
            // Count down as of the trip's start.
            assertTrue(il.transit.planner.remind.Notifications.countdown(d.app, r, itinerary.start))
            val until = SystemClock.uptimeMillis() + 3_000
            fun posted() = nm.activeNotifications.filter { it.notification.channelId == "trip_reminders" }
            while (posted().isEmpty() && SystemClock.uptimeMillis() < until) SystemClock.sleep(100)
            val all = posted()
            assertEquals("one countdown notification: ${all.map { it.id }}", 1, all.size)
            assertEquals(1001, all.single().id) // ID_LEAVE, on every Android version
            liveUpdateCheck("J14", all.single())
            // Phase 11 E2: the countdown and "your trip changed" both offer "Missed it? Plan from here".
            val label = d.str(R.string.missed_it_action)
            fun hasMissedIt(id: Int): Boolean {
                val n = nm.activeNotifications.firstOrNull { it.id == id } ?: return false
                val actions = n.notification.actions ?: return false
                return actions.any { "${it.title}" == label }
            }
            assertTrue("J14: no \"$label\" on the countdown", hasMissedIt(1001))
            Notifications.changed(d.app, r)
            val changedBy = SystemClock.uptimeMillis() + 3_000
            // By its title: another notice on the channel (a test reminder) must not stand in for it.
            val changedTitle = d.str(R.string.notify_changed_title)
            fun titleOf(sbn: android.service.notification.StatusBarNotification): String {
                val extras = sbn.notification.extras
                return "${extras.getCharSequence(android.app.Notification.EXTRA_TITLE)}"
            }
            val changedId = { nm.activeNotifications.firstOrNull { titleOf(it) == changedTitle }?.id }
            while (changedId() == null && SystemClock.uptimeMillis() < changedBy) SystemClock.sleep(100)
            val id = changedId() ?: throw AssertionError("J14: \"trip changed\" was not posted")
            assertTrue("J14: no \"$label\" on \"trip changed\"", hasMissedIt(id))
            nm.cancel(id)
        } finally {
            il.transit.planner.remind.Notifications.cancelCountdown(d.app)
        }
    }

    /** 16+: ProgressStyle, promotion asked and possible; below: the same notification with a bar. */
    private fun liveUpdateCheck(journey: String, sbn: android.service.notification.StatusBarNotification?) {
        assertTrue("$journey: no ongoing notification", sbn != null)
        val n = sbn!!.notification
        val template = n.extras.getString(android.app.Notification.EXTRA_TEMPLATE)
        if (AppDriver.sdk >= 36) {
            assertEquals("$journey: template", "android.app.Notification\$ProgressStyle", template)
            assertTrue("$journey: promotion not requested", n.extras.getBoolean("android.requestPromotedOngoing")) // EXTRA_REQUEST_PROMOTED_ONGOING: not in the 36 SDK stubs
            // Live Updates ship in Android 16 QPR1 (36.1); on 36.0 the platform's flag keeps
            // hasPromotableCharacteristics() false for every app, so it is only checked from 36.1.
            val sdkFull = runCatching { android.os.Build.VERSION::class.java.getField("SDK_INT_FULL").getInt(null) }.getOrDefault(0)
            if (sdkFull >= 3_600_001) assertTrue("$journey: not promotable", n.hasPromotableCharacteristics())
        } else {
            assertTrue("$journey: no progress bar", n.extras.getInt(android.app.Notification.EXTRA_PROGRESS_MAX) > 0)
        }
    }

    /**
     * Phase 11 E1: Settings → Check reminders → Test reminder in 1 minute → the real exact alarm
     * fires and the receiver posts "Test: your reminders work" on the reminders' channel within 90 s;
     * the check then says it rang. A reminder is active the whole time (leaving in 3 h, no alarms of
     * its own armed): the test alarm must leave it exactly as it was and send no request.
     */
    @Test fun j15_test_reminder_rings() = AppDriver(compose).launch().use { d ->
        val nm = d.app.getSystemService(android.app.NotificationManager::class.java)
        nm.cancel(TEST_NOTIFICATION)
        val now = System.currentTimeMillis() / 1000
        val active = il.transit.core.remind.Reminder(
            fromLat = BGU.lat,
            fromLon = BGU.lon,
            toLat = TEL_AVIV.lat,
            toLon = TEL_AVIV.lon,
            settings = UserSettings(),
            tripId = "j15",
            line = "5",
            boardStop = "Rager",
            scheduledBoardingEpoch = now + 3 * 3600 + 300,
            leaveAtEpoch = now + 3 * 3600,
        )
        val store = d.app.store
        kotlinx.coroutines.runBlocking { store.setReminder(active) }
        try {
            d.tapMenuItem(R.string.settings)
            compose.waitUntil(5_000) { d.vm.state.value.showSettings }
            val list = hasScrollAction() and hasAnyAncestor(isDialog())
            val row = hasTestTag("check_reminders")
            // The row is in the middle of the list: slow, short drags (a fling would pass it by).
            repeat(30) {
                if (compose.onAllNodes(row).fetchSemanticsNodes().isNotEmpty()) return@repeat
                d.awaitNode(list)
                compose.onAllNodes(list).onFirst().performTouchInput {
                    val step = height / 6f
                    swipe(Offset(centerX, centerY + step), Offset(centerX, centerY - step), 600)
                }
                compose.waitForIdle()
            }
            d.awaitNode(row)
            compose.onNode(row).performSemanticsAction(SemanticsActions.OnClick)
            d.awaitNode(hasTestTag("check_test"))
            val calls = d.app.replay.calls
            compose.onNode(hasTestTag("check_test")).performSemanticsAction(SemanticsActions.OnClick)
            val before = calls.get() // from the tap to the delivery: the alarm and the receiver only
            val until = SystemClock.uptimeMillis() + 90_000
            fun posted() = nm.activeNotifications.filter { it.id == TEST_NOTIFICATION }
            while (posted().isEmpty() && SystemClock.uptimeMillis() < until) SystemClock.sleep(500)
            val sent = calls.get() - before
            val n = posted()
            assertEquals("J15: the test reminder was not posted within 90 s", 1, n.size)
            val notification = n.single().notification
            assertEquals("trip_reminders", notification.channelId)
            assertEquals(d.str(R.string.check_test_title), notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString())
            assertEquals("J15: the test alarm sent requests", 0, sent)
            val after = kotlinx.coroutines.runBlocking { store.reminder.first() }
            assertEquals("J15: the test alarm changed the active reminder", active, after)
            d.awaitNode(hasTestTag("check_test_rang"), 10_000) // the check re-reads every 3 s
        } finally {
            nm.cancel(TEST_NOTIFICATION)
            kotlinx.coroutines.runBlocking { store.setReminder(null) }
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

    private companion object {
        /** `Notifications.ID_TEST`. */
        const val TEST_NOTIFICATION = 1005
    }
}
