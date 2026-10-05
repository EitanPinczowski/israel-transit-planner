package il.transit.planner.uitest

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import il.transit.core.api.Itinerary
import il.transit.core.history.TripRecord
import il.transit.core.update.LatestRelease
import il.transit.planner.R
import il.transit.planner.UiTestApp
import il.transit.planner.UiTestApp.Companion.BGU
import il.transit.planner.UiTestApp.Companion.MEITAR
import il.transit.planner.UiTestApp.Companion.REHOVOT
import il.transit.planner.UiTestApp.Companion.TEL_AVIV
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.Field
import il.transit.planner.ui.screens.modeTabLabel
import il.transit.planner.ui.screens.modeTabTag
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Drives the app into every screen state a user meets, takes a screenshot of each and
 * runs the [LayoutAudit] on it. The CI script runs this class once per phone profile
 * (screen size, density, font scale, language, theme), so each state is seen on every one.
 *
 * States are set up through the ViewModel where the path to them is not the point (the
 * journeys in JourneysTest cover the taps); the screen itself is the real one.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ScreensTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val permissions = Permissions.grantAll()

    private fun screen(name: String, places: List<il.transit.core.user.SavedPlace> = UiTestApp.PLACES, before: (UiTestApp) -> Unit = {}, setup: AppDriver.() -> Unit) {
        AppDriver(compose, places).use { d ->
            before(d.app)
            d.launch()
            d.setup()
            d.settle()
            val shot = d.screenshot(name)
            val found = LayoutAudit(d).run(name, shot).filter { it.rule in LayoutAudit.BLOCKING }
            // After the JSON and screenshot are written, so the report still shows them.
            assertTrue("blocking layout findings on $name: ${found.joinToString { "${it.rule} ${it.where}: ${it.detail}" }}", found.isEmpty())
        }
    }

    private fun AppDriver.trip() {
        place(Field.FROM, BGU)
        place(Field.TO, TEL_AVIV)
        awaitSearch()
        assertTrue("trip search found nothing", vm.state.value.options.isNotEmpty())
    }

    @Test fun s01_home_empty() = screen("01-home-empty", places = emptyList()) { modeTabsOnOneLine() }

    /** The owner's rule (2026-10-05): the five mode tabs on ONE line, all on screen, each a full
     *  tap target that says its name, on every profile (font 2.0 and 320 dp wide included). */
    private fun AppDriver.modeTabsOnOneLine() {
        compose.waitForIdle()
        val density = activity.resources.displayMetrics.density
        val width = activity.window.decorView.width
        val tabs = AppMode.entries.map { mode ->
            val node = compose.onNodeWithTag(modeTabTag(mode)).fetchSemanticsNode("mode tab $mode")
            val said = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            assertEquals("tab $mode says its name", str(modeTabLabel(mode)), said)
            mode to node.boundsInWindow
        }
        tabs.forEach { (mode, b) ->
            assertTrue("tab $mode on screen: $b in $width px", b.left >= 0f && b.right <= width)
            assertTrue("tab $mode tall enough: ${b.height / density} dp", b.height / density >= 47.5f)
            assertTrue("tab $mode wide enough: ${b.width / density} dp", b.width / density >= 40f)
            assertEquals("tab $mode on the same line as Trip", tabs[0].second.top, b.top, 1f)
        }
    }

    @Test fun s02_home_saved() = screen("02-home-saved") {
        runBlocking {
            app.store.setTrips(listOf(il.transit.core.user.SavedTrip("עבודה", BGU, TEL_AVIV)))
        }
        compose.waitForIdle()
    }

    @Test fun s03_search_with_keyboard() = screen("03-search-keyboard") {
        onMain { it.startEditing(Field.TO) }
        compose.waitForIdle()
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        field.performTextInput("תל")
        compose.waitUntil(5_000) { vm.state.value.suggestions.size > 1 }
    }

    @Test fun s04_trip_results() = screen("04-trip-results") { trip() }

    @Test fun s05_trip_second_option() = screen("05-trip-second-option") {
        trip()
        onMain { it.select(1) }
    }

    @Test fun s06_trip_network_error() = screen("06-trip-network-error") {
        app.replay.failing = true
        place(Field.FROM, BGU)
        place(Field.TO, TEL_AVIV)
        awaitSearch()
    }

    @Test fun s07_trip_offline_banner() = screen("07-trip-offline-banner") {
        trip()
        app.replay.failing = true
        onMain { it.plan() }
        awaitSearch()
        compose.waitUntil(5_000) { vm.state.value.offlineSince != null }
    }

    @Test fun s08_better_start() = screen("08-better-start") {
        onMain { it.setMode(AppMode.BETTER_START) }
        place(Field.FROM, MEITAR)
        place(Field.TO, TEL_AVIV)
        onMain { it.setMaxDrive(20) }
        awaitSearch()
    }

    @Test fun s09_better_start_none() = screen("09-better-start-none") {
        onMain { it.setMode(AppMode.BETTER_START) }
        place(Field.FROM, MEITAR)
        place(Field.TO, TEL_AVIV)
        awaitSearch()
    }

    /** The tallest search card: three place rows and a slider. */
    @Test fun s10_drop_off_fields() = screen("10-drop-off-fields") {
        onMain { it.setMode(AppMode.DROP_OFF) }
        place(Field.FROM, MEITAR)
        compose.waitForIdle()
    }

    @Test fun s11_drop_off_results() = screen("11-drop-off-results") {
        onMain { it.setMode(AppMode.DROP_OFF) }
        place(Field.FROM, MEITAR)
        place(Field.DRIVER_TO, TEL_AVIV)
        place(Field.TO, REHOVOT)
        awaitSearch()
    }

    @Test fun s12_pick_up() = screen("12-pick-up") {
        onMain { it.setMode(AppMode.PICK_UP) }
        place(Field.FROM, TEL_AVIV)
        place(Field.TO, MEITAR)
        onMain { it.setMaxPickUpDrive(30) }
        awaitSearch()
    }

    @Test fun s13_stop_departures() = screen("13-stop-departures") {
        onMain { it.openStop("il-Israel-MOT_37314", "באר שבע צפון") }
        compose.waitUntil(5_000) { vm.state.value.stopSheet?.loading == false }
    }

    @Test fun s14_settings_top() = screen("14-settings-top") {
        onMain { it.showSettings(true) }
    }

    @Test fun s15_settings_bottom() = screen("15-settings-bottom") {
        onMain { it.showSettings(true) }
        val scrollable = hasScrollAction() and hasAnyAncestor(isDialog())
        awaitNode(scrollable)
        val list = compose.onAllNodes(scrollable).onFirst()
        repeat(4) { list.performTouchInput { swipeUp() } }
    }

    @Test fun s16_history() = screen("16-history") {
        trip()
        val its: List<Itinerary> = vm.state.value.options
        runBlocking {
            its.take(3).forEachIndexed { i, it ->
                app.history.add(TripRecord.from(it, BGU.name, TEL_AVIV.name, "TRIP", UiTestApp.NOW.minusSeconds(86_400L * i), if (i == 0) 12 else null))
            }
        }
        onMain {
            it.clearResults()
            it.showHistory(true)
        }
    }

    /** Worst stacking: update banner + search card + results panel. */
    @Test fun s17_update_banner_with_results() = screen(
        "17-update-banner-results",
        before = { it.latestRelease = LatestRelease("9.9.9", "https://example.invalid/release", null) },
    ) {
        compose.waitUntil(5_000) { vm.state.value.update != null }
        trip()
    }

    @Test fun s18_save_trip_dialog() = screen("18-save-trip-dialog") {
        trip()
        tapSaveTrip()
        val field = compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
        field.performClick()
        field.performTextInput("עבודה")
    }
}
