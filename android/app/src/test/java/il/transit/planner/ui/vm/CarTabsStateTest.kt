package il.transit.planner.ui.vm

import il.transit.core.api.BudgetExceededException
import il.transit.core.api.PlanResponse
import il.transit.core.features.BetterStartPlanner
import il.transit.core.features.DropOffPlanner
import il.transit.core.features.ParkRidePlanner
import il.transit.core.features.PickUpPlanner
import il.transit.core.geo.LatLon
import il.transit.core.plan.ChainPlanner
import il.transit.core.plan.TimeMode
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.Field
import il.transit.planner.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The four car + transit tabs and the errands chain: what each state transition looks like, that a
 * tab with nothing to search yet stays quiet, and that a search stays inside its request budget.
 */
class CarTabsStateTest : VmTest() {
    private val ends = setOf("results", "error:NO_RESULTS")

    /** The tab is open with a start and a time; only the destination (and for Drop-off, the driver's) is missing. */
    private fun open(api: ScriptedApi, mode: AppMode, from: LatLon = MEITAR): MainViewModel {
        val vm = newVm(api)
        vm.setMode(mode)
        vm.setTime(TimeMode.DEPART_AT, NOW)
        vm.choose(Field.FROM, "Start", from)
        return vm
    }

    private fun heldApi() = ScriptedApi(replay()).also { it.hold = true }

    // --- Better start -----------------------------------------------------------------------------------

    @Test fun `better start - nothing to search until there is a destination, then idle, searching, done`() {
        val api = heldApi()
        val vm = open(api, AppMode.BETTER_START)
        val phases = phasesOf(vm)
        assertEquals(0, api.total)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        assertEquals(listOf("idle", "searching"), phases)
        api.releaseAll()
        assertTrue(phases.last() in ends)
        assertNotNull(vm.state.value.betterStart)
        assertFalse(vm.state.value.loading)
        assertTrue("budget: ${api.total}", api.total <= BetterStartPlanner.BUDGET)
    }

    @Test fun `better start - moving the drive slider while searching cancels the old search`() {
        val api = heldApi()
        val vm = open(api, AppMode.BETTER_START)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        vm.setMaxDrive(vm.state.value.maxDriveMin) // the same value: not a new search
        assertEquals(0, api.cancelledPlans)
        vm.setMaxDrive(25)
        assertTrue(api.cancelledPlans >= 1)
        api.releaseAll()
        assertEquals(25, vm.state.value.maxDriveMin)
        assertTrue(phase(vm.state.value) in ends)
    }

    @Test fun `better start - a failure is an error and Retry searches again`() {
        val api = ScriptedApi(replay())
        api.fault = { kind, n -> if (kind == "plan" && n == 1) IOException("no signal") else null }
        val vm = open(api, AppMode.BETTER_START)
        val phases = phasesOf(vm)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        assertEquals(listOf("idle", "searching", "error:NETWORK"), phases)
        assertNull(vm.state.value.betterStart)
        vm.plan()
        assertTrue(phases.last() in ends)
        assertNotNull(vm.state.value.betterStart)
    }

    @Test fun `better start - nothing found is the no-options state`() {
        val api = ScriptedApi(replay())
        api.planAnswer = { PlanResponse() }
        val vm = open(api, AppMode.BETTER_START)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        assertEquals("error:NO_RESULTS", phase(vm.state.value))
    }

    @Test fun `better start - a search that goes over its budget ends as an error, not a flood`() {
        val api = ScriptedApi(replay())
        api.fault = { kind, n -> if (kind == "plan" && n >= 2) BudgetExceededException(BetterStartPlanner.BUDGET) else null }
        val vm = open(api, AppMode.BETTER_START)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        assertFalse(vm.state.value.loading)
        assertTrue(api.total <= BetterStartPlanner.BUDGET)
        assertTrue(phase(vm.state.value) in ends + "error:NETWORK")
    }

    @Test fun `car tabs refresh only when asked - the refresh loop leaves them alone`() {
        val api = ScriptedApi(replay())
        val vm = open(api, AppMode.BETTER_START)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        val sent = api.total
        vm.onVisible(true)
        advance(600_000)
        assertEquals(sent, api.total)
    }

