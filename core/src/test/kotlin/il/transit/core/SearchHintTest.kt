package il.transit.core

import il.transit.core.present.needsFullNameHint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHintTest {
    @Test fun `short word with no answer containing it asks for the full name`() {
        // What Transitous really answered for "רגר" near Be'er Sheva (2026-10-03).
        assertTrue(needsFullNameHint("רגר", listOf("הגר", "הגר", "רגבה", "רגואן")))
        assertTrue(needsFullNameHint(" רגר ", listOf("הגר")))
    }

    @Test fun `no hint when an answer contains the word`() {
        assertFalse(needsFullNameHint("רגר", listOf("הגר", "שדרות יצחק רגר")))
        assertFalse(needsFullNameHint("ben", listOf("Ben Gurion Airport"))) // case-insensitive
        assertFalse(needsFullNameHint("צהל", listOf("צה\"ל"))) // quote marks ignored
        assertFalse(needsFullNameHint("צהל", listOf("צה״ל")))
    }

    @Test fun `no hint for long, multi-word, one-letter or empty searches`() {
        assertFalse(needsFullNameHint("שדרות רגר", listOf("הגר")))
        assertFalse(needsFullNameHint("רוטשילד", listOf("הגר")))
        assertFalse(needsFullNameHint("ב", listOf("הגר")))
        assertFalse(needsFullNameHint("רגר", emptyList()))
    }
}
