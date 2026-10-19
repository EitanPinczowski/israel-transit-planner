package il.transit.planner.ui.vm

import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.geo.LatLon
import il.transit.core.plan.CalendarEvent
import il.transit.planner.data.CalendarReader
import il.transit.planner.ui.CalendarViewModel
import il.transit.planner.ui.TripDetailsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** The trip sheet and the calendar picker: each has its own state holder, tested like the main one. */
class DetailStateTest : VmTest() {
    private val itinerary: Itinerary = planFixture("plan_bgu_telaviv").itineraries.first()
    private val transitLeg: Leg = itinerary.firstTransitLeg!!
    private val walkLeg: Leg = itinerary.legs.first { !it.isTransit }

    private fun sheetVm(api: ScriptedApi) = track(TripDetailsViewModel(api) { "en" })

    // --- trip sheet -------------------------------------------------------------------------------------

    @Test fun `trip sheet - a walking leg opens nothing and sends nothing`() {
        val api = ScriptedApi(replay())
        val vm = sheetVm(api)
        vm.open(walkLeg)
        assertNull(vm.sheet.value)
        assertEquals(0, api.total)
    }

    @Test fun `trip sheet - opening a ride shows its stops after one trip request, closing clears it`() {
        val api = ScriptedApi(replay())
        val vm = sheetVm(api)
        val states = record(vm.sheet)
        vm.open(transitLeg, color = "#123456")
        val sheet = vm.sheet.value!!
        assertEquals(transitLeg, sheet.leg)
        assertNotNull(sheet.details)
        assertFalse(sheet.legOnly)
        assertEquals("#123456", sheet.details!!.color)
        assertEquals(1, api.count("trip"))
        assertEquals(null, states.first())
        vm.close()
        assertNull(vm.sheet.value)
        assertNull(states.last())
    }

    @Test fun `trip sheet - a failed trip request still shows the leg's own stops`() {
        val api = ScriptedApi(replay())
        api.fault = { kind, _ -> if (kind == "trip") IOException("no signal") else null }
        val vm = sheetVm(api)
        vm.open(transitLeg)
        val sheet = vm.sheet.value!!
        assertTrue(sheet.legOnly)
        assertNotNull(sheet.details)
    }

    @Test fun `trip sheet - opening another ride replaces the first`() {
        val api = ScriptedApi(replay())
        val vm = sheetVm(api)
        val other = planFixture("plan_bgu_telaviv").itineraries.last().firstTransitLeg ?: transitLeg
        vm.open(transitLeg)
        vm.open(other)
        assertEquals(other, vm.sheet.value!!.leg)
    }

    @Test fun `trip sheet - vehicle positions are asked at most once in front, never out of sight`() {
        val shown = ScriptedApi(replay())
        sheetVm(shown).open(transitLeg)
        advance(120_000)
        assertTrue("in front: ${shown.count("mapTrips")}", shown.count("mapTrips") <= 1)
        val hidden = ScriptedApi(replay())
        val vm = sheetVm(hidden)
        vm.onVisible(false)
        vm.open(transitLeg)
        advance(120_000)
        assertEquals(0, hidden.count("mapTrips"))
    }

    // --- calendar -----------------------------------------------------------------------------------------

    private class FakeCalendar(var granted: Boolean, private val events: List<CalendarEvent>) : CalendarReader {
        override fun hasPermission() = granted
        override suspend fun upcoming(now: Instant): List<CalendarEvent> = events
    }

    private fun event(location: String): CalendarEvent {
        val start = Instant.now().plusSeconds(3600)
        return CalendarEvent("Dentist", location, start, start.plusSeconds(3600))
    }

    private fun calendarVm(api: ScriptedApi, source: FakeCalendar) = track(CalendarViewModel(api, source, "en"))

    @Test fun `calendar - no permission asks why first, no is remembered, yes loads the events`() {
        val source = FakeCalendar(granted = false, events = listOf(event("Herzl 12, Beer Sheva")))
        val vm = calendarVm(ScriptedApi(replay()), source)
        vm.onChip()
        assertTrue(vm.state.value.rationale)
        assertNull(vm.state.value.events)
        vm.onPermission(false)
        assertFalse(vm.state.value.rationale)
        assertTrue(vm.state.value.refused)
        source.granted = true
        vm.onPermission(true)
        assertFalse(vm.state.value.refused)
        assertEquals(
            1,
            vm.state.value.events!!
                .size,
        )
        vm.dismiss()
        assertNull(vm.state.value.events)
    }

    @Test fun `calendar - with permission the chip lists the events at once`() {
        val vm = calendarVm(ScriptedApi(replay()), FakeCalendar(true, listOf(event("Herzl 12, Beer Sheva"))))
        vm.onChip()
        assertFalse(vm.state.value.rationale)
        assertEquals(
            1,
            vm.state.value.events!!
                .size,
        )
    }

    @Test fun `calendar - picking an event looks the place up once and asks to confirm`() {
        val api = ScriptedApi(replay())
        val e = event("Herzl 12, Beer Sheva")
        val vm = calendarVm(api, FakeCalendar(true, listOf(e)))
        var notFound = false
        vm.pick(e, LatLon(31.25, 34.79), 10) { _, _, _ -> notFound = true }
        assertFalse(vm.state.value.resolving)
        assertNotNull(vm.state.value.confirm)
        assertFalse(notFound)
        assertEquals(1, api.count("geocode"))
    }

    @Test fun `calendar - a place that cannot be looked up goes back to typing`() {
        val api = ScriptedApi(replay())
        api.fault = { kind, _ -> if (kind == "geocode") IOException("no signal") else null }
        val e = event("Herzl 12, Beer Sheva")
        val vm = calendarVm(api, FakeCalendar(true, listOf(e)))
        var typed: String? = null
        vm.pick(e, null, 10) { place, at, _ -> typed = place.also { assertNull(at) } }
        assertFalse(vm.state.value.resolving)
        assertNull(vm.state.value.confirm)
        assertEquals("Herzl 12, Beer Sheva", typed)
    }
}
