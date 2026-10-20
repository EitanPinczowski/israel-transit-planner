package il.transit.core.ride

import il.transit.core.NOON
import il.transit.core.api.StreetModes
import il.transit.core.geo.LatLon
import il.transit.core.itinerary
import il.transit.core.leg
import il.transit.core.place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2: a leg's alert (and its "at the stop" end) waits until the leg is armed: by stop progress (its
 * listed stops passed in order up to the third from last), or by time (boarded and at most
 * `earliestAlert`, 20 min, from its stop). The two bugs T5's GPS replay found: a one-minute first bus
 * whose stop is near the origin, and a loop line passing the stop mid-ride; and the review's: an
 * earlier bus than planned, and a second bus caught early.
 */
class RideTrackerArmingTest {
    // 0.001° lat ≈ 111 m.
    private val home = place("home", LatLon(31.2500, 34.8000))
    private val board = place("A", LatLon(31.2530, 34.8000))
    private val alight = place("B", LatLon(31.2560, 34.8000)) // 330 m from home, 330 m from A
    private val far = place("C", LatLon(31.3500, 34.8000))

    /** Walk 4 min to A, a one-minute bus to B, then a long bus B → C (40 min). */
    private val trip = itinerary(
        leg(StreetModes.WALK, home, board, NOON, NOON.plusSeconds(240)),
        leg("BUS", board, alight, NOON.plusSeconds(240), NOON.plusSeconds(300)),
        leg("BUS", alight, far, NOON.plusSeconds(360), NOON.plusSeconds(360 + 40 * 60)),
    )
    private val longEnd = NOON.plusSeconds(360 + 40 * 60)

    private fun at(lat: Double) = LatLon(lat, 34.8000)

    @Test fun `no alert while walking to the first stop, even within 400 m of where it gets off`() {
        val t = RideTracker(trip)
        assertNull(t.update(home.latLon, NOON))
        assertNull(t.update(at(31.2525), NOON.plusSeconds(239))) // 390 m from B, one second before boarding
        assertEquals(0, t.currentLeg)
        val e = t.update(board.latLon, NOON.plusSeconds(240)) // boarded
        assertEquals(RideEvent.Approaching(0, "B", isLastLeg = false), e)
    }

    @Test fun `walking past the stop before boarding does not end the leg`() {
        val t = RideTracker(trip)
        assertNull(t.update(alight.latLon, NOON.plusSeconds(60))) // at B, before boarding at A
        assertEquals(0, t.currentLeg)
        assertEquals(RideEvent.Approaching(0, "B", isLastLeg = false), t.update(board.latLon, NOON.plusSeconds(240)))
    }

    @Test fun `passing the stop more than 20 min before the vehicle is due neither alerts nor ends the leg`() {
        val t = RideTracker(trip)
        t.update(board.latLon, NOON.plusSeconds(240))
        t.update(alight.latLon, NOON.plusSeconds(300)) // leg 0 done at B
        assertEquals(1, t.currentLeg)
        val early = longEnd.minusSeconds(20 * 60 + 1)
        assertNull(t.update(far.latLon, early)) // a loop: at C 20 min + 1 s early
        assertEquals(1, t.currentLeg)
        assertNull(t.update(at(31.3200), early.plusSeconds(1))) // back out, 3.3 km away, armed now
        assertEquals(RideEvent.Approaching(1, "C", isLastLeg = true), t.update(at(31.3480), longEnd.minusSeconds(60)))
    }

    @Test fun `exactly 20 min before the end the alert is armed`() {
        val t = RideTracker(trip)
        t.update(board.latLon, NOON.plusSeconds(240))
        t.update(alight.latLon, NOON.plusSeconds(300))
        assertEquals(RideEvent.Approaching(1, "C", isLastLeg = true), t.update(at(31.3480), longEnd.minusSeconds(20 * 60)))
    }

    @Test fun `the earliest alert is a parameter`() {
        val t = RideTracker(trip, earliestAlert = java.time.Duration.ofMinutes(5))
        t.update(board.latLon, NOON.plusSeconds(240))
        t.update(alight.latLon, NOON.plusSeconds(300))
        assertNull(t.update(at(31.3480), longEnd.minusSeconds(5 * 60 + 1)))
        assertTrue(t.update(at(31.3480), longEnd.minusSeconds(5 * 60)) is RideEvent.Approaching)
    }

    @Test fun `a leg whose end passed still moves on by time, armed or not`() {
        val t = RideTracker(trip)
        assertNull(t.update(home.latLon, NOON.plusSeconds(300 + 181))) // leg 0 over by time, leg 1 not boarded
        assertEquals(1, t.currentLeg)
    }

    // A bus line due north along 34.80 with four listed stops 1.1 km apart: P → S1..S4 → Q.
    private val p = place("P", LatLon(31.3000, 34.8000))
    private val q = place("Q", LatLon(31.3500, 34.8000))
    private val listed = (1..4).map { place("S$it", LatLon(31.3000 + it * 0.01, 34.8000)) }

