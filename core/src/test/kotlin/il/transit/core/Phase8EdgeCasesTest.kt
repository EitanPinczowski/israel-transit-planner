package il.transit.core

import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.api.TransitHttpException
import il.transit.core.diag.CrashLog
import il.transit.core.features.ParkRidePlanner
import il.transit.core.features.ParkRideQuery
import il.transit.core.geo.LatLon
import il.transit.core.plan.CalendarEvent
import il.transit.core.plan.CalendarSuggest
import il.transit.core.present.StopRole
import il.transit.core.present.TripDetailsSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Phase 8 edge cases (tester, Phase 9 round 2): what each feature does when the input or the
 * network gives it nothing to work with. The happy paths are in TripDetailsTest,
 * ParkRideTest, CalendarSuggestTest and CrashLogTest.
 */
class Phase8EdgeCasesTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name.json")!!.readText()
    private val trip470 by lazy { MotisJson.decodeFromString(Itinerary.serializer(), fixture("trip_bus_470")) }
    private fun at(utc: String) = Instant.parse("2026-10-03T${utc}Z")

    /** Bus 470 from Soroka (its stop 1) to Nahal Sorek camp (stop 3), as a plan gives it. */
    private fun userLeg(): Leg {
        val t = trip470.legs.single()
        val stops = listOf(t.from) + t.intermediateStops + listOf(t.to)
        return t.copy(from = stops[1], to = stops[3], intermediateStops = listOf(stops[2]), startTime = stops[1].departure!!, endTime = stops[3].arrival!!)
    }

    // --- trip details: no trip id, failed request ---

    @Test fun `a leg without a trip id asks nothing, ever, and shows its own stops`() = runTest {
        val fake = FakeTransitApi() // any call would fail loudly
        val session = TripDetailsSession(fake, userLeg().copy(tripId = null))
        val d = session.open(at("20:33:00"))
        assertTrue(session.legOnly)
        assertNull(d.tripId)
        assertEquals(listOf(StopRole.BOARD, StopRole.RIDE, StopRole.ALIGHT), d.rows.map { it.role })
        // Two minutes in front of the user: no vehicle to look for without an id.
        var t = at("20:33:00")
        repeat(120) {
            assertNull(session.tick(t, visible = true))
            t = t.plusSeconds(1)
        }
        assertEquals(emptyList<String>(), fake.calls)
    }

    @Test fun `a refused trip request is retried once, not remembered, and the next tap gets the whole trip`() = runTest {
        var answers = 0
        val fake = FakeTransitApi().apply {
            onTrip = { if (++answers <= 2) throw TransitHttpException(503, "busy") else trip470 }
        }
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: ZoneId?) = this
            override fun instant() = at("20:33:00")
        }
        val api = GuardedTransitApi(fake, clock)
        val first = TripDetailsSession(api, userLeg())
        assertEquals(3, first.open(at("20:33:00")).rows.size)
        assertTrue(first.legOnly)
        assertEquals(2, fake.calls.count { it == "trip" }) // the call and its one retry

        // Same second, same trip: the failure was not cached, so the re-tap asks again.
        val second = TripDetailsSession(api, userLeg())
        val d = second.open(at("20:33:00"))
        assertFalse(second.legOnly)
        assertEquals(trip470.legs.single().intermediateStops.size + 2, d.rows.size)
        assertEquals(3, fake.calls.count { it == "trip" })
    }

    @Test fun `an error the server will repeat (404) is not retried`() = runTest {
        val fake = FakeTransitApi().apply { onTrip = { throw TransitHttpException(404, "unknown trip") } }
        val session = TripDetailsSession(GuardedTransitApi(fake), userLeg())
        session.open(at("20:33:00"))
        assertTrue(session.legOnly)
        assertEquals(listOf("trip"), fake.calls)
    }

    @Test fun `closing the sheet while the trip request runs cancels it - it is not a failure`() = runTest {
        val fake = object : TransitApiAdapter() {
            override suspend fun trip(tripId: String, language: String): Itinerary = awaitCancellation()
        }
        val session = TripDetailsSession(fake, userLeg())
        val job = async { session.open(at("20:33:00")) }
        yield()
        job.cancel()
        assertTrue(runCatching { job.await() }.exceptionOrNull() is CancellationException)
        assertNull(session.details)
    }

    // --- park & ride: no station in range ---

    @Test fun `Eilat has no train station in reach - only the baseline is asked`() = runTest {
        val eilat = LatLon(29.5577, 34.9519)
        val fake = FakeTransitApi().apply { onPlan = { PlanResponse() } }
        // The real bundled station list; the nearest train is in Dimona, ~150 km away.
        val r = ParkRidePlanner(fake).plan(ParkRideQuery(eilat, LatLon(32.0839, 34.7983), NOON, maxDriveMin = 30))
        assertEquals(listOf("plan"), fake.calls)
        assertEquals(0, r.stationsInReach)
        assertTrue(r.options.isEmpty())
        assertNull(r.baseline) // and no transit answer either: still no crash, just nothing to show
    }

    @Test fun `stations near as the crow flies but no road within the limit - two requests, nothing offered`() = runTest {
        val meitar = LatLon(31.3236, 34.9381)
        val telAviv = LatLon(32.0839, 34.7983)
        val bus = itinerary(leg("BUS", place("Meitar", meitar), place("TA", telAviv), NOON.plusSeconds(600), NOON.plusSeconds(7200)))
        val fake = FakeTransitApi().apply {
            onPlan = { PlanResponse(listOf(bus)) }
            onOneToMany = { _, many, _ -> many.map { null } } // MOTIS: no path within maxSeconds
        }
        val r = ParkRidePlanner(fake).plan(ParkRideQuery(meitar, telAviv, NOON, maxDriveMin = 20))
        assertEquals(listOf("oneToMany", "plan"), fake.calls.sorted())
        assertEquals(0, r.stationsInReach)
        assertTrue(r.options.isEmpty())
        assertEquals(bus.end, r.baseline?.end)
    }

    // --- calendar: no events, all-day only ---

    private val now = Instant.parse("2026-10-05T05:00:00Z") // Monday 08:00 Israel

    @Test fun `an empty calendar offers nothing`() {
        assertEquals(emptyList<CalendarEvent>(), CalendarSuggest.pick(emptyList(), now))
    }

    @Test fun `a day of all-day events only offers nothing - not even the ones with a place`() {
        val midnight = Instant.parse("2026-10-04T21:00:00Z")
        val allDay = listOf(
            CalendarEvent("Holiday", "", midnight, midnight.plusSeconds(86_400), allDay = true),
            CalendarEvent("Conference", "Expo Tel Aviv", midnight.plusSeconds(86_400), midnight.plusSeconds(2 * 86_400), allDay = true),
            CalendarEvent("Trip", "31.2622, 34.8013", midnight.plusSeconds(86_400), midnight.plusSeconds(2 * 86_400), allDay = true),
        )
        assertEquals(emptyList<CalendarEvent>(), CalendarSuggest.pick(allDay, now))
    }

    // --- crash log: empty and full ---

    private val zone = ZoneId.of("Asia/Jerusalem")
    private fun crash(i: Int, error: Throwable = IllegalStateException("crash $i")) =
        CrashLog.format(error, now.plusSeconds(i * 60L), zone, "0.8.0", "8.0 (SDK 26)", "main")

    @Test fun `an empty log has nothing to share, and the first crash starts it`() {
        for (empty in listOf(null, "", "\n\n")) {
            assertNull(CrashLog.report(empty, "0.8.0"))
            val one = CrashLog.append(empty, crash(1))
            assertEquals(1, CrashLog.entries(one).size)
            assertTrue(CrashLog.report(one, "0.8.0")!!.contains("(1 crash, newest first)"))
        }
    }

    @Test fun `a full log stays at five, drops the oldest, and stays small with the longest traces`() {
        fun deep(n: Int): Nothing = if (n == 0) throw StackOverflowError("deep") else deep(n - 1)
        val huge = runCatching { deep(3000) }.exceptionOrNull()!!
        var text: String? = null
        for (i in 1..CrashLog.MAX_ENTRIES) text = CrashLog.append(text, crash(i, huge))
        assertEquals(CrashLog.MAX_ENTRIES, CrashLog.entries(text).size)

        // Full: one more pushes out exactly the oldest.
        val first = CrashLog.entries(text).first()
        text = CrashLog.append(text, crash(6))
        val list = CrashLog.entries(text)
        assertEquals(CrashLog.MAX_ENTRIES, list.size)
        assertFalse(first in list)
        assertTrue(list.last().contains("crash 6"))

        // Five cut traces: the shared text stays a few tens of KB, never the whole stack.
        val report = CrashLog.report(text, "0.8.0")!!
        assertTrue(report.startsWith("Israel Transit Planner 0.8.0 — crash log (5 crashes, newest first)"))
        assertTrue(report.length < CrashLog.MAX_ENTRIES * (CrashLog.MAX_TRACE_CHARS + 500))
        assertTrue(report.indexOf("crash 6") < report.indexOf("StackOverflowError"))
    }
}

/** Every call fails unless overridden: for the one call a test cares about. */
private abstract class TransitApiAdapter : il.transit.core.api.TransitApi {
    override suspend fun plan(req: il.transit.core.api.PlanRequest): PlanResponse = error("unexpected plan")
    override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean): List<Int?> = error("unexpected oneToMany")
    override suspend fun stops(box: il.transit.core.geo.BBox, modes: Set<String>?, language: String): List<il.transit.core.api.Place> = error("unexpected stops")
    override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<il.transit.core.api.GeocodeMatch> = error("unexpected geocode")
    override suspend fun reverseGeocode(at: LatLon, language: String, max: Int): List<il.transit.core.api.GeocodeMatch> = error("unexpected reverseGeocode")
    override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String): il.transit.core.api.StopTimesResponse = error("unexpected stopTimes")
    override suspend fun trip(tripId: String, language: String): Itinerary = error("unexpected trip")
    override suspend fun mapTrips(box: il.transit.core.geo.BBox, start: Instant, end: Instant, zoom: Double, language: String): List<il.transit.core.api.TripSegment> =
        error("unexpected mapTrips")
}
