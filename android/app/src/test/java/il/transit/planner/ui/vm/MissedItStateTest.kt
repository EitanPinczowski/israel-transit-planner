package il.transit.planner.ui.vm

import il.transit.core.api.GuardedTransitApi
import il.transit.core.geo.LatLon
import il.transit.core.plan.MissedIt
import il.transit.core.plan.TimeMode
import il.transit.core.remind.Reminder
import il.transit.core.user.UserSettings
import il.transit.planner.data.UserStore
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.Field
import il.transit.planner.ui.PlaceRef
import il.transit.planner.ui.UiError
import il.transit.planner.ui.screens.MissedItUi
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

    @Test fun `a tap - searching, then options from here, leaving now, with the banner`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        val phases = phasesOf(vm)
        vm.missedIt(toSavidor) { here }
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
        vm.missedIt(toSavidor.copy(arriveBy = true)) { here }
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
        vm.missedIt(toSavidor) { here }
        vm.missedIt(toSavidor) { here }
        assertEquals(1, api.count("plan"))
        assertNotNull(vm.state.value.results)
    }

    @Test fun `no location (no permission) - From focused, nothing sent`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.missedIt(toSavidor) { null }
        assertTrue(vm.state.value.loading) // waiting for a fix
        advance(MissedIt.WAIT_MS)
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(Field.FROM, s.editing)
        assertEquals(MissedItUi.Kind.NEED_START, s.missedIt?.kind)
        assertEquals(PlaceRef.Point("Savidor", SAVIDOR), s.to)
        assertEquals(0, api.total)
    }

    @Test fun `a stale fix waits for a fresh one`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        var f: MissedIt.Fix? = here.copy(ageSec = 600)
        vm.missedIt(toSavidor) { f }
        assertEquals(0, api.total)
        f = here
        advance(500)
        assertEquals(1, api.count("plan"))
        val kind = vm.state.value.missedIt
            ?.kind
        assertEquals(MissedItUi.Kind.PLANNED, kind)
    }

    @Test fun `at the destination - already there, no request`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.missedIt(toSavidor) { here.copy(at = LatLon(SAVIDOR.lat + 0.0005, SAVIDOR.lon)) }
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
        vm.missedIt(toSavidor) { here }
        assertEquals(UiError.NETWORK, vm.state.value.error)
        assertNull(vm.state.value.missedIt)
    }

    @Test fun `the counting-down reminder ends, a later one stays`() = runBlocking {
        val r = Reminder(BGU.lat, BGU.lon, SAVIDOR.lat, SAVIDOR.lon, UserSettings(), null, "5", "Stop", NOW.epochSecond + 300, leaveAtEpoch = NOW.epochSecond + 60, counting = true)
        val store = UserStore(MemoryPrefs())
        store.setReminder(r)
        newVm(ScriptedApi(replay()), store = store).missedIt(toSavidor) { here }
        assertNull(store.reminder.first())

        val later = r.copy(counting = false, leaveAtEpoch = NOW.epochSecond + 86_400)
        store.setReminder(later)
        newVm(ScriptedApi(replay()), store = store).missedIt(toSavidor) { here }
        assertEquals(later, store.reminder.first())
    }
}
