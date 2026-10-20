package il.transit.planner.ui.vm

import androidx.lifecycle.SavedStateHandle
import il.transit.core.api.parseTime
import il.transit.core.geo.LatLon
import il.transit.core.plan.TabResult
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPlanner
import il.transit.planner.data.PlanCacheStore
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.Field
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.SavedUi
import il.transit.planner.ui.UiState
import kotlinx.coroutines.runBlocking
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

    private var files = 0

    /** A fresh cache file per search (a test may run several), not there yet. */
    private fun tabFile(): File = tmp.newFile("tab_cache_${files++}.json").also { it.delete() }

    private fun read(handle: SavedStateHandle) = SavedUi.read { k -> handle.get<Any?>(k) }

    /** Searches [mode] with a live ViewModel and waits until its answer is kept; returns the ViewModel. */
    private fun search(mode: AppMode, handle: SavedStateHandle, file: File): MainViewModel {
        val vm = newVm(ScriptedApi(replay()), saved = handle, tabCache = PlanCacheStore.tabs(file, CLOCK))
        if (mode == AppMode.TRIP) {
            vm.choose(Field.TO, "Savidor", SAVIDOR)
            vm.choose(Field.STOP, "Soroka", soroka)
        } else {
            vm.setMode(mode)
            vm.setTime(TimeMode.DEPART_AT, NOW)
            // Limits at which the replay offers options, not only the baseline.
            vm.setMaxDrive(20)
            vm.setMaxPickUpDrive(30)
            if (mode == AppMode.PICK_UP) {
                vm.choose(Field.FROM, "Tel Aviv", TEL_AVIV)
                vm.choose(Field.TO, "Meitar", MEITAR)
            } else {
                vm.choose(Field.FROM, "Meitar", MEITAR)
                if (mode == AppMode.DROP_OFF) vm.choose(Field.DRIVER_TO, "Ramat Gan", RAMAT_GAN)
                vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
            }
        }
        val state = vm.state
        settle { state.value.options.isNotEmpty() }
        val found = vm.state.value.options
        assertTrue("$mode found something", found.isNotEmpty())
        settle { read(handle)?.cacheKey != null && file.exists() }
        return vm
    }

    /** [search], then the second option if there is one and its first ride's trip sheet; returns what was shown. */
    private fun shown(mode: AppMode, handle: SavedStateHandle, file: File): UiState {
        val vm = search(mode, handle, file)
        if (vm.state.value.options.size > 1) vm.select(1)
        vm.rideSheet(0)
        settle { read(handle)?.sheet == SavedUi.Sheet.Ride(0) }
        return vm.state.value
    }

    /**
     * Waits, moving the test clock along, until the restore has finished with nothing to show: it then
     * saves the state again, and with nothing shown there is no cache key.
     */
    private fun restoredEmpty(handle: SavedStateHandle) = settle {
        advance(10)
        read(handle)?.cacheKey == null
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

    @Test fun `options that left meanwhile are dropped, the rest come back`() {
        val handle = SavedStateHandle()
        val file = tabFile()
        val before = shown(AppMode.PICK_UP, handle, file)
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
        listOf(AppMode.BETTER_START, AppMode.DROP_OFF, AppMode.PICK_UP, AppMode.PARK_RIDE, AppMode.TRIP).forEach { mode ->
            val handle = SavedStateHandle()
            val file = tabFile()
            val before = shown(mode, handle, file)
            val (vm, api) = reborn(handle, file, Clock.fixed(NOW.plusSeconds(2 * 86_400), ZoneOffset.UTC))
            restoredEmpty(handle)
            val s = vm.state.value
            assertEquals(mode, s.mode)
            assertEquals(before.from, s.from)
            assertEquals(before.to, s.to)
            assertEquals(before.driverTo, s.driverTo)
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
        restoredEmpty(handle)
        val s = vm.state.value
        assertEquals(AppMode.DROP_OFF, s.mode)
        assertEquals(before.driverTo, s.driverTo)
        assertEquals(before.to, s.to)
        assertFalse(s.hasResults)
        assertEquals(0, api.total)
    }

    @Test fun `a corrupt tab cache file gives the inputs only, with no plan request`() {
        val handle = SavedStateHandle()
        val file = tabFile()
        val before = shown(AppMode.PICK_UP, handle, file)
        file.writeText(file.readText().let { it.take(it.length / 2) })
        val (vm, api) = reborn(handle, file)
        restoredEmpty(handle)
        val s = vm.state.value
        assertEquals(AppMode.PICK_UP, s.mode)
        assertEquals(before.from, s.from)
        assertEquals(before.to, s.to)
        assertFalse(s.hasResults)
        assertEquals(0, api.count("plan"))
        assertEquals(0, api.total)
    }

    @Test fun `a cache key of another tab is not shown in the saved one`() {
        val file = tabFile()
        val betterStart = SavedStateHandle()
        search(AppMode.BETTER_START, betterStart, file)
        val otherKey = read(betterStart)!!.cacheKey!!
        assertTrue(otherKey.startsWith("BETTER_START-"))
        val handle = SavedStateHandle()
        search(AppMode.PICK_UP, handle, file)
        handle["d11.cacheKey"] = otherKey // the saved tab is Pick-up; the key names Better start's entry, which is on file
        val (vm, api) = reborn(handle, file)
        restoredEmpty(handle)
        val s = vm.state.value
        assertEquals(AppMode.PICK_UP, s.mode)
        assertFalse(s.hasResults)
        assertNull(s.betterStart)
        assertEquals(0, api.total)
    }

    @Test fun `a new search after a restore replaces the kept entry`() {
        val handle = SavedStateHandle()
        val file = tabFile()
        shown(AppMode.PARK_RIDE, handle, file)
        val key = read(handle)!!.cacheKey!!
        val later = Clock.fixed(NOW.plusSeconds(30), ZoneOffset.UTC)
        val (vm, api) = reborn(handle, file, later)
        settle { vm.state.value.hasResults }
        assertEquals(0, api.total)
        vm.plan()
        fun keptAt() = runBlocking { PlanCacheStore.tabs(file, later).entries() }.firstOrNull { it.key == key }?.savedAt
        settle {
            advance(10)
            val done = api.count("plan") > 0 && !vm.state.value.loading
            done && keptAt() == later.instant()
        }
        val kept = runBlocking { PlanCacheStore.tabs(file, later).entries() }.filter { it.key == key }
        assertEquals("one entry per key", 1, kept.size)
        val s = vm.state.value
        assertEquals(TabResult.ParkRide(s.parkRide.result!!, s.parkRide.origin), kept.single().value)
    }

    /**
     * R1's promise is 0 plan requests. A stop's departures sheet that was open comes back too, and it
     * fetches its departures again (one `stoptimes`, as the Trip tab's restore always did): that one is
     * the only request.
     */
    @Test fun `a stop sheet comes back and fetches its departures, the only request`() {
        val handle = SavedStateHandle()
        val file = tabFile()
        val vm = search(AppMode.BETTER_START, handle, file)
        vm.openStop("il-Israel-MOT_26669", "Soroka")
        settle {
            advance(10)
            read(handle)?.sheet == SavedUi.Sheet.Stop("il-Israel-MOT_26669", "Soroka")
        }
        val before = vm.state.value
        val (back, api) = reborn(handle, file)
        settle {
            advance(10)
            val s = back.state.value
            val sheet = s.stopSheet
            s.hasResults && sheet != null && !sheet.loading
        }
        val after = back.state.value
        assertEquals(before.options, after.options)
        assertEquals("il-Israel-MOT_26669", after.stopSheet!!.stopId)
        assertEquals("0 plan requests", 0, api.count("plan"))
        assertEquals("the departures, fetched again", 1, api.count("stopTimes"))
        assertEquals("nothing else", 1, api.total)
    }

    /** Each tab's own slider, moved after the search: it comes back with the options it found. */
    private fun sliderComesBack(mode: AppMode, value: Int, move: (MainViewModel) -> Unit, slider: (UiState) -> Int) {
        val handle = SavedStateHandle()
        val file = tabFile()
        val vm = search(mode, handle, file)
        move(vm)
        settle {
            advance(10)
            read(handle)?.cacheKey?.contains("-$value:") == true
        }
        val before = vm.state.value
        assertEquals(value, slider(before))
        val (back, api) = reborn(handle, file)
        settle { back.state.value.hasResults }
        val after = back.state.value
        assertEquals("$mode slider", value, slider(after))
        assertEquals(before.options, after.options)
        assertEquals(0, api.total)
    }

    @Test fun `better start's slider comes back with its options`() =
        sliderComesBack(AppMode.BETTER_START, 30, { it.setMaxDrive(30) }) { it.maxDriveMin }

    @Test fun `drop-off's slider comes back with its options`() =
        sliderComesBack(AppMode.DROP_OFF, 25, { it.setMaxDetour(25) }) { it.maxDetourMin }

    @Test fun `pick-up's slider comes back with its options`() =
        sliderComesBack(AppMode.PICK_UP, 40, { it.setMaxPickUpDrive(40) }) { it.maxPickUpDriveMin }

    @Test fun `park and ride's slider comes back with its options`() =
        sliderComesBack(AppMode.PARK_RIDE, 35, { it.setMaxParkRideDrive(35) }) { it.parkRide.maxDriveMin }
}
