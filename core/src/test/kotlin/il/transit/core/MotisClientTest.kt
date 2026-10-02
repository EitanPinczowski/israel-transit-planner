package il.transit.core

import il.transit.core.api.Endpoint
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.MotisClient
import il.transit.core.api.PlanRequest
import il.transit.core.api.Preferences
import il.transit.core.api.StreetModes
import il.transit.core.api.TransitHttpException
import il.transit.core.geo.LatLon
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Duration

class MotisClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: MotisClient

    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()

    @Before fun start() {
        server = MockWebServer().apply { start() }
        client = MotisClient(server.url("/").toString())
    }

    @After fun stop() = server.shutdown()

    // runBlocking, not runTest: the client hops to Dispatchers.IO for real socket I/O.

    @Test fun `plan sends the query Transitous expects and parses a recorded answer`() = runBlocking {
        // Recorded from api.transitous.org: BGU → Tel Aviv HaShalom, Monday 08:00.
        server.enqueue(MockResponse().setBody(fixture("plan_bgu_telaviv.json")))
        val req = PlanRequest(
            from = Endpoint.Coord(LatLon(31.262, 34.801)),
            to = Endpoint.Stop("il-Israel-MOT_37314"),
            time = NOON,
            preTransitModes = listOf(StreetModes.CAR_DROPOFF),
            maxPreTransitSec = 600,
            preferences = Preferences(transitModes = setOf("RAIL"), maxTransfers = 2),
        )
        val resp = client.plan(req)

        val recorded = server.takeRequest()
        val url = recorded.requestUrl!!
        assertEquals("/api/v6/plan", url.encodedPath)
        assertEquals("31.262,34.801", url.queryParameter("fromPlace"))
        assertEquals("il-Israel-MOT_37314", url.queryParameter("toPlace"))
        assertEquals("CAR_DROPOFF", url.queryParameter("preTransitModes"))
        assertEquals("600", url.queryParameter("maxPreTransitTime"))
        assertEquals("RAIL", url.queryParameter("transitModes"))
        assertEquals("2", url.queryParameter("maxTransfers"))
        assertEquals("he", url.queryParameter("language"))
        assertNull(url.queryParameter("maxDirectTime"))
        assertTrue(recorded.getHeader("User-Agent")!!.contains("github.com/EitanPinczowski/israel-transit-planner"))

        assertEquals(5, resp.itineraries.size)
        val train = resp.itineraries[1]
        assertEquals(listOf("WALK", "REGIONAL_RAIL", "WALK"), train.legs.map { l -> l.mode })
        val rail = train.firstTransitLeg!!
        assertEquals("באר שבע צפון", rail.from.name)
        assertEquals("il-Israel-MOT_37314", rail.from.stopId)
        assertEquals("תל אביב מרכז", rail.to.name)
        // Israel Railways: no short name, train number as headsign, real terminus in tripTo.
        assertEquals("", rail.routeShortName)
        assertEquals("406", rail.headsign)
        assertEquals("כרמיאל", rail.tripTo!!.name)
        assertEquals(4, rail.intermediateStops.size)
        assertFalse(rail.realTime) // recorded days ahead: no real-time yet
        assertEquals(6, rail.legGeometry!!.precision)
        assertEquals(java.time.Instant.parse("2026-10-05T05:21:00Z"), train.start)
        // Every bus leg has a line number.
        resp.itineraries.flatMap { it.legs }.filter { it.mode == "BUS" }.forEach { assertTrue(it.routeShortName!!.isNotBlank()) }
    }

    @Test fun `a car route asks for a longer direct trip than the 30-min default`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("plan_direct_car.json")))
        val resp = client.plan(
            PlanRequest(
                from = Endpoint.Coord(LatLon(31.3236, 34.9381)),
                to = Endpoint.Coord(LatLon(32.0839, 34.7983)),
                time = NOON,
                directModes = listOf(StreetModes.CAR),
                directOnly = true,
                maxDirectSec = 7200,
            ),
        )
        assertEquals("7200", server.takeRequest().requestUrl!!.queryParameter("maxDirectTime"))
        val car = resp.direct.single()
        assertEquals(4383, car.duration) // Meitar → Tel Aviv, 73 min: dropped without maxDirectTime
        assertTrue(resp.itineraries.isEmpty())
        assertTrue(il.transit.core.geo.Geo.decodePolyline(car.legs.single().legGeometry!!.points, 6).size > 100)
    }

    @Test fun `recorded one-to-many, stops, stoptimes and geocode answers parse`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("one_to_many_car.json")))
        val many = listOf(LatLon(31.262089, 34.809288), LatLon(31.369907, 34.79804), LatLon(31.423239, 34.78607), LatLon(31.40, 34.20))
        // The last point is in the sea: MOTIS answers {} for it.
        assertEquals(listOf(877, 1131, 1074, null), client.oneToMany(LatLon(31.3236, 34.9381), many, StreetModes.CAR, 5400, arriveBy = false))
        server.takeRequest()

        server.enqueue(MockResponse().setBody(fixture("map_stops_beersheva_north.json")))
        val stops = client.stops(il.transit.core.geo.BBox(LatLon(31.258, 34.802), LatLon(31.266, 34.814)))
        assertEquals(16, stops.size)
        assertTrue(stops.all { it.stopId!!.startsWith("il-Israel-MOT_") })
        assertEquals(listOf("REGIONAL_RAIL"), stops.single { it.name == "באר שבע צפון" }.modes)
        server.takeRequest()

        server.enqueue(MockResponse().setBody(fixture("stoptimes_beersheva_north.json")))
        val board = client.stopTimes("il-Israel-MOT_37314", NOON)
        assertEquals("באר שבע צפון", board.place!!.name)
        assertEquals(10, board.stopTimes.size)
        assertEquals("כרמיאל", board.stopTimes[2].tripTo!!.name)
        server.takeRequest()

        server.enqueue(MockResponse().setBody(fixture("geocode_rager.json")))
        val matches = client.geocode("רגר", near = LatLon(31.262, 34.801))
        assertEquals(8, matches.size)
        val geoUrl = server.takeRequest().requestUrl!!
        assertEquals("31.262,34.801", geoUrl.queryParameter("place"))
        assertEquals(MotisClient.PLACE_BIAS.toString(), geoUrl.queryParameter("placeBias"))
        // With the bias every answer is in Israel (without it: Agra, Zagreb, Riga…).
        assertTrue(matches.all { it.lat in 29.4..33.4 && it.lon in 34.2..35.9 })

        server.enqueue(MockResponse().setBody(fixture("reverse_geocode_bgu.json")))
        assertEquals("ארומה", client.reverseGeocode(LatLon(31.262, 34.801)).first().name)
    }

    @Test fun `a direct-only request sends an empty transitModes`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"itineraries":[],"direct":[]}"""))
        client.plan(
            PlanRequest(
                from = Endpoint.Coord(LatLon(31.25, 34.8)),
                to = Endpoint.Coord(LatLon(32.08, 34.78)),
                time = NOON,
                directModes = listOf(StreetModes.CAR),
                directOnly = true,
                preferences = Preferences(transitModes = setOf("RAIL")), // ignored: no transit at all
            ),
        )
        val url = server.takeRequest().requestUrl!!
        assertEquals("", url.queryParameter("transitModes"))
        assertEquals("CAR", url.queryParameter("directModes"))
    }

    @Test fun `one-to-many maps a missing path to null and keeps order`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"duration": 610.0}, {}, {"duration": 95}]"""))
        val many = listOf(LatLon(31.0, 34.0), LatLon(31.1, 34.1), LatLon(31.2, 34.2))
        val out = client.oneToMany(LatLon(31.25, 34.8), many, StreetModes.CAR, 3600, arriveBy = true)
        assertEquals(listOf(610, null, 95), out)
        val url = server.takeRequest().requestUrl!!
        assertEquals("31.0;34.0,31.1;34.1,31.2;34.2", url.queryParameter("many"))
        assertEquals("true", url.queryParameter("arriveBy"))
    }

    @Test fun `http errors surface with their code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"bad"}"""))
        try {
            client.geocode("רגר")
            fail("expected an exception")
        } catch (e: TransitHttpException) {
            assertEquals(400, e.code)
        }
    }

    @Test fun `guard retries a 429 once, then serves repeats from cache`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setBody("""[{"type":"STOP","name":"רגר","id":"x","lat":31.26,"lon":34.8,"tokens":[],"areas":[],"score":1.0}]"""))
        val guarded = GuardedTransitApi(client, backoff = Duration.ZERO)
        assertEquals("רגר", guarded.geocode("רגר").single().name)
        assertEquals("רגר", guarded.geocode("רגר").single().name) // cached: no third request
        assertEquals(2, server.requestCount)
    }

    @Test fun `guard gives up after a second refusal`() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(503)) }
        val guarded = GuardedTransitApi(client, backoff = Duration.ZERO)
        try {
            guarded.geocode("x")
            fail("expected an exception")
        } catch (e: TransitHttpException) {
            assertEquals(503, e.code)
        }
    }
}
