package il.transit.planner.uitest

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import il.transit.core.api.Leg
import il.transit.core.features.BetterStartPlanner
import il.transit.core.features.DropOffPlanner
import il.transit.core.features.ParkRidePlanner
import il.transit.core.features.PickUpPlanner
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import il.transit.core.plan.ChainPlanner
import il.transit.core.plan.MissedIt
import il.transit.core.present.DayTimetable
import il.transit.core.present.DepartureRow
import il.transit.core.search.PlaceSearch
import il.transit.core.user.SavedPlace
import il.transit.planner.MockNetwork
import il.transit.planner.R
import il.transit.planner.UiTestApp.Companion.BGU
import il.transit.planner.UiTestApp.Companion.MEITAR
import il.transit.planner.UiTestApp.Companion.REHOVOT
import il.transit.planner.UiTestApp.Companion.TEL_AVIV
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.Field
import il.transit.planner.ui.Suggestion
import il.transit.planner.ui.TimetableAction
import il.transit.planner.ui.TripDetailsViewModel
import il.transit.planner.ui.UiError
import il.transit.planner.ui.screens.MissedItUi
import il.transit.planner.uitest.mock.Fault
import il.transit.planner.uitest.mock.MockServerRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

/**
 * Phase 11 T4: the network-fault matrix. Every screen that calls the network, against a local
 * `MockWebServer` (the emulator's loopback, [MockServerRule]) through the app's real HTTP clients
 * and guards ([MockNetwork]), under each [Fault]: 429 twice, 503 twice, a server that never answers,
 * an HTML page, an empty body, `{}`, a body cut halfway, offline (server down + airplane mode), and
 * 429 then the answer. 0 requests to the internet.
 *
 * Each case asserts what the user sees: the screen finishes (no endless spinner: [WAIT_MS]), says
 * the right thing (its message, or the offline copy where one exists; silent screens show nothing
 * new), Retry works, and the app does not crash (the run script fails on any crash). Each case also
 * asserts the server's own count for the screen's endpoints: at most the feature's budget constant
 * (the ones `BudgetInventoryTest` pins), doubled where the guard may retry once (429/503). After the
 * matrix, the same action again sends 0 (the caches).
 *
 * Adding a screen: a [Screen] with its endpoints, its budget constant, how to act and what the
 * user then sees ([Outcome]); see the `ui-testing` skill.
 */
@RunWith(AndroidJUnit4::class)
class FaultMatrixTest {
    @get:Rule val compose = createEmptyComposeRule()

    @get:Rule val permissions = Permissions.grantAll()

    @get:Rule val mock = MockServerRule()

    enum class Outcome { OK, FAILED, OFFLINE_COPY, EMPTY }

    /** One run of the matrix on one screen. [seq] counts actions, so inputs can differ per action. */
    class Ctx(val d: AppDriver, val net: MockNetwork) {
        var seq = 0
        var actAt = 0L
        var leg: Leg? = null
        var row: DepartureRow? = null
        val vm get() = d.vm
        val s get() = d.vm.state.value
        val elapsed get() = SystemClock.uptimeMillis() - actAt
    }

    class Screen(
        val name: String,
        /** The mock server's endpoints this screen calls, and whose faults it is tested under. */
        val endpoints: Set<String>,
        /** Requests one action may send without retries (the feature's budget constant). */
        val budget: Int,
        val act: (Ctx) -> Unit,
        /** What the user sees once the screen is done; null while it still loads. */
        val outcome: (Ctx) -> Outcome?,
        /** The text a FAILED screen shows; null: the screen fails silently (decoration). */
        val message: ((Ctx) -> String)? = null,
        /** The user's Retry; null: none on this screen (the next action asks again). */
        val retry: ((Ctx) -> Unit)? = null,
        val prepare: (Ctx) -> Unit = {},
        val reset: (Ctx) -> Unit = {},
        /** The same action as the last one (the cache check); null: no identical action exists. */
        val repeat: ((Ctx) -> Unit)? = null,
        /** Normal answers replaced while this screen runs (endpoint → body). */
        val bodies: Map<String, String> = emptyMap(),
        /** A failure may leave what the screen showed before (map stops keep their layer). */
        val keepsOld: Boolean = false,
    )

