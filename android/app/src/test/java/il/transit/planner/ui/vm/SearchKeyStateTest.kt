package il.transit.planner.ui.vm

import il.transit.core.api.GeocodeArea
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.TransitApi
import il.transit.core.geo.LatLon
import il.transit.core.search.BackupGeocoder
import il.transit.planner.data.UserStore
import il.transit.planner.ui.Field
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.PlaceRef
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 11 F9 (owner's bug bash on the Pixel): "Herzl 12 Be'er Sheva" seemed not found. Transitous answers only the
 * town; Google's phone Geocoder answers "Herzl Street 12" (probe run 41599177172: 1.2-1.8 s for the whole search on
 * the emulator). Until it does, the list holds only the town, as a closest match. The keyboard's Search key picked
 * that first row. Now it never picks a closest match, and pressed while the search runs it waits for the answer.
 */
class SearchKeyStateTest : VmTest() {
    private val town = GeocodeMatch("PLACE", "Be'er Sheva", "town", 31.2457, 34.7925, areas = listOf(GeocodeArea("Be'er Sheva", 8.0)))
    private val herzl = GeocodeMatch(
        "ADDRESS",
        "Herzl Street 12",
        "android:31.238587,34.7872408",
        31.238587,
        34.7872408,
        street = "Herzl Street",
        houseNumber = "12",
        areas = listOf(GeocodeArea("Israel", 2.0), GeocodeArea("Be'er Sheva", 8.0, default = true)),
    )

    /** Transitous: only the town, as recorded for this text (T9, 2026-11-02). */
    private inner class TownOnly : TransitApi by replay() {
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> = listOf(town)
    }

    /** The phone's Geocoder, answering after [ms] (virtual time). */
    private class SlowPhone(val ms: Long, val answer: List<GeocodeMatch>) : BackupGeocoder {
        var calls = 0
        override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
            calls++
            delay(ms)
            return answer
        }
    }

    private fun vm(phone: BackupGeocoder): MainViewModel =
        track(MainViewModel(TownOnly(), UserStore(MemoryPrefs()), { "en" }, clock = CLOCK, backup = phone)).also { it.locationProvider = { fix } }

    @Test fun `Search while the phone Geocoder is asked waits for its answer and picks the street, not the town`() {
        val phone = SlowPhone(1_500, listOf(herzl))
        val vm = vm(phone)
        vm.startEditing(Field.TO)
        vm.onQuery("Herzl 12 Be'er Sheva")
        advance(400) // the typing pause, and Transitous's answer
        val interim = vm.state.value.suggestions
        assertTrue("the interim list is the town only, as a closest match: $interim", interim.isNotEmpty() && interim.all { it.closest })
        assertEquals(1, phone.calls)

        vm.searchAction() // the keyboard's Search, before the phone answered
        assertEquals("nothing picked yet", Field.TO, vm.state.value.editing)
        assertNull(vm.state.value.to as? PlaceRef.Point)

        advance(1_500)
        val to = vm.state.value.to as PlaceRef.Point
        assertEquals("Herzl Street 12", to.name)
        assertEquals(LatLon(herzl.lat, herzl.lon), to.at)
        assertNull(vm.state.value.editing)
        assertEquals(1, phone.calls)
    }

    @Test fun `Search with only closest matches picks nothing - the list says not found`() {
        val vm = vm(SlowPhone(100, emptyList()))
        vm.startEditing(Field.TO)
        vm.onQuery("Herzl 12 Be'er Sheva")
        advance(1_000)
        assertTrue(vm.state.value.places.notFound)
        vm.searchAction()
        assertEquals(Field.TO, vm.state.value.editing)
        assertNull(vm.state.value.to as? PlaceRef.Point)
    }

    @Test fun `Search after the answer picks the first answer at once`() {
        val vm = vm(SlowPhone(100, listOf(herzl)))
        vm.startEditing(Field.TO)
        vm.onQuery("Herzl 12 Be'er Sheva")
        advance(1_000)
        assertEquals("Herzl Street 12", vm.state.value.suggestions.first().name)
        vm.searchAction()
        assertEquals("Herzl Street 12", (vm.state.value.to as PlaceRef.Point).name)
    }
}
