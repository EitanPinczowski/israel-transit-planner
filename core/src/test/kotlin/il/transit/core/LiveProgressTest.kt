package il.transit.core

import il.transit.core.api.StreetModes
import il.transit.core.geo.LatLon
import il.transit.core.present.LegPalette
import il.transit.core.remind.LiveProgress
import il.transit.core.ride.RideTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveProgressTest {
    // Walk 12:00–12:02, wait, bus 5 12:03–12:15 (A → S1 → S2 → B, north along 34.80), train 12:20–12:40.
    private val home = place("home", LatLon(31.2500, 34.8000))
    private val a = place("A", LatLon(31.2510, 34.8000))
    private val s1 = place("S1", LatLon(31.2610, 34.8000))
    private val s2 = place("S2", LatLon(31.2710, 34.8000))
    private val b = place("B", LatLon(31.2810, 34.8000))
    private val c = place("C", LatLon(31.4000, 34.8000))

    private val walk = leg(StreetModes.WALK, home, a, NOON, NOON.plusSeconds(120))
    private val bus = leg("BUS", a, b, NOON.plusSeconds(180), NOON.plusSeconds(900))
        .copy(routeShortName = "5", intermediateStops = listOf(s1, s2))
    private val train = leg("RAIL", b, c, NOON.plusSeconds(1200), NOON.plusSeconds(2400)).copy(routeShortName = "406")
    private val trip = itinerary(walk, bus, train, transfers = 1)

    private fun at(lat: Double) = LatLon(lat, 34.8000)

    @Test fun `a 3-leg trip - one segment per leg in the leg colours, a point per boarding`() {
        val p = LiveProgress.start(trip)!!
        assertEquals(listOf(180, 1020, 1200), p.segments.map { it.length }) // the wait at B rides with the bus
        assertEquals(LegPalette.colors(trip.legs), p.segments.map { it.color })
        assertEquals(listOf(180, 1200), p.points)
        assertEquals(2400, p.max)
        assertEquals(0, p.progress)
        assertNull(p.chip)
    }

    @Test fun `a walk-only start - the countdown walks to the stop and waits there`() {
        val leave = LiveProgress.countdown(trip, boardAt = NOON.plusSeconds(180), now = NOON)!!
        assertEquals(0, leave.progress)
        assertNull(leave.chip) // the chip shows the notification's own countdown
        assertEquals(120, LiveProgress.countdown(trip, NOON.plusSeconds(180), NOON.plusSeconds(120))!!.progress)
        // A bus 4 min late: the tracker holds short of the stop, then waits on the point.
        assertEquals(0, LiveProgress.countdown(trip, NOON.plusSeconds(420), NOON.plusSeconds(120))!!.progress)
        assertEquals(180, LiveProgress.countdown(trip, NOON.plusSeconds(420), NOON.plusSeconds(500))!!.progress)
        // A trip that is only a walk: one grey segment, no point.
        val stroll = LiveProgress.start(itinerary(walk))!!
        assertEquals(1, stroll.segments.size)
        assertTrue(stroll.points.isEmpty())
    }

    @Test fun `progress at the change - the tracker on the change point, the chip counts stops`() {
        val t = RideTracker(trip)
        val now = NOON.plusSeconds(900)
        t.update(b.latLon, NOON.plusSeconds(890).minusSeconds(1))
        val onBus = LiveProgress.ride(trip, t.progress(at(31.2805), NOON.plusSeconds(890)), t.currentLeg, NOON.plusSeconds(890))!!
        assertEquals(LiveProgress.Chip.Stops(1), onBus.chip)
        // Off the bus at B at 12:15: the tracker now follows the train leg.
        t.update(b.latLon, now)
        val p = t.progress(b.latLon, now)!!
        assertEquals("406", p.line)
        val live = LiveProgress.ride(trip, p, t.currentLeg, now)!!
        assertTrue(live.progress in 900..1200) // between getting off and the train's point
        assertEquals(1200, live.points.last())
    }

    @Test fun `after the last stop - the tracker is at least at the end of the last ride`() {
        val done = LiveProgress.ride(trip, null, 2, NOON.plusSeconds(2400))!!
        assertEquals(2400, done.progress)
        // Off early (GPS says the ride is over before the timetable does): at the end of the train.
        assertEquals(2400, LiveProgress.ride(trip, null, 2, NOON.plusSeconds(2000))!!.progress)
        // With a walk after: it keeps moving with the clock, never past the end.
        val home2 = place("home2", LatLon(31.4010, 34.8000))
        val withWalk = itinerary(walk, bus, train, leg(StreetModes.WALK, c, home2, NOON.plusSeconds(2400), NOON.plusSeconds(2700)))
        assertEquals(2400, LiveProgress.ride(withWalk, null, 2, NOON.plusSeconds(2300))!!.progress)
        assertEquals(2550, LiveProgress.ride(withWalk, null, 2, NOON.plusSeconds(2550))!!.progress)
        assertEquals(2700, LiveProgress.ride(withWalk, null, 2, NOON.plusSeconds(9999))!!.progress)
    }

    @Test fun `a delay holds the tracker back and never past the bus being ridden`() {
        val t = RideTracker(trip)
        // At S1 (due 12:07 by interpolation) at 12:12: 5 min late.
        val now = NOON.plusSeconds(720)
        t.update(s1.latLon, now)
        val p = t.progress(s1.latLon, now)!!
        assertTrue(p.delayMin >= 4)
        val live = LiveProgress.ride(trip, p, t.currentLeg, now)!!
        assertEquals(720 - 60 * p.delayMin, live.progress)
        assertEquals(LiveProgress.Chip.Stops(p.stopsLeft), live.chip)
        // Very late, still on the bus past its planned end: held at the end of the bus segment.
        val stuck = p.copy(delayMin = 0)
        assertEquals(900, LiveProgress.ride(trip, stuck, 0, NOON.plusSeconds(1100))!!.progress)
    }

    @Test fun `a leg with no stops listed shows minutes to the stop`() {
        val bare = itinerary(walk, bus.copy(intermediateStops = emptyList()))
        val t = RideTracker(bare)
        val now = NOON.plusSeconds(400)
        t.update(s1.latLon, now)
        val live = LiveProgress.ride(bare, t.progress(s1.latLon, now), t.currentLeg, now)!!
        assertTrue(live.chip is LiveProgress.Chip.Minutes)
        assertTrue((live.chip as LiveProgress.Chip.Minutes).n > 0)
    }

    @Test fun `the chip never reads 0 min - at the stop it shows the icon only`() {
        val bare = itinerary(walk, bus.copy(intermediateStops = emptyList()))
        val t = RideTracker(bare)
        val now = NOON.plusSeconds(400)
        t.update(s1.latLon, now)
        val p = t.progress(s1.latLon, now)!!
        assertNull(LiveProgress.ride(bare, p.copy(arriveAt = now), t.currentLeg, now)!!.chip)
        assertNull(LiveProgress.ride(bare, p.copy(arriveAt = now.minusSeconds(30)), t.currentLeg, now)!!.chip)
        assertEquals(LiveProgress.Chip.Minutes(1), LiveProgress.ride(bare, p.copy(arriveAt = now.plusSeconds(10)), t.currentLeg, now)!!.chip)
    }

    @Test fun `the ridden leg is the tracker's own - the same line and stop twice`() {
        // Bus 5 A → B, then bus 5 again B → A → B (a loop): names and line repeat.
        val back = leg("BUS", b, a, NOON.plusSeconds(960), NOON.plusSeconds(1500)).copy(routeShortName = "5")
        val again = leg("BUS", a, b, NOON.plusSeconds(1560), NOON.plusSeconds(2100)).copy(routeShortName = "5")
        val loop = itinerary(walk, bus.copy(intermediateStops = emptyList()), back, again)
        val t = RideTracker(loop)
        val now = NOON.plusSeconds(600)
        t.update(s1.latLon, now)
        assertEquals(0, t.currentLeg)
        val p = t.progress(s1.latLon, now)!!.copy(delayMin = 0)
        // Late on the first bus: held at its end (900), not at the end of the third leg's B.
        assertEquals(900, LiveProgress.ride(loop, p, t.currentLeg, NOON.plusSeconds(1700))!!.progress)
    }
}
