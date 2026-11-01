package il.transit.planner.uitest

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import il.transit.core.history.TripRecord
import il.transit.planner.R
import il.transit.planner.UiTestApp
import il.transit.planner.UiTestApp.Companion.BGU
import il.transit.planner.UiTestApp.Companion.MEITAR
import il.transit.planner.UiTestApp.Companion.REHOVOT
import il.transit.planner.UiTestApp.Companion.TEL_AVIV
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.Field
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 11 T8, the five biggest gaps of `docs/testing/inventory.md`: features with a unit test and a screenshot but no journey that
 * taps through them. Run on API 34 and 35 (`tools/ui_behaviour_run.sh`). Every answer is the replay's (0 requests to the internet);
 * `app.replay.calls` is the budget check. Like every journey, each is followed by LeakCanary (AppDriver.close).
 */
@RunWith(AndroidJUnit4::class)
class MoreJourneysTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val permissions = Permissions.grantAll()

    /** The share sheet is stubbed (nothing leaves the phone); returns the text the chooser carries. */
    private fun sharedText(action: () -> Unit): Intent {
        Intents.init()
        try {
            Intents.intending(not(IntentMatchers.isInternal())).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
            action()
            compose.waitForIdle()
            return Intents.getIntents().last { it.action == Intent.ACTION_CHOOSER }
        } finally {
            Intents.release()
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.inner(): Intent = getParcelableExtra(Intent.EXTRA_INTENT)!!

    /** Drop-off: Meitar by car to Tel Aviv, I get off on the way and continue to Rehovot; pick an option, "Send to driver". */
    @Test fun m01_drop_off_pick_and_send() = AppDriver(compose).launch().use { d ->
        d.onMain { it.setMode(AppMode.DROP_OFF); it.setMaxDrive(20); it.setMaxPickUpDrive(30) } // limits at which the replay offers options (as ProcessDeathProbe)
        d.place(Field.FROM, MEITAR)
        d.place(Field.DRIVER_TO, TEL_AVIV)
        val before = d.app.replay.calls.get()
        d.place(Field.TO, REHOVOT)
        d.awaitSearch()
        val asked = d.app.replay.calls.get() - before
        assertTrue("drop-off search: $asked requests (budget ${il.transit.core.features.DropOffPlanner.BUDGET})", asked in 1..il.transit.core.features.DropOffPlanner.BUDGET)
        val s = d.vm.state.value
        assertTrue("no drop-off options: error=${s.error}", s.hasResults)
        d.onMain { it.select(0) }
        d.awaitNode(hasText(d.str(R.string.send_to_driver)))
        val chooser = sharedText { compose.onNode(hasText(d.str(R.string.send_to_driver))).performClick() }
        val text = chooser.inner().getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertTrue("share text carries both navigation links: $text", "waze.com" in text && "google.com/maps" in text)
        assertEquals("sending is a share, not a search", asked, d.app.replay.calls.get() - before)
    }

    /** Park & ride: my car to a train station, on by train; pick an option; Start trip begins the ride. */
    @Test fun m02_park_and_ride_search_and_start() = AppDriver(compose).launch().use { d ->
        d.onMain { it.setMode(AppMode.PARK_RIDE); it.setMaxDrive(20); it.setMaxPickUpDrive(30) }
        d.place(Field.FROM, MEITAR)
        val before = d.app.replay.calls.get()
        d.place(Field.TO, TEL_AVIV)
        d.awaitSearch()
        val asked = d.app.replay.calls.get() - before
        assertTrue("park & ride search: $asked requests (budget ${il.transit.core.features.ParkRidePlanner.BUDGET})", asked in 1..il.transit.core.features.ParkRidePlanner.BUDGET)
        assertTrue("no park & ride result: error=${d.vm.state.value.error}", d.vm.state.value.hasResults)
        d.onMain { it.select(0) }
        d.awaitNode(hasText(d.str(R.string.action_start_trip)) and hasClickAction())
        compose.onNode(hasText(d.str(R.string.action_start_trip)) and hasClickAction()).performClick()
        compose.waitUntil(8_000) { d.vm.state.value.riding }
        assertTrue(d.vm.state.value.riding)
        d.onMain { it.stopRide() }
    }

    /** Stop sheet → a departure row → that line's day timetable (2 `stoptimes` pages) → tomorrow → back. */
    @Test fun m03_stop_sheet_opens_the_day_timetable() = AppDriver(compose).launch().use { d ->
        d.onMain { it.openStop(FaultStop.ID, "תחנה מרכזית") }
        compose.waitUntil(10_000) { d.vm.state.value.stopSheet?.loading == false }
        val sheet = d.vm.state.value.stopSheet
        assertNotNull(sheet)
        assertTrue("the stop sheet has departures", sheet!!.rows.isNotEmpty())
        val before = d.app.replay.calls.get()
        // The row's click action is labelled "Day timetable of line …" (StopSheet.kt).
        val opener = SemanticsMatcher("opens a day timetable") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label?.startsWith(d.str(R.string.timetable_open, "").trim()) == true
        }
        d.awaitNode(opener)
        compose.onAllNodes(opener)[0].performClick()
        compose.waitUntil(10_000) { d.vm.state.value.stopSheet?.timetable?.let { !it.loading } == true }
        val t = d.vm.state.value.stopSheet?.timetable
        assertNotNull("the timetable is open", t)
        val opened = d.app.replay.calls.get() - before
        assertTrue("opening a timetable: $opened requests (at most ${il.transit.core.present.DayTimetable.PAGES_PER_OPEN} pages)", opened in 0..il.transit.core.present.DayTimetable.PAGES_PER_OPEN)
        compose.onNode(hasText(d.str(R.string.timetable_tomorrow)) and hasClickAction()).performClick()
        compose.waitUntil(10_000) { d.vm.state.value.stopSheet?.timetable?.let { it.tomorrow && !it.loading } == true }
        assertTrue(d.vm.state.value.stopSheet?.timetable?.tomorrow == true)
    }

    /** An address shared in from another app (ACTION_SEND of a geo link): the planner's destination becomes that point, no crash. */
    @Test fun m04_shared_place_becomes_the_destination() = AppDriver(compose).launch(
        Intent(Intent.ACTION_SEND).setClassName(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
            il.transit.planner.MainActivity::class.java.name,
        ).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "geo:32.0839,34.7983?q=32.0839,34.7983(Savidor)"),
    ).use { d ->
        compose.waitUntil(15_000) {
            val s = d.vm.state.value
            s.mode == AppMode.TRIP && (s.to as? il.transit.planner.ui.PlaceRef.Point) != null
        }
        val to = d.vm.state.value.to as il.transit.planner.ui.PlaceRef.Point
        assertEquals(32.0839, to.at.lat, 0.001)
        assertEquals(34.7983, to.at.lon, 0.001)
    }

    /** History → Monthly report → a finished month → the share sheet with a CSV and the summary text. */
    @Test fun m05_monthly_report_exports_a_csv() = AppDriver(compose).launch().use { d ->
        d.onMain { it.setMode(AppMode.TRIP) }
        d.place(Field.FROM, BGU)
        d.place(Field.TO, TEL_AVIV)
        d.awaitSearch()
        val itin = d.vm.state.value.options.first()
        runBlocking {
            // September 2026 is finished at the uitest clock (5 Oct): three trips there.
            for (day in 8L..10L) {
                d.app.history.add(TripRecord.from(itin, BGU.name, TEL_AVIV.name, "TRIP", UiTestApp.NOW.minusSeconds(86_400L * (day + 10)), null))
            }
        }
        d.onMain { it.clearResults(); it.showHistory(true) }
        d.awaitNode(hasText(d.str(R.string.report_button)) and hasClickAction())
        compose.onNode(hasText(d.str(R.string.report_button)) and hasClickAction()).performClick()
        d.awaitNode(hasText(d.str(R.string.report_pick)))
        val chooser = sharedText {
            compose.onNode(hasClickAction() and hasText("2026", substring = true) and hasText("3", substring = true)).performClick()
        }
        val send = chooser.inner()
        assertEquals("text/csv", send.type)
        assertNotNull("the CSV rides as a stream", send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
        assertTrue("the summary text is there", send.getStringExtra(Intent.EXTRA_TEXT).orEmpty().isNotBlank())
    }

    /** `FaultMatrixTest.STOP_ID`, the one stop id the replay answers with a departure board. */
    private object FaultStop { const val ID = FaultMatrixTest.STOP_ID }
}
