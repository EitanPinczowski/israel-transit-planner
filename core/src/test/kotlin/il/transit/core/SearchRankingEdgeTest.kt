package il.transit.core

import il.transit.core.api.GeocodeMatch
import il.transit.core.search.LocalFirst
import il.transit.core.search.LocalHit.Kind
import il.transit.core.search.Recents
import il.transit.core.user.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 11 T9: ranking edge cases of what the phone shows before the server answers (`LocalFirst`, `Recents`),
 * 0 requests. Each test pins what the code does today, including the limits (noted), so a change is a decision.
 */
class SearchRankingEdgeTest {
    private fun match(name: String, lat: Double, lon: Double, id: String = name) = GeocodeMatch("ADDRESS", name, id, lat, lon)

    // -- a saved place against a server hit with the same name -----------------------------------------

    @Test fun `a server hit with a saved place's name and spot is dropped, the saved place stays first`() {
        val home = SavedPlace("בית", 32.1893, 34.8668)
        val local = LocalFirst.suggest("בית", listOf(home), "בית", emptyList())
        val server = listOf(match("בית", 32.18935, 34.86685, "near"), match("בית ספר", 32.2, 34.9, "other"))
        assertEquals(listOf("other"), LocalFirst.serverAfter(local, server).map { it.id })
    }

    @Test fun `the same name 3 km away is a different place so both are offered, the saved one first`() {
        val work = SavedPlace("עבודה", 32.08, 34.78)
        val local = LocalFirst.suggest("עבודה", listOf(work), null, emptyList())
        val farSameName = match("עבודה", 32.11, 34.78, "far")
        assertEquals(listOf(work), local.map { it.place })
        assertEquals(listOf("far"), LocalFirst.serverAfter(local, listOf(farSameName)).map { it.id })
    }

    @Test fun `the name match ignores case, quotes and hyphens but the spot must be within 100 m`() {
        val place = SavedPlace("Tel-Aviv Savidor", 32.0840, 34.7980)
        val local = LocalFirst.suggest("", listOf(place), null, emptyList())
        assertTrue(LocalFirst.serverAfter(local, listOf(match("TEL AVIV SAVIDOR", 32.0841, 34.7981))).isEmpty())
        assertEquals(1, LocalFirst.serverAfter(local, listOf(match("TEL AVIV SAVIDOR", 32.0860, 34.7980))).size)
    }

    @Test fun `Home is first even when a saved place is listed before it and also matches`() {
        val a = SavedPlace("טבנקין עבודה", 32.08, 34.78)
        val h = SavedPlace("טבנקין בית", 32.1893, 34.8668)
        val hits = LocalFirst.suggest("טבנקין", listOf(a, h), "טבנקין בית", emptyList())
        assertEquals(listOf(Kind.HOME, Kind.SAVED), hits.map { it.kind })
        assertEquals(listOf(h, a), hits.map { it.place })
    }

    // -- recents: order and dedupe ---------------------------------------------------------------------

    @Test fun `recents are newest first`() {
        val a = SavedPlace("א", 32.0, 34.8)
        val b = SavedPlace("ב", 32.1, 34.8)
        val c = SavedPlace("ג", 32.2, 34.8)
        assertEquals(listOf(c, b, a), Recents.add(Recents.add(Recents.add(emptyList(), a), b), c))
    }

    @Test fun `picking a recent again moves it to the top and keeps one copy`() {
        val a = SavedPlace("קניון רעננה", 32.18, 34.87)
        val b = SavedPlace("הטכניון", 32.777, 35.022)
        val again = SavedPlace("קניון רעננה", 32.1801, 34.8701) // ~14 m away
        val list = Recents.add(Recents.add(emptyList(), a), b)
        assertEquals(listOf(again, b), Recents.add(list, again))
    }

    @Test fun `the same name in two towns is two recents`() {
        val bs = SavedPlace("הרצל 10", 31.25, 34.79)
        val ra = SavedPlace("הרצל 10", 32.18, 34.87)
        assertEquals(listOf(ra, bs), Recents.add(listOf(bs), ra))
    }

    @Test fun `the list is capped and the oldest falls off`() {
        var list = emptyList<SavedPlace>()
        for (i in 1..(Recents.MAX + 3)) list = Recents.add(list, SavedPlace("מקום $i", 32.0 + i / 100.0, 34.8))
        assertEquals(Recents.MAX, list.size)
        assertEquals("מקום ${Recents.MAX + 3}", list.first().name)
        assertFalse(list.any { it.name == "מקום 1" })
    }

    @Test fun `only four recents show, the newest four, and typed text filters before the cap`() {
        val recents = (1..8).map { SavedPlace("קפה $it", 32.0 + it / 100.0, 34.8) } + SavedPlace("מסעדה", 32.5, 34.8)
        val shown = LocalFirst.suggest("קפה", emptyList(), null, recents)
        assertEquals(listOf("קפה 1", "קפה 2", "קפה 3", "קפה 4"), shown.map { it.place.name })
        assertEquals(listOf("מסעדה"), LocalFirst.suggest("מסעדה", emptyList(), null, recents).map { it.place.name })
    }

    // -- prefix against whole word ---------------------------------------------------------------------

    @Test fun `a typed prefix matches while typing`() {
        assertTrue(LocalFirst.matches("טבנ", "טבנקין 15"))
        assertTrue(LocalFirst.matches("tab", "Tabenkin 15"))
    }

    @Test fun `every typed word must appear, in any order`() {
        assertTrue(LocalFirst.matches("15 טבנקין", "טבנקין 15 רעננה"))
        assertFalse(LocalFirst.matches("טבנקין 16", "טבנקין 15 רעננה"))
    }

    /** A limit, pinned: a word matches inside another word too ("קין" finds טבנקין); only the server's `exact` is whole-word. */
    @Test fun `a fragment inside a word matches locally`() {
        assertTrue(LocalFirst.matches("קין", "טבנקין 15"))
    }

    @Test fun `a comma or extra spaces between words change nothing`() {
        assertTrue(LocalFirst.matches("טבנקין,  רעננה", "טבנקין 15 רעננה"))
    }

    // -- mixed Hebrew and English ----------------------------------------------------------------------

    @Test fun `Hebrew and Latin words in one query both have to match`() {
        assertTrue(LocalFirst.matches("Tel אביב", "Tel אביב Savidor"))
        assertFalse(LocalFirst.matches("Tel רעננה", "Tel אביב Savidor"))
    }

    /** A limit, pinned: local hits do not transliterate, so a Latin query does not find a Hebrew-named saved place. */
    @Test fun `a Latin query does not find a Hebrew name locally`() {
        val home = SavedPlace("תל אביב", 32.08, 34.78)
        assertTrue(LocalFirst.suggest("Tel Aviv", listOf(home), null, emptyList()).isEmpty())
        assertEquals(listOf(home), LocalFirst.suggest("תל", listOf(home), null, emptyList()).map { it.place })
    }

    @Test fun `case does not matter in Latin names`() {
        assertTrue(LocalFirst.matches("HERZL", "Herzl 12"))
    }
}
