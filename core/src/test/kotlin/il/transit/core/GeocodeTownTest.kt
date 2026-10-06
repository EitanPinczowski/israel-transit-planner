package il.transit.core

import il.transit.core.api.GeocodeArea
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.MotisJson
import il.transit.core.present.geocodeDetail
import il.transit.core.present.geocodeTown
import il.transit.core.present.rankByTypedTown
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeocodeTownTest {
    private fun fixture(name: String): List<GeocodeMatch> {
        val text = javaClass.getResource("/fixtures/$name.json")!!.readText()
        return MotisJson.decodeFromString(ListSerializer(GeocodeMatch.serializer()), text)
    }

    private fun match(name: String, vararg areas: GeocodeArea, street: String? = null, number: String? = null) =
        GeocodeMatch("ADDRESS", name, name, 32.0, 34.8, street = street, houseNumber = number, areas = areas.toList())

    private val israel = GeocodeArea("ישראל", 2.0)
    private val haifaDistrict = GeocodeArea("מחוז חיפה", 4.0, matched = true)

    @Test fun `every answer of a real search carries its town`() {
        // "הרצל חיפה" asked near Be'er Sheva (2026-10-05).
        val found = fixture("geocode_herzl_haifa")
        assertTrue(found.isNotEmpty())
        found.forEach { assertTrue(it.name, geocodeTown(it) != null) }
    }

    @Test fun `answers in the typed town come first`() {
        val found = fixture("geocode_herzl_haifa")
        assertEquals("באר שבע", geocodeTown(found.first())) // Transitous's own order: nearest first
        val ranked = rankByTypedTown(found)
        assertEquals("חיפה", geocodeTown(ranked.first()))
        assertEquals(found.toSet(), ranked.toSet())
        // Every Haifa answer now comes before the Be'er Sheva one.
        val bs = ranked.indexOfFirst { geocodeTown(it) == "באר שבע" }
        assertTrue(ranked.withIndex().filter { geocodeTown(it.value) == "חיפה" }.all { it.index < bs })
    }

    @Test fun `a matched district alone does not reorder`() {
        val hadera = match("הרצל 10", israel, haifaDistrict, GeocodeArea("חדרה", 8.0, default = true))
        val near = match("הרצל 12", israel, GeocodeArea("באר שבע", 8.0, default = true))
        assertEquals(listOf(near, hadera), rankByTypedTown(listOf(near, hadera)))
    }

    @Test fun `the town itself has no town line`() {
        val haifa = GeocodeMatch("PLACE", "חיפה", "r1", 32.8, 35.0, category = "place_6",
            areas = listOf(israel, GeocodeArea("חיפה", 8.0, matched = true, default = true)))
        assertNull(geocodeTown(haifa))
        assertNull(geocodeDetail(haifa))
    }

    @Test fun `detail joins street and town, without repeating the name`() {
        val town = GeocodeArea("זכרון יעקב", 8.0, default = true)
        assertEquals("זכרון יעקב", geocodeDetail(match("הרצל 10", israel, town, street = "הרצל", number = "10")))
        assertEquals("הרצל 10 · זכרון יעקב", geocodeDetail(match("בית הקפה", israel, town, street = "הרצל", number = "10")))
        assertNull(geocodeDetail(match("ישראל", israel)))
    }

    @Test fun `no level-8 area falls back to the default one`() {
        val m = match("הרצל 10", israel, haifaDistrict, GeocodeArea("נפת חדרה", 5.0, default = true))
        assertNull(geocodeTown(m)) // a sub-district is too vague to call a town
        val m2 = match("x", israel, GeocodeArea("מועצה אזורית", 6.0, default = true))
        assertEquals("מועצה אזורית", geocodeTown(m2))
    }
}
