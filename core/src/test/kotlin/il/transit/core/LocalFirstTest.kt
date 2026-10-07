package il.transit.core

import il.transit.core.api.GeocodeMatch
import il.transit.core.geo.LatLon
import il.transit.core.search.LocalFirst
import il.transit.core.search.LocalHit.Kind
import il.transit.core.search.PinName
import il.transit.core.search.Recents
import il.transit.core.user.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pick 20: saved places (Home first) and recent picks above server answers, 0 requests. */
class LocalFirstTest {
    private val home = SavedPlace("בית", 32.1893, 34.8668)
    private val work = SavedPlace("עבודה - טבנקין", 32.08, 34.78)
    private val uni = SavedPlace("אוניברסיטה", 31.262, 34.801)
    private val saved = listOf(work, uni, home)

    @Test fun `blank text - Home, then saved places, then recent picks`() {
        val recent = SavedPlace("קניון רעננה", 32.18, 34.87)
        val hits = LocalFirst.suggest("", saved, "בית", listOf(recent))
        assertEquals(listOf(home, work, uni, recent), hits.map { it.place })
        assertEquals(listOf(Kind.HOME, Kind.SAVED, Kind.SAVED, Kind.RECENT), hits.map { it.kind })
    }

    @Test fun `typing keeps only matches, every word counts, Home still first`() {
        val homeTabenkin = SavedPlace("בית טבנקין", 32.1893, 34.8668)
        val places = listOf(work, homeTabenkin)
        val hits = LocalFirst.suggest("טבנקין", places, "בית טבנקין", emptyList())
        assertEquals(listOf(homeTabenkin, work), hits.map { it.place })
        assertEquals(listOf(work), LocalFirst.suggest("עבודה טבנקין", places, "בית טבנקין", emptyList()).map { it.place })
        assertTrue(LocalFirst.suggest("הרצל", places, null, emptyList()).isEmpty())
    }

    @Test fun `quote marks, case and hyphens don't matter`() {
        assertTrue(LocalFirst.matches("צהל", "רחוב צה״ל 5"))
        assertTrue(LocalFirst.matches("TEL AVIV", "Tel-Aviv Savidor"))
    }

    @Test fun `a recent pick that is a saved place shows once, as saved`() {
        val again = SavedPlace("בית", 32.1894, 34.8669)
        val hits = LocalFirst.suggest("בי", saved, "בית", listOf(again))
        assertEquals(listOf(Kind.HOME), hits.map { it.kind })
    }

    @Test fun `at most four recent picks`() {
        val many = (1..9).map { SavedPlace("מקום $it", 32.0 + it / 100.0, 34.8) }
        assertEquals(LocalFirst.MAX_RECENT_SHOWN, LocalFirst.suggest("מקום", emptyList(), null, many).size)
    }

    @Test fun `server answers that a local hit shows already are dropped`() {
        val local = LocalFirst.suggest("", listOf(home), "בית", emptyList())
        val dup = GeocodeMatch("ADDRESS", "בית", "a", 32.1894, 34.8668)
        val far = GeocodeMatch("ADDRESS", "בית", "b", 31.0, 34.8)
        val other = GeocodeMatch("ADDRESS", "טבנקין 15", "c", 32.1893, 34.8668)
        assertEquals(listOf(far, other), LocalFirst.serverAfter(local, listOf(dup, far, other)))
    }

    @Test fun `recent picks - newest first, a repeat moves up, ten kept`() {
        var r = emptyList<SavedPlace>()
        (1..12).forEach { r = Recents.add(r, SavedPlace("מקום $it", 32.0 + it / 100.0, 34.8)) }
        assertEquals(Recents.MAX, r.size)
        assertEquals("מקום 12", r.first().name)
        r = Recents.add(r, SavedPlace("מקום 5", 32.05, 34.8))
        assertEquals("מקום 5", r.first().name)
        assertEquals(1, r.count { it.name == "מקום 5" })
    }

    @Test fun `a pin is named by the nearest answer, else by its coordinates`() {
        assertEquals("טבנקין 15", PinName.of(listOf(GeocodeMatch("ADDRESS", "טבנקין 15", "a", 32.18, 34.87))))
        assertNull(PinName.of(emptyList()))
        assertEquals("32.1801, 34.8712", PinName.coords(LatLon(32.18014, 34.87116)))
    }
}