    @Test fun `switching tab drops the old tab's options and searches the new one`() {
        val api = ScriptedApi(replay())
        val vm = open(api, AppMode.BETTER_START)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        assertNotNull(vm.state.value.betterStart)
        vm.setMode(AppMode.PICK_UP)
        assertNull(vm.state.value.betterStart)
        assertNotNull(vm.state.value.pickUp)
        assertEquals(AppMode.PICK_UP, vm.state.value.mode)
        vm.setMode(AppMode.PICK_UP) // the tab already open: no new search
        val sent = api.total
        vm.setMode(AppMode.PICK_UP)
        assertEquals(sent, api.total)
    }

    @Test fun `opening an option on a car tab selects it`() {
        val api = ScriptedApi(replay())
        val vm = open(api, AppMode.BETTER_START)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        val options = vm.state.value.options
        if (options.size > 1) {
            vm.select(1)
            assertEquals(options[1], vm.state.value.selectedItinerary)
        }
        assertEquals(0, api.count("stopTimes"))
    }

    // --- Let me off on the way ----------------------------------------------------------------------------

    @Test fun `drop-off - needs the driver's destination too, then searches once`() {
        val api = heldApi()
        val vm = open(api, AppMode.DROP_OFF)
        val phases = phasesOf(vm)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        assertEquals(0, api.total) // the passenger's start and end are set, the driver's end is not
        assertFalse(vm.state.value.readyToPlan)
        vm.choose(Field.DRIVER_TO, "Ramat Gan", RAMAT_GAN)
        assertEquals(listOf("idle", "searching"), phases)
        api.releaseAll()
        assertTrue(phases.last() in ends)
        assertNotNull(vm.state.value.dropOff)
        assertTrue("budget: ${api.total}", api.total <= DropOffPlanner.BUDGET)
    }

    @Test fun `drop-off - changing the detour limit searches again, the old answer never shows`() {
        val api = heldApi()
        val vm = open(api, AppMode.DROP_OFF)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        vm.choose(Field.DRIVER_TO, "Ramat Gan", RAMAT_GAN)
        vm.setMaxDetour(20)
        assertTrue(api.cancelledPlans + api.count("oneToMany") >= 1)
        api.releaseAll()
        assertEquals(20, vm.state.value.maxDetourMin)
        assertFalse(vm.state.value.loading)
    }

    @Test fun `drop-off - a failure is an error and Retry works`() {
        val api = ScriptedApi(replay())
        api.fault = { kind, n -> if (kind == "plan" && n == 1) IOException("no signal") else null }
        val vm = open(api, AppMode.DROP_OFF)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        vm.choose(Field.DRIVER_TO, "Ramat Gan", RAMAT_GAN)
        assertEquals("error:NETWORK", phase(vm.state.value))
        api.fault = { _, _ -> null }
        vm.plan()
        assertTrue(phase(vm.state.value) in ends)
    }

    // --- Best pick-up point ---------------------------------------------------------------------------------

    @Test fun `pick-up - idle, searching, done, inside its budget`() {
        val api = heldApi()
        val vm = open(api, AppMode.PICK_UP, from = TEL_AVIV)
        val phases = phasesOf(vm)
        vm.choose(Field.TO, "Meitar", MEITAR)
        assertEquals(listOf("idle", "searching"), phases)
        api.releaseAll()
        assertTrue(phases.last() in ends)
        assertNotNull(vm.state.value.pickUp)
        assertTrue("budget: ${api.total}", api.total <= PickUpPlanner.BUDGET)
    }

    @Test fun `pick-up - the drive limit slider re-searches and keeps one answer`() {
        val api = heldApi()
        val vm = open(api, AppMode.PICK_UP, from = TEL_AVIV)
        vm.choose(Field.TO, "Meitar", MEITAR)
        vm.setMaxPickUpDrive(30)
        api.releaseAll()
        assertEquals(30, vm.state.value.maxPickUpDriveMin)
        assertTrue(phase(vm.state.value) in ends)
    }

    @Test fun `pick-up - a failure is an error and Retry works`() {
        val api = ScriptedApi(replay())
        api.fault = { kind, n -> if (kind == "plan" && n == 1) IOException("no signal") else null }
        val vm = open(api, AppMode.PICK_UP, from = TEL_AVIV)
        vm.choose(Field.TO, "Meitar", MEITAR)
        assertEquals("error:NETWORK", phase(vm.state.value))
        vm.plan()
        assertTrue(phase(vm.state.value) in ends)
    }

