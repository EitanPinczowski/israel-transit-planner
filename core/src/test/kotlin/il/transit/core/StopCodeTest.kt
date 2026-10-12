package il.transit.core

import il.transit.core.api.GeocodeMatch
import il.transit.core.api.MotisJson
import il.transit.core.api.Place
import il.transit.core.geo.LatLon
import il.transit.core.plan.StopsCacheJson
import il.transit.core.plan.StopsTileCache
import il.transit.core.search.MatchLevel
import il.transit.core.search.PlaceSearch
import il.transit.core.search.StopCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 10 D6: a typed stop number is looked up on the phone, 0 requests. */
class StopCodeTest {
    private fun text(name: String) = javaClass.getResource("/fixtures/$name.json")!!.readText()
    private fun geocode(name: String) = MotisJson.decodeFromString(ListSerializer(GeocodeMatch.serializer()), text(name))

    /** Recorded 2026-10-12: 16 stops around Be'er Sheva North, each with its `stopCode`. */
    private val stops: List<Place> = MotisJson.decodeFromString(ListSerializer(Place.serializer()), text("map_stops_beersheva_north"))

    @Test fun `Transitous geocode does not find a stop by its code`() {
        // 13907 is Be'er Sheva central's intercity platforms (plan_bgu_telaviv). Recorded 2026-10-12 with the bias the app sends.
        for (f in listOf("geocode_stopcode_13907", "geocode_stopcode_13907_stop")) {
            val found = geocode(f)
            assertTrue(f, found.isNotEmpty())
            assertTrue(f, found.none { PlaceSearch.inIsrael(LatLon(it.lat, it.lon)) })
            assertTrue(f, found.none { it.name.contains("13907") })
        }
    }

    @Test fun `what is a stop code`() {
        assertEquals("47899", StopCode.of("47899"))
        assertEquals("47899", StopCode.of(" תחנה 47899 "))
        assertEquals("47899", StopCode.of("תחנת 47899"))
        assertEquals("47899", StopCode.of("Stop 47899"))
        assertEquals("47899", StopCode.of("stop #47899"))
        assertEquals("123", StopCode.of("123"))
        assertEquals("123456", StopCode.of("123456"))
        assertNull("15 alone is too short", StopCode.of("15"))
        assertNull(StopCode.of("1234567"))
        assertNull("a house on a street", StopCode.of("הרצל 15"))
        assertNull(StopCode.of("הרצל 1500"))
        assertNull(StopCode.of("Herzl 47899"))
        assertNull(StopCode.of("47899 תל אביב"))
        assertNull(StopCode.of("12א"))
    }

    @Test fun `a code is found among the stops the phone holds`() {
        val hit = StopCode.find("14381", stops, null)
        assertEquals(listOf("il-Israel-MOT_26873"), hit.map { it.stopId })
        assertTrue(StopCode.find("99999", stops, null).isEmpty())
        // Leading zeros on either side.
        val zero = stops.first().copy(stopCode = "0381", stopId = "z")
        assertEquals("z", StopCode.find("381", listOf(zero), null).single().stopId)
    }

    @Test fun `a station and its platforms are one row, the parent first`() {
        val parent = Place("ת. מרכזית", 31.2430, 34.7967, stopId = "p", stopCode = "13907")
        val platform = Place("ת. מרכזית", 31.2431, 34.7968, stopId = "c", parentId = "p", stopCode = "13907")
        val other = Place("ת. מרכזית/רציף 2", 31.2432, 34.7969, stopId = "c2", parentId = "p", stopCode = "13907")
        assertEquals(listOf("p", "c2"), StopCode.find("13907", listOf(platform, other, parent), null).map { it.stopId })
    }

    @Test fun `the stops cache keeps the code across a restart`() {
        val restored = StopsCacheJson.decode(StopsCacheJson.encode(listOf(StopsTileCache.Entry(il.transit.core.geo.Tile(0, 0), java.time.Instant.EPOCH, stops))))
        assertEquals("14381", restored.single().stops.first().stopCode)
    }

    @Test fun `a typed stop number sends no request - a hit, a miss, no cache`() = runTest {
        val api = FakeTransitApi()
        val search = PlaceSearch(api, null) { stops }
        val hit = search.search("תחנה 14381", "he", null)
        assertEquals("14381", hit.stopCode)
        assertEquals(listOf("il-Israel-MOT_26873"), hit.exact.map { it.id })
        assertEquals(StopCode.CATEGORY, hit.exact.single().category)
        assertEquals(MatchLevel.PLACE, hit.level(hit.exact.single()))
        assertFalse(hit.notFound)

        val miss = search.search("47899", "he", null)
        assertEquals("47899", miss.stopCode)
        assertTrue(miss.notFound)

        val none = PlaceSearch(api, null).search("47899", "he", null)
        assertTrue(none.notFound)
        assertEquals(emptyList<String>(), api.calls)
    }
}
