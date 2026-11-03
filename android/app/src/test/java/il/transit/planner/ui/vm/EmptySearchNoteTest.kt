package il.transit.planner.ui.vm

import il.transit.core.api.GeocodeMatch
import il.transit.core.api.TransitApi
import il.transit.core.geo.LatLon
import il.transit.core.search.BackupGeocoder
import il.transit.core.search.SearchTrace
import il.transit.planner.data.UserStore
import il.transit.planner.ui.Field
import il.transit.planner.ui.MainViewModel
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Phase 11 F11 (bug bash A4): the line Settings → Crash log keeps for the last search that found nothing. A pick never
 * writes "closed before the answer"; closing the box while the search runs does. A Transitous failure that leaves only
 * a bundled station shows it and still says "No connection".
 */
class EmptySearchNoteTest : VmTest() {
    private val notes = mutableListOf<Pair<String, Boolean>>()

    private val town = GeocodeMatch("PLACE", "Be'er Sheva", "town", 31.2457, 34.7925)

    private inner class TownOnly : TransitApi by replay() {
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> = listOf(town)
    }

    private inner class Down : TransitApi by replay() {
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> = throw IOException("unreachable")
    }

    private class SlowPhone(val ms: Long) : BackupGeocoder {
        override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
            delay(ms)
            return emptyList()
        }
    }

    private fun vm(api: TransitApi, phone: BackupGeocoder? = SlowPhone(1_500)): MainViewModel =
        track(MainViewModel(api, UserStore(MemoryPrefs()), { "en" }, clock = CLOCK, backup = phone, noteEmptySearch = { l, c -> notes += l to c }))
            .also { it.locationProvider = { null } }

    @Test fun `a pick while the search runs writes no line, then or later`() {
        val vm = vm(TownOnly())
        vm.startEditing(Field.TO)
        vm.onQuery("Herzl 12 Be'er Sheva")
        advance(400) // Transitous's interim answer; the phone is still asked
        val first = vm.state.value.suggestions[0]
        vm.pick(first)
        advance(2_000)
        vm.startEditing(Field.FROM)
        vm.cancelEditing()
        assertEquals(emptyList<Pair<String, Boolean>>(), notes)
    }

    @Test fun `closing the box while the search runs writes a closed line`() {
        val vm = vm(TownOnly())
        vm.startEditing(Field.TO)
        vm.onQuery("Herzl 12 Be'er Sheva")
        advance(400)
        vm.cancelEditing()
        assertEquals(1, notes.size)
        assertTrue(notes[0].second)
        assertTrue(notes[0].first, SearchTrace.CLOSED in notes[0].first)
    }

    @Test fun `no exact answer writes a not found line`() {
        val vm = vm(TownOnly())
        vm.startEditing(Field.TO)
        vm.onQuery("Herzl 12 Be'er Sheva")
        advance(2_500)
        assertEquals(1, notes.size)
        assertFalse(notes[0].second)
        assertTrue(notes[0].first, "not found" in notes[0].first && "fix no" in notes[0].first)
    }

    @Test fun `Transitous down - the bundled station shows with No connection`() {
        val vm = vm(Down(), phone = null)
        vm.startEditing(Field.TO)
        vm.onQuery("Rehovot railway station")
        advance(400)
        val s = vm.state.value
        assertTrue("the station row: ${s.suggestions}", s.suggestions.any { !it.closest && !it.saved && !it.recent })
        assertTrue("No connection shows", s.places.offline)
        assertFalse(s.places.notFound)
    }
}