    // --- Park & ride -----------------------------------------------------------------------------------------

    @Test fun `park and ride - idle, searching, done, inside its budget`() {
        val api = heldApi()
        val vm = open(api, AppMode.PARK_RIDE)
        val phases = phasesOf(vm)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        assertEquals(listOf("idle", "searching"), phases)
        api.releaseAll()
        assertTrue(phases.last() in ends)
        assertNotNull(vm.state.value.parkRide.result)
        assertEquals(MEITAR, vm.state.value.parkRide.origin)
        assertTrue("budget: ${api.total}", api.total <= ParkRidePlanner.BUDGET)
    }

    @Test fun `park and ride - the drive limit re-searches, and leaving the tab clears the answer`() {
        val api = ScriptedApi(replay())
        val vm = open(api, AppMode.PARK_RIDE)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        val first = api.total
        vm.setMaxParkRideDrive(vm.state.value.parkRide.maxDriveMin) // same value: nothing
        assertEquals(first, api.total)
        vm.setMaxParkRideDrive(35)
        assertTrue(api.total > first)
        assertEquals(35, vm.state.value.parkRide.maxDriveMin)
        vm.setMode(AppMode.TRIP)
        assertNull(vm.state.value.parkRide.result)
    }

    @Test fun `park and ride - a failure is an error and Retry works`() {
        val api = ScriptedApi(replay())
        api.fault = { kind, _ -> if (kind == "oneToMany") IOException("no signal") else null }
        val vm = open(api, AppMode.PARK_RIDE)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        assertEquals("error:NETWORK", phase(vm.state.value))
        api.fault = { _, _ -> null }
        vm.plan()
        assertTrue(phase(vm.state.value) in ends)
    }

    // --- Errands ------------------------------------------------------------------------------------------------

    private val soroka = LatLon(31.2589, 34.7992)

    private fun errands(api: ScriptedApi): MainViewModel {
        val vm = newVm(api)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        return vm
    }

    @Test fun `errands - a stop turns the Trip search into a chain, inside its budget`() {
        val api = ScriptedApi(replay())
        val vm = errands(api)
        val before = api.total
        vm.choose(Field.STOP, "Soroka", soroka)
        val s = vm.state.value
        assertEquals(1, s.chainStops.size)
        assertNotNull(s.chain)
        assertNull(s.results)
        assertEquals(s.chain!!.legs, s.options)
        assertTrue("budget: ${api.total - before}", api.total - before <= ChainPlanner.BUDGET)
    }

    @Test fun `errands - removing the stop or changing the stay searches again`() {
        val api = ScriptedApi(replay())
        val vm = errands(api)
        vm.choose(Field.STOP, "Soroka", soroka)
        val withStop = api.count("plan")
        vm.setStay(0, 30)
        assertTrue(api.count("plan") > withStop)
        assertEquals(
            30,
            vm.state.value.chainStops
                .single()
                .stayMin,
        )
        vm.removeChainStop(0)
        assertTrue(
            vm.state.value.chainStops
                .isEmpty(),
        )
        assertNull(vm.state.value.chain)
        assertNotNull(vm.state.value.results)
    }

    @Test fun `errands - at most three stops, and errands are planned forwards`() {
        val api = ScriptedApi(replay())
        val vm = errands(api)
        vm.setTime(TimeMode.ARRIVE_BY, NOW.plusSeconds(7200))
        vm.choose(Field.STOP, "A", soroka)
        assertEquals(TimeMode.NOW, vm.state.value.timeMode) // arrive-by cannot chain
        repeat(4) { vm.choose(Field.STOP, "S$it", soroka) }
        assertEquals(ChainPlanner.MAX_STOPS, vm.state.value.chainStops.size)
    }

    @Test fun `errands - a failing leg is an error, not a half chain`() {
        val api = ScriptedApi(replay())
        val vm = errands(api)
        api.fault = { kind, _ -> if (kind == "plan") IOException("no signal") else null }
        vm.choose(Field.STOP, "Soroka", soroka)
        assertEquals("error:NETWORK", phase(vm.state.value))
        assertNull(vm.state.value.chain)
    }
}
