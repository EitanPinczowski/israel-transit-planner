package il.transit.planner.uitest

import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import il.transit.planner.MockNetwork
import il.transit.planner.UiTestApp.Companion.BGU
import il.transit.planner.UiTestApp.Companion.TEL_AVIV
import il.transit.planner.ui.Field
import il.transit.planner.ui.PlaceRef
import il.transit.planner.ui.Suggestion
import il.transit.planner.uitest.mock.Fault
import il.transit.planner.uitest.mock.MockServerRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 11 T9: search and everyday-use journeys, on the emulator against the local mock server (0 requests to the
 * internet), in the run's language (Hebrew and English runs, see [Run]). They go through the ViewModel's own entry
 * points, as the matrix does, and through Compose's semantics (text input, the IME action) for the field itself,
 * so none of them needs injected touch input (which the API 35 image does not deliver, see `ui-testing`).
 * [AppDriver] runs the accessibility check after every step and fails the journey on an ATF error.
 *
 * The server counts every endpoint: typing must not send a request per keystroke.
 */
@RunWith(AndroidJUnit4::class)
class SearchJourneysTest {
    @get:Rule val compose = createEmptyComposeRule()

    @get:Rule val permissions = Permissions.grantAll()

    @get:Rule val mock = MockServerRule()

    private lateinit var net: MockNetwork

    private fun journey(block: (AppDriver) -> Unit) {
        val d = AppDriver(compose)
        net = d.app.useMockServer(mock.url)
        d.launch().use { block(d) }
    }

    private val AppDriver.st get() = vm.state.value

    private fun il.transit.planner.ui.UiState.recentNames() = places.recents.map { it.name }

    private fun AppDriver.type(text: String, field: Field = Field.TO) = onMain {
        it.startEditing(field)
        it.onQuery(text)
    }

    /** A server answer shows (not only the saved places and recents the phone has), or the search says why not. */
    private fun AppDriver.awaitAnswer() = compose.waitUntil(WAIT_MS) {
        val s = st
        s.places.notFound || s.places.offline || s.suggestions.any { x -> !x.saved && !x.recent && !x.home }
    }

    private fun AppDriver.serverSuggestion(): Suggestion = st.suggestions.first { !it.saved && !it.recent && !it.home }

    // -- typing ---------------------------------------------------------------------------------------

    @Test fun typing_key_by_key_sends_one_request_per_pause() {
        journey { d ->
            mock.settle()
            val before = mock.count("geocode")
            // A text no other journey types (each journey has fresh guards anyway), every key in one burst on the main thread:
            // all of them land inside one debounce window, so this does not depend on the emulator's timing.
            val text = "רגר שדרות יצחק"
            d.onMain {
                it.startEditing(Field.TO)
                for (i in 1..text.length) it.onQuery(text.substring(0, i))
            }
            d.awaitAnswer()
            mock.settle(1_000)
            val sent = mock.count("geocode") - before
            Log.i(MockServerRule.TAG, "Typing ${text.length} keys in one burst -> $sent geocode request(s)")
            assertTrue("one pause sends 1..$GEOCODE_PER_PAUSE geocode request(s), not one per key ($sent): server=${mock.counts()}", sent in 1..GEOCODE_PER_PAUSE)
        }
    }

    @Test fun clear_empties_the_answers_and_back_closes_the_search() {
        journey { d ->
            d.type("רגר")
            d.awaitAnswer()
            assertTrue(d.st.suggestions.isNotEmpty())
            // The X clears the text: the server's answers go, the field stays open.
            d.onMain { it.onQuery("") }
            compose.waitUntil(WAIT_MS) { d.st.suggestions.none { x -> !x.saved && !x.recent && !x.home } }
            assertEquals(Field.TO, d.st.editing)
            assertEquals("", d.st.query)
            // Back closes the search.
            d.onMain { it.cancelEditing() }
            compose.waitForIdle()
            assertNull(d.st.editing)
            assertTrue(d.st.suggestions.isEmpty())
        }
    }

    // -- the field itself (Compose semantics, no injected touch) ----------------------------------------

    @Test fun the_search_field_takes_text_and_its_search_key_picks_the_first_answer() {
        journey { d ->
            d.onMain { it.startEditing(Field.TO) }
            d.awaitNode(hasSetTextAction(), 10_000)
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("רגר")
            d.awaitAnswer()
            val first = d.st.suggestions.first()
            compose.onAllNodes(hasSetTextAction()).onFirst().performImeAction()
            compose.waitUntil(WAIT_MS) { d.st.editing == null }
            val to = d.st.to
            assertNotNull("the search key picked nothing", to)
            Log.i(MockServerRule.TAG, "IME search key picked '${first.name}' -> $to")
        }
    }

