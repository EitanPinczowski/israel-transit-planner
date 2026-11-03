package il.transit.core.geo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** F9 (bug bash): the long-press pin is a map feature, at the pressed point, [lon, lat]. */
class MapPinTest {
    @Test fun a_pin_is_one_point_at_the_press() {
        val fc = Json.parseToJsonElement(MapData.pin(LatLon(31.2622, 34.8013))).jsonObject
        val features = fc["features"]!!.jsonArray
        assertEquals(1, features.size)
        val geometry = features[0].jsonObject["geometry"]!!.jsonObject
        assertEquals("Point", geometry["type"]!!.jsonPrimitive.content)
        val coords = geometry["coordinates"]!!.jsonArray.map { it.jsonPrimitive.double }
        assertEquals(listOf(34.8013, 31.2622), coords)
    }

    @Test fun no_pin_clears_the_layer() {
        assertEquals(MapData.EMPTY, MapData.pin(null))
    }
}
