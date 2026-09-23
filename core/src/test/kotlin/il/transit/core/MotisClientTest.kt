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

    @Test fun `plan sends the query Transitous expects and parses the answer`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("plan_synthetic.json")))
        val req = PlanRequest(
            from = Endpoint.Coord(LatLon(31.262, 34.801)),
            to = Endpoint.Stop("il-mot_17022"),
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
        assertEquals("il-mot_17022", url.queryParameter("toPlace"))
        assertEquals("CAR_DROPOFF", url.queryParameter("preTransitModes"))
        assertEquals("600", url.queryParameter("maxPreTransitTime"))
        assertEquals("RAIL", url.queryParameter("transitModes"))
        assertEquals("2", url.queryParameter("maxTransfers"))
        assertEquals("he", url.queryParameter("language"))
        assertTrue(recorded.getHeader("User-Agent")!!.contains("github.com/EitanPinczowski/israel-transit-planner"))

        val it = resp.itineraries.single()
        assertEquals(listOf("WALK", "REGIONAL_RAIL"), it.legs.map { l -> l.mode })
        assertEquals("באר שבע צפון/אוניברסיטה", it.firstTransitLeg!!.from.name)
        assertTrue(it.firstTransitLeg!!.realTime)
        assertEquals(1, it.firstTransitLeg!!.intermediateStops.size)
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
