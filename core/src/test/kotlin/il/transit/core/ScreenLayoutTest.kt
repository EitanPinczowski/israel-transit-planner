package il.transit.core

import il.transit.core.present.ScreenLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenLayoutTest {
    @Test fun `phones are narrow, tablets and landscape are wide`() {
        assertFalse(ScreenLayout.isWide(320f))
        assertFalse(ScreenLayout.isWide(411f))
        assertTrue(ScreenLayout.isWide(600f))
        assertTrue(ScreenLayout.isWide(914f)) // a phone in landscape
        assertTrue(ScreenLayout.isWide(841f)) // tablet
    }

    @Test fun `on a phone the panel leaves the map its share`() {
        // 640 dp phone, search summary ends at 150 dp: 640 - 150 - 192 = 298.
        val cap = ScreenLayout.panelCap(640f, 150f, wide = false)
        assertEquals(298f, cap, 0.01f)
        // What remains for the map between the two is at least 30% of the height.
        assertTrue(640f - 150f - cap >= 0.3f * 640f - 0.01f)
    }

    @Test fun `in a side column the panel may take everything under the search`() {
        assertEquals(411f - 120f, ScreenLayout.panelCap(411f, 120f, wide = true), 0.01f)
    }

    @Test fun `a very tall search area still leaves a usable panel`() {
        assertEquals(ScreenLayout.MIN_PANEL_DP, ScreenLayout.panelCap(640f, 600f, wide = false), 0.01f)
    }
}
