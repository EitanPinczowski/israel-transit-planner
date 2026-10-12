package il.transit.core

import il.transit.core.search.Aliases
import il.transit.core.search.QueryText
import il.transit.core.search.Towns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 10 D2: acronyms with ", ״, ', ׳ or no mark; an ambiguous one is left as typed. */
class AliasesTest {
    private val expected = mapOf(
        "ת?א" to "תל אביב", "ר?ג" to "רמת גן", "פ?ת" to "פתח תקווה", "ב?ש" to "באר שבע", "ק?ש" to "קריית שמונה",
        "ק?ג" to "קריית גת", "ר?ל" to "ראשון לציון", "ראשל?צ" to "ראשון לציון", "נס?צ" to "נס ציונה", "כ?ס" to "כפר סבא",
        "הוד?ש" to "הוד השרון", "ב?ב" to "בני ברק", "את?א" to "אוניברסיטת תל אביב", "בי?ח" to "בית חולים",
    )

    @Test fun `every acronym, with every mark`() {
        for ((form, full) in expected) for (mark in listOf("\"", "״", "'", "׳")) {
            assertEquals("$form $mark", "הרצל 5 $full", QueryText.expand("הרצל 5 ${form.replace("?", mark)}"))
        }
    }

    @Test fun `with no mark only where the letters are no other word`() {
        assertEquals("הרצל 5 ראשון לציון", QueryText.expand("הרצל 5 ראשלצ"))
        assertEquals("הרצל 5 נס ציונה", QueryText.expand("הרצל 5 נסצ"))
        assertEquals("הרצל 5 הוד השרון", QueryText.expand("הרצל 5 הודש"))
        assertEquals("בית חולים איכילוב", QueryText.expand("ביח איכילוב"))
        // "תא", "פת", "בב", "קש": words (a cell, bread, ...), never a town without the mark.
        for (w in listOf("תא", "פת", "בב", "קש", "רג", "כס", "ים", "אתא")) assertEquals(w, "הרצל 5 $w", QueryText.expand("הרצל 5 $w"))
    }

    @Test fun `an ambiguous acronym stays as typed`() {
        for (a in Aliases.AMBIGUOUS) assertNull(a, Aliases.acronym(a, marked = true, english = false))
        assertEquals("הרצל 5 ק\"א", QueryText.expand("הרצל 5 ק\"א")) // קריית אתא or קריית ארבע
        assertEquals("ביאליק 3 ק״ב", QueryText.expand("ביאליק 3 ק״ב"))
        // Words with a mark inside that are no acronym stay too.
        assertEquals("ז'בוטינסקי 100 פתח תקווה", QueryText.expand("ז'בוטינסקי 100 פ\"ת"))
        assertEquals("צה\"ל 5 תל אביב", QueryText.expand("צה\"ל 5 ת\"א"))
    }

    @Test fun `an acronym in English text is spelled out in English`() {
        assertEquals("Herzl 5, Tel Aviv", QueryText.expand("Herzl 5, ת\"א"))
        assertEquals("Dizengoff 50 Tel Aviv", QueryText.expand("Dizengoff 50 ת'א"))
        assertEquals(Towns.named("תל אביב"), Towns.typedIn(QueryText.expand("Herzl 5, ת\"א")).firstOrNull())
        // Latin letters are never read as an acronym ("TA", "BS").
        assertEquals("Herzl 5 TA", QueryText.expand("Herzl 5 TA"))
    }

    @Test fun `the hospital acronym reaches the hospital alias, the university its town`() {
        assertEquals("איכילוב תל אביב", Aliases.rewrite(QueryText.expand("בי\"ח איכילוב")))
        assertEquals(Towns.named("תל אביב"), Towns.typedIn(QueryText.expand("את\"א")).firstOrNull())
    }
}
