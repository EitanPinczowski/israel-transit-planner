package il.transit.core

import il.transit.core.api.GeocodeArea
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.MotisJson
import il.transit.core.search.MatchLevel
import il.transit.core.search.MatchNote
import il.transit.core.search.PlaceSearch
import il.transit.core.search.SearchAnswer
import il.transit.core.search.Towns
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 10 D2: each answer's level; only HOUSE and PLACE are the answer, as the old yes/no rule said. */
class MatchLevelTest {
    private fun at(name: String, street: String? = null, number: String? = null, town: String? = null) =
        GeocodeMatch("ADDRESS", name, name, 32.0, 34.8, street = street, houseNumber = number,
            areas = listOfNotNull(town?.let { GeocodeArea(it, 8.0) }))

    private fun place(name: String, town: String? = null, type: String = "PLACE", category: String? = null) =
        GeocodeMatch(type, name, name, 32.0, 34.8, category = category, areas = listOfNotNull(town?.let { GeocodeArea(it, 8.0) }))

    private fun level(q: String, m: GeocodeMatch) = PlaceSearch.level(q, m)

    @Test fun `the house typed, the street without it, the town only`() {
        assertEquals(MatchLevel.HOUSE, level("טבנקין 15 רעננה", at("טבנקין 15", "טבנקין", "15", "רעננה")))
        assertEquals(MatchLevel.STREET, level("טבנקין 15 רעננה", at("טבנקין 12", "טבנקין", "12", "רעננה")))
        assertEquals(MatchLevel.STREET, level("טבנקין 15 רעננה", at("טבנקין", "טבנקין", null, "רעננה")))
        assertEquals(MatchLevel.TOWN, level("טבנקין 15 רעננה", place("רעננה", category = "place_8")))
        assertEquals(MatchLevel.NONE, level("טבנקין 15 רעננה", at("סירקין", "סירקין", null, "רעננה")))
        // No number typed: the street itself is the address as typed.
        assertEquals(MatchLevel.HOUSE, level("שדרות רגר באר שבע", at("שדרות רגר", "שדרות רגר", null, "באר שבע")))
        // A lettered house: 12 is the street only.
        assertEquals(MatchLevel.STREET, level("ביאליק 12א רמת גן", at("ביאליק 12", "ביאליק", "12", "רמת גן")))
    }

    @Test fun `places, stops, and the town alone`() {
        assertEquals(MatchLevel.PLACE, level("רעננה", place("רעננה", category = "place_8")))
        assertEquals(MatchLevel.PLACE, level("קניון הנגב באר שבע", place("קניון הנגב", "באר שבע")))
        assertEquals(MatchLevel.PLACE, level("הרצל חיפה", place("הרצל/יואל", "חיפה", type = "STOP")))
        assertEquals(MatchLevel.NONE, level("הטכניון חיפה", place("אובליסק הטכניון", "חיפה")))
        // A stop on the street is not the house typed.
        assertEquals(MatchLevel.NONE, level("הרצל 12 חיפה", place("הרצל/יואל", "חיפה", type = "STOP")))
    }

    @Test fun `exact is HOUSE or PLACE, nothing else`() {
        MatchLevel.entries.forEach { assertEquals(it == MatchLevel.HOUSE || it == MatchLevel.PLACE, it.isAnswer) }
        val cases = listOf(
            "טבנקין 15 רעננה" to at("טבנקין 15", "טבנקין", "15", "רעננה"),
            "טבנקין 15 רעננה" to at("טבנקין 12", "טבנקין", "12", "רעננה"),
            "רעננה" to place("רעננה"),
            "יהודה 5 תל אביב" to at("בן יהודה 5", "בן יהודה", "5", "תל אביב-יפו"),
        )
        cases.forEach { (q, m) -> assertEquals(q, level(q, m).isAnswer, PlaceSearch.exact(q, m)) }
    }

    @Test fun `a Hebrew street with a number Transitous has only without it - street only, says which number`() {
        // Recorded 2026-10-04: "הרצל 12, באר שבע" → הרצל 126 first (same street, the number matched loosely).
        val found = MotisJson.decodeFromString(ListSerializer(GeocodeMatch.serializer()), javaClass.getResource("/fixtures/geocode_calendar_herzl_he.json")!!.readText())
        val q = "הרצל 12, באר שבע"
        val answer = SearchAnswer(found, town = Towns.typedIn(q).first(), query = q)
        val herzl126 = found.first { it.name.startsWith("הרצל 126") }
        assertEquals(MatchNote(MatchLevel.STREET, "12"), answer.note(herzl126))
        assertFalse(herzl126 in answer.exact)
        assertTrue(herzl126 in answer.closest)
    }

    @Test fun `the note names the typed house as typed`() {
        val a = SearchAnswer(listOf(at("רוטשילד 10", "רוטשילד", "10", "תל אביב-יפו")), town = Towns.named("תל אביב"), query = "רוטשילד 12-14 תל אביב")
        assertEquals(MatchNote(MatchLevel.STREET, "12–14"), a.note(a.primary[0]))
        val lettered = SearchAnswer(listOf(at("ביאליק 12", "ביאליק", "12")), town = Towns.named("רמת גן"), query = "ביאליק 12א רמת גן")
        assertEquals("12א", lettered.note(lettered.primary[0]).house)
        // Only a street-only row carries the number.
        val town = SearchAnswer(listOf(place("רמת גן")), town = Towns.named("רמת גן"), query = "ביאליק 12א רמת גן")
        assertEquals(MatchNote(MatchLevel.TOWN), town.note(town.primary[0]))
    }
}
