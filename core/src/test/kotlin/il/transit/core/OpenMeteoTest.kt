package il.transit.core

import il.transit.core.api.MotisClient
import il.transit.core.api.TransitHttpException
import il.transit.core.geo.LatLon
import il.transit.core.weather.GuardedWeather
import il.transit.core.weather.OpenMeteoClient
import il.transit.core.weather.RainForecast
import il.transit.core.weather.WeatherSource
import il.transit.core.weather.parseOpenMeteo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Open-Meteo (Phase 10 D9): the answer, what it is told, its guard. Fixtures recorded 2026-10-13. */
class OpenMeteoTest {
    private fun text(name: String) = javaClass.getResource("/fixtures/$name.json")!!.readText()

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    /** Counts calls; answers [answer] or throws what [fail] gives for that call number. */
    private class FakeWeather(val fail: (Int) -> Exception? = { null }) : WeatherSource {
        val cells = mutableListOf<LatLon>()
        override suspend fun rain(cell: LatLon): RainForecast {
            cells += cell
            fail(cells.size)?.let { throw it }
            return RainForecast(emptyList())
        }
    }

    private val now = Instant.parse("2026-10-16T05:00:00Z") // 08:00 in Israel
    private val telAviv = LatLon(32.0853, 34.7818)

    @Test fun `the rainy forecast parses, each value moved to the hour it is for`() {
        val f = parseOpenMeteo(text("openmeteo_rain_telaviv")) // 16–17 Oct, Tel Aviv's cell
        assertEquals(48, f.hours.size)
        // Stamped 00:00 UTC on the 16th = rain in the hour before it.
        assertEquals(Instant.parse("2026-10-15T23:00:00Z"), f.hours.first().start)
        // Stamped 10:00 in Israel (07:00 UTC): 0.5 mm between 09:00 and 10:00.
        val h = f.hours.single { it.start == Instant.parse("2026-10-16T06:00:00Z") }
        assertEquals(21, h.probability)
        assertEquals(0.5, h.mm!!, 1e-9)
        assertEquals(Instant.parse("2026-10-16T07:00:00Z"), h.end)
        // Early on the 17th the chance passes 60% with almost no rain.
        assertTrue(f.hours.any { (it.probability ?: 0) >= 60 && (it.mm ?: 0.0) < 0.5 })
    }

    @Test fun `the dry forecast has nothing to report`() {
        val f = parseOpenMeteo(text("openmeteo_dry_beersheva")) // Be'er Sheva, 54 h from the hour it was recorded (2026-10-13)
        assertEquals(54, f.hours.size)
        assertTrue(f.hours.all { (it.probability ?: 0) < 60 && (it.mm ?: 0.0) < 0.5 })
    }

    @Test fun `an error answer is an exception, never an empty forecast`() {
        try {
            parseOpenMeteo(text("openmeteo_error"))
            fail("expected an error")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Latitude"))
        }
    }

    @Test fun `the cell is the start rounded to 0_1 degree, all Open-Meteo is told`() {
        assertEquals(LatLon(32.1, 34.8), GuardedWeather.cell(telAviv))
        assertEquals(LatLon(31.3, 34.8), GuardedWeather.cell(LatLon(31.25, 34.79))) // half rounds up
        assertEquals(LatLon(31.2, 34.8), GuardedWeather.cell(LatLon(31.2437, 34.7976)))
    }

