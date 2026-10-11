package il.transit.core

import il.transit.core.api.GeocodeArea
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.MotisJson
import il.transit.core.search.PlaceSearch
import il.transit.core.search.QueryText
import il.transit.core.search.Towns
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner, 2026-10-07: "no address left undefined or wrong". Query text, typed towns, wrong-town filter. */
class TownsTest {
    private fun transitous(name: String): List<GeocodeMatch> =
        MotisJson.decodeFromString(ListSerializer(GeocodeMatch.serializer()), javaClass.getResource("/fixtures/$name.json")!!.readText())

    private fun typed(q: String) = Towns.typedIn(q).map { it.name }

    // --- abbreviations and street words --------------------------------------------------

    @Test fun `town abbreviations with gershayim are spelled out, plain words are not`() {
        assertEquals("אבן גבירול 30 תל אביב", QueryText.expand("אבן גבירול 30 ת\"א"))
        assertEquals("אבן גבירול 30 תל אביב", QueryText.expand("אבן גבירול 30 ת״א"))
        assertEquals("שדרות רגר 1 באר שבע", QueryText.expand("שד' רגר 1 ב\"ש"))
        assertEquals("שדרות רגר באר שבע", QueryText.expand("שד׳ רגר ב״ש"))
        assertEquals("ז'בוטינסקי 100 פתח תקווה", QueryText.expand("ז'בוטינסקי 100 פ\"ת"))
        assertEquals("ויצמן 50 כפר סבא", QueryText.expand("ויצמן 50 כ\"ס"))
        assertEquals("יפו 97 ירושלים", QueryText.expand("יפו 97 י-ם"))
        // No gershayim: words, kept as typed ("תא" is a cell, "ים" the sea).
        assertEquals("חוף הים תא", QueryText.expand("חוף הים תא"))
    }

    @Test fun `רחוב and רח' are dropped, the rest kept, commas too`() {
        assertEquals("טבנקין 15 רעננה", QueryText.expand("רח' טבנקין 15 רעננה"))
        assertEquals("טבנקין 15 רעננה", QueryText.expand("רחוב טבנקין 15 רעננה"))
        assertEquals("טבנקין 15, רעננה", QueryText.expand("רח׳ טבנקין 15, רעננה"))
        assertEquals("רחוב", QueryText.expand("רחוב")) // alone, it is what was typed
        assertEquals("Herzl St 12, Haifa", QueryText.expand("Herzl St 12, Haifa"))
    }

    // --- which town the text names -------------------------------------------------------

    @Test fun `the town at the end, in Hebrew, English or abbreviated`() {
        assertEquals(listOf("רעננה"), typed("טבנקין 15 רעננה"))
        assertEquals(listOf("רעננה"), typed("טבנקין 15, רעננה"))
        assertEquals(listOf("רעננה"), typed("Tabenkin 15, Ra'anana"))
        assertEquals(listOf("תל אביב-יפו"), typed("אבן גבירול 30 ת\"א"))
        assertEquals(listOf("תל אביב-יפו"), typed("Rothschild Blvd 10, Tel Aviv"))
        assertEquals(listOf("באר שבע"), typed("שד' רגר 1 ב\"ש"))
        assertEquals(listOf("פתח תקווה"), typed("ז׳בוטינסקי 100 פ\"ת"))
        assertEquals(listOf("מודיעין-מכבים-רעות"), typed("קניון עזריאלי מודיעין"))
        assertEquals(listOf("ראשון לציון"), typed("רוטשילד 5 ראשון לציון")) // two words
    }

    @Test fun `the town first, only when a comma follows it`() {
        assertEquals(listOf("רעננה"), typed("רעננה, טבנקין 15"))
        assertTrue(typed("יבנה 5").isEmpty()) // Yavne Street, not the town of Yavne
    }

    @Test fun `a street named after a town is not a town`() {
        assertTrue(typed("שדרות ירושלים").isEmpty())
        assertTrue(typed("רחוב חיפה 5").isEmpty())
        assertTrue(typed("ירושלים 5").isEmpty())
        assertEquals(listOf("ירושלים"), typed("דרך חברון 50 ירושלים"))
        assertTrue(typed("טבנקין").isEmpty())
        assertEquals(listOf("רעננה"), typed("רעננה")) // only the town: it is the town
    }

