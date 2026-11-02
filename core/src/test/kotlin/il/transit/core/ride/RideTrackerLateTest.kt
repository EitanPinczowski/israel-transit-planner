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

    // --- Review round (boss, 2026-11-02): what an extended leg must not do. 0.001° lat ≈ 111 m. ---

    /** Fixes 20 s apart at these latitudes on the line, from [from]; every event they raise. */
    private fun track(t: RideTracker, from: Instant, vararg lats: Double): List<RideEvent?> =
        lats.mapIndexed { k, lat -> t.update(at(lat), from.plusSeconds(20L * k)) }

    @Test fun `a second bus back along the same street alerts - waiting at the transfer stop moves on to it`() {
        // Bus 1 P → Q (late), then bus 2 from Q back south to T, 555 m, on bus 1's street.
        val tt = place("T", LatLon(31.3450, 34.8000))
        val t = RideTracker(
            itinerary(
                leg("BUS", p, q, NOON, end).copy(intermediateStops = listed),
                leg("BUS", q, tt, end.plusSeconds(120), end.plusSeconds(360)),
            ),
        )
        stay(t, p.latLon, NOON, end.plusSeconds(5 * 60)) // bus 1 is 5 min late
        val ride = track(t, end.plusSeconds(6 * 60), 31.30, 31.31, 31.32, 31.33, 31.34, 31.3470)
        assertTrue("alerted (at S4, the stop before Q): $ride", RideEvent.Approaching(0, "Q", isLastLeg = false) in ride)
        // off at Q, but no fix within 80 m of it: the first is 111 m past it, still on bus 1's route
        assertNull(t.update(at(31.3510), end.plusSeconds(10 * 60)))
        assertEquals("waiting at bus 2's stop is bus 2", 1, t.currentLeg)
        val second = track(t, end.plusSeconds(12 * 60), 31.3500, 31.3490, 31.3480, 31.3470)
        assertTrue("bus 2 alerts: $second", RideEvent.Approaching(1, "T", isLastLeg = true) in second)
    }

    @Test fun `got off early and walks back along the bus street - the extended leg ends, it does not run 30 min`() {
        val t = RideTracker(trip)
        stay(t, p.latLon, NOON, end.plusSeconds(5 * 60))
        track(t, end.plusSeconds(6 * 60), 31.30, 31.305, 31.31, 31.315, 31.32) // rides to S2
        assertEquals(0, t.currentLeg)
        // off at S2, walking home back south on the same street, 1.2 m/s
        val walk = (1..10).map { k -> t.update(at(31.32 - 0.0002 * k), end.plusSeconds(8 * 60 + 20L * k)) }
        assertEquals("ended 150 m back: ${walk.size} fixes", 1, t.currentLeg)
        assertTrue(walk.none { it is RideEvent.Approaching })
    }

    @Test fun `rides past the stop with no fix at it - the leg ends as the distance grows`() {
        val t = RideTracker(trip, routeM = 1000.0) // the street goes on past Q: stay "on the route" past it
        stay(t, p.latLon, NOON, end.plusSeconds(5 * 60))
        val ride = track(t, end.plusSeconds(6 * 60), 31.31, 31.32, 31.33, 31.34, 31.3485)
        assertTrue("alerted (at S4, the stop before Q): $ride", RideEvent.Approaching(0, "Q", isLastLeg = false) in ride)
        track(t, end.plusSeconds(8 * 60), 31.3512, 31.3530) // 133 m past Q, then 333 m: 200 m past its closest
        assertEquals(1, t.currentLeg)
    }

    @Test fun `a bus the other way on the same street does not alert during the extension`() {
        val t = RideTracker(trip)
        // first fix in the extension 222 m from Q (inside approachM), then south, away from Q
        val ride = track(t, end.plusSeconds(5 * 60), 31.3480, 31.3460, 31.3440, 31.3420, 31.3400)
        assertTrue("no alert going the wrong way: $ride", ride.none { it is RideEvent.Approaching })
        assertEquals("150 m back along the route ends it", 1, t.currentLeg)
    }

    @Test fun `waiting at a short hop's stop within 400 m of its end - no alert until the bus moves`() {
        val a = place("A", LatLon(31.2530, 34.8000))
        val b = place("B", LatLon(31.2560, 34.8000)) // 333 m
        val t = RideTracker(itinerary(leg("BUS", a, b, NOON, NOON.plusSeconds(120)), leg("BUS", b, r, NOON.plusSeconds(600), NOON.plusSeconds(3600))))
        // the ride's GPS starts late, in the extension: the rider still waits at A
        assertTrue(stay(t, a.latLon, NOON.plusSeconds(6 * 60), NOON.plusSeconds(9 * 60)).all { it == null })
        assertEquals(0, t.currentLeg)
        val moves = (1..6).map { k -> t.update(at(31.2530 + 0.0004 * k), NOON.plusSeconds(9 * 60 + 15L * k)) }
        val k = moves.indexOfFirst { it == RideEvent.Approaching(0, "B", isLastLeg = false) }
        assertTrue("alerts once the bus has gone 150 m: $moves", k >= 3) // 0.0004° × 4 = 178 m ≥ 150 m
    }

    @Test fun `the same short hop forward alerts at once while the bus is on time`() {
        val a = place("A", LatLon(31.2530, 34.8000))
        val b = place("B", LatLon(31.2560, 34.8000))
        val t = RideTracker(itinerary(leg("BUS", a, b, NOON, NOON.plusSeconds(120))))
        assertEquals(RideEvent.Approaching(0, "B", isLastLeg = true), t.update(a.latLon, NOON)) // R2's rule, unchanged
    }

    @Test fun `hovering about 200 m from the route does not keep resetting the off-route timer`() {
        val t = RideTracker(trip)
        stay(t, p.latLon, NOON, end.plusSeconds(4 * 60))
        // alternately 190 m and 210 m west of the line (0.001° lon ≈ 95 m here)
        val west = listOf(0.0020, 0.0022)
        var time = end.plusSeconds(4 * 60)
        var k = 0
        while (t.currentLeg == 0 && k < 30) {
            t.update(at(31.3050, 34.8000 - west[k % 2]), time)
            time = time.plusSeconds(20)
            k++
        }
        assertEquals(1, t.currentLeg)
        assertTrue("ended after $k fixes", k <= 6)
    }

    @Test fun `a GPS gap then one off-route fix does not end the leg, a minute of them does`() {
        val t = RideTracker(trip)
        stay(t, p.latLon, NOON, end.plusSeconds(5 * 60))
        val off = at(31.3000, 34.8100) // 950 m east
        // no fixes for 10 min (a tunnel, a pocket), then one off the route
        assertNull(t.update(off, end.plusSeconds(15 * 60)))
        assertEquals("one fix is not leaving", 0, t.currentLeg)
        t.update(off, end.plusSeconds(15 * 60 + 59))
        assertEquals(0, t.currentLeg)
        t.update(off, end.plusSeconds(16 * 60))
        assertEquals("60 s of them is", 1, t.currentLeg)
    }
}
