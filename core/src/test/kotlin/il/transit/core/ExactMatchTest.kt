package il.transit.core

import il.transit.core.api.GeocodeArea
import il.transit.core.api.GeocodeMatch
import il.transit.core.search.Aliases
import il.transit.core.search.PlaceSearch
import il.transit.core.search.SearchAnswer
import il.transit.core.search.Towns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner, 2026-10-07: never a wrong place as the answer. Exact vs closest, and everyday names. */
class ExactMatchTest {
    private fun at(name: String, street: String? = null, number: String? = null, town: String? = null) =
        GeocodeMatch("ADDRESS", name, name, 32.0, 34.8, street = street, houseNumber = number,
            areas = listOfNotNull(town?.let { GeocodeArea(it, 8.0) }))

    @Test fun `exact - the street typed, one letter off allowed, and the house number`() {
        val haifa = Towns.named("חיפה")
        assertTrue(PlaceSearch.exact("טבנקין 15 רעננה", at("טבנקין 15", "טבנקין", "15", "רעננה"), Towns.named("רעננה")))
        assertTrue(PlaceSearch.exact("טבנקן 15 רעננה", at("טבנקין 15", "טבנקין", "15", "רעננה"), Towns.named("רעננה")))
        assertTrue(PlaceSearch.exact("דיזינגוף 50 תל אביב", at("דיזנגוף 50", "דיזנגוף", "50"), Towns.named("תל אביב")))
        assertTrue(PlaceSearch.exact("Rothschild Blvd 10, Tel Aviv", at("Rothschild Boulevard 10", number = "10"), Towns.named("Tel Aviv")))
        assertTrue(PlaceSearch.exact("ביאליק 12א רמת גן", at("ביאליק 12", "ביאליק", "12"), Towns.named("רמת גן")))
        // Live, 2026-10-07: another street with the same number is not the answer.
        assertFalse(PlaceSearch.exact("שדרות הנשיא 100 חיפה", at("שדרות המגינים 100", "שדרות המגינים", "100", "חיפה"), haifa))
        assertFalse(PlaceSearch.exact("רוטשילד 1", at("רוטשילד 114", "רוטשילד", "114")))
        assertFalse(PlaceSearch.exact("טבנקין 15 רעננה", at("סירקין", "סירקין", null, "רעננה"), Towns.named("רעננה")))
    }

    @Test fun `kind-of-place words don't count, the name does`() {
        assertFalse(PlaceSearch.exact("בית חולים איכילוב", at("בית חולים זיו")))
        assertTrue(PlaceSearch.exact("תחנה מרכזית חדשה תל אביב", at("ת.מרכזית תל אביב קומה 7/רציפים"), Towns.named("תל אביב")))
        assertTrue(PlaceSearch.exact("התחנה המרכזית ירושלים", at("התחנה המרכזית"), Towns.named("ירושלים")))
        assertTrue(PlaceSearch.exact("אוניברסיטת בן גוריון", at("אוניברסיטת בן גוריון/יצחק רגר")))
    }

    @Test fun `no exact answer - not found, and the rest are labelled closest, never the answer`() {
        val wrong = at("שדרות המגינים 100", "שדרות המגינים", "100", "חיפה")
        val answer = SearchAnswer(listOf(wrong), town = Towns.named("חיפה"), query = "שדרות הנשיא 100 חיפה")
        assertTrue(answer.notFound)
        assertTrue(answer.exact.isEmpty())
        assertEquals(listOf(wrong), answer.closest)
        // An exact one goes before closer-looking guesses, from any source.
        val right = at("שדרות הנשיא 100", "שדרות הנשיא", "100", "חיפה")
        val both = SearchAnswer(listOf(wrong), listOf(right), Towns.named("חיפה"), missed = false, query = "שדרות הנשיא 100 חיפה")
        assertEquals(listOf(right, wrong), both.all)
        assertFalse(both.notFound)
    }

    @Test fun `everyday names are searched as the map name, with the town`() {
        assertEquals("איכילוב תל אביב", Aliases.rewrite("בית חולים איכילוב"))
        assertEquals("איכילוב תל אביב", Aliases.rewrite("איכילוב"))
        assertEquals("Ichilov Hospital, Tel Aviv", Aliases.rewrite("Ichilov hospital"))
        // The name is what makes it exact: the Sourasky *library* (live, 2026-10-07) is not Ichilov.
        val library = GeocodeMatch("PLACE", "הספרייה המרכזית ע\"ש סוראסקי", "l", 32.113, 34.804, areas = listOf(GeocodeArea("תל אביב-יפו", 8.0)))
        val hospital = GeocodeMatch("PLACE", "בי\"ח איכילוב", "h", 32.0812, 34.789, areas = listOf(GeocodeArea("תל אביב-יפו", 8.0)))
        val q = Aliases.rewrite("איכילוב")
        assertFalse(PlaceSearch.exact(q, library, Towns.typedIn(q).first()))
        assertTrue(PlaceSearch.exact(q, hospital, Towns.typedIn(q).first()))
        assertEquals("המרכז הרפואי שיבא רמת גן", Aliases.rewrite("בית חולים תל השומר"))
        assertEquals("הקריה הרפואית רמב\"ם חיפה", Aliases.rewrite("בית חולים רמב\"ם חיפה"))
        // The town the alias names is then the typed town: other towns' hospitals are dropped.
        assertEquals("תל אביב-יפו", Towns.typedIn(Aliases.rewrite("איכילוב")).single().name)
        // Not an alias: left alone (a street, an address, an unknown place).
        assertEquals("טבנקין 15 רעננה", Aliases.rewrite("טבנקין 15 רעננה"))
        assertEquals("איכילוב 5", Aliases.rewrite("איכילוב 5"))
        assertEquals("תל השומר", Aliases.rewrite("תל השומר")) // the neighbourhood, not the hospital
    }
}
