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

    private fun place(name: String, town: String? = null, type: String = "PLACE", category: String? = null) =
        GeocodeMatch(type, name, name, 32.0, 34.8, category = category, areas = listOfNotNull(town?.let { GeocodeArea(it, 8.0) }))

    @Test fun `exact - the street typed, one letter off allowed, and the house number`() {
        val haifa = Towns.named("חיפה")
        assertTrue(PlaceSearch.exact("טבנקין 15 רעננה", at("טבנקין 15", "טבנקין", "15", "רעננה"), Towns.named("רעננה")))
        assertTrue(PlaceSearch.exact("טבנקן 15 רעננה", at("טבנקין 15", "טבנקין", "15", "רעננה"), Towns.named("רעננה")))
        assertTrue(PlaceSearch.exact("דיזינגוף 50 תל אביב", at("דיזנגוף 50", "דיזנגוף", "50"), Towns.named("תל אביב")))
        assertTrue(PlaceSearch.exact("Rothschild Blvd 10, Tel Aviv", at("Rothschild Boulevard 10", number = "10"), Towns.named("Tel Aviv")))
        // Review F12: a typed letter is that house; 12 or 12ב is not 12א.
        assertTrue(PlaceSearch.exact("ביאליק 12א רמת גן", at("ביאליק 12א", "ביאליק", "12א"), Towns.named("רמת גן")))
        assertFalse(PlaceSearch.exact("ביאליק 12א רמת גן", at("ביאליק 12", "ביאליק", "12"), Towns.named("רמת גן")))
        assertFalse(PlaceSearch.exact("ביאליק 12ב רמת גן", at("ביאליק 12א", "ביאליק", "12א"), Towns.named("רמת גן")))
        assertTrue(PlaceSearch.exact("ביאליק 12 רמת גן", at("ביאליק 12", "ביאליק", "12"), Towns.named("רמת גן")))
        // Live, 2026-10-07: another street with the same number is not the answer.
        assertFalse(PlaceSearch.exact("שדרות הנשיא 100 חיפה", at("שדרות המגינים 100", "שדרות המגינים", "100", "חיפה"), haifa))
        assertFalse(PlaceSearch.exact("רוטשילד 1", at("רוטשילד 114", "רוטשילד", "114")))
        assertFalse(PlaceSearch.exact("טבנקין 15 רעננה", at("סירקין", "סירקין", null, "רעננה"), Towns.named("רעננה")))
    }

    @Test fun `kind-of-place words don't count, the name does`() {
        assertFalse(PlaceSearch.exact("בית חולים איכילוב", at("בית חולים זיו")))
        assertTrue(PlaceSearch.exact("תחנה מרכזית חדשה תל אביב", place("ת.מרכזית תל אביב קומה 7/רציפים", type = "STOP"), Towns.named("תל אביב")))
        assertTrue(PlaceSearch.exact("התחנה המרכזית ירושלים", place("התחנה המרכזית"), Towns.named("ירושלים")))
        assertTrue(PlaceSearch.exact("אוניברסיטת בן גוריון", place("אוניברסיטת בן גוריון/יצחק רגר", type = "STOP")))
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

    // --- review 2026-10-11 (F1, F2, F3, F9, F11, F12) -----------------------------------------

    @Test fun `nothing left but the town or kind words is never any answer in that town`() {
        val tlv = Towns.named("תל אביב")
        // Live, run 37589447696: a shelter, an obelisk and an inn were taken as the answer.
        assertFalse(PlaceSearch.exact("התחנה המרכזית החדשה תל אביב", place("המקלט הגרעיני בתחנה המרכזית החדשה", "תל אביב-יפו"), tlv))
        assertFalse(PlaceSearch.exact("הטכניון חיפה", place("אובליסק הטכניון", "חיפה"), Towns.named("חיפה")))
        assertTrue(PlaceSearch.exact("הטכניון חיפה", place("הטכניון - מכון טכנולוגי לישראל", "חיפה"), Towns.named("חיפה")))
        assertFalse(PlaceSearch.exact("מצפה רמון", place("פונדק רמון", "מצפה רמון"), Towns.named("מצפה רמון")))
        assertTrue(PlaceSearch.exact("מצפה רמון", place("מצפה רמון", category = "place_8"), Towns.named("מצפה רמון")))
        // The town alone, a kind word alone, a number alone: never just any place in the town.
        val raanana = Towns.named("רעננה")
        assertFalse(PlaceSearch.exact("רעננה", at("אחוזה 100", "אחוזה", "100", "רעננה"), raanana))
        assertFalse(PlaceSearch.exact("קניון רעננה", at("אחוזה 100", "אחוזה", "100", "רעננה"), raanana))
        assertTrue(PlaceSearch.exact("קניון רעננה", place("קניון רננים", "רעננה"), raanana))
        assertFalse(PlaceSearch.exact("15 רעננה", at("טבנקין 15", "טבנקין", "15", "רעננה"), raanana))
        assertFalse(PlaceSearch.exact("תחנה מרכזית חיפה", at("הרצל 10", "הרצל", "10", "חיפה"), Towns.named("חיפה")))
        assertFalse(PlaceSearch.exact("בית חולים רעננה", place("בית ספר אביב", "רעננה"), raanana))
    }

    @Test fun `a street named after a town is that street, not any address in the town`() {
        assertFalse(PlaceSearch.exact("יפו 97", at("דיזנגוף 97", "דיזנגוף", "97", "תל אביב-יפו")))
        assertTrue(PlaceSearch.exact("יפו 97", at("יפו 97", "יפו", "97", "ירושלים")))
        assertFalse(PlaceSearch.exact("חיפה 10", at("הרצל 10", "הרצל", "10", "חיפה")))
        assertTrue(PlaceSearch.exact("חיפה 10", at("דרך חיפה 10", "דרך חיפה", "10", "תל אביב-יפו")))
        assertFalse(PlaceSearch.exact("ירושלים 5", at("יפו 5", "יפו", "5", "ירושלים")))
    }

    @Test fun `whole words - another street that contains the word is not it`() {
        val tlv = Towns.named("תל אביב")
        assertFalse(PlaceSearch.exact("יהודה 5 תל אביב", at("בן יהודה 5", "בן יהודה", "5", "תל אביב-יפו"), tlv))
        assertFalse(PlaceSearch.exact("יהודה 5 תל אביב", at("יהודה הלוי 5", "יהודה הלוי", "5", "תל אביב-יפו"), tlv))
        assertTrue(PlaceSearch.exact("בן יהודה 5 תל אביב", at("בן יהודה 5", "בן יהודה", "5", "תל אביב-יפו"), tlv))
        assertFalse(PlaceSearch.exact("דוד 12 חיפה", at("דוד המלך 12", "דוד המלך", "12", "חיפה"), Towns.named("חיפה")))
        assertFalse(PlaceSearch.exact("גבירול 3", at("גבירולית 3", "גבירולית", "3")))
        // A first name people leave out is still the street.
        assertTrue(PlaceSearch.exact("ויצמן 50 כפר סבא", at("חיים ויצמן 50", "חיים ויצמן", "50", "כפר סבא"), Towns.named("כפר סבא")))
        // A range is any house in it.
        assertTrue(PlaceSearch.exact("רוטשילד 12-14 תל אביב", at("רוטשילד 14", "רוטשילד", "14", "תל אביב-יפו"), tlv))
        assertFalse(PlaceSearch.exact("רוטשילד 12-14 תל אביב", at("רוטשילד 16", "רוטשילד", "16", "תל אביב-יפו"), tlv))
    }

    @Test fun `English typed, Hebrew answered - the same street`() {
        val tlv = Towns.named("Tel Aviv")
        assertTrue(PlaceSearch.exact("Rothschild Blvd 10, Tel Aviv", at("שדרות רוטשילד 10", "שדרות רוטשילד", "10", "תל אביב-יפו"), tlv))
        assertTrue(PlaceSearch.exact("Rothschild Boulevard 10, Tel Aviv", at("Rothschild Boulevard 10", "Rothschild Boulevard", "10"), tlv))
        assertTrue(PlaceSearch.exact("Dizengoff Center", place("דיזנגוף סנטר", "תל אביב-יפו")))
        assertTrue(PlaceSearch.exact("Tabenkin 15, Ra'anana", at("טבנקין 15", "טבנקין", "15", "רעננה"), Towns.named("רעננה")))
        assertTrue(PlaceSearch.exact("Herzl St 12, Haifa", at("הרצל 12", "הרצל", "12", "חיפה"), Towns.named("Haifa")))
        assertTrue(PlaceSearch.exact("Weizmann 50, Kfar Saba", at("ויצמן 50", "ויצמן", "50", "כפר סבא"), Towns.named("Kfar Saba")))
        assertFalse(PlaceSearch.exact("Rothschild Blvd 10, Tel Aviv", at("שדרות בן גוריון 10", "שדרות בן גוריון", "10", "תל אביב-יפו"), tlv))
        assertTrue(PlaceSearch.exact("Ichilov Hospital, Tel Aviv", place("בי\"ח איכילוב", "תל אביב-יפו"), tlv))
    }

    @Test fun `an alias never overrides another typed town - those are streets`() {
        assertEquals("ברזילי ירושלים", Aliases.rewrite("ברזילי ירושלים"))
        assertEquals("בילינסון תל אביב", Aliases.rewrite("בילינסון תל אביב"))
        assertEquals("וולפסון באר שבע", Aliases.rewrite("וולפסון באר שבע"))
        assertEquals("ברזילי אשקלון", Aliases.rewrite("ברזילי אשקלון"))
        assertEquals("איכילוב תל אביב", Aliases.rewrite("איכילוב תל אביב"))
        // Never the Sourasky library (live, 2026-10-07): the alias names the hospital.
        assertEquals("איכילוב תל אביב", Aliases.rewrite("סוראסקי"))
    }

    @Test fun `a town with ב-, ל-, מ- in front is the town`() {
        assertEquals("רעננה", Towns.typedIn("טבנקין 15 ברעננה").single().name)
        assertEquals("טבנקין 15 רעננה", il.transit.core.search.QueryText.expand("טבנקין 15 ברעננה"))
        assertEquals("ביאליק 5 תל אביב", il.transit.core.search.QueryText.expand("ביאליק 5 בתל אביב"))
        assertEquals("הרצל 3 חיפה", il.transit.core.search.QueryText.expand("הרצל 3 בחיפה"))
        assertTrue(PlaceSearch.exact("טבנקין 15 ברעננה", at("טבנקין 15", "טבנקין", "15", "רעננה"), Towns.named("רעננה")))
        // A town that starts with one of those letters stays as it is.
        assertEquals("סוקולוב 30 הרצליה", il.transit.core.search.QueryText.expand("סוקולוב 30 הרצליה"))
        assertEquals("הרצל 1 בת ים", il.transit.core.search.QueryText.expand("הרצל 1 בת ים"))
        assertEquals("הרצל 1 לוד", il.transit.core.search.QueryText.expand("הרצל 1 לוד"))
    }
}
