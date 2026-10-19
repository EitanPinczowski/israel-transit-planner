package il.transit.planner.uitest

import android.app.NotificationManager
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import il.transit.core.api.ReplayTransitApi
import il.transit.core.present.LegPalette
import il.transit.core.remind.Reminder
import il.transit.core.user.UserSettings
import il.transit.planner.R
import il.transit.planner.UiTestApp
import il.transit.planner.UiTestApp.Companion.BGU
import il.transit.planner.UiTestApp.Companion.TEL_AVIV
import il.transit.planner.remind.ReminderCheck
import il.transit.planner.remind.ReminderScheduler
import il.transit.planner.ui.AppLocale
import il.transit.planner.ui.Field
import il.transit.planner.ui.TripDetailsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 11 T7, the `system` job (API 34, `tools/ui_system_run.sh`): what the phone does to the app
 * and what the app must still do. Two kinds of test live here:
 *  - **arm_\* / mid-flow / airplane**: the test body is the whole step;
 *  - **arm_test_reminder, arm_trip_state**: only arm something and end; the shell then does what
 *    an instrumented test cannot survive (Doze, a reboot) and checks the phone's own state
 *    (`dumpsys alarm`, `dumpsys notification`).
 * Every step logs what it saw under tag [TAG]; it fails only on a broken promise (same results,
 * same option, no second request, no crash). Everything answers from the replay: 0 requests.
 */
@RunWith(AndroidJUnit4::class)
class SystemJourneysTest {
    @get:Rule val compose = createEmptyComposeRule()

    @get:Rule val permissions = Permissions.grantAll()

    private fun say(msg: String) = Log.i(TAG, msg)

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as UiTestApp

    // --- steps the shell finishes (Doze, reboot) ---------------------------------------------

    /** Arms E1's test reminder (the same exact alarm a trip reminder uses) and ends: the shell does the rest. */
    @Test fun arm_test_reminder() {
        runBlocking { app.reset() }
        app.getSystemService(NotificationManager::class.java).cancel(TEST_NOTIFICATION)
        val due = ReminderCheck.startTest(app)
        say("test-reminder due=${due.toEpochMilli()}")
    }

    /** A trip reminder (leave in 3 h) and a Rav-Kav pass-expiry date, both armed for real. */
    @Test fun arm_trip_state() {
        runBlocking { app.reset() }
        val now = System.currentTimeMillis() / 1000
        val r = Reminder(
            fromLat = BGU.lat,
            fromLon = BGU.lon,
            toLat = TEL_AVIV.lat,
            toLon = TEL_AVIV.lon,
            settings = UserSettings(),
            tripId = "system",
            line = "5",
            boardStop = "Rager",
            scheduledBoardingEpoch = now + 3 * 3600 + 300,
            leaveAtEpoch = now + 3 * 3600,
        )
        val endsOn = LocalDate.now(ZoneId.of("Asia/Jerusalem")).plusDays(30)
        runBlocking {
            app.store.setReminder(r)
            app.store.setSettings(UserSettings(tourSeen = true, passEndsOn = endsOn.toString()))
        }
        ReminderScheduler.schedule(app, r)
        ReminderScheduler.syncPass(app, endsOn)
        say("trip-state leaveAt=${r.leaveAt.toEpochMilli()} passEndsOn=$endsOn")
    }

    /** After the clock or zone moved: the stored reminder is the one that was armed (same absolute time). */
    @Test fun trip_state_unchanged() {
        val expect = InstrumentationRegistry.getArguments().getString("leaveAt")?.toLong()
        val r = runBlocking { app.store.reminder.first() }
        say("trip-state-now leaveAt=${r?.leaveAt?.toEpochMilli()} expect=$expect")
        assertTrue("the reminder is gone", r != null)
        if (expect != null) assertEquals("the reminder's absolute time moved", expect, r!!.leaveAt.toEpochMilli())
    }

    // --- airplane mode -----------------------------------------------------------------------

    /**
     * Airplane mode on (the real switch), then a refresh: the offline banner, the saved results
     * still there, no crash; off again: refresh works. The replay is not the network, so "no
     * signal" is `replay.failing`, exactly as J8 does it: the switch itself must not hurt the app.
     */
    @Test fun airplane_mode() = AppDriver(compose).launch().use { d ->
        try {
            d.place(Field.FROM, BGU)
            d.place(Field.TO, TEL_AVIV)
            d.awaitSearch()
            val shown = d.vm.state.value.options.size
            assertTrue("no results before the switch", shown > 0)
            say(d.shell("cmd connectivity airplane-mode enable").trim().ifEmpty { "airplane on" })
            SystemClock.sleep(3_000)
            d.app.replay.failing = true
            compose.onNode(hasContentDescription(d.str(R.string.refresh))).performClick()
            compose.waitUntil(10_000) { d.vm.state.value.offlineSince != null }
            assertEquals("saved results lost offline", shown, d.vm.state.value.options.size)
            say("airplane: offline banner up, $shown saved options")
            say(d.shell("cmd connectivity airplane-mode disable").trim().ifEmpty { "airplane off" })
            SystemClock.sleep(3_000)
            d.app.replay.failing = false
            compose.onNode(hasContentDescription(d.str(R.string.refresh))).performClick()
            compose.waitUntil(10_000) { d.vm.state.value.offlineSince == null && !d.vm.state.value.loading }
            assertTrue("refresh after airplane off brought nothing", d.vm.state.value.options.isNotEmpty())
        } finally {
            d.shell("cmd connectivity airplane-mode disable")
        }
    }

