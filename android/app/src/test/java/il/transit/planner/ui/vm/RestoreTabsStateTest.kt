package il.transit.planner.ui.vm

import androidx.lifecycle.SavedStateHandle
import il.transit.core.api.parseTime
import il.transit.core.geo.LatLon
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPlanner
import il.transit.planner.data.PlanCacheStore
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.Field
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.SavedUi
import il.transit.planner.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.ZoneOffset

/**
 * Phase 11 R1: after Android kills the app in the background, each car tab and errands comes back
 * with the same options, the same selected one and its open trip sheet, from the tab cache named in
 * the saved state: **0 requests**. What already left is dropped; a missing entry leaves the inputs only.
 */
class RestoreTabsStateTest : VmTest() {
    @get:Rule val tmp = TemporaryFolder()

    private val soroka = LatLon(31.2589, 34.7992)

    private fun tabFile(): File = tmp.newFile("tab_cache.json").also { it.delete() }

    private fun read(handle: SavedStateHandle) = SavedUi.read { k -> handle.get<Any?>(k) }

    /** Searches [mode] with a live ViewModel, opens the second option's ride if there is one; returns what was shown. */
    private fun shown(mode: AppMode, handle: SavedStateHandle, file: File): UiState {
        val vm = newVm(ScriptedApi(replay()), saved = handle, tabCache = PlanCacheStore.tabs(file, CLOCK))
        if (mode == AppMode.TRIP) {
            vm.choose(Field.TO, "Savidor", SAVIDOR)
            vm.choose(Field.STOP, "Soroka", soroka)
        } else {
            vm.setMode(mode)
            vm.setTime(TimeMode.DEPART_AT, NOW)
            vm.choose(Field.FROM, "Meitar", MEITAR)
            if (mode == AppMode.DROP_OFF) vm.choose(Field.DRIVER_TO, "Ramat Gan", RAMAT_GAN)
            vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        }
        val found = vm.state.value.options
        assertTrue("$mode found something", found.isNotEmpty())
        if (vm.state.value.options.size > 1) vm.select(1)
        vm.rideSheet(0)
        settle { read(handle)?.cacheKey != null && file.exists() }
        return vm.state.value
    }

    /** A new process: a new store on the same file, a new ViewModel on the same saved state. */
    private fun reborn(handle: SavedStateHandle, file: File, clock: Clock = CLOCK): Pair<MainViewModel, ScriptedApi> {
        val api = ScriptedApi(replay(clock))
        return newVm(api, clock = clock, saved = handle, tabCache = PlanCacheStore.tabs(file, clock)) to api
    }

    private fun backAsItWas(mode: AppMode) {
        val handle = SavedStateHandle()
        val file = tabFile()
        val before = shown(mode, handle, file)
        val (vm, api) = reborn(handle, file)
        settle { vm.state.value.hasResults }
        val after = vm.state.value
        assertEquals(mode, after.mode)
        assertEquals(before.from, after.from)
        assertEquals(before.to, after.to)
        assertEquals(before.driverTo, after.driverTo)
        assertEquals(before.chainStops, after.chainStops)
        assertEquals(before.options, after.options)
        assertEquals(before.selected, after.selected)
        assertEquals(before.selectedItinerary, after.selectedItinerary)
        assertEquals("the car path", before.selectedCarPath, after.selectedCarPath)
        assertEquals(before.betterStart, after.betterStart)
        assertEquals(before.dropOff, after.dropOff)
        assertEquals(before.pickUp, after.pickUp)
        assertEquals(before.parkRide, after.parkRide)
        assertEquals(before.chain, after.chain)
        assertEquals("the trip sheet opens again", 0, vm.takeRestoredRide())
        assertEquals("restoring sends no request", 0, api.total)
    }

    @Test fun `better start comes back with no request`() = backAsItWas(AppMode.BETTER_START)

    @Test fun `drop-off comes back with no request, its car path too`() = backAsItWas(AppMode.DROP_OFF)

    @Test fun `pick-up comes back with no request`() = backAsItWas(AppMode.PICK_UP)

    @Test fun `park and ride comes back with no request, its car path too`() = backAsItWas(AppMode.PARK_RIDE)

    @Test fun `errands come back with their stop and their chain, with no request`() = backAsItWas(AppMode.TRIP)

    @Test fun `the sliders come back with the options they found`() {
        val handle = SavedStateHandle()
        val file = tabFile()
        val vm = newVm(ScriptedApi(replay()), saved = handle, tabCache = PlanCacheStore.tabs(file, CLOCK))
        vm.setMode(AppMode.PARK_RIDE)
        vm.setTime(TimeMode.DEPART_AT, NOW)
        vm.choose(Field.FROM, "Meitar", MEITAR)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        vm.setMaxParkRideDrive(35)
        settle { read(handle)?.cacheKey?.contains("-35:") == true }
        val (back, api) = reborn(handle, file)
        settle { back.state.value.hasResults }
        assertEquals(35, back.state.value.parkRide.maxDriveMin)
        assertEquals(vm.state.value.parkRide.result, back.state.value.parkRide.result)
        assertEquals(0, api.total)
    }

    @Test fun `options that left meanwhile are dropped, the rest come back`() {
        val handle = SavedStateHandle()
        val file = tabFile()
        val before = shown(AppMode.PARK_RIDE, handle, file)
        val boards = before.options.mapNotNull { o -> o.firstTransitLeg?.let { parseTime(it.startTime) } }.sorted()
        assertTrue("needs two departures: $boards", boards.size >= 2 && boards.first() < boards.last())
        val later = Clock.fixed(boards.first().plusSeconds(TripPlanner.DEPARTED_GRACE_SEC + 1), ZoneOffset.UTC)
        val (vm, api) = reborn(handle, file, later)
        settle { vm.state.value.hasResults }
        val restored = vm.state.value.options
        val left = restored.mapNotNull { o -> o.firstTransitLeg?.let { parseTime(it.startTime) } }
        assertEquals(boards.count { it > boards.first() }, left.size)
        assertTrue(left.all { it > boards.first() })
        assertEquals(0, api.total)
    }

    @Test fun `long after, nothing is left - the inputs come back and no stale option`() {
        listOf(AppMode.BETTER_START, AppMode.TRIP).forEach { mode ->
            val handle = SavedStateHandle()
            val file = tabFile()
            val before = shown(mode, handle, file)
            val (vm, api) = reborn(handle, file, Clock.fixed(NOW.plusSeconds(2 * 86_400), ZoneOffset.UTC))
            advance(1_000)
            Thread.sleep(200) // the store reads on an IO thread; give it time to answer "nothing"
            advance(1_000)
            val s = vm.state.value
            assertEquals(mode, s.mode)
            assertEquals(before.to, s.to)
            assertEquals(before.chainStops, s.chainStops)
            assertFalse("$mode shows no stale result", s.hasResults)
            assertNull(vm.takeRestoredRide())
            assertEquals(0, api.total)
        }
    }

    @Test fun `a missing entry shows the tab with its inputs and no results`() {
        val handle = SavedStateHandle()
        val file = tabFile()
        val before = shown(AppMode.DROP_OFF, handle, file)
        assertNotNull(read(handle)!!.cacheKey)
        file.delete()
        val (vm, api) = reborn(handle, file)
        Thread.sleep(200)
        advance(1_000)
        val s = vm.state.value
        assertEquals(AppMode.DROP_OFF, s.mode)
        assertEquals(before.driverTo, s.driverTo)
        assertEquals(before.to, s.to)
        assertFalse(s.hasResults)
        assertEquals(0, api.total)
    }
}