    private fun busPQ(start: java.time.Instant, minutes: Long) =
        leg("BUS", p, q, start, start.plusSeconds(minutes * 60)).copy(intermediateStops = listed)

    /** Rides P → Q at an even pace from [from] for [minutes], a fix every 20 s; the alerts with their time. */
    private fun ride(t: RideTracker, from: java.time.Instant, minutes: Long): List<Pair<java.time.Instant, RideEvent.Approaching>> {
        val steps = minutes * 3
        return (0..steps).mapNotNull { k ->
            val time = from.plusSeconds(k * 20)
            (t.update(at(31.3000 + 0.05 * k / steps), time) as? RideEvent.Approaching)?.let { time to it }
        }
    }

    @Test fun `an earlier bus as long as the leg (12 min early on a 12-min leg) alerts before the stop`() {
        val t = RideTracker(itinerary(busPQ(NOON, 12)))
        val alerts = ride(t, NOON.minusSeconds(12 * 60), 12) // 11:48 → 12:00, due 12:00 → 12:12
        assertEquals(listOf(RideEvent.Approaching(0, "Q", isLastLeg = true)), alerts.map { it.second })
        assertTrue("fired at ${alerts.single().first}, at the stop", alerts.single().first.isBefore(NOON.minusSeconds(60)))
    }

    @Test fun `an earlier bus that arrives before the planned one leaves (10 min early on a 5-min leg) still alerts`() {
        val t = RideTracker(itinerary(busPQ(NOON, 5)))
        val alerts = ride(t, NOON.minusSeconds(10 * 60), 5)
        assertEquals(listOf(RideEvent.Approaching(0, "Q", isLastLeg = true)), alerts.map { it.second })
    }

    @Test fun `a second bus caught early alerts on time, not held behind the first`() {
        // Bus 1 A → P with no listed stops, due 12:00–12:12, ridden 15 min early: armed by time only, its
        // alert comes late, at 12:00 at P (the documented cost). Bus 2 must not wait for bus 1's end + 3 min.
        val a = place("A", LatLon(31.2500, 34.8000))
        val t = RideTracker(itinerary(leg("BUS", a, p, NOON, NOON.plusSeconds(720)), busPQ(NOON.plusSeconds(900), 20)))
        var time = NOON.minusSeconds(900)
        var lat = 31.2500
        while (lat < 31.3000) { assertNull(t.update(at(lat), time)); lat += 0.0025; time = time.plusSeconds(36) }
        val alerts = ride(t, NOON, 8) // bus 2 at 12:00–12:08, due 12:15–12:35
        val (firedAt, alert) = alerts.last()
        assertEquals(RideEvent.Approaching(1, "Q", isLastLeg = true), alert)
        assertTrue("bus 2 alerted at $firedAt", firedAt.isBefore(NOON.plusSeconds(8 * 60)))
    }

    @Test fun `without stops passed in order, the stop before yours alerts from 20 min before the end`() {
        val end = NOON.plusSeconds(40 * 60)
        val t = RideTracker(itinerary(busPQ(NOON, 40)))
        val s4 = listed.last().latLon // 1.1 km from Q: only the stop-before rule can fire
        assertNull(t.update(s4, end.minusSeconds(20 * 60 + 1)))
        assertEquals(RideEvent.Approaching(0, "Q", isLastLeg = true), t.update(s4, end.minusSeconds(20 * 60)))
    }

    @Test fun `a loop past your stop mid-ride does not arm the leg, the stops after it in order do`() {
        val t = RideTracker(itinerary(busPQ(NOON, 60)))
        assertNull(t.update(at(31.3100), NOON.plusSeconds(600))) // S1
        assertNull(t.update(at(31.3200), NOON.plusSeconds(1200))) // S2
        assertNull(t.update(q.latLon, NOON.plusSeconds(1500))) // the loop, at Q 35 min early
        assertEquals(0, t.currentLeg)
        assertNull(t.update(at(31.3300), NOON.plusSeconds(1800))) // S3: armed now, 2.2 km out
        assertEquals(RideEvent.Approaching(0, "Q", isLastLeg = true), t.update(at(31.3400), NOON.plusSeconds(2100))) // S4
    }

    @Test fun `stops are matched in order - one may be skipped, two may not`() {
        val t = RideTracker(itinerary(busPQ(NOON, 60)))
        assertNull(t.update(at(31.3200), NOON.plusSeconds(600))) // S2 (S1 skipped): matched
        assertEquals(RideEvent.Approaching(0, "Q", isLastLeg = true), t.update(at(31.3400), NOON.plusSeconds(900))) // S4 (S3 skipped)
        val u = RideTracker(itinerary(busPQ(NOON, 60)))
        assertNull(u.update(at(31.3300), NOON.plusSeconds(600))) // S3 straight from P: two skipped, not matched
        assertNull(u.update(at(31.3400), NOON.plusSeconds(900))) // S4: still not armed, 45 min early
    }
}
