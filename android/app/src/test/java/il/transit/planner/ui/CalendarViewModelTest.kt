package il.transit.planner.ui

import il.transit.core.api.GeocodeMatch
import il.transit.core.api.Itinerary
import il.transit.core.api.MotisJson
import il.transit.core.api.Place
import il.transit.core.api.PlanRequest
import il.transit.core.api.PlanResponse
import il.transit.core.api.StopTimesResponse
import il.transit.core.api.TransitApi
import il.transit.core.api.TripSegment
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import il.transit.core.plan.CalendarEvent
import il.transit.planner.data.CalendarReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.builtins.ListSerializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * "From my calendar" (#25) when the calendar gives nothing to work with: the permission
 * refused, no events, all-day events only, and a place the geocoder only roughly matches.
 * Plain JVM (no Paparazzi): the dialogs themselves are in PanelsTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private class FakeCalendar(var granted: Boolean, private val events: List<CalendarEvent>) : CalendarReader {
        var reads = 0
        override fun hasPermission() = granted
        override suspend fun upcoming(now: Instant): List<CalendarEvent> {
            reads++
            return if (granted) events else emptyList()
        }
    }

    /** Only geocode answers (a recorded one); anything else would be a bug in the flow. */
    private class GeocodeOnly(private val answer: () -> List<GeocodeMatch>) : TransitApi {
        val geocoded = mutableListOf<String>()
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
            geocoded += text
            return answer()
        }
        override suspend fun plan(req: PlanRequest): PlanResponse = error("plan")
        override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean): List<Int?> = error("oneToMany")
        override suspend fun stops(box: BBox, modes: Set<String>?, language: String): List<Place> = error("stops")
        override suspend fun reverseGeocode(at: LatLon, language: String, max: Int): List<GeocodeMatch> = error("reverseGeocode")
        override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String): StopTimesResponse = error("stopTimes")
        override suspend fun trip(tripId: String, language: String): Itinerary = error("trip")
        override suspend fun mapTrips(box: BBox, start: Instant, end: Instant, zoom: Double, language: String): List<TripSegment> = error("mapTrips")
    }

    private val noNetwork = GeocodeOnly { error("no request expected") }
    private val inTwoHours = Instant.now().plusSeconds(7200)
    private val dentist = CalendarEvent("Dentist", "הרצל 12, באר שבע", inTwoHours, inTwoHours.plusSeconds(1800))

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    @Test fun `permission refused - the rationale, then nothing read and the chip keeps working by typing`() {
        val cal = FakeCalendar(granted = false, events = listOf(dentist))
        val vm = CalendarViewModel(noNetwork, cal, "he")
        vm.onChip()
        assertTrue(vm.state.value.rationale)
        assertNull(vm.state.value.events)
        vm.onPermission(granted = false)
        val s = vm.state.value
        assertFalse(s.rationale)
        assertTrue(s.refused)
        assertNull(s.events)
        assertEquals(0, cal.reads)
        // Asked again later: the rationale again, never a silent read.
        vm.dismiss()
        vm.onChip()
        assertTrue(vm.state.value.rationale)
        assertEquals(0, cal.reads)
    }

    @Test fun `permission granted at the prompt reads the calendar once`() {
        val cal = FakeCalendar(granted = false, events = listOf(dentist))
        val vm = CalendarViewModel(noNetwork, cal, "he")
        vm.onChip()
        cal.granted = true
        vm.onPermission(granted = true)
        assertEquals(listOf(dentist), vm.state.value.events)
        assertEquals(1, cal.reads)
    }

    @Test fun `no events - the list opens empty`() {
        val vm = CalendarViewModel(noNetwork, FakeCalendar(granted = true, events = emptyList()), "he")
        vm.onChip()
        assertEquals(emptyList<CalendarEvent>(), vm.state.value.events)
    }

    @Test fun `all-day events only - the list opens empty`() {
        val today = Instant.now().plusSeconds(600)
        val events = listOf(
            CalendarEvent("Holiday", "", today, today.plusSeconds(86_400), allDay = true),
            CalendarEvent("Conference", "Expo Tel Aviv", today, today.plusSeconds(86_400), allDay = true),
        )
        val vm = CalendarViewModel(noNetwork, FakeCalendar(granted = true, events = events), "he")
        vm.onChip()
        assertEquals(emptyList<CalendarEvent>(), vm.state.value.events)
    }

    @Test fun `an approximate match is shown for a check, with the event's own text, before anything is planned`() {
        val recorded = MotisJson.decodeFromString(
            ListSerializer(GeocodeMatch.serializer()),
            javaClass.getResource("/fixtures/geocode_calendar_herzl_he.json")!!.readText(),
        )
        val api = GeocodeOnly { recorded }
        val vm = CalendarViewModel(api, FakeCalendar(granted = true, events = listOf(dentist)), "he")
        var notFound = false
        vm.pick(dentist, near = null, bufferMin = 10) { _, _, _ -> notFound = true }
        val confirm = vm.state.value.confirm
        assertNotNull(confirm)
        confirm!!
        assertFalse(vm.state.value.resolving)
        assertFalse(notFound)
        assertTrue(confirm.dest.approximate)
        assertEquals("הרצל 126", confirm.dest.name) // recorded 2026-10-04: 12 came back as 126
        assertEquals("הרצל 12, באר שבע", confirm.dest.source)
        assertEquals(inTwoHours.minusSeconds(600), confirm.arriveBy)
        assertEquals(listOf("הרצל 12, באר שבע"), api.geocoded) // the place only, never the title
    }

    @Test fun `a failed lookup falls back to typing the place, with the arrive-by kept`() {
        val vm = CalendarViewModel(GeocodeOnly { throw java.io.IOException("offline") }, FakeCalendar(true, listOf(dentist)), "he")
        var typed: Pair<String, Instant?>? = null
        vm.pick(dentist, near = null, bufferMin = 10) { place, _, arriveBy -> typed = place to arriveBy }
        assertNull(vm.state.value.confirm)
        assertFalse(vm.state.value.resolving)
        assertEquals("הרצל 12, באר שבע" to inTwoHours.minusSeconds(600), typed)
    }
}
