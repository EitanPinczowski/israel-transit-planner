package il.transit.core

import il.transit.core.present.LegKind
import il.transit.core.present.chipTextColor
import il.transit.core.present.contrastRatio
import il.transit.core.present.defaultColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chip labels are 12sp: WCAG AA asks for 4.5:1 at that size. */
class ContrastTest {
    @Test fun `contrast ratio matches the WCAG reference values`() {
        assertEquals(21.0, contrastRatio("#000000", "#FFFFFF"), 0.01)
        assertEquals(1.0, contrastRatio("#1E88E5", "#1E88E5"), 0.001)
        assertEquals(contrastRatio("#C62828", "#FFFFFF"), contrastRatio("#FFFFFF", "#C62828"), 1e-9)
        // #767676 is the classic "just passes AA on white" grey.
        assertEquals(4.54, contrastRatio("#767676", "#FFFFFF"), 0.01)
    }

    @Test fun `chip text picked for every filled chip colour reaches AA`() {
        // WALK chips have no fill (text on the surface), so they are not listed.
        val filled = LegKind.entries.filter { it != LegKind.WALK }.map(::defaultColor)
        // Line colours MOTIS may send: Israeli operator liveries, light and dark.
        val routeColours = listOf("#FFD600", "#F9A825", "#00A651", "#E30613", "#0072BC", "#FFFFFF", "#000000", "#7FB2E5")
        for (bg in filled + routeColours) {
            val ratio = contrastRatio(bg, chipTextColor(bg))
            assertTrue("$bg with ${chipTextColor(bg)} is only %.2f:1".format(ratio), ratio >= 4.5)
        }
    }

    @Test fun `white stays the label colour on the dark defaults`() {
        for (kind in listOf(LegKind.TRAIN, LegKind.LIGHT_RAIL, LegKind.CAR, LegKind.OTHER)) {
            assertEquals(kind.name, "#FFFFFF", chipTextColor(defaultColor(kind)))
        }
    }
}