    private fun run(c: Ctx, s: Screen, label: String, action: (Ctx) -> Unit): Outcome {
        c.actAt = SystemClock.uptimeMillis()
        action(c)
        var out: Outcome? = null
        try {
            compose.waitUntil(WAIT_MS) { out = s.outcome(c); out != null }
        } catch (e: Throwable) {
            throw AssertionError("${s.name} / $label: still loading after ${WAIT_MS / 1000} s (endless spinner?) server=${mock.counts()}", e)
        }
        compose.waitForIdle()
        return requireNotNull(out)
    }

    private fun matrix(s: Screen) {
        val d = AppDriver(compose)
        val net = d.app.useMockServer(mock.url)
        mock.bodies = s.bodies
        d.launch().use {
            val c = Ctx(d, net)
            s.prepare(c)
            for (f in Fault.values()) {
                net.clearCaches()
                if (f == Fault.OFFLINE) mock.goOffline() else mock.script(s.endpoints, f)
                val before = mock.count(s.endpoints)
                val out = run(c, s, "$f") { c.seq++; s.act(c) }
                val sent = mock.count(s.endpoints) - before
                val cap = s.budget * (if (f.retried) 2 else 1)
                Log.i(MockServerRule.TAG, "${s.name} $f -> $out sent=$sent cap=$cap server=${mock.counts()}")
                assertTrue("${s.name} / $f: sent $sent requests, its cap is $cap (budget ${s.budget})", sent <= cap)
                if (f == Fault.RETRY_THEN_OK) {
                    assertEquals("${s.name} / $f: the guard's retry did not get the answer", Outcome.OK, out)
                    assertTrue("${s.name} / $f: no refused request then answer (sent $sent)", sent >= 2)
                } else {
                    val allowed = buildSet {
                        add(Outcome.FAILED)
                        add(Outcome.OFFLINE_COPY)
                        if (f == Fault.EMPTY_OBJECT) add(Outcome.EMPTY)
                        if (s.keepsOld || s.message == null && f == Fault.EMPTY_OBJECT) add(Outcome.OK)
                        if (s.keepsOld) add(Outcome.EMPTY)
                    }
                    assertTrue("${s.name} / $f: the user sees $out", out in allowed)
                    if (out == Outcome.FAILED) s.message?.let { m -> d.awaitNode(hasText(m(c), substring = true)) }
                    if (f == Fault.OFFLINE) mock.goOnline(net)
                    mock.script(emptyList(), null)
                    net.clearCaches()
                    s.retry?.let { r ->
                        val again = run(c, s, "$f, then Retry", r)
                        assertEquals("${s.name} / $f: Retry did not bring the answer", Outcome.OK, again)
                    }
                }
                s.reset(c)
            }
            // A second identical action within the cache time sends nothing.
            mock.script(emptyList(), null)
            s.repeat?.let { r ->
                val before = mock.count(s.endpoints)
                run(c, s, "repeat", r)
                assertEquals("${s.name}: the same action again within the cache time", 0, mock.count(s.endpoints) - before)
            }
        }
    }

    // --- shared pieces -------------------------------------------------------------------------

    private fun Ctx.place(field: Field, p: SavedPlace) = d.place(field, p)

    /** Sets the places once (the last one starts the search); afterwards the same search again. */
    private fun planAct(vararg places: Pair<Field, SavedPlace>): (Ctx) -> Unit = { c ->
        if (c.s.to == null) places.forEach { (f, p) -> c.place(f, p) } else c.d.onMain { it.plan() }
    }

    private val planOutcome: (Ctx) -> Outcome? = { c ->
        val s = c.s
        when {
            s.loading -> null
            s.offlineSince != null -> Outcome.OFFLINE_COPY
            s.error == UiError.NETWORK -> Outcome.FAILED
            s.error == UiError.NO_RESULTS -> Outcome.EMPTY
            s.error != null -> Outcome.FAILED
            s.hasResults -> Outcome.OK
            else -> null
        }
    }

    private val networkMessage: (Ctx) -> String = { c -> c.d.str(R.string.err_network) }

    /** The error row's Retry when it shows; the offline copy has none (the refresh asks again). */
    private val retryButton: (Ctx) -> Unit = { c ->
        val button = compose.onAllNodes(hasText(c.d.str(R.string.retry)))
        if (button.fetchSemanticsNodes().isNotEmpty()) button.onFirst().performClick() else c.d.onMain { it.plan() }
    }