    @Test fun the_caret_follows_the_language_direction() {
        journey { d ->
            d.onMain { it.startEditing(Field.TO) }
            d.awaitNode(hasSetTextAction(), 10_000)
            val field = compose.onAllNodes(hasSetTextAction()).onFirst()
            val typed = "רגר"
            field.performTextInput(typed)
            compose.waitForIdle()
            val node = field.fetchSemanticsNode()
            val want = if (Run.locale == "he") LayoutDirection.Rtl else LayoutDirection.Ltr
            if (Run.locale == "he" || Run.locale == "en") {
                assertEquals("the field's layout direction", want, node.layoutInfo.layoutDirection)
            }
            // The caret sits after what was typed (typing Hebrew in an RTL field moves it left, but its offset is the text length).
            val selection = node.config.getOrNull(SemanticsProperties.TextSelectionRange)
            assertNotNull(selection)
            assertEquals(typed.length, selection!!.end)
        }
    }

    // -- rotation (Phase 11 F8: T9's finding) ----------------------------------------------------------------

    /**
     * Rotating with the search open keeps the field open, the typed text and its answers, and sends no request (the
     * ViewModel keeps them; before F8 the field's focus loss on re-creation closed the search). Hebrew and English: the
     * faults job runs this class in both. Closing it afterwards still works.
     */
    @Test fun rotating_with_the_search_open_keeps_the_text_and_answers() {
        journey { d ->
            d.type("רגר")
            d.awaitAnswer()
            // The field has focus (as it has when typed into), so its loss on re-creation is what is tested.
            d.awaitNode(hasSetTextAction(), 10_000)
            compose.waitUntil(WAIT_MS) {
                compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.Focused) == true }
            }
            val shown = d.st.suggestions
            mock.settle(1_000)
            val sent = mock.count("geocode")
            try {
                for (back in listOf(false, true)) {
                    d.inst.runOnMainSync {
                        d.activity.requestedOrientation = if (back) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                    SystemClock.sleep(1_500)
                    d.refreshActivity()
                    compose.waitForIdle()
                    val turn = if (back) "back to portrait" else "to landscape"
                    assertEquals("the search is still open after turning $turn", Field.TO, d.st.editing)
                    assertEquals("the typed text after turning $turn", "רגר", d.st.query)
                    assertEquals("the answers after turning $turn", shown, d.st.suggestions)
                    d.awaitNode(hasSetTextAction(), 10_000)
                }
                mock.settle(1_000)
                assertEquals("rotation sent no request: server=${mock.counts()}", sent, mock.count("geocode"))
                // Back (or tapping away) still closes it.
                d.onMain { it.cancelEditing() }
                compose.waitForIdle()
                assertNull(d.st.editing)
            } finally {
                d.inst.runOnMainSync { d.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
                d.refreshActivity()
            }
        }
    }

    // -- the states ----------------------------------------------------------------------------------------

    @Test fun no_result_says_so_and_offers_the_map() {
        journey { d ->
            mock.bodies = mapOf("geocode" to "[]", "photon" to "{\"features\":[]}")
            d.type("בלבלבלבל")
            d.awaitAnswer()
            assertTrue("not found", d.st.places.notFound)
            assertFalse(d.st.places.offline)
            mock.bodies = emptyMap()
        }
    }

    @Test fun offline_says_offline_not_not_found_and_search_recovers() {
        journey { d ->
            mock.goOffline()
            try {
                d.type("רגר")
                d.awaitAnswer()
                assertTrue("offline", d.st.places.offline)
                assertFalse("offline is not 'not found'", d.st.places.notFound)
            } finally {
                mock.goOnline(net)
            }
            d.type("רגר ה")
            d.awaitAnswer()
            assertFalse(d.st.places.offline)
        }
    }

    @Test fun an_empty_search_offers_what_the_phone_has() {
        journey { d ->
            d.onMain { it.startEditing(Field.FROM) }
            compose.waitForIdle()
            val s = d.st
            assertTrue("saved places listed at once", s.suggestions.any { it.saved || it.home })
            mock.settle()
            assertEquals("an empty field sends nothing: ${mock.counts()}", 0, mock.count("geocode"))
        }
    }

