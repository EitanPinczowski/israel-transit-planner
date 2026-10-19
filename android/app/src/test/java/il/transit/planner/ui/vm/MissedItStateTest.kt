package il.transit.planner.ui.vm

import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.Itinerary
import il.transit.core.geo.LatLon
import il.transit.core.plan.MissedIt
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripSort
import il.transit.core.remind.Reminder
import il.transit.core.ride.RideProgress
import il.transit.core.user.SavedPlace
import il.transit.core.user.UserSettings
import il.transit.planner.Rides
import il.transit.planner.data.UserStore
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.Field
import il.transit.planner.ui.PlaceRef
import il.transit.planner.ui.UiError
import il.transit.planner.ui.screens.MissedItUi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Phase 11 E2: "Missed it? Plan from here": the Trip tab from the current fix, at most 1 `plan` per tap. */
class MissedItStateTest : VmTest() {
    private val here = MissedIt.Fix(BGU, ageSec = 20, accuracyM = 15f)
    private val toSavidor = MissedIt.Target("Savidor", SAVIDOR)

    /** The phone's location: [last] at once; [fresh] is the one new fix asked for (by default none comes). */
    private class Fixes(var last: MissedIt.Fix?, val fresh: suspend () -> MissedIt.Fix? = { awaitCancellation() }) : MissedIt.Fixes {
        var asked = 0
        override fun last() = last
        override suspend fun fresh(): MissedIt.Fix? {
            asked++
            return fresh.invoke()
        }
    }

    private fun at(f: MissedIt.Fix?) = Fixes(f)

    private class FakeRides : Rides {
        override val active = MutableStateFlow(false)
        override val progress = MutableStateFlow<RideProgress?>(null)
        val started = ArrayList<Boolean>()
        var stops = 0
        override fun start(itinerary: Itinerary, speak: Boolean, arriveBy: Boolean) {
            started += arriveBy
            active.value = true
        }
        override fun stop() {
            stops++
            active.value = false
        }
    }

    @Test fun `a tap - searching, then options from here, leaving now, with the banner`() {
        val api = ScriptedApi(replay()).also { it.hold = true }
        val vm = newVm(api)
        val phases = phasesOf(vm)
        vm.missedIt(toSavidor, at(here))
        assertEquals(listOf("idle", "searching"), phases)
        assertEquals(1, api.pendingPlans)
        api.release()
        assertEquals(listOf("idle", "searching", "results"), phases)
        assertEquals(1, api.count("plan"))
        val s = vm.state.value
        assertEquals(AppMode.TRIP, s.mode)
        assertEquals(PlaceRef.MyLocation, s.from)
        assertEquals(PlaceRef.Point("Savidor", SAVIDOR), s.to)
        assertEquals(TimeMode.NOW, s.timeMode)
        assertTrue(s.options.isNotEmpty())
        assertEquals(MissedItUi(MissedItUi.Kind.PLANNED, SAVIDOR, NOW, false), s.missedIt)
        val req = api.planRequests.single()
        assertEquals(BGU.lat, req.from.let { (it as il.transit.core.api.Endpoint.Coord).at.lat }, 1e-9)
        assertFalse(req.arriveBy)
    }

