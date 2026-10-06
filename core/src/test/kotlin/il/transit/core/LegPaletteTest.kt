package il.transit.core

import il.transit.core.api.Leg
import il.transit.core.geo.LatLon
import il.transit.core.geo.MapData
import il.transit.core.present.LegKind
import il.transit.core.present.LegPalette
import il.transit.core.present.defaultColor
import il.transit.core.present.summarize
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegPaletteTest {
    private val a = place("A", LatLon(31.25, 34.79))
    private val b = place("B", LatLon(31.26, 34.80))
    private val c = place("C", LatLon(31.27, 34.81))
    private val d = place("D", LatLon(31.28, 34.82))
    private val e = place("E", LatLon(31.29, 34.83))

    private fun l(mode: String, from: il.transit.core.api.Place, to: il.transit.core.api.Place, min: Long, color: String? = null): Leg =
        leg(mode, from, to, NOON.plusSeconds(min * 60), NOON.plusSeconds((min + 10) * 60)).copy(routeColor = color, routeShortName = mode.take(1))

    @Test fun `two buses get two colours`() {
        val it = itinerary(l("WALK", a, b, 0), l("BUS", b, c, 10), l("WALK", c, c, 20), l("BUS", c, d, 30), transfers = 1)
        val colors = LegPalette.colors(it.legs)
        assertNotEquals(colors[1], colors[3])
        assertTrue(colors[1] in LegPalette.COLORS && colors[3] in LegPalette.COLORS)
        assertEquals("walks stay grey", defaultColor(LegKind.WALK), colors[0])
        assertEquals(defaultColor(LegKind.WALK), colors[2])
    }

    @Test fun `a unique operator colour is kept`() {
        val it = itinerary(l("BUS", a, b, 0), l("TRAM", b, c, 20, color = "9933ff"))
        assertEquals(listOf(defaultColor(LegKind.BUS), "#9933FF"), LegPalette.colors(it.legs))
    }

    @Test fun `only the legs that clash change`() {
        // Two buses share the default blue; the train keeps its green; the palette skips
        // its blue and green entries, which would look like the kept train and the car.
        val it = itinerary(l("BUS", a, b, 0), l("RAIL", b, c, 20), l("BUS", c, d, 40), transfers = 2)
        val colors = LegPalette.colors(it.legs)
        assertEquals(defaultColor(LegKind.TRAIN), colors[1])
        assertEquals(listOf("#0072B2" != colors[0], colors[0] != colors[2]), listOf(true, true))
        assertTrue(colors[0] !in listOf("#009E73")) // too close to the train's green
    }

    @Test fun `three buses and a train all differ`() {
        val it = itinerary(l("BUS", a, b, 0), l("BUS", b, c, 20), l("RAIL", c, d, 40), l("BUS", d, e, 60), transfers = 3)
        val colors = LegPalette.colors(it.legs)
        assertEquals(4, colors.toSet().size)
    }

    @Test fun `the same operator colour twice is split too`() {
        val it = itinerary(l("BUS", a, b, 0, color = "FF0000"), l("BUS", b, c, 20, color = "ff0000"))
        val colors = LegPalette.colors(it.legs)
        assertNotEquals(colors[0], colors[1])
    }

    @Test fun `white or yellow operator colours that vanish on the light map are replaced`() {
        val white = itinerary(l("BUS", a, b, 0, color = "FFFFFF"))
        assertTrue(LegPalette.colors(white.legs)[0] in LegPalette.COLORS)
        val yellow = itinerary(l("TRAM", a, b, 0, color = "FFEB3B"))
        assertTrue(LegPalette.colors(yellow.legs)[0] in LegPalette.COLORS)
        val red = itinerary(l("TRAM", a, b, 0, color = "C62828"))
        assertEquals("#C62828", LegPalette.colors(red.legs)[0])
    }

    @Test fun `stable across calls`() {
        val it = itinerary(l("BUS", a, b, 0), l("BUS", b, c, 20), l("BUS", c, d, 40))
        assertEquals(LegPalette.colors(it.legs), LegPalette.colors(it.copy()))
        assertEquals(LegPalette.colors(it.legs), summarize(it).chips.map { ch -> ch.color })
    }

    @Test fun `chip, map line and stop dots share one colour`() {
        val it = itinerary(l("BUS", a, b, 0), l("BUS", b, c, 20))
        val chips = summarize(it).chips.map { ch -> ch.color }
        val features = Json.parseToJsonElement(MapData.itinerary(it)).jsonObject["features"]!!.jsonArray.map { f -> f.jsonObject }
        val lines = features.filter { f -> f["geometry"]!!.jsonObject["type"]!!.jsonPrimitive.content == "LineString" }
            .map { f -> f["properties"]!!.jsonObject["color"]!!.jsonPrimitive.content }
        val dots = features.filter { f -> f["geometry"]!!.jsonObject["type"]!!.jsonPrimitive.content == "Point" }
            .map { f -> f["properties"]!!.jsonObject["color"]!!.jsonPrimitive.content }
        assertEquals(chips, lines)
        assertEquals(listOf(chips[0], chips[0], chips[1], chips[1]), dots)
    }

    @Test fun `palette has six colours, each 3 to 1 on both maps`() {
        assertTrue(LegPalette.COLORS.size >= 6)
        assertEquals(LegPalette.COLORS.size, LegPalette.COLORS.toSet().size)
        for (col in LegPalette.COLORS) {
            assertTrue("$col on light", LegPalette.contrast(col, LegPalette.MAP_LIGHT) >= 3.0)
            assertTrue("$col on dark", LegPalette.contrast(col, LegPalette.MAP_DARK) >= 3.0)
        }
    }

    @Test fun `a trip with no transit is untouched`() {
        val it = itinerary(l("WALK", a, b, 0))
        assertEquals(listOf(defaultColor(LegKind.WALK)), LegPalette.colors(it.legs))
    }
}