    // --- rotation / theme / language mid-flow ------------------------------------------------

    private enum class Switch { ROTATION, THEME, LANGUAGE }

    @Test fun mid_flow_rotation() {
        midFlow(Switch.ROTATION)
    }

    @Test fun mid_flow_theme() {
        midFlow(Switch.THEME)
    }

    @Test fun mid_flow_language() {
        midFlow(Switch.LANGUAGE)
    }

    private fun AppDriver.flip(sw: Switch, back: Boolean) {
        when (sw) {
            Switch.ROTATION -> inst.runOnMainSync {
                activity.requestedOrientation = if (back) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            Switch.THEME -> shell("cmd uimode night ${if (back) "no" else "yes"}")
            Switch.LANGUAGE -> inst.runOnMainSync { AppLocale.apply(activity, if (back) null else "en") }
        }
        SystemClock.sleep(1_500)
        refreshActivity()
        compose.waitForIdle()
    }

    /**
     * The switch [sw] while (1) a search is loading, (2) the trip sheet is open, (3) a leave
     * countdown is up. Promise each time: the same options and the same selected one afterwards,
     * and no request the plain flow did not already make.
     */
    private fun midFlow(sw: Switch): Unit = AppDriver(compose).launch().use { d ->
        try {
            // The plain search, once: its request count is what any later search may cost.
            val c0 = d.app.replay.calls.get()
            d.place(Field.FROM, BGU)
            d.place(Field.TO, TEL_AVIV)
            d.awaitSearch()
            val plain = d.app.replay.calls.get() - c0
            val ids = d.vm.state.value.options.map { it.start }
            assertTrue("no results to start from", ids.isNotEmpty())
            say("$sw: a plain search costs $plain requests, ${ids.size} options")

            // (1) loading: a search held for 4 s; the switch lands in the middle of it.
            d.onMain { it.clearResults() }
            d.app.planDelayMs = 4_000
            val c1 = d.app.replay.calls.get()
            d.onMain { it.plan() }
            SystemClock.sleep(1_000)
            assertTrue("the search was not loading", d.vm.state.value.loading)
            d.flip(sw, back = false)
            d.app.planDelayMs = 0
            d.awaitSearch(timeoutMs = 20_000)
            val loadingCost = d.app.replay.calls.get() - c1
            assertEquals("$sw while loading: other options", ids, d.vm.state.value.options.map { it.start })
            assertTrue("$sw while loading: $loadingCost requests, one search is $plain", loadingCost <= plain)
            say("$sw while loading: same ${ids.size} options, $loadingCost requests")

            // (2) the trip sheet open on the second option's first ride.
            d.onMain { it.select(if (ids.size > 1) 1 else 0) }
            compose.waitForIdle()
            val selected = d.vm.state.value.selected
            val itin = d.vm.state.value.selectedItinerary!!
            val leg = itin.legs.first { it.isTransit }
            val trip = ViewModelProvider(d.activity)[TripDetailsViewModel::class.java]
            d.inst.runOnMainSync { trip.open(leg, LegPalette.colorOf(itin.legs, leg)) }
            SystemClock.sleep(2_000)
            val c2 = d.app.replay.calls.get()
            d.flip(sw, back = true)
            SystemClock.sleep(2_000)
            assertEquals("$sw with the sheet open: other option", selected, d.vm.state.value.selected)
            assertEquals("$sw with the sheet open: other options", ids, d.vm.state.value.options.map { it.start })
            val sheetOpen = ViewModelProvider(d.activity)[TripDetailsViewModel::class.java].sheet.value != null
            val sheetCost = d.app.replay.calls.get() - c2
            say("$sw with the sheet open: option $selected kept, sheet open=$sheetOpen, $sheetCost requests")
            assertTrue("$sw with the sheet open: the sheet closed", sheetOpen)
            assertEquals("$sw with the sheet open: second request", 0, sheetCost)

            // (3) a leave countdown up (as J14 posts it).
            val moved = ReplayTransitApi.moved(d.vm.state.value.selectedItinerary!!, Instant.now())
            val reminder = Reminder.from(moved, moved.legs.first().from.latLon, moved.legs.last().to.latLon, UserSettings())!!
            val nm = d.app.getSystemService(NotificationManager::class.java)
            try {
                assertTrue(il.transit.planner.remind.Notifications.countdown(d.app, reminder, moved.start))
                val c3 = d.app.replay.calls.get()
                d.flip(sw, back = false)
                SystemClock.sleep(1_500)
                val n = nm.activeNotifications.count { it.id == COUNTDOWN_NOTIFICATION }
                assertEquals("$sw during a countdown: other option", selected, d.vm.state.value.selected)
                assertEquals("$sw during a countdown: countdown notifications", 1, n)
                assertEquals("$sw during a countdown: second request", 0, d.app.replay.calls.get() - c3)
                say("$sw during a countdown: option $selected kept, 1 notification, 0 requests")
            } finally {
                il.transit.planner.remind.Notifications.cancelCountdown(d.app)
            }
        } finally {
            d.app.planDelayMs = 0
            d.shell("cmd uimode night no")
            d.inst.runOnMainSync {
                d.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                AppLocale.apply(d.activity, null)
            }
        }
    }

    private companion object {
        const val TAG = "SystemJourney"

        /** `Notifications.ID_TEST`, `ID_LEAVE`. */
        const val TEST_NOTIFICATION = 1005
        const val COUNTDOWN_NOTIFICATION = 1001
    }
}