    private fun planScreen(name: String, mode: AppMode, budget: Int, endpoints: Set<String>, vararg places: Pair<Field, SavedPlace>) = Screen(
        name = name,
        endpoints = endpoints,
        budget = budget,
        prepare = { c -> if (mode != AppMode.TRIP) c.d.onMain { it.setMode(mode) } },
        act = planAct(*places),
        outcome = planOutcome,
        message = networkMessage,
        retry = retryButton,
        repeat = { c -> c.d.onMain { it.plan() } },
    )

    /** A Trip search with normal answers, for the screens that start from its results. */
    private fun Ctx.tripResults() {
        place(Field.FROM, BGU)
        place(Field.TO, TEL_AVIV)
        d.awaitSearch()
        assertTrue("no Trip results to start from", s.options.isNotEmpty())
    }

    // --- the matrix: one test per networked screen ---------------------------------------------

    @Test fun trip_search() = matrix(planScreen("Trip search", AppMode.TRIP, 1, setOf("plan"), Field.FROM to BGU, Field.TO to TEL_AVIV))

    @Test fun earlier_later() = matrix(
        Screen(
            name = "Earlier / Later",
            endpoints = setOf("plan"),
            budget = 1,
            prepare = { c -> c.tripResults() },
            act = { c -> c.d.onMain { it.later() } },
            outcome = { c -> c.s.pages.let { p -> if (p.loading != null) null else if (p.failed) Outcome.FAILED else Outcome.OK } },
            message = { c -> c.d.str(R.string.page_failed) },
            retry = { c -> c.d.onMain { it.later() } },
        ),
    )

    @Test fun better_start() = matrix(
        planScreen("Better start", AppMode.BETTER_START, BetterStartPlanner.BUDGET, setOf("plan"), Field.FROM to MEITAR, Field.TO to TEL_AVIV),
    )

    @Test fun drop_off() = matrix(
        planScreen(
            "Drop-off", AppMode.DROP_OFF, DropOffPlanner.BUDGET, setOf("plan", "one-to-many"),
            Field.FROM to MEITAR, Field.DRIVER_TO to TEL_AVIV, Field.TO to REHOVOT,
        ),
    )

    @Test fun pick_up() = matrix(
        planScreen("Pick-up", AppMode.PICK_UP, PickUpPlanner.BUDGET, setOf("plan"), Field.FROM to TEL_AVIV, Field.TO to MEITAR),
    )

    @Test fun park_ride() = matrix(
        planScreen("Park & ride", AppMode.PARK_RIDE, ParkRidePlanner.BUDGET, setOf("plan", "one-to-many"), Field.FROM to MEITAR, Field.TO to TEL_AVIV),
    )

    @Test fun errands() = matrix(
        Screen(
            name = "Errands",
            endpoints = setOf("plan"),
            budget = ChainPlanner.BUDGET,
            prepare = { c -> c.tripResults() },
            act = { c ->
                if (c.s.chainStops.isEmpty()) {
                    c.place(Field.STOP, REHOVOT)
                } else {
                    c.d.onMain { it.plan() }
                }
            },
            outcome = { c ->
                val s = c.s
                when {
                    s.loading -> null
                    s.error == UiError.NO_RESULTS -> Outcome.EMPTY
                    s.error != null -> Outcome.FAILED
                    s.chain != null -> Outcome.OK
                    else -> null
                }
            },
            message = networkMessage,
            retry = retryButton,
            repeat = { c -> c.d.onMain { it.plan() } },
        ),
    )

    @Test fun stop_departures() = matrix(
        Screen(
            name = "Stop sheet departures",
            endpoints = setOf("stoptimes"),
            budget = 1,
            act = { c -> c.d.onMain { it.openStop(STOP_ID, "תחנה מרכזית") } },
            outcome = { c ->
                val sheet = c.s.stopSheet
                when {
                    sheet == null || sheet.loading -> null
                    sheet.failed -> Outcome.FAILED
                    sheet.saved != null -> Outcome.OFFLINE_COPY
                    sheet.rows.isEmpty() -> Outcome.EMPTY
                    else -> Outcome.OK
                }
            },
            message = networkMessage,
            retry = { c -> c.d.onMain { it.openStop(STOP_ID, "תחנה מרכזית") } },
            reset = { c -> c.d.onMain { it.closeStop() } },
            repeat = { c -> c.d.onMain { it.openStop(STOP_ID, "תחנה מרכזית") } },
        ),
    )