    // -- everyday use ----------------------------------------------------------------------------------------

    @Test fun a_picked_place_comes_back_as_a_recent_and_a_saved_one_can_be_picked() {
        journey { d ->
            d.type("רגר")
            d.awaitAnswer()
            val picked = d.serverSuggestion()
            d.onMain { it.pick(picked) }
            // The recent is stored a moment after the pick (a coroutine), so wait for it.
            compose.waitUntil(WAIT_MS) { d.st.recentNames().contains(picked.name) }
            // Open the empty field; if the recent is not listed yet, open it again (the list is built when the field opens).
            val until = SystemClock.uptimeMillis() + 10_000
            do {
                d.onMain { it.startEditing(Field.FROM) }
                compose.waitForIdle()
                if (d.st.suggestions.any { it.recent && it.name == picked.name }) break
                SystemClock.sleep(500)
            } while (SystemClock.uptimeMillis() < until)
            val listed = d.st.suggestions.map { "${it.name}(saved=${it.saved},recent=${it.recent},home=${it.home})" }
            assertTrue("the pick '${picked.name}' is a recent; recents=${d.st.recentNames()} listed=$listed", d.st.suggestions.any { it.recent && it.name == picked.name })
            val saved = d.st.suggestions.first { it.saved || it.home }
            d.onMain { it.pick(saved) }
            compose.waitForIdle()
            assertNull(d.st.editing)
            assertNotNull(d.st.from)
        }
    }

    @Test fun swap_exchanges_from_and_to_and_plans_again() {
        journey { d ->
            d.place(Field.FROM, BGU)
            d.place(Field.TO, TEL_AVIV)
            d.awaitSearch()
            val from = d.st.from
            val to = d.st.to
            d.onMain { it.swap() }
            d.awaitSearch()
            assertEquals(to, d.st.from)
            assertEquals(from, d.st.to)
            assertTrue(d.st.options.isNotEmpty())
        }
    }

    @Test fun my_location_fills_the_start() {
        journey { d ->
            d.onMain {
                it.startEditing(Field.FROM)
                it.pickMyLocation()
            }
            compose.waitForIdle()
            assertEquals(PlaceRef.MyLocation, d.st.from)
            assertNull(d.st.editing)
        }
    }

    @Test fun a_failed_search_can_be_retried() {
        journey { d ->
            mock.script(setOf("plan"), Fault.TWICE_503)
            d.place(Field.FROM, BGU)
            d.place(Field.TO, TEL_AVIV)
            d.awaitSearch()
            assertNotNull("the 503s should end in an error", d.st.error)
            mock.script(emptyList(), null)
            d.onMain { it.plan() }
            d.awaitSearch()
            assertNull(d.st.error)
            assertTrue(d.st.options.isNotEmpty())
        }
    }

    // -- task completion --------------------------------------------------------------------------------------

    /**
     * From a cold start with two saved places: start field, pick it, destination field, pick it: [STEPS] ViewModel actions
     * (the search runs by itself once both are set). The steps are the test's own list, NOT counted taps on UI nodes: the
     * journeys avoid injected input, so what is asserted is that a result arrives after exactly these actions, and that
     * the first result shows within [FIRST_RESULT_MS] on the mock server (the time gate).
     */
    @Test fun plan_a_to_b_from_cold_start_in_four_steps_under_the_time_gate() {
        journey { d ->
            val t0 = SystemClock.uptimeMillis()
            d.onMain { it.startEditing(Field.FROM) }
            d.onMain { it.pick(Suggestion(BGU.name, null, BGU.latLon, saved = true, isStop = false)) }
            d.onMain { it.startEditing(Field.TO) }
            d.onMain { it.pick(Suggestion(TEL_AVIV.name, null, TEL_AVIV.latLon, saved = true, isStop = false)) }
            d.awaitSearch()
            val ms = SystemClock.uptimeMillis() - t0
            Log.i(MockServerRule.TAG, "Task completion: $STEPS steps, first result after $ms ms")
            assertTrue("no result after $STEPS steps", d.st.options.isNotEmpty())
            assertTrue("first result after $ms ms, gate $FIRST_RESULT_MS ms", ms <= FIRST_RESULT_MS)
        }
    }

    private companion object {
        const val WAIT_MS = 20_000L
        const val STEPS = 4
        const val GEOCODE_PER_PAUSE = 1
        const val FIRST_RESULT_MS = 8_000L
    }
}
