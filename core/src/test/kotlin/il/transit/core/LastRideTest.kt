package il.transit.core

import il.transit.core.api.BudgetedTransitApi
import il.transit.core.api.Endpoint
import il.transit.core.api.Itinerary
import il.transit.core.api.PlanResponse
import il.transit.core.api.Preferences
import il.transit.core.geo.LatLon
import il.transit.core.plan.LastRide
import il.transit.core.plan.LastRideFinder
import il.transit.core.present.lastRideNote
import il.transit.core.user.UserSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class LastRideTest {
    private val a = LatLon(31.2525, 34.7915)
    private val b = LatLon(32.0839, 34.7983)

    /** A one-bus trip leaving at [isoUtc], 90 minutes long. */
    private fun trip(isoUtc: String): Itinerary {
        val s = Instant.parse(isoUtc)
        return itinerary(leg("BUS", place("a", a), place("b", b), s, s.plusSeconds(90 * 60)))
    }

    /** A trip leaving at [isoUtc] that waits out Shabbat: [hours] long. */
    private fun longTrip(isoUtc: String, hours: Long): Itinerary {
        val s = Instant.parse(isoUtc)
        return itinerary(leg("BUS", place("a", a), place("b", b), s, s.plusSeconds(hours * 3600)))
    }

    /** The fake answers depart-at searches from [after], keeping only trips after the asked time. */
    private fun finder(evening: List<Itinerary>, after: List<Itinerary>): Pair<FakeTransitApi, LastRideFinder> {
        val api = FakeTransitApi()
        api.onPlan = { req -> PlanResponse(if (req.arriveBy) evening else after.filter { it.start >= req.time }) }
        return api to LastRideFinder(BudgetedTransitApi(api, LastRideFinder.BUDGET))
    }

    @Test fun `friday - last trip before Shabbat and when service resumes`() = runBlocking {
        // Friday 9 Oct: last bus 15:29 Israel (12:29Z); next one Saturday 19:40 (16:40Z).
        val (api, f) = finder(
            evening = listOf(trip("2026-10-09T11:44:00Z"), trip("2026-10-09T12:29:00Z"), trip("2026-10-09T12:14:00Z")),
            // MOTIS also offers a Friday 16:20 bus that waits out Shabbat: not "the next trip".
            after = listOf(longTrip("2026-10-09T13:20:00Z", 27), trip("2026-10-10T16:40:00Z"), trip("2026-10-10T17:10:00Z")),
        )
        val lr = f.find(Endpoint.Coord(a), Endpoint.Coord(b), LocalDate.of(2026, 10, 9), Preferences(), "he")
        assertEquals(2, api.calls.size)
        // 1st: arrive by Saturday 03:00 Israel; 2nd: depart a minute after the last trip.
        assertTrue(api.planRequests[0].arriveBy)
        assertEquals(Instant.parse("2026-10-10T00:00:00Z"), api.planRequests[0].time)
        assertEquals(Instant.parse("2026-10-09T12:30:00Z"), api.planRequests[1].time)
        assertEquals(Instant.parse("2026-10-09T12:29:00Z"), lr.last!!.start)
        assertTrue(lr.longGap)

        // Morning search on Friday: not "soon", but Shabbat is coming, so it is shown.
        val note = lastRideNote(lr, trip("2026-10-09T06:00:00Z"))!!
        assertEquals("15:29", note.lastTime)
        assertFalse(note.selectedIsLast)
        assertEquals(DayOfWeek.SATURDAY, note.resumesDay)
        assertEquals("19:40", note.resumesTime)
        assertTrue(lastRideNote(lr, trip("2026-10-09T12:29:00Z"))!!.selectedIsLast)
    }

    @Test fun `weeknight - a night bus counts, and the note waits until it matters`() = runBlocking {
        // Monday: last departure 23:35 Israel (20:35Z); next one Tuesday 05:10.
        val (_, f) = finder(
            evening = listOf(trip("2026-10-05T19:35:00Z"), trip("2026-10-05T20:35:00Z")),
            after = listOf(trip("2026-10-06T02:10:00Z")),
        )
        val lr = f.find(Endpoint.Coord(a), Endpoint.Coord(b), LocalDate.of(2026, 10, 5), Preferences(), "he")
        assertFalse(lr.longGap) // 5 h 35 m: an ordinary night
        assertNull(lastRideNote(lr, trip("2026-10-05T12:00:00Z"))) // 15:00 - too early to bother
        val evening = lastRideNote(lr, trip("2026-10-05T18:00:00Z"))!! // 21:00 - within 3 h
        assertEquals("23:35", evening.lastTime)
        assertNull(evening.resumesTime)
        assertEquals("23:35", lastRideNote(lr, null, always = true)!!.lastTime) // "last trip back"
    }

    @Test fun `a night bus that arrives after 03_00 is found by the follow-up search`() = runBlocking {
        // Arrive-by 03:00 sees 23:35 as the last; a 00:35 night bus (arrives 03:10) exists too.
        val (api, f) = finder(
            evening = listOf(trip("2026-10-05T20:35:00Z")),
            after = listOf(trip("2026-10-05T21:35:00Z"), trip("2026-10-06T02:10:00Z")),
        )
        val lr = f.find(Endpoint.Coord(a), Endpoint.Coord(b), LocalDate.of(2026, 10, 5), Preferences(), "he")
        assertEquals(Instant.parse("2026-10-05T21:35:00Z"), lr.last!!.start) // 00:35 Israel
        assertEquals(Instant.parse("2026-10-06T02:10:00Z"), lr.next!!.start)
        assertEquals(3, api.calls.size) // the budget, exactly
    }

    @Test fun `a line that runs all night has no last trip to warn about`() = runBlocking {
        // 469 Be'er Sheva → Tel Aviv: hourly through the night (seen live, 2026-10-03).
        val (api, f) = finder(
            evening = listOf(trip("2026-10-05T20:35:00Z")),
            after = listOf(trip("2026-10-05T21:35:00Z"), trip("2026-10-05T22:35:00Z"), trip("2026-10-05T23:35:00Z")),
        )
        val lr = f.find(Endpoint.Coord(a), Endpoint.Coord(b), LocalDate.of(2026, 10, 5), Preferences(), "he")
        assertTrue(lr.runsAllNight)
        assertFalse(lr.longGap)
        assertNull(lastRideNote(lr, trip("2026-10-05T20:35:00Z"), always = true))
        assertEquals(3, api.calls.size)
    }

    @Test fun `trips outside the service day are ignored, and none found means no second request`() = runBlocking {
        val (api, f) = finder(evening = listOf(trip("2026-10-04T17:00:00Z")), after = emptyList())
        val lr = f.find(Endpoint.Coord(a), Endpoint.Coord(b), LocalDate.of(2026, 10, 5), Preferences(), "he")
        assertNull(lr.last)
        assertEquals(1, api.calls.size)
        assertNull(lastRideNote(lr, null, always = true))
    }

    @Test fun `when to look automatically, and service days`() {
        assertTrue(LastRideFinder.worthAsking(Instant.parse("2026-10-05T15:00:00Z"))) // Mon 18:00
        assertFalse(LastRideFinder.worthAsking(Instant.parse("2026-10-05T09:00:00Z"))) // Mon 12:00
        assertTrue(LastRideFinder.worthAsking(Instant.parse("2026-10-09T06:00:00Z"))) // Fri 09:00
        assertEquals(LocalDate.of(2026, 10, 5), LastRideFinder.serviceDay(Instant.parse("2026-10-05T22:30:00Z"))) // Tue 01:30
        assertEquals(LocalDate.of(2026, 10, 6), LastRideFinder.serviceDay(Instant.parse("2026-10-06T02:00:00Z"))) // Tue 05:00
    }

    @Test fun `accessible setting asks for step-free walking`() {
        val on = UserSettings(accessible = true).preferences()
        assertTrue(on.wheelchair)
        val q = il.transit.core.api.PlanRequest(Endpoint.Coord(a), Endpoint.Coord(b), Instant.EPOCH, preferences = on).toQuery()
        assertTrue("pedestrianProfile" to "WHEELCHAIR" in q)
        val off = il.transit.core.api.PlanRequest(Endpoint.Coord(a), Endpoint.Coord(b), Instant.EPOCH).toQuery()
        assertFalse(off.any { it.first == "pedestrianProfile" })
        assertFalse(LastRide(null, null).longGap)
    }
}
