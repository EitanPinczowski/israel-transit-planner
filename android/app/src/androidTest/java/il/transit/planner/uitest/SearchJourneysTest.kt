package il.transit.planner.uitest

import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
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

    private fun AppDriver.type(text: String, field: Field = Field.TO) = onMain {
        it.startEditing(field)
        it.onQuery(text)
    }

    /** A server answer shows (not only the saved places and recents the phone has), or the search says why not. */
    private fun AppDriver.awaitAnswer() = compose.waitUntil(WAIT_MS) {
        val s = vm.state.value
        s.places.notFound || s.places.offline || s.suggestions.any { x -> !x.saved && !x.recent && !x.home }
    }

    private fun AppDriver.serverSuggestion(): Suggestion = vm.state.value.suggestions.first { !it.saved && !it.recent && !it.home }

    // -- typing ---------------------------------------------------------------------------------------

    @Test fun typing_key_by_key_sends_one_request_per_pause() {
        journey { d ->
            mock.settle()
            val before = mock.count("geocode")
            val text = "שדרות רגר"
            for (i in 1..text.length) {
                d.onMain { if (i == 1) it.startEditing(Field.TO); it.onQuery(text.substring(0, i)) }
                SystemClock.sleep(40)
            }
            d.awaitAnswer()
            mock.settle(1_000)
            val sent = mock.count("geocode") - before
            Log.i(MockServerRule.TAG, "Typing ${text.length} keys 40 ms apart -> $sent geocode request(s)")
            assertEquals("one pause, one geocode request (not one per key): server=${mock.counts()}", 1, sent)
        }
    }

    @Test fun clear_empties_the_answers_and_back_closes_the_search() {
        journey { d ->
            d.type("רגר")
            d.awaitAnswer()
            assertTrue(d.vm.state.value.suggestions.isNotEmpty())
            // The X clears the text: the server's answers go, the field stays open.
            d.onMain { it.onQuery("") }
            compose.waitUntil(WAIT_MS) { d.vm.state.value.suggestions.none { x -> !x.saved && !x.recent && !x.home } }
            assertEquals(Field.TO, d.vm.state.value.editing)
            assertEquals("", d.vm.state.value.query)
            // Back closes the search.
            d.onMain { it.cancelEditing() }
            compose.waitForIdle()
            assertNull(d.vm.state.value.editing)
            assertTrue(d.vm.state.value.suggestions.isEmpty())
        }
    }

    @Test fun rotating_during_a_search_keeps_the_text_and_the_answers() {
        journey { d ->
            d.type("רגר")
            d.awaitAnswer()
            val before = d.vm.state.value
            val sent = mock.count("geocode")
            try {
                d.inst.runOnMainSync { d.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                SystemClock.sleep(1_500)
                d.refreshActivity()
                compose.waitForIdle()
                val after = d.vm.state.value
                assertEquals(before.editing, after.editing)
                assertEquals(before.query, after.query)
                assertEquals(before.suggestions, after.suggestions)
                mock.settle()
                assertEquals("rotation asked the server again: ${mock.counts()}", sent, mock.count("geocode"))
            } finally {
                d.inst.runOnMainSync { d.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
                SystemClock.sleep(500)
            }
        }
    }

    // -- the field itself (Compose semantics, no injected touch) ----------------------------------------

    @Test fun the_search_field_takes_text_and_its_search_key_picks_the_first_answer() {
        journey { d ->
            d.onMain { it.startEditing(Field.TO) }
            d.awaitNode(hasSetTextAction(), 10_000)
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("רגר")
            d.awaitAnswer()
            val first = d.vm.state.value.suggestions.first()
            compose.onAllNodes(hasSetTextAction()).onFirst().performImeAction()
            compose.waitUntil(WAIT_MS) { d.vm.state.value.editing == null }
            val to = d.vm.state.value.to
            assertNotNull("the search key picked nothing", to)
            Log.i(MockServerRule.TAG, "IME search key picked '${first.name}' -> $to")
        }
    }

    @Test fun the_caret_follows_the_language_direction() {
        journey { d ->
            d.onMain { it.startEditing(Field.TO) }
            d.awaitNode(hasSetTextAction(), 10_000)
            val field = compose.onAllNodes(hasSetTextAction()).onFirst()
            field.performTextInput("רגר")
            compose.waitForIdle()
            val node = field.fetchSemanticsNode()
            val want = if (Run.locale == "he") LayoutDirection.Rtl else LayoutDirection.Ltr
            if (Run.locale == "he" || Run.locale == "en") {
                assertEquals("the field's layout direction", want, node.layoutInfo.layoutDirection)
            }
            // The caret sits after what was typed (typing Hebrew in an RTL field moves it left, but its offset is the text length).
            val selection = node.config.getOrNull(SemanticsProperties.TextSelectionRange)
            assertNotNull(selection)
            assertEquals(3, selection!!.end)
        }
    }

    // -- the states ----------------------------------------------------------------------------------------

    @Test fun no_result_says_so_and_offers_the_map() {
        journey { d ->
            mock.bodies = mapOf("geocode" to "[]", "photon" to "{\"features\":[]}")
            d.type("בלבלבלבל")
            d.awaitAnswer()
            assertTrue("not found", d.vm.state.value.places.notFound)
            assertFalse(d.vm.state.value.places.offline)
            mock.bodies = emptyMap()
        }
    }

    @Test fun offline_says_offline_not_not_found_and_search_recovers() {
        journey { d ->
            mock.goOffline()
            try {
                d.type("רגר")
                d.awaitAnswer()
                assertTrue("offline", d.vm.state.value.places.offline)
                assertFalse("offline is not 'not found'", d.vm.state.value.places.notFound)
            } finally {
                mock.goOnline(net)
            }
            d.type("רגר ה")
            d.awaitAnswer()
            assertFalse(d.vm.state.value.places.offline)
        }
    }

    @Test fun an_empty_search_offers_what_the_phone_has() {
        journey { d ->
            d.onMain { it.startEditing(Field.FROM) }
            compose.waitForIdle()
            val s = d.vm.state.value
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
            compose.waitForIdle()
            d.onMain { it.startEditing(Field.FROM) }
            compose.waitForIdle()
            assertTrue("the pick is a recent", d.vm.state.value.suggestions.any { it.recent && it.name == picked.name })
            val saved = d.vm.state.value.suggestions.first { it.saved || it.home }
            d.onMain { it.pick(saved) }
            compose.waitForIdle()
            assertNull(d.vm.state.value.editing)
            assertNotNull(d.vm.state.value.from)
        }
    }

    @Test fun swap_exchanges_from_and_to_and_plans_again() {
        journey { d ->
            d.place(Field.FROM, BGU)
            d.place(Field.TO, TEL_AVIV)
            d.awaitSearch()
            val from = d.vm.state.value.from
            val to = d.vm.state.value.to
            d.onMain { it.swap() }
            d.awaitSearch()
            assertEquals(to, d.vm.state.value.from)
            assertEquals(from, d.vm.state.value.to)
            assertTrue(d.vm.state.value.options.isNotEmpty())
        }
    }

    @Test fun my_location_fills_the_start() {
        journey { d ->
            d.onMain {
                it.startEditing(Field.FROM)
                it.pickMyLocation()
            }
            compose.waitForIdle()
            assertEquals(PlaceRef.MyLocation, d.vm.state.value.from)
            assertNull(d.vm.state.value.editing)
        }
    }

    @Test fun a_failed_search_can_be_retried() {
        journey { d ->
            mock.script(setOf("plan"), Fault.TWICE_503)
            d.place(Field.FROM, BGU)
            d.place(Field.TO, TEL_AVIV)
            d.awaitSearch()
            assertNotNull("the 503s should end in an error", d.vm.state.value.error)
            mock.script(emptyList(), null)
            d.onMain { it.plan() }
            d.awaitSearch()
            assertNull(d.vm.state.value.error)
            assertTrue(d.vm.state.value.options.isNotEmpty())
        }
    }

    // -- task completion --------------------------------------------------------------------------------------

    /**
     * From a cold start with two saved places: tap the start, pick it, tap the destination, pick it: [TAPS] taps (the
     * search runs by itself once both are set), and the first result shows within [FIRST_RESULT_MS] on the mock server.
     */
    @Test fun plan_a_to_b_from_cold_start_in_four_taps() {
        journey { d ->
            var taps = 0
            val t0 = SystemClock.uptimeMillis()
            d.onMain { it.startEditing(Field.FROM) }; taps++
            d.onMain { it.pick(Suggestion(BGU.name, null, BGU.latLon, saved = true, isStop = false)) }; taps++
            d.onMain { it.startEditing(Field.TO) }; taps++
            d.onMain { it.pick(Suggestion(TEL_AVIV.name, null, TEL_AVIV.latLon, saved = true, isStop = false)) }; taps++
            d.awaitSearch()
            val ms = SystemClock.uptimeMillis() - t0
            Log.i(MockServerRule.TAG, "Task completion: $taps taps, first result after $ms ms")
            assertTrue("no result", d.vm.state.value.options.isNotEmpty())
            assertTrue("$taps taps, expected at most $TAPS", taps <= TAPS)
            assertTrue("first result after $ms ms, gate $FIRST_RESULT_MS ms", ms <= FIRST_RESULT_MS)
        }
    }

    private companion object {
        const val WAIT_MS = 20_000L
        const val TAPS = 4
        const val FIRST_RESULT_MS = 8_000L
    }
}
