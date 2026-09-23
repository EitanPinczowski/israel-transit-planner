package il.transit.core

import il.transit.core.api.StreetModes
import il.transit.core.geo.LatLon
import il.transit.core.history.History
import il.transit.core.history.TripRecord
import il.transit.core.ride.RideEvent
import il.transit.core.ride.RideTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideTrackerTest {
    // Walk → bus north along 34.80 (stops every ~1.1 km) → walk. 0.01° lat ≈ 1.1 km.
    private val home = place("home", LatLon(31.2500, 34.8000))
    private val board = place("A", LatLon(31.2510, 34.8000))
    private val s1 = place("S1", LatLon(31.2610, 34.8000))
    private val s2 = place("S2", LatLon(31.2710, 34.8000))
    private val alight = place("B", LatLon(31.2810, 34.8000))
    private val dest = place("dest", LatLon(31.2820, 34.8010))

    private val trip = itinerary(
        leg(StreetModes.WALK, home, board, NOON, NOON.plusSeconds(120)),
        leg("BUS", board, alight, NOON.plusSeconds(180), NOON.plusSeconds(900)).copy(intermediateStops = listOf(s1, s2)),
        leg(StreetModes.WALK, alight, dest, NOON.plusSeconds(900), NOON.plusSeconds(1020)),
    )

    private fun at(lat: Double) = LatLon(lat, 34.8000)

    @Test fun `alerts once when passing the stop before yours, then finishes at the destination`() {
        val t = RideTracker(trip)
        val events = mutableListOf<RideEvent>()
        var clock = NOON.plusSeconds(200)
        // Ride north in ~110 m steps from the boarding stop to the alighting stop.
        var lat = 31.2510
        while (lat <= 31.2811) {
            t.update(at(lat), clock)?.let(events::add)
            lat += 0.001
            clock = clock.plusSeconds(20)
        }
        t.update(dest.latLon, clock)?.let(events::add)
        val alerts = events.filterIsInstance<RideEvent.Approaching>()
        assertEquals(1, alerts.size)
        assertEquals("B", alerts.single().stopName)
        assertTrue(alerts.single().isLastLeg)
        assertEquals(RideEvent.Finished, events.last())
    }

    @Test fun `the alert fires near S2 even before the 400 m radius`() {
        val t = RideTracker(trip, approachM = 200.0)
        assertNull(t.update(at(31.2600), NOON.plusSeconds(300)))
        val e = t.update(at(31.2705), NOON.plusSeconds(500)) // 55 m from S2, ~1.1 km from B
        assertTrue(e is RideEvent.Approaching)
    }

    @Test fun `without intermediate stops it falls back to distance`() {
        val t = RideTracker(trip.copy(legs = trip.legs.map { it.copy(intermediateStops = emptyList()) }))
        assertNull(t.update(at(31.2710), NOON.plusSeconds(500))) // 1.1 km out: quiet
        assertTrue(t.update(at(31.2780), NOON.plusSeconds(600)) is RideEvent.Approaching) // 330 m
        assertNull(t.update(at(31.2790), NOON.plusSeconds(620))) // once only
    }

    @Test fun `a trip long past its arrival finishes even without GPS reaching the end`() {
        val t = RideTracker(trip)
        assertEquals(RideEvent.Finished, t.update(at(31.20), NOON.plusSeconds(1020 + 31 * 60)))
    }
}

class HistoryTest {
    private fun rec(daysAgo: Long, to: String, mode: String = "TRIP", saved: Int? = null) = TripRecord(
        startedAtEpoch = NOON.minusSeconds(daysAgo * 86_400).epochSecond,
        from = "home", to = to, mode = mode, transitMin = 30, walkMin = 10, transfers = 1, savedMin = saved,
    )

    @Test fun `stats count the week, hours, savings and the top destination`() {
        val records = listOf(rec(1, "BGU"), rec(2, "BGU", "BETTER_START", 12), rec(10, "Tel Aviv", "DROP_OFF", 7))
        val s = History.stats(records, NOON)
        assertEquals(3, s.trips)
        assertEquals(2, s.tripsThisWeek)
        assertEquals(1.5, s.transitHours, 1e-9)
        assertEquals(19, s.minutesSaved)
        assertEquals(2, s.featureTrips)
        assertEquals("BGU", s.topDestination)
    }

    @Test fun `records come from an itinerary, newest first, capped, and round-trip as JSON`() {
        val a = place("A", LatLon(31.0, 34.0))
        val b = place("B", LatLon(31.1, 34.0))
        val it = itinerary(
            leg(StreetModes.WALK, a, a, NOON, NOON.plusSeconds(300)),
            leg("BUS", a, b, NOON.plusSeconds(300), NOON.plusSeconds(2100)),
            transfers = 0,
        )
        val r = TripRecord.from(it, "A", "B", "TRIP", NOON, savedMin = -3)
        assertEquals(30, r.transitMin)
        assertEquals(5, r.walkMin)
        assertNull(r.savedMin) // a "saving" of -3 is not a saving
        var list = emptyList<TripRecord>()
        repeat(History.MAX_RECORDS + 5) { i -> list = History.add(list, r.copy(startedAtEpoch = i.toLong())) }
        assertEquals(History.MAX_RECORDS, list.size)
        assertEquals((History.MAX_RECORDS + 4).toLong(), list.first().startedAtEpoch)
        assertEquals(list, History.decode(History.encode(list)))
        assertTrue(History.decode("nope").isEmpty())
    }
}
