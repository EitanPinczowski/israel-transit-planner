package il.transit.core

import il.transit.core.api.Leg
import il.transit.core.geo.LatLon
import il.transit.core.geo.OptionsMap
import il.transit.core.present.LegPalette
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 10 D11: the options not selected, drawn faded under the selected one. */
class OptionsMapTest {
    private val a = place("A", LatLon(31.25, 34.79))
    private val b = place("B", LatLon(31.26, 34.80))
    private val c = place("C", LatLon(31.27, 34.81))

    private fun l(mode: String, from: il.transit.core.api.Place, to: il.transit.core.api.Place, min: Long): Leg =
        leg(mode, from, to, NOON.plusSeconds(min * 60), NOON.plusSeconds((min + 10) * 60)).copy(routeShortName = mode.take(1))

    private val one = itinerary(l("WALK", a, b, 0), l("BUS", b, c, 10))
    private val two = itinerary(l("BUS", a, b, 5), l("WALK", b, b, 15), l("BUS", b, c, 20), transfers = 1)
    private val three = itinerary(l("RAIL", a, c, 8))

    private fun props(json: String) = Json.parseToJsonElement(json).jsonObject["features"]!!.jsonArray
        .map { f -> f.jsonObject["properties"]!!.jsonObject }

    @Test fun `every option but the selected one, rides only, tagged with its index`() {
        val p = props(OptionsMap.others(listOf(one, two, three), selected = 1))
        assertEquals(listOf(0, 2), p.map { it["option"]!!.jsonPrimitive.int })
        assertEquals(listOf(LegPalette.colors(one.legs)[1], LegPalette.colors(three.legs)[0]), p.map { it["color"]!!.jsonPrimitive.content })
    }

    @Test fun `two rides of one option keep their own palette colours`() {
        val p = props(OptionsMap.others(listOf(one, two), selected = 0))
        val colors = LegPalette.colors(two.legs)
        assertEquals(listOf(colors[0], colors[2]), p.map { it["color"]!!.jsonPrimitive.content })
        assertEquals(listOf(1, 1), p.map { it["option"]!!.jsonPrimitive.int })
    }

    @Test fun `a single option draws nothing extra`() {
        assertEquals(0, props(OptionsMap.others(listOf(one), selected = 0)).size)
        assertEquals(0, props(OptionsMap.others(emptyList(), selected = 0)).size)
    }
}