    // runBlocking, not runTest: the client hops to OkHttp's threads for real socket I/O.
    @Test fun `the client asks with our User-Agent for the cell only, 54 hours in UTC seconds`() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setBody(text("openmeteo_rain_telaviv")))
            server.enqueue(MockResponse().setResponseCode(400).setBody(text("openmeteo_error")))
            val client = OpenMeteoClient(server.url("/").toString())

            val f = client.rain(LatLon(32.1, 34.8))
            assertEquals(48, f.hours.size)
            val r = server.takeRequest()
            assertEquals(MotisClient.USER_AGENT, r.getHeader("User-Agent"))
            val u = r.requestUrl!!
            assertEquals("/v1/forecast", u.encodedPath)
            assertEquals("32.1", u.queryParameter("latitude"))
            assertEquals("34.8", u.queryParameter("longitude"))
            assertEquals("precipitation_probability,precipitation", u.queryParameter("hourly"))
            assertEquals("unixtime", u.queryParameter("timeformat"))
            assertEquals("54", u.queryParameter("forecast_hours"))
            assertEquals(setOf("latitude", "longitude", "hourly", "timeformat", "forecast_hours"), u.queryParameterNames)

            try {
                client.rain(LatLon(91.0, 34.8))
                fail("expected an HTTP error")
            } catch (e: TransitHttpException) {
                assertEquals(400, e.code)
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun `cached for an hour per cell`() = runTest {
        val clock = MutableClock(now)
        val source = FakeWeather()
        val w = GuardedWeather(source, clock)
        assertNotNull(w.forTrip(now, telAviv))
        clock.now = now.plus(Duration.ofMinutes(59))
        assertNotNull(w.forTrip(now.plusSeconds(7200), LatLon(32.06, 34.83))) // same cell
        assertEquals(1, source.cells.size)
        assertNotNull(w.forTrip(now, LatLon(31.25, 34.79))) // Be'er Sheva: its own cell
        assertEquals(2, source.cells.size)
        clock.now = now.plus(Duration.ofMinutes(61))
        assertNotNull(w.forTrip(now.plusSeconds(7200), telAviv))
        assertEquals(listOf(LatLon(32.1, 34.8), LatLon(31.3, 34.8), LatLon(32.1, 34.8)), source.cells)
    }

    @Test fun `only trips starting within 48 hours`() = runTest {
        val source = FakeWeather()
        val w = GuardedWeather(source, MutableClock(now))
        assertNull(w.forTrip(now.plus(Duration.ofHours(48)).plusSeconds(60), telAviv))
        assertEquals(0, source.cells.size)
        assertNotNull(w.forTrip(now.plus(Duration.ofHours(47)), telAviv))
        assertEquals(1, source.cells.size)
    }

    @Test fun `a failure is silent and not cached`() = runTest {
        val source = FakeWeather { n -> if (n == 1) IOException("timeout") else null }
        val w = GuardedWeather(source, MutableClock(now))
        assertNull(w.forTrip(now, telAviv))
        assertNotNull(w.forTrip(now, telAviv)) // asked again
        assertEquals(2, source.cells.size)
    }

    @Test fun `one retry after 429 or 503, then silent`() = runTest {
        val once = FakeWeather { n -> if (n == 1) TransitHttpException(429, "slow down") else null }
        assertNotNull(GuardedWeather(once, MutableClock(now), backoff = Duration.ZERO).forTrip(now, telAviv))
        assertEquals(2, once.cells.size)

        val twice = FakeWeather { TransitHttpException(503, "busy") }
        assertNull(GuardedWeather(twice, MutableClock(now), backoff = Duration.ZERO).forTrip(now, telAviv))
        assertEquals(2, twice.cells.size) // never a third

        val bad = FakeWeather { TransitHttpException(400, "bad") }
        assertNull(GuardedWeather(bad, MutableClock(now)).forTrip(now, telAviv))
        assertEquals(1, bad.cells.size)
    }

    @Test fun `at most two requests at once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var running = 0
        var most = 0
        val source = WeatherSource {
            running++
            most = maxOf(most, running)
            gate.await()
            running--
            RainForecast(emptyList())
        }
        val w = GuardedWeather(source, MutableClock(now))
        val calls = listOf(LatLon(32.1, 34.8), LatLon(31.3, 34.8), LatLon(32.8, 35.0)).map { async { w.forTrip(now, it) } }
        testScheduler.advanceUntilIdle()
        assertEquals(2, running)
        gate.complete(Unit)
        calls.awaitAll()
        assertEquals(2, most)
    }
}
