package il.transit.core

import il.transit.core.api.GeocodeArea
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.TransitApi
import il.transit.core.geo.LatLon
import il.transit.core.search.MatchLevel
import il.transit.core.search.Neighbourhoods
import il.transit.core.search.PlaceSearch
import il.transit.core.search.Towns
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 10 D2: a neighbourhood and no town searches near its town; a name in two towns, nearby first. */
class NeighbourhoodsTest {
    private fun answer(name: String, town: String, type: String = "PLACE", street: String? = null, number: String? = null) =
        GeocodeMatch(type, name, "$name/$town", Towns.named(town)!!.lat, Towns.named(town)!!.lon, street = street, houseNumber = number,
            areas = listOf(GeocodeArea(town, 8.0)))

    /** Records the bias of each geocode; answers what [answers] says. */
    private class BiasApi(val answers: (String) -> List<GeocodeMatch>) : TransitApi by FakeTransitApi() {
        val biases = mutableListOf<LatLon?>()
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
            biases += near
            return answers(text)
        }
    }

    @Test fun `the table - about 150, every town known, no name twice`() {
        assertTrue(Neighbourhoods.ALL.size >= 150)
        Neighbourhoods.ALL.forEach { n -> assertEquals(n.names.first(), n.townNames.size, n.towns.size) }
        val names = Neighbourhoods.ALL.flatMap { it.names.map(PlaceSearch::normalize) }
        assertEquals(names.size, names.toSet().size)
        assertEquals(listOf("תל אביב-יפו"), Neighbourhoods.named("רמת אביב")!!.towns.map { it.name })
        assertEquals(listOf("חיפה", "תל אביב-יפו"), Neighbourhoods.named("נווה שאנן")!!.towns.map { it.name }.sorted())
        assertEquals(listOf("ירושלים"), Neighbourhoods.named("גילה")!!.towns.map { it.name })
        assertEquals(setOf("ירושלים", "באר שבע"), Neighbourhoods.named("רמות")!!.towns.map { it.name }.toSet())
    }

    @Test fun `typed - alone, with a street, not as a street, not before only a number`() {
        assertEquals("רמת אביב", Neighbourhoods.typedIn("רמת אביב").single().names.first())
        assertEquals("רמת אביב ג'", Neighbourhoods.typedIn("רמת אביב ג'").single().names.first()) // the longest name
        assertEquals("רמת אביב", Neighbourhoods.typedIn("איינשטיין 40 רמת אביב").single().names.first())
        assertEquals("גילה", Neighbourhoods.typedIn("קניון גילה").single().names.first())
        assertTrue(Neighbourhoods.typedIn("רחוב רמות").isEmpty())
        assertTrue(Neighbourhoods.typedIn("גילה 5").isEmpty())
        assertTrue(Neighbourhoods.typedIn("דיזנגוף 50").isEmpty())
    }

    @Test fun `one town - searched near its centre, the town in the detail line`() = runBlocking {
        val api = BiasApi { listOf(answer("רמת אביב", "תל אביב-יפו")) }
        val a = PlaceSearch(api, null).search("רמת אביב", "he", near = LatLon(31.25, 34.79)) // the user in Be'er Sheva
        assertEquals(Towns.named("תל אביב")!!.centre, api.biases.single())
        assertEquals(MatchLevel.PLACE, a.level(a.exact.single()))
        assertEquals("תל אביב-יפו", il.transit.core.present.geocodeDetail(a.exact.single()))
    }

    @Test fun `two towns - the nearer one asked first, both listed, nearer first`() = runBlocking {
        val both = listOf(answer("רמות", "באר שבע"), answer("רמות", "ירושלים"))
        val jerusalem = LatLon(31.78, 35.2)
        val api = BiasApi { both }
        val a = PlaceSearch(api, null).search("רמות", "he", near = jerusalem)
        assertEquals(Towns.named("ירושלים")!!.centre, api.biases.single())
        assertEquals(listOf("ירושלים", "באר שבע"), a.exact.map { it.areas.single().name })
        val b = PlaceSearch(api, null).search("רמות", "he", near = LatLon(31.25, 34.8))
        assertEquals(Towns.named("באר שבע")!!.centre, api.biases.last())
        assertEquals(listOf("באר שבע", "ירושלים"), b.exact.map { it.areas.single().name })
        assertEquals(1, Neighbourhoods.townsByDistance(Neighbourhoods.named("רמות")!!, null).indexOfFirst { it.name == "באר שבע" })
    }

    @Test fun `a street in a neighbourhood - the neighbourhood words are where, not the street`() {
        val einstein = answer("איינשטיין 40", "תל אביב-יפו", type = "ADDRESS", street = "איינשטיין", number = "40")
        assertEquals(MatchLevel.HOUSE, PlaceSearch.level("איינשטיין 40 רמת אביב", einstein))
        // ...but only in the neighbourhood's town.
        assertEquals(MatchLevel.NONE, PlaceSearch.level("איינשטיין 40 רמת אביב", answer("איינשטיין 40", "חיפה", type = "ADDRESS", street = "איינשטיין", number = "40")))
    }

    @Test fun `a typed town wins over a neighbourhood`() = runBlocking {
        val api = BiasApi { emptyList() }
        PlaceSearch(api, null).search("רמות באר שבע", "he", near = null)
        assertEquals(Towns.named("באר שבע")!!.centre, api.biases.single())
    }
}
