package il.transit.core.ride

import il.transit.core.NOON
import il.transit.core.geo.LatLon
import il.transit.core.itinerary
import il.transit.core.leg
import il.transit.core.place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * F7: a late bus keeps its "get off at the next stop" alert while the rider waits at the boarding stop or
 * rides the route, up to [RideTracker.MAX_LATE] past the planned end. A rider who leaves the route (walks
 * away, takes a car) loses it as before: 3 min past the planned end, or a minute after leaving if later.
 * The GPS replays of 5, 10 and 20 min late buses are in [RideReplayTest].
 */
class RideTrackerLateTest {
    // A bus line due north along 34.80, P → S1..S4 → Q, 1.1 km between stops; then a second bus Q → R.
    private val p = place("P", LatLon(31.3000, 34.8000))
    private val q = place("Q", LatLon(31.3500, 34.8000))
    private val r = place("R", LatLon(31.4500, 34.8000))
    private val listed = (1..4).map { place("S$it", LatLon(31.3000 + it * 0.01, 34.8000)) }
    private val end = NOON.plusSeconds(20 * 60)

    /** Bus P → Q 12:00–12:20, bus Q → R 12:40–13:10. */
    private val trip = itinerary(
        leg("BUS", p, q, NOON, end).copy(intermediateStops = listed),
        leg("BUS", q, r, NOON.plusSeconds(40 * 60), NOON.plusSeconds(70 * 60)),
    )

    private fun at(lat: Double, lon: Double = 34.8000) = LatLon(lat, lon)

    /** Fixes every 20 s at [pos] from [from] to [to]; any alert they raise. */
    private fun stay(t: RideTracker, pos: LatLon, from: Instant, to: Instant): List<RideEvent?> =
        generateSequence(from) { it.plusSeconds(20) }.takeWhile { !it.isAfter(to) }.map { t.update(pos, it) }.toList()

    /** Rides P → Q in [minutes] from [from], a fix every 20 s; the alerts with their time. */
    private fun ride(t: RideTracker, from: Instant, minutes: Long): List<Pair<Instant, RideEvent>> {
        val steps = minutes * 3
        return (0..steps).mapNotNull { k ->
            val time = from.plusSeconds(k * 20)
            t.update(at(31.3000 + 0.05 * k / steps), time)?.let { time to it }
        }
    }

    @Test fun `a bus 25 min late, waited for at the stop, still alerts before Q`() {
        val t = RideTracker(trip)
        assertTrue(stay(t, p.latLon, NOON, NOON.plusSeconds(25 * 60)).all { it == null })
        assertEquals(0, t.currentLeg)
        val alerts = ride(t, NOON.plusSeconds(25 * 60), 20)
        assertEquals(RideEvent.Approaching(0, "Q", isLastLeg = false), alerts.first().second)
        assertTrue("alerted at ${alerts.first().first}", alerts.first().first.isBefore(end.plusSeconds(25 * 60)))
    }

    @Test fun `waiting past the hard bound gives the leg up`() {
        assertEquals(Duration.ofMinutes(30), RideTracker.MAX_LATE)
        val t = RideTracker(trip)
        stay(t, p.latLon, NOON, end.plus(RideTracker.MAX_LATE))
        assertEquals("still waiting at the bound", 0, t.currentLeg)
        assertNull(t.update(p.latLon, end.plus(RideTracker.MAX_LATE).plusSeconds(1)))
        assertEquals("given up 1 s past it", 1, t.currentLeg)
    }

    @Test fun `a bus still being ridden past the bound loses its alert`() {
        val t = RideTracker(trip)
        stay(t, p.latLon, NOON, NOON.plusSeconds(35 * 60))
        assertTrue(ride(t, NOON.plusSeconds(35 * 60), 20).none { it.second is RideEvent.Approaching && (it.second as RideEvent.Approaching).legIndex == 0 })
    }

    @Test fun `walking away from the stop while waiting drops the alert as before`() {
        val t = RideTracker(trip)
        stay(t, p.latLon, NOON, end.plusSeconds(60)) // waiting, the bus late
        // walks west, 1 m/s: 200 m off the route after 200 s
        var time = end.plusSeconds(80)
        var lon = 34.8000
        while (t.currentLeg == 0 && time.isBefore(end.plusSeconds(20 * 60))) {
            t.update(at(31.3000, lon), time)
            lon -= 20.0 / 95_000 // ~20 m per 20 s fix
            time = time.plusSeconds(20)
        }
        assertEquals(1, t.currentLeg)
        assertTrue("dropped at $time: within 2 min of leaving the route", time.isBefore(end.plusSeconds(80 + 200 + 120)))
    }

    @Test fun `a rider off the route before the planned end loses the leg 3 min after it, as before`() {
        val t = RideTracker(trip)
        val car = at(31.3200, 34.8300) // driving, 2.8 km east of the line
        assertNull(t.update(car, end.minusSeconds(120)))
        assertNull(t.update(car, end.plusSeconds(180)))
        assertEquals(0, t.currentLeg)
        t.update(car, end.plusSeconds(181))
        assertEquals(1, t.currentLeg)
        assertTrue("no alert for Q once dropped", stay(t, q.latLon, end.plusSeconds(200), end.plusSeconds(400)).none { it is RideEvent.Approaching && it.legIndex == 0 })
    }

    @Test fun `one 300 m GPS jump while riding a late bus does not drop it`() {
        val t = RideTracker(trip)
        stay(t, p.latLon, NOON, NOON.plusSeconds(10 * 60))
        assertNull(t.update(at(31.3100), end.plusSeconds(240))) // S1, the bus 10 min late
        assertNull(t.update(at(31.3150, 34.8032), end.plusSeconds(260))) // thrown 300 m east
        assertNull(t.update(at(31.3160), end.plusSeconds(280))) // back on the line
        assertEquals(0, t.currentLeg)
        val rest = (1..30).map { k -> t.update(at(31.3160 + 0.034 * k / 30), end.plusSeconds(280L + k * 20)) } // on to Q
        assertTrue(rest.contains(RideEvent.Approaching(0, "Q", isLastLeg = false)))
    }

    @Test fun `a leg with no polyline or stops in between uses the line between its stops`() {
        val t = RideTracker(itinerary(leg("BUS", p, q, NOON, end), leg("BUS", q, r, NOON.plusSeconds(40 * 60), NOON.plusSeconds(70 * 60))))
        assertNull(t.update(at(31.3250), end.plusSeconds(600))) // halfway along P → Q, 10 min late
        assertEquals(0, t.currentLeg)
        assertEquals(RideEvent.Approaching(0, "Q", isLastLeg = false), t.update(at(31.3470), end.plusSeconds(900)))
    }
}
