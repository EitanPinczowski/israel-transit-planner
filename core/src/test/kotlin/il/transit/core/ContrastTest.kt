package il.transit.core

import il.transit.core.present.LegKind
import il.transit.core.present.LegPalette
import il.transit.core.present.defaultColor
import il.transit.core.present.onColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    @Test fun `light route colours get black text`() {
        assertEquals("#000000", onColor("#FFD600")) // yellow
        assertEquals("#000000", onColor("#FFFFFF"))
        assertEquals("#000000", onColor("#00E5FF")) // cyan
        assertEquals("#000000", onColor("ffb300")) // no '#', lower case
    }

    @Test fun `dark route colours get white text`() {
        assertEquals("#FFFFFF", onColor("#0D47A1"))
        assertEquals("#FFFFFF", onColor("#000000"))
        assertEquals("#FFFFFF", onColor("#6A1B9A"))
    }

    @Test fun `every default colour except walk keeps white text`() {
        LegKind.entries.filter { it != LegKind.WALK }.forEach { k ->
            assertEquals(k.name, "#FFFFFF", onColor(defaultColor(k)))
        }
    }

    @Test fun `malformed colours fall back to white`() {
        assertEquals("#FFFFFF", onColor(""))
        assertEquals("#FFFFFF", onColor("#12"))
        assertEquals("#FFFFFF", onColor("#GGGGGG"))
    }

    /**
     * Phase 10 D1: the amber tight-transfer chip is `tertiaryContainer` with
     * `onTertiaryContainer` text (values copied from `ui/Theme.kt`). WCAG AA for normal text,
     * 4.5:1, in both themes; the text says the risk, so colour is never the only signal.
     */
    @Test fun `the amber transfer chip reads in both themes`() {
        val light = LegPalette.contrast("#271900", "#FFDEA6")
        val dark = LegPalette.contrast("#FFDEA6", "#5E4200")
        assertTrue("light $light", light >= 4.5)
        assertTrue("dark $dark", dark >= 4.5)
    }

    /** Phase 11 E3: the accessible-bus badge (`primaryContainer`) and "Fewer stops" (`secondaryContainer`). */
    @Test fun `the leg-label chips read in both themes`() {
        val pairs = mapOf(
            "badge light" to LegPalette.contrast("#001D36", "#D1E4FF"),
            "badge dark" to LegPalette.contrast("#D1E4FF", "#00497D"),
            "fewer light" to LegPalette.contrast("#101C2B", "#D7E3F7"),
            "fewer dark" to LegPalette.contrast("#D7E3F7", "#3B4858"),
        )
        pairs.forEach { (name, c) -> assertTrue("$name $c", c >= 4.5) }
    }

    /**
     * Phase 11 E4: the data card (`secondaryContainer` with `onSecondaryContainer` text) and the payment chooser's
     * second line ("Installed" in `primary`, "Get it on Google Play" in `onSurfaceVariant`) on the dialog's
     * `surfaceContainerHigh`, values copied from `ui/Theme.kt`.
     */
    @Test fun `the data card and the payment chooser read in both themes`() {
        val pairs = mapOf(
            "card light" to LegPalette.contrast("#101C2B", "#D7E3F7"),
            "card dark" to LegPalette.contrast("#D7E3F7", "#3B4858"),
            "installed light" to LegPalette.contrast("#0061A4", "#E6E8EE"),
            "installed dark" to LegPalette.contrast("#9ECAFF", "#282A2F"),
            "get it light" to LegPalette.contrast("#43474E", "#E6E8EE"),
            "get it dark" to LegPalette.contrast("#C3C7CF", "#282A2F"),
        )
        pairs.forEach { (name, c) -> assertTrue("$name $c", c >= 4.5) }
    }
}