    @Test fun day_timetable() = matrix(
        Screen(
            name = "Day timetable",
            endpoints = setOf("stoptimes"),
            budget = DayTimetable.PAGES_PER_OPEN,
            prepare = { c ->
                c.d.onMain { it.openStop(STOP_ID, "תחנה מרכזית") }
                compose.waitUntil(WAIT_MS) { c.s.stopSheet?.loading == false }
                c.row = c.s.stopSheet?.rows?.firstOrNull()
                assertTrue("no departures to open a timetable from", c.row != null)
            },
            act = { c -> c.d.onMain { it.timetable(TimetableAction.Open(c.row!!)) } },
            outcome = { c ->
                val t = c.s.stopSheet?.timetable
                when {
                    t == null || t.loading -> null
                    t.failed -> Outcome.FAILED
                    t.savedAt != null -> Outcome.OFFLINE_COPY
                    t.rows.isEmpty() -> Outcome.EMPTY
                    else -> Outcome.OK
                }
            },
            message = networkMessage,
            retry = { c -> c.d.onMain { it.timetable(TimetableAction.Retry) } },
            reset = { c -> c.d.onMain { it.timetable(TimetableAction.Close) } },
            repeat = { c -> c.d.onMain { it.timetable(TimetableAction.Open(c.row!!)) } },
        ),
    )

    @Test fun trip_sheet() {
        fun Ctx.trips() = ViewModelProvider(d.activity, TripDetailsViewModel.factory(d.app))[TripDetailsViewModel::class.java]
        val open: (Ctx) -> Unit = { c -> c.d.inst.runOnMainSync { c.trips().open(c.leg!!) } }
        matrix(
            Screen(
                name = "Trip sheet",
                endpoints = setOf("trip"),
                budget = 1,
                prepare = { c ->
                    c.tripResults()
                    c.leg = c.s.options.flatMap { it.legs }.first { it.isTransit }
                },
                act = open,
                outcome = { c ->
                    val sheet = c.trips().sheet.value
                    when {
                        sheet?.details == null -> null
                        sheet.legOnly -> Outcome.FAILED
                        else -> Outcome.OK
                    }
                },
                message = { c -> c.d.str(R.string.trip_leg_only) },
                retry = open,
                reset = { c -> c.d.inst.runOnMainSync { c.trips().close() } },
                repeat = open,
            ),
        )
    }

    @Test fun map_stops() {
        // Each action looks at another stretch of map, so neither the ViewModel nor the stops cache has it.
        fun box(seq: Int) = LatLon(29.6 + 0.25 * seq, 34.9).let { a -> BBox(a, LatLon(a.lat + 0.004, a.lon + 0.006)) }
        matrix(
            Screen(
                name = "Map stops",
                endpoints = setOf("map/stops"),
                budget = 1,
                act = { c -> c.d.onMain { it.onViewport(box(c.seq), 16.0) } },
                // Stops are decoration: a failure leaves the layer as it was. Done once the request had its time.
                outcome = { c -> if (c.elapsed < SILENT_MS) null else if (c.vm.stops.value.contains("\"Feature\"")) Outcome.OK else Outcome.FAILED },
                repeat = { c -> c.d.onMain { it.onViewport(box(c.seq), 16.0) } },
                keepsOld = true,
            ),
        )
    }

    @Test fun typing_search_transitous() = matrix(typing("Typing search (Transitous)", setOf("geocode", "photon"), 1 + PlaceSearch.BACKUP_PER_SEARCH))

    /** Transitous answers but knows nothing: Photon is asked, through its own base URL. */
    @Test fun typing_search_photon() = matrix(typing("Typing search (Photon)", setOf("photon"), PlaceSearch.BACKUP_PER_SEARCH, mapOf("geocode" to "[]")))

    private fun typing(name: String, endpoints: Set<String>, budget: Int, bodies: Map<String, String> = emptyMap()): Screen {
        // Another word each time, so no cache and no "same text" shortcut answers it.
        fun text(seq: Int) = "רגר " + "אבגדהוזחטיכלמנסעפצקרשת"[seq % 22] + "ה".repeat(seq / 22)
        val type: (Ctx) -> Unit = { c ->
            c.d.onMain {
                it.startEditing(Field.TO)
                it.onQuery(text(c.seq))
            }
        }
        return Screen(
            name = name,
            endpoints = endpoints,
            budget = budget,
            bodies = bodies,
            act = type,
            outcome = { c ->
                val s = c.s
                when {
                    s.places.offline || s.places.notFound -> Outcome.FAILED
                    s.suggestions.any { !it.saved && !it.recent && !it.home } -> Outcome.OK
                    else -> null
                }
            },
            message = { c -> c.d.str(if (c.s.places.offline) R.string.search_offline else R.string.search_not_found) },
            retry = { c -> c.seq++; type(c) },
            reset = { c -> c.d.onMain { it.cancelEditing() } },
            repeat = type,
        )
    }

