package il.transit.planner.ui.vm

import il.transit.core.api.Endpoint
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.PlanResponse
import il.transit.core.plan.PlanCache
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPages
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripQuery
import il.transit.core.plan.TripSort
import il.transit.planner.data.PlanCacheStore
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.Field
import il.transit.planner.ui.PlaceRef
import il.transit.planner.ui.TripPagesUi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/** The Trip tab (A to B): every state it goes through, and what a newer search does to an older one. */
class TripStateTest : VmTest() {
    @get:Rule val tmp = TemporaryFolder()

    private fun heldApi() = ScriptedApi(replay()).also { it.hold = true }

    @Test fun `idle, searching, results - one plan and the first option opened`() {
        val api = heldApi()
        val vm = newVm(api)
        val phases = phasesOf(vm)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        assertEquals(listOf("idle", "searching"), phases)
        assertEquals(1, api.pendingPlans)
        api.release()
        assertEquals(listOf("idle", "searching", "results"), phases)
        val s = vm.state.value
        assertEquals(0, s.selected)
        assertEquals(NOW, s.resultsAt)
        assertTrue(s.options.isNotEmpty())
        assertNull(s.error)
        assertEquals(1, api.count("plan"))
    }

    @Test fun `a trip that is not complete sends nothing`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.plan()
        vm.setTime(TimeMode.DEPART_AT, NOW.plusSeconds(3600))
        vm.setMode(AppMode.PICK_UP)
        assertEquals(0, api.total)
        assertEquals(listOf("idle"), phasesOf(vm))
    }

    @Test fun `no location fix - the no-location error, no request, no spinner`() {
        fix = null
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        val phases = phasesOf(vm)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        assertEquals(listOf("idle", "error:NO_LOCATION"), phases)
        assertEquals(0, api.total)
        assertFalse(vm.state.value.loading)
    }

    @Test fun `an empty answer is no options, and a later search can still succeed`() {
        val api = ScriptedApi(replay())
        api.planAnswer = { PlanResponse() }
        val vm = newVm(api)
        val phases = phasesOf(vm)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        assertEquals(listOf("idle", "searching", "error:NO_RESULTS"), phases)
        api.planAnswer = null
        vm.plan()
        assertEquals(listOf("idle", "searching", "error:NO_RESULTS", "searching", "results"), phases)
        assertNull(vm.state.value.error)
    }

    @Test fun `a failed search is an error, and Retry gives results`() {
        val api = ScriptedApi(replay())
        api.fault = { kind, n -> if (kind == "plan" && n == 1) IOException("no signal") else null }
        val vm = newVm(api)
        val phases = phasesOf(vm)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        assertEquals(listOf("idle", "searching", "error:NETWORK"), phases)
        assertNull(vm.state.value.results)
        vm.plan()
        assertEquals(listOf("idle", "searching", "error:NETWORK", "searching", "results"), phases)
        assertEquals(2, api.count("plan"))
    }

    @Test fun `no signal with a saved copy shows that copy, marked offline, and Retry replaces it`() {
        val api = ScriptedApi(replay())
        val planned = runBlocking {
            TripPlanner(replay()).plan(TripQuery(Endpoint.Coord(BGU), Endpoint.Coord(SAVIDOR), TimeMode.NOW), NOW)
        }
        val cache = PlanCacheStore(tmp.newFile("trip_cache.json").also { it.delete() }, CLOCK)
        runBlocking { cache.put(PlanCache.key("TRIP-NOW", BGU, SAVIDOR), planned) }
        api.fault = { kind, n -> if (kind == "plan" && n == 1) IOException("no signal") else null }
        val vm = newVm(api, planCache = cache)
        val phases = phasesOf(vm)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        settle { phases.last() == "offline" }
        assertEquals(listOf("idle", "searching", "offline"), phases)
        assertEquals(NOW, vm.state.value.offlineSince)
        assertNull(vm.state.value.error)
        assertTrue(
            vm.state.value.options
                .isNotEmpty(),
        )
        vm.plan()
        settle { phases.last() == "results" }
        assertNull(vm.state.value.offlineSince)
    }

    @Test fun `changing To while searching cancels the old search and only the new answer shows`() {
        val tlv = planFixture("plan_now_bs_hahagana")
        val bgu = planFixture("plan_bgu_telaviv")
        val api = heldApi()
        api.planAnswer = { req -> if ((req.to as Endpoint.Coord).at == HAIFA) bgu else tlv }
        val vm = newVm(api, RECORDED_CLOCK)
        val phases = phasesOf(vm)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        vm.choose(Field.TO, "Haifa", HAIFA)
        assertEquals(1, api.cancelledPlans)
        assertEquals(1, api.pendingPlans)
        api.release(2)
        assertEquals(listOf("idle", "searching", "results"), phases)
        val shown = vm.state.value.results!!
            .itineraries
            .map { it.startTime }
        assertTrue(shown.isNotEmpty())
        assertTrue(shown.all { t -> bgu.itineraries.any { it.startTime == t } })
        assertTrue(shown.none { t -> tlv.itineraries.any { it.startTime == t } })
        assertEquals("Haifa", (vm.state.value.to as PlaceRef.Point).name)
    }

    @Test fun `changing From while searching cancels the old search`() {
        val api = heldApi()
        val vm = newVm(api)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        vm.choose(Field.FROM, "Meitar", MEITAR)
        assertEquals(1, api.cancelledPlans)
        api.release(2)
        assertEquals(2, api.count("plan"))
        assertEquals(MEITAR, (api.planRequests.last().from as Endpoint.Coord).at)
        assertEquals("results", phase(vm.state.value))
    }

    @Test fun `swap while searching starts over from the other end`() {
        val api = heldApi()
        val vm = newVm(api)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        vm.swap()
        assertEquals(1, api.cancelledPlans)
        api.release(2)
        val last = api.planRequests.last()
        assertEquals(SAVIDOR, (last.from as Endpoint.Coord).at)
        assertEquals(BGU, (last.to as Endpoint.Coord).at)
    }

    @Test fun `choosing a new time while searching cancels the old search`() {
        val api = heldApi()
        val vm = newVm(api)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        val later = NOW.plusSeconds(3 * 3600)
        vm.setTime(TimeMode.DEPART_AT, later)
        assertEquals(1, api.cancelledPlans)
        api.release(2)
        assertEquals(later, api.planRequests.last().time)
        assertEquals(TimeMode.DEPART_AT, vm.state.value.timeMode)
        assertEquals(later, vm.state.value.time)
        assertTrue(
            vm.state.value.options
                .all { it.start.isAfter(later) },
        )
    }

    @Test fun `depart at and arrive by reach the request, and Now forgets the time`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        val at = NOW.plusSeconds(86_400)
        vm.setTime(TimeMode.ARRIVE_BY, at)
        assertTrue(api.planRequests.last().arriveBy)
        assertEquals(at, api.planRequests.last().time)
        vm.setTime(TimeMode.NOW, at)
        assertNull(vm.state.value.time)
        assertFalse(api.planRequests.last().arriveBy)
        assertEquals(NOW, api.planRequests.last().time)
        assertEquals(3, api.count("plan"))
    }

    @Test fun `a second tap on the same search within a minute sends nothing through the guard`() {
        val api = ScriptedApi(replay())
        val guarded = GuardedTransitApi(api, CLOCK)
        val vm = newVm(guarded)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        vm.plan()
        vm.plan()
        assertEquals(1, api.count("plan"))
        assertEquals("results", phase(vm.state.value))
    }

    @Test fun `the refresh loop re-plans quietly every two minutes and a failure leaves the results alone`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        vm.onVisible(true)
        val phases = phasesOf(vm)
        val shown = vm.state.value.results
        advance(120_000)
        assertEquals(2, api.count("plan"))
        assertEquals(listOf("results"), phases) // never "searching": the list stays on screen
        api.fault = { _, _ -> IOException("no signal") }
        advance(120_000)
        assertEquals(3, api.count("plan"))
        assertEquals(listOf("results"), phases)
        assertNull(vm.state.value.error)
        assertNotNull(vm.state.value.results)
        vm.onVisible(false)
        advance(600_000)
        assertEquals(3, api.count("plan")) // out of sight: no requests
        assertNotNull(shown)
    }

    // --- tabs ------------------------------------------------------------------------------------

    @Test fun `switching tab mid-search cancels it and the other tab searches instead`() {
        val api = heldApi()
        val vm = newVm(api)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        vm.setMode(AppMode.PICK_UP)
        assertEquals(1, api.cancelledPlans)
        api.releaseAll()
        assertEquals(AppMode.PICK_UP, vm.state.value.mode)
        assertNull(vm.state.value.results)
        assertNotNull(vm.state.value.pickUp)
    }

    @Test fun `switching to a tab that cannot search yet drops the old search and its spinner`() {
        val api = heldApi()
        val vm = newVm(api)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        vm.setMode(AppMode.DROP_OFF) // needs the driver's destination first: nothing to search
        assertFalse(vm.state.value.loading)
        api.release()
        val s = vm.state.value
        assertEquals(AppMode.DROP_OFF, s.mode)
        assertFalse("no spinner on a tab with nothing to search", s.loading)
        assertNull("the Trip answer must not turn up on another tab", s.results)
        assertFalse(s.hasResults)
        assertEquals("idle", phase(s))
    }

    @Test fun `arrive by is a Trip-tab thing - a car tab turns it into leave now`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.setTime(TimeMode.ARRIVE_BY, NOW.plusSeconds(7200))
        vm.setMode(AppMode.BETTER_START)
        assertEquals(TimeMode.NOW, vm.state.value.timeMode)
        vm.setMode(AppMode.TRIP)
        assertEquals(TimeMode.NOW, vm.state.value.timeMode)
    }

    // --- options: select, sort, Earlier / Later -----------------------------------------------------

    @Test fun `sorting keeps the opened option and sends no request`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        assertTrue(vm.state.value.options.size >= 2)
        vm.select(1)
        val opened = vm.state.value.selectedItinerary
        vm.setTripSort(TripSort.LEAST_WALKING)
        assertEquals(TripSort.LEAST_WALKING, vm.state.value.settings.tripSort)
        assertEquals(opened, vm.state.value.selectedItinerary)
        vm.setTripSort(TripSort.FEWEST_TRANSFERS)
        assertEquals(opened, vm.state.value.selectedItinerary)
        assertEquals(1, api.count("plan"))
    }

    @Test fun `selecting an option changes only the selection`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        val before = vm.state.value
        vm.select(1)
        assertEquals(1, vm.state.value.selected)
        assertSame(before.results, vm.state.value.results)
        vm.selectFromMap(0)
        assertEquals(0, vm.state.value.selected)
        assertEquals(before.mapPick + 1, vm.state.value.mapPick)
        vm.selectFromMap(0) // already open: nothing happens
        assertEquals(before.mapPick + 1, vm.state.value.mapPick)
    }

    private fun pagedApi(): ScriptedApi {
        val first = planFixture("plan_pages_first")
        val later = planFixture("plan_pages_later")
        val earlier = planFixture("plan_pages_earlier")
        return ScriptedApi(replay()).also { api ->
            api.planAnswer = { req ->
                when (req.pageCursor) {
                    null -> first
                    first.nextPageCursor -> later
                    first.previousPageCursor -> earlier
                    else -> error("unexpected cursor ${req.pageCursor}")
                }
            }
        }
    }

    @Test fun `Later adds a page, one request, and the opened option stays opened`() {
        val api = pagedApi()
        val vm = newVm(api, RECORDED_CLOCK)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        val before = vm.state.value.options.size
        assertTrue(before >= 2)
        vm.select(1)
        val opened = vm.state.value.selectedItinerary
        api.hold = true
        vm.later()
        assertEquals(TripPages.Direction.LATER, vm.state.value.pages.loading)
        vm.later() // a second tap while one is loading is ignored
        assertEquals(2, api.count("plan"))
        api.release()
        assertEquals(TripPagesUi(), vm.state.value.pages)
        assertTrue(vm.state.value.options.size > before)
        assertEquals(opened, vm.state.value.selectedItinerary)
    }

    @Test fun `Earlier adds a page the same way`() {
        val api = pagedApi()
        val vm = newVm(api, RECORDED_CLOCK)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        val before = vm.state.value.options.size
        vm.earlier()
        assertEquals(2, api.count("plan"))
        assertTrue(vm.state.value.options.size > before)
        assertEquals(TripPagesUi(), vm.state.value.pages)
    }

    @Test fun `a failed page keeps the list and says so, and tapping again retries`() {
        val api = pagedApi()
        val vm = newVm(api, RECORDED_CLOCK)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        val shown = vm.state.value.results
        api.fault = { kind, n -> if (kind == "plan" && n == 2) IOException("no signal") else null }
        vm.later()
        assertTrue(vm.state.value.pages.failed)
        assertSame(shown, vm.state.value.results)
        vm.later()
        assertFalse(vm.state.value.pages.failed)
        assertTrue(
            vm.state.value.results!!
                .itineraries.size > shown!!.itineraries.size,
        )
    }

    @Test fun `a page that answers after a new search began is dropped`() {
        val api = pagedApi()
        val vm = newVm(api, RECORDED_CLOCK)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        val before = vm.state.value.options.size
        api.hold = true
        vm.later() // plan 2 waits
        vm.plan() // a new search: plan 3 waits
        api.release(2)
        assertEquals(TripPagesUi(), vm.state.value.pages)
        assertEquals(before, vm.state.value.options.size)
        assertEquals("results", phase(vm.state.value))
    }

    // --- the stop sheet and the stops layer ---------------------------------------------------------

    @Test fun `opening a stop shows a spinner, then its departures, and closing clears it`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.openStop("il-Israel-MOT_37314", "Be'er Sheva North")
        val sheet = vm.state.value.stopSheet!!
        assertFalse(sheet.loading)
        assertTrue(sheet.rows.isNotEmpty())
        assertFalse(sheet.failed)
        assertEquals(1, api.count("stopTimes"))
        vm.closeStop()
        assertNull(vm.state.value.stopSheet)
    }

    @Test fun `a stop that cannot be loaded says so, and a search clears the sheet`() {
        val api = ScriptedApi(replay())
        api.fault = { kind, _ -> if (kind == "stopTimes") IOException("no signal") else null }
        val vm = newVm(api)
        vm.openStop("il-Israel-MOT_37314", "Be'er Sheva North")
        assertTrue(
            vm.state.value.stopSheet!!
                .failed,
        )
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        assertNull(vm.state.value.stopSheet)
    }
}