    // --- an answer is in the typed town or not -------------------------------------------

    @Test fun `in the town by its own town name, else by distance`() {
        val raanana = Towns.named("רעננה")!!
        val kfarSaba = Towns.named("Kfar Saba")!!
        // Recorded answers carry their town: every one of them is in Ra'anana.
        transitous("geocode_tabenkin_raanana").forEach { assertTrue(it.name, Towns.holds(raanana, it)) }
        // Named Kfar Saba, though 2 km from Ra'anana's centre: not Ra'anana.
        val border = GeocodeMatch("ADDRESS", "ויצמן 1", "x", 32.1780, 34.8890, areas = listOf(GeocodeArea("כפר סבא", 8.0)))
        assertFalse(Towns.holds(raanana, border))
        assertTrue(Towns.holds(kfarSaba, border))
        // English town name (the phone's Geocoder in English).
        assertTrue(Towns.holds(raanana, border.copy(areas = listOf(GeocodeArea("Ra'anana", 8.0)))))
        // No town named: by distance.
        assertTrue(Towns.holds(raanana, border.copy(areas = emptyList(), lat = 32.188, lon = 34.8605)))
        assertFalse(Towns.holds(raanana, border.copy(areas = emptyList(), lat = 32.116, lon = 34.8276)))
    }

    @Test fun `every town in the table is in Israel's box and has a name in both languages`() {
        assertTrue(Towns.ALL.size >= 95)
        Towns.ALL.forEach { t ->
            assertTrue(t.name, t.lat in 29.45..33.35 && t.lon in 34.2..35.95)
            assertTrue(t.name, t.names.any { n -> n.any { it in 'א'..'ת' } } && t.names.any { n -> n.any { it in 'A'..'z' } })
        }
        listOf("טבנקין 15 רעננה" to "רעננה", "x Tel Aviv" to "תל אביב-יפו").forEach { (q, town) ->
            assertNotNull(q, Towns.typedIn(q).firstOrNull()?.takeIf { it.name == town })
        }
    }

    // --- through the search --------------------------------------------------------------

    @Test fun `a typed town drops Transitous answers from other towns too, and biases the request`() = runTest {
        val haifa = transitous("geocode_herzl_haifa") // the first answer is in Be'er Sheva
        var asked: il.transit.core.geo.LatLon? = null
        val api = object : il.transit.core.api.TransitApi by FakeTransitApi() {
            override suspend fun geocode(text: String, language: String, near: il.transit.core.geo.LatLon?, max: Int): List<GeocodeMatch> {
                asked = near
                return haifa
            }
        }
        val answer = PlaceSearch(api, null).search("הרצל חיפה", "he", il.transit.core.geo.LatLon(31.25, 34.79))
        assertEquals("חיפה", answer.town?.name)
        assertEquals(Towns.named("חיפה")!!.centre, asked)
        assertTrue(answer.primary.isNotEmpty())
        assertTrue(answer.primary.all { il.transit.core.present.geocodeTown(it) == "חיפה" })
        assertFalse(answer.notFound)
    }

    @Test fun `nothing in the typed town anywhere is a clear not-found, never another town`() = runTest {
        val api = FakeTransitApi().apply { onGeocode = { transitous("geocode_herzl_haifa") } } // Haifa + Be'er Sheva
        val photon = object : il.transit.core.search.BackupGeocoder {
            var calls = 0
            override suspend fun search(text: String, language: String, near: il.transit.core.geo.LatLon?, max: Int) =
                il.transit.core.search.parsePhoton(javaClass.getResource("/fixtures/photon_tabenkin_raanana.json")!!.readText()).also { calls++ }
        }
        val answer = PlaceSearch(api, photon).search("הרצל 12 רעננה", "he", null)
        assertEquals(1, photon.calls) // no answer in Ra'anana: a miss
        assertTrue(answer.notFound)
    }
}