    @Test fun pin_name() {
        fun point(seq: Int) = LatLon(31.20 + 0.01 * seq, 34.78)
        val press: (Ctx) -> Unit = { c -> c.d.onMain { it.onMapLongPress(point(c.seq)) } }
        matrix(
            Screen(
                name = "Pin name",
                endpoints = setOf("reverse-geocode"),
                budget = 1,
                act = press,
                // No name: the pin shows its place without one; never "naming…" forever.
                outcome = { c ->
                    val pin = c.s.places.pin
                    when {
                        pin == null || pin.naming -> null
                        pin.name != null -> Outcome.OK
                        else -> Outcome.FAILED
                    }
                },
                retry = { c -> c.d.onMain { it.closePin() }; press(c) },
                reset = { c -> c.d.onMain { it.closePin() } },
                repeat = press,
            ),
        )
    }

    @Test fun rain_note() {
        // Each search starts in another 0.1° cell, so the forecast cache (1 h per cell) never answers.
        fun start(seq: Int) = SavedPlace("נקודה $seq", 31.05 + 0.15 * seq, 34.85)
        // "Now" a second later each time: the fixed clock would give every search the same resultsAt.
        val search: (Ctx) -> Unit = { c ->
            c.d.app.clockShift = Duration.ofSeconds(c.seq.toLong())
            c.place(Field.FROM, start(c.seq))
        }
        matrix(
            Screen(
                name = "Rain note",
                endpoints = setOf("meteo"),
                budget = 1,
                prepare = { c -> c.place(Field.TO, TEL_AVIV) },
                act = search,
                // Silent on failure: the options stay, no note. Done once the forecast came or had its time.
                outcome = { c ->
                    val s = c.s
                    when {
                        s.loading || !s.hasResults -> null
                        s.rain.forecast != null && s.rain.forResults == s.resultsAt -> Outcome.OK
                        c.elapsed > SILENT_MS -> Outcome.FAILED
                        else -> null
                    }
                },
                repeat = search,
            ),
        )
    }

    @Test fun missed_it() {
        val fix = MissedIt.Fix(LatLon(BGU.lat, BGU.lon), ageSec = 10, accuracyM = 10f)
        val fixes = object : MissedIt.Fixes {
            override fun last() = fix
            override suspend fun fresh() = fix
        }
        val target = MissedIt.Target("תל אביב", LatLon(TEL_AVIV.lat, TEL_AVIV.lon))
        val tap: (Ctx) -> Unit = { c -> c.d.onMain { it.missedIt(target, fixes) } }
        matrix(
            Screen(
                name = "Missed it",
                endpoints = setOf("plan"),
                budget = MissedIt.PLANS_PER_TAP,
                // Two minutes on per tap: a tap within 60 s of the last one is answered without asking.
                act = { c ->
                    c.d.app.clockShift = Duration.ofMinutes(2L * c.seq)
                    tap(c)
                },
                outcome = { c ->
                    val s = c.s
                    when {
                        s.loading -> null
                        s.error == UiError.NO_RESULTS -> Outcome.EMPTY
                        s.error != null -> Outcome.FAILED
                        s.missedIt?.kind == MissedItUi.Kind.PLANNED && s.options.isNotEmpty() -> Outcome.OK
                        else -> null
                    }
                },
                message = networkMessage,
                retry = { c ->
                    c.d.app.clockShift = Duration.ofMinutes(2L * c.seq + 1)
                    tap(c)
                },
                repeat = tap,
            ),
        )
    }

    companion object {
        /** Longest a screen may load under any fault (3 s timeouts, one 1 s Retry-After, a few requests in turn). */
        const val WAIT_MS = 30_000L

        /** How long a silent screen (map stops, rain) is given before "nothing shown" counts as its answer. */
        const val SILENT_MS = 5_000L

        const val STOP_ID = "fault-matrix-stop"
    }
}
