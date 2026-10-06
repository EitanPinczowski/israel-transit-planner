package il.transit.core

import il.transit.core.user.Appearance
import il.transit.core.user.UserJson
import il.transit.core.user.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceTest {
    @Test fun `language, theme and tour survive a JSON round trip`() {
        val s = UserSettings(language = "en", theme = Appearance.DARK, tourSeen = true, lastTripAlert = true)
        assertEquals(s, UserJson.decodeSettings(UserJson.encodeSettings(s)))
        val system = UserSettings(language = null, theme = Appearance.SYSTEM)
        assertEquals(system, UserJson.decodeSettings(UserJson.encodeSettings(system)))
    }

    @Test fun `a v0_7_0 settings file decodes with the defaults and keeps its values`() {
        val old = """{"maxTransfers":1,"maxWalkMin":10,"modeFilter":"ALL","walkSpeed":"FAST","peakFactor":1.4,"homePlace":"Home"}"""
        val s = UserJson.decodeSettings(old)
        assertNull(s.language)
        assertEquals(Appearance.SYSTEM, s.theme)
        assertFalse("the tour shows once after the update", s.tourSeen)
        assertEquals(1, s.maxTransfers)
        assertEquals(10, s.maxWalkMin)
        assertEquals("Home", s.homePlace)
    }

    @Test fun `theme picks dark`() {
        assertTrue(Appearance.dark(Appearance.SYSTEM, systemDark = true))
        assertFalse(Appearance.dark(Appearance.SYSTEM, systemDark = false))
        assertTrue(Appearance.dark(Appearance.DARK, systemDark = false))
        assertFalse(Appearance.dark(Appearance.LIGHT, systemDark = true))
        assertTrue("unknown follows the phone", Appearance.dark("SEPIA", systemDark = true))
    }

    @Test fun `Transitous language follows the app, then the phone`() {
        assertEquals("en", Appearance.transitLanguage("en", "iw"))
        assertEquals("he", Appearance.transitLanguage("he", "en"))
        assertEquals("he", Appearance.transitLanguage(null, "iw"))
        assertEquals("he", Appearance.transitLanguage(null, "he-IL"))
        assertEquals("en", Appearance.transitLanguage(null, "fr"))
    }

    @Test fun `stored tags normalise to the Settings choices`() {
        assertEquals("he", Appearance.normalizeLanguage("iw"))
        assertEquals("he", Appearance.normalizeLanguage("he-IL"))
        assertEquals("en", Appearance.normalizeLanguage("en-US"))
        assertNull(Appearance.normalizeLanguage(""))
        assertNull(Appearance.normalizeLanguage("fr"))
        assertNull(Appearance.normalizeLanguage(null))
    }
}