    @Test fun `from a car tab with an arrive-by search - a plain Trip leaving now, and the banner says so`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.setMode(AppMode.PICK_UP)
        vm.missedIt(toSavidor.copy(arriveBy = true), at(here))
        val s = vm.state.value
        assertEquals(AppMode.TRIP, s.mode)
        assertEquals(TimeMode.NOW, s.timeMode)
        assertNull(s.pickUp)
        assertTrue(s.missedIt!!.wasArriveBy)
        val req = api.planRequests.single()
        assertFalse(req.arriveBy)
        assertTrue(req.postTransitModes.none { it == "CAR" })
    }

    @Test fun `two fast taps send 1 request`() {
        val api = ScriptedApi(replay())
        val vm = newVm(GuardedTransitApi(api, CLOCK))
        vm.missedIt(toSavidor, at(here))
        vm.missedIt(toSavidor, at(here))
        assertEquals(1, api.count("plan"))
        assertNotNull(vm.state.value.results)
    }

    @Test fun `no location (no permission) - From focused, nothing sent`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.missedIt(toSavidor, at(null))
        assertTrue(vm.state.value.loading) // waiting for a fix
        advance(MissedIt.WAIT_MS)
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(Field.FROM, s.editing)
        assertEquals(MissedItUi.Kind.NEED_START, s.missedIt?.kind)
        assertEquals(PlaceRef.Point("Savidor", SAVIDOR), s.to)
        assertEquals(0, api.total)
    }

    @Test fun `a stale fix asks the phone for one new fix and plans from it`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        val gate = kotlinx.coroutines.CompletableDeferred<MissedIt.Fix>()
        val fixes = Fixes(here.copy(ageSec = 600)) { gate.await() }
        vm.missedIt(toSavidor, fixes)
        assertEquals(1, fixes.asked)
        assertEquals(0, api.total)
        gate.complete(here.copy(ageSec = 0))
        assertEquals(1, api.count("plan"))
        assertEquals(MissedItUi.Kind.PLANNED, vm.state.value.missedIt?.kind)
    }

    @Test fun `a usable last fix asks for no new one`() {
        val fixes = Fixes(here)
        newVm(ScriptedApi(replay())).missedIt(toSavidor, fixes)
        assertEquals(0, fixes.asked)
    }

    @Test fun `no new fix within 10 s - From focused and empty, nothing sent`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        val fixes = Fixes(here.copy(ageSec = 600))
        vm.missedIt(toSavidor, fixes)
        advance(MissedIt.WAIT_MS - 1)
        assertTrue(vm.state.value.loading)
        advance(1)
        assertEquals(1, fixes.asked)
        assertEquals(Field.FROM, vm.state.value.editing)
        assertEquals("", vm.state.value.query)
        assertEquals(0, api.total)
    }

    @Test fun `two taps while the first still waits for its answer send 1 request`() {
        val api = ScriptedApi(replay()).also { it.hold = true }
        val vm = newVm(api)
        val first = vm.missedIt(toSavidor, at(here))
        val second = vm.missedIt(toSavidor, at(here))
        assertTrue(first === second)
        api.release()
        assertEquals(1, api.count("plan"))
        assertNotNull(vm.state.value.results)
    }

    @Test fun `a new search after it hides the banner`() {
        val vm = newVm(ScriptedApi(replay()))
        vm.missedIt(toSavidor, at(here))
        assertNotNull(vm.state.value.missedIt)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        assertNull(vm.state.value.missedIt)
        assertNotNull(vm.state.value.results)
    }

    @Test fun `From, the tab, swap and the time each hide the banner`() {
        val steps: List<Pair<String, (il.transit.planner.ui.MainViewModel) -> Unit>> = listOf(
            "from" to { vm -> vm.choose(Field.FROM, "Meitar", MEITAR) },
            "tab" to { vm -> vm.setMode(AppMode.BETTER_START) },
            "swap" to { vm -> vm.swap() },
            "arrive by" to { vm -> vm.setTime(TimeMode.ARRIVE_BY, NOW.plusSeconds(7_200)) },
            "search again" to { vm -> vm.plan() },
        )
        for ((name, step) in steps) {
            val vm = newVm(ScriptedApi(replay()))
            vm.missedIt(toSavidor, at(here))
            assertNotNull(name, vm.state.value.missedIt)
            step(vm)
            assertNull(name, vm.state.value.missedIt)
        }
    }

    @Test fun `the active ride stops`() {
        val rides = FakeRides().also { it.active.value = true }
        newVm(ScriptedApi(replay()), rides = rides).missedIt(toSavidor, at(here))
        assertEquals(1, rides.stops)
        val idle = FakeRides()
        newVm(ScriptedApi(replay()), rides = idle).missedIt(toSavidor, at(here))
        assertEquals("no ride, nothing to stop", 0, idle.stops)
    }

    @Test fun `every car tab's options and the errands go`() {
        for (mode in listOf(AppMode.BETTER_START, AppMode.DROP_OFF, AppMode.PARK_RIDE)) {
            val api = ScriptedApi(replay())
            val vm = newVm(api)
            vm.setMode(mode)
            vm.setTime(TimeMode.DEPART_AT, NOW)
            vm.choose(Field.FROM, "Start", MEITAR)
            vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
            if (mode == AppMode.DROP_OFF) vm.choose(Field.DRIVER_TO, "Ramat Gan", RAMAT_GAN)
            val s0 = vm.state.value
            assertTrue("$mode searched", s0.betterStart != null || s0.dropOff != null || s0.parkRide.result != null)
            val before = api.count("plan")
            vm.missedIt(toSavidor, at(here))
            val s = vm.state.value
            assertEquals(mode.name, AppMode.TRIP, s.mode)
            assertNull(s.betterStart)
            assertNull(s.dropOff)
            assertNull(s.parkRide.result)
            assertEquals(1, api.count("plan") - before)
        }
        val vm = newVm(ScriptedApi(replay()))
        vm.choose(Field.STOP, "Soroka", MEITAR)
        assertEquals(1, vm.state.value.chainStops.size)
        vm.missedIt(toSavidor, at(here))
        assertTrue(vm.state.value.chainStops.isEmpty())
        assertNull(vm.state.value.chain)
    }

    @Test fun `no name in the tap - the saved place there, else the place on screen, else none`() = runBlocking {
        val nameless = MissedIt.Target(null, SAVIDOR)
        val store = UserStore(MemoryPrefs())
        store.setPlaces(listOf(SavedPlace("Work", SAVIDOR.lat + 0.0005, SAVIDOR.lon)))
        val saved = newVm(ScriptedApi(replay()), store = store)
        saved.missedIt(nameless, at(here))
        assertEquals(PlaceRef.Point("Work", SAVIDOR), saved.state.value.to)

        val shown = newVm(ScriptedApi(replay()))
        shown.choose(Field.TO, "Savidor Center", LatLon(SAVIDOR.lat, SAVIDOR.lon + 0.0005))
        shown.missedIt(nameless, at(here))
        assertEquals(PlaceRef.Point("Savidor Center", SAVIDOR), shown.state.value.to)

        val none = newVm(ScriptedApi(replay()))
        none.choose(Field.TO, "Haifa", HAIFA)
        none.missedIt(nameless, at(here))
        assertEquals(PlaceRef.Point(null, SAVIDOR), none.state.value.to)
    }

    @Test fun `the plan uses the stored settings, and the list their sort`() = runBlocking {
        val mine = UserSettings(tripSort = TripSort.FEWEST_TRANSFERS, maxWalkMin = 7)
        val store = UserStore(MemoryPrefs())
        store.setSettings(mine)
        val api = ScriptedApi(replay())
        val vm = newVm(api, store = store)
        vm.missedIt(toSavidor, at(here))
        val trip = ScriptedApi(replay())
        TripPlanner(trip).plan(MissedIt.query(toSavidor, BGU, mine, "en"), NOW)
        assertEquals(trip.planRequests.single(), api.planRequests.single())
        val transfers = vm.state.value.options.map { it.transfers }
        assertTrue(transfers.size > 1)
        assertEquals(transfers.sorted(), transfers)
    }

    @Test fun `arrive-by travels with the ride and the reminder into the tap`() = runBlocking {
        val rides = FakeRides()
        val store = UserStore(MemoryPrefs())
        val vm = newVm(ScriptedApi(replay()), store = store, rides = rides)
        vm.choose(Field.TO, "Tel Aviv", TEL_AVIV)
        vm.setTime(TimeMode.ARRIVE_BY, NOW.plusSeconds(7_200))
        assertTrue(vm.state.value.options.isNotEmpty())
        assertTrue(vm.startRide())
        assertEquals(listOf(true), rides.started)
        assertTrue(vm.remindSelected())
        val stored = store.reminder.first()!!
        assertTrue(stored.arriveBy)
        // "Start trip" on that reminder's countdown rides it as arrive-by too.
        vm.startReminderRide()
        assertEquals(listOf(true, true), rides.started)
        // Its notifications hand MissedIt.Target(r.toName, r.to, r.arriveBy) over: the banner says "leaving now instead".
        vm.missedIt(MissedIt.Target(stored.toName, stored.to, stored.arriveBy), at(here))
        assertTrue(vm.state.value.missedIt!!.wasArriveBy)
    }

    @Test fun `at the destination - already there, no request`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.missedIt(toSavidor, at(here.copy(at = LatLon(SAVIDOR.lat + 0.0005, SAVIDOR.lon))))
        assertEquals(0, api.total)
        val kind = vm.state.value.missedIt
            ?.kind
        assertEquals(MissedItUi.Kind.ALREADY_THERE, kind)
        vm.clearResults()
        assertNull(vm.state.value.missedIt)
    }

    @Test fun `no signal - the network error, the banner not shown`() {
        val api = ScriptedApi(replay()).also { it.fault = { kind, _ -> if (kind == "plan") IOException("offline") else null } }
        val vm = newVm(api)
        vm.missedIt(toSavidor, at(here))
        assertEquals(UiError.NETWORK, vm.state.value.error)
        assertNull(vm.state.value.missedIt)
    }

    @Test fun `the counting-down reminder ends, a later one stays`() = runBlocking {
        val r = Reminder(BGU.lat, BGU.lon, SAVIDOR.lat, SAVIDOR.lon, UserSettings(), null, "5", "Stop", NOW.epochSecond + 300, leaveAtEpoch = NOW.epochSecond + 60, counting = true)
        val store = UserStore(MemoryPrefs())
        store.setReminder(r)
        newVm(ScriptedApi(replay()), store = store).missedIt(toSavidor, at(here))
        assertNull(store.reminder.first())

        val later = r.copy(counting = false, leaveAtEpoch = NOW.epochSecond + 86_400)
        store.setReminder(later)
        newVm(ScriptedApi(replay()), store = store).missedIt(toSavidor, at(here))
        assertEquals(later, store.reminder.first())
    }
}
