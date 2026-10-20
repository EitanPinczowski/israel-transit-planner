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
 * R2: a leg's alert (and its "at the stop" end) waits until the rider has boarded and the vehicle is
 * at most `earliestAlert` (20 min) from its stop. The two bugs T5's GPS replay found: a one-minute
 * first bus whose stop is near the origin, and a loop line passing the stop mid-ride.
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
}
