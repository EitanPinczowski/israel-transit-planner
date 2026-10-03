package il.transit.core

import il.transit.core.present.LegKind
import il.transit.core.present.defaultColor
import il.transit.core.present.onColor
import org.junit.Assert.assertEquals
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
}
