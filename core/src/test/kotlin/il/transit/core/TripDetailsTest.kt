package il.transit.core

import il.transit.core.api.Alert
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.api.MotisJson
import il.transit.core.api.Place
import il.transit.core.api.StopTime
import il.transit.core.api.TimeRange
import il.transit.core.api.TripRef
import il.transit.core.api.TripSegment
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import il.transit.core.present.AlertText
import il.transit.core.present.StopRole
import il.transit.core.present.TripDetailsSession
import il.transit.core.present.departureRow
import il.transit.core.present.pointAlong
import il.transit.core.present.summarize
import il.transit.core.present.tripDetails
import il.transit.core.present.vehicleAt
import il.transit.core.present.vehicleBox
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Phase 8 B1: the trip sheet and the vehicle on the map. Recorded answers from 2026-10-03. */
class TripDetailsTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name.json")!!.readText()
    private val trip470 by lazy { MotisJson.decodeFromString(Itinerary.serializer(), fixture("trip_bus_470")) }
    private val segments by lazy {
        MotisJson.decodeFromString(ListSerializer(TripSegment.serializer()), fixture("map_trips_beersheva"))
    }
    private val bus470 = "20261003_23:30_il-Israel-MOT_585172431_031026"

    /** The user's leg on bus 470: Soroka (stop 1) → Nahal Sorek camp (stop 3), as a plan gives it. */
    private fun userLeg(): Leg {
        val t = trip470.legs.single()
        val stops = listOf(t.from) + t.intermediateStops + listOf(t.to)
        return t.copy(
            from = stops[1],
            to = stops[3],
            intermediateStops = listOf(stops[2]),
            startTime = stops[1].departure!!,
            endTime = stops[3].arrival!!,
        )
    }

    private fun at(utc: String) = Instant.parse("2026-10-03T${utc}Z")

    // --- rows ---

    @Test fun `every stop of the recorded 470, boarding and alighting marked, scheduled times only`() {
        val d = tripDetails(userLeg(), trip470, now = at("20:40:00"))
        assertEquals(8, d.rows.size)
        assertEquals("470", d.line)
        assertEquals(bus470, d.tripId)
        assertEquals(
            listOf(StopRole.BEFORE, StopRole.BOARD, StopRole.RIDE, StopRole.ALIGHT, StopRole.AFTER, StopRole.AFTER, StopRole.AFTER, StopRole.AFTER),
            d.rows.map { it.role },
        )
        // Israel time (UTC+3 in October); the last stop shows its arrival.
        assertEquals(listOf("23:30", "23:37", "23:42"), d.rows.take(3).map { it.scheduled })
        assertEquals("01:00", d.rows.last().scheduled)
        // No real-time on MOT lines: no live time, no delay, no alert — nothing to draw.
        assertFalse(d.realTime)
        assertTrue(d.rows.all { it.live == null && it.delayMin == null && !it.cancelled && it.alerts.isEmpty() })
        assertTrue(d.alerts.isEmpty())
        // 20:40 UTC: left Soroka (20:37), next is Merkaz Oren (20:42).
        assertEquals(2, d.nextIndex)
        assertEquals(listOf(true, true, false), d.rows.take(3).map { it.passed })
        assertTrue(d.rows[2].isNext)
        assertTrue(d.path.size > 10)
    }

    @Test fun `next stop moves with the clock and is gone once the trip has arrived`() {
        val d = tripDetails(userLeg(), trip470, now = at("20:00:00"))
        assertEquals(0, d.nextIndex)
        assertTrue(d.rows.none { it.passed })
        assertNull(tripDetails(userLeg(), trip470, now = at("22:00:01")).nextIndex)
    }

    @Test fun `without a trip answer the leg's own stops are shown, boarding to alighting`() {
        val d = tripDetails(userLeg(), trip = null, now = at("20:30:00"))
        assertEquals(listOf(StopRole.BOARD, StopRole.RIDE, StopRole.ALIGHT), d.rows.map { it.role })
        assertEquals("470", d.line)
    }

    @Test fun `a sibling platform with another id is matched by distance`() {
        val leg = userLeg().let { l -> l.copy(from = l.from.copy(stopId = "il-Israel-MOT_other", lat = l.from.lat + 0.0005)) }
        val d = tripDetails(leg, trip470, now = at("20:30:00"))
        assertEquals(StopRole.BOARD, d.rows[1].role)
    }

    /** A hand-built busofash-like trip: the only kind that could carry live data in Israel. */
    private fun liveTrip(): Itinerary {
        val a = LatLon(32.08, 34.78)
        fun stop(i: Int, sched: String, live: String, cancelled: Boolean = false, alerts: List<Alert> = emptyList()) = Place(
            name = "S$i", lat = a.lat + i * 0.01, lon = a.lon, stopId = "s$i",
            scheduledArrival = "2026-10-03T$sched:00+03:00", arrival = "2026-10-03T$live:00+03:00",
            scheduledDeparture = "2026-10-03T$sched:00+03:00", departure = "2026-10-03T$live:00+03:00",
            cancelled = cancelled, alerts = alerts,
        )
        val stopAlert = Alert(headerText = "התחנה הועתקה", descriptionText = "לרחוב הסמוך")
        val stops = listOf(
            stop(0, "23:00", "23:00"),
            stop(1, "23:05", "23:08"),
            stop(2, "23:10", "23:13", cancelled = true, alerts = listOf(stopAlert)),
            stop(3, "23:15", "23:17"),
        )
        val leg = Leg(
            mode = "BUS", from = stops[0], to = stops[3], intermediateStops = stops.subList(1, 3), duration = 1020,
            startTime = stops[0].departure!!, endTime = stops[3].arrival!!, realTime = true, routeShortName = "N1",
            headsign = "רידינג", tripId = "night-1",
            alerts = listOf(
                Alert(headerText = "עבודות", descriptionText = "מסלול עוקף"),
                Alert(headerText = "עבודות", descriptionText = "מסלול עוקף"), // duplicate folds
                Alert(headerText = "ישן", impactPeriod = listOf(TimeRange(end = "2026-10-01T00:00:00Z"))), // expired
            ),
        )
        return Itinerary(1020, leg.startTime, leg.endTime, 0, listOf(leg))
    }

    @Test fun `live times, delays, a cancelled stop and alerts show only when the answer has them`() {
        val trip = liveTrip()
        val d = tripDetails(trip.legs.single(), trip, now = Instant.parse("2026-10-03T20:09:00Z")) // 23:09
        assertTrue(d.realTime)
        assertEquals(listOf(null, "23:08", "23:13", "23:17"), d.rows.map { it.live })
        assertEquals(listOf(0, 3, 3, 2), d.rows.map { it.delayMin })
        assertEquals(listOf(false, false, true, false), d.rows.map { it.cancelled })
        // The next stop skips the cancelled one.
        assertEquals(3, d.nextIndex)
        assertEquals(listOf(AlertText("עבודות", "מסלול עוקף")), d.alerts)
        assertEquals("התחנה הועתקה", d.rows[2].alerts.single().header)
        assertEquals("רידינג", d.headsign)
    }

    @Test fun `alerts reach leg chips and departure rows, and nothing when there are none`() {
        val trip = liveTrip()
        val chips = summarize(trip).chips
        assertTrue(chips.single().alert)
        assertEquals(0, chips.single().legIndex)
        assertFalse(summarize(itinerary(userLeg())).chips.single().alert)

        val st = StopTime(
            place = Place("S", 32.0, 34.8, departure = "2026-10-03T23:00:00+03:00", scheduledDeparture = "2026-10-03T23:00:00+03:00"),
            mode = "BUS", routeShortName = "N1", tripId = "night-1",
            alerts = listOf(Alert(headerText = "שביתה")),
        )
        assertEquals("שביתה", departureRow(st).alerts.single().header)
        assertEquals("night-1", departureRow(st).tripId)
        assertTrue(departureRow(st.copy(alerts = emptyList())).alerts.isEmpty())
    }

    @Test fun `chip leg index skips the short walks the chips leave out`() {
        val t = trip470.legs.single()
        val p = t.from
        val start = t.start
        val hop = leg("WALK", p, p, start.minusSeconds(30), start) // 30 s: not a chip
        val chips = summarize(itinerary(hop, t)).chips
        assertEquals(1, chips.size)
        assertEquals(1, chips.single().legIndex)
    }

    // --- vehicle position ---

    @Test fun `the 470 is half way along its first hop at 20 33 30, by the timetable`() {
        val m = vehicleAt(segments, bus470, at("20:33:30"))!!
        assertFalse(m.realTime)
        assertFalse(m.atStop)
        val hop = segments.single { it.tripId == bus470 }
        val path = hop.path()
        assertTrue(Geo.distanceToLineM(m.at, path) < 5)
        val total = Geo.lengthM(path)
        val along = Geo.alongLineM(m.at, path)
        assertEquals(0.5, along / total, 0.05)
        // Interpolates between refreshes: a minute later it is further on, same answer.
        val later = vehicleAt(segments, bus470, at("20:34:30"))!!
        assertTrue(Geo.alongLineM(later.at, path) > along)
    }

    @Test fun `unknown trip or a time outside the hops gives no mark`() {
        assertNull(vehicleAt(segments, "not-a-trip", at("20:33:30")))
        assertNull(vehicleAt(segments, bus470, at("20:50:00")))
    }

    @Test fun `between two hops the vehicle waits at the stop`() {
        val a = place("A", LatLon(31.0, 34.0))
        val b = place("B", LatLon(31.01, 34.0))
        val c = place("C", LatLon(31.02, 34.0))
        fun hop(f: Place, t: Place, dep: String, arr: String) =
            TripSegment(listOf(TripRef("x")), "BUS", f, t, "2026-10-03T$dep:00Z", "2026-10-03T$arr:00Z", realTime = true)
        val hops = listOf(hop(b, c, "20:05", "20:10"), hop(a, b, "20:00", "20:03"))
        val m = vehicleAt(hops, "x", at("20:04:00"))!!
        assertTrue(m.atStop)
        assertTrue(m.realTime)
        assertEquals(b.latLon, m.at)
        // No polyline: a straight line from stop to stop (half way from B to C).
        assertEquals(31.015, vehicleAt(hops, "x", at("20:07:30"))!!.at.lat, 1e-6)
    }

    @Test fun `point along a path is measured by length`() {
        val path = listOf(LatLon(31.0, 34.0), LatLon(31.0, 34.01), LatLon(31.0, 34.03))
        assertEquals(34.015, pointAlong(path, 0.5).lon, 1e-4)
        assertEquals(path.first(), pointAlong(path, -1.0))
        assertEquals(path.last(), pointAlong(path, 2.0))
    }

    @Test fun `the box covers the stretch the timetable puts the vehicle on, and nothing outside the trip`() {
        val d = tripDetails(userLeg(), trip470, now = at("20:33:00"))
        val box = vehicleBox(d, at("20:33:30"))!!
        val m = vehicleAt(segments, bus470, at("20:33:30"))!!
        assertTrue(m.at.lat in box.min.lat..box.max.lat && m.at.lon in box.min.lon..box.max.lon)
        // Only Be'er Sheva, not the whole road to Jerusalem.
        assertTrue(box.max.lat < 31.35)
        assertNotNull(vehicleBox(d, at("20:28:30"))) // about to leave
        assertNull(vehicleBox(d, at("20:20:00"))) // long before
        assertNull(vehicleBox(d, at("22:00:01"))) // arrived
    }

    // --- requests: 1 trip per tap (cached 30 s), 1 map/trips per 30 s while visible ---

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }

    @Test fun `request pattern - one trip per tap, one map trips per 30 s, none in the background`() = runTest {
        val fake = FakeTransitApi().apply {
            onTrip = { trip470 }
            onMapTrips = { _, _, _, zoom -> assertEquals(TripDetailsSession.ZOOM, zoom, 0.0); segments }
        }
        val clock = MutableClock(at("20:33:00"))
        val api = GuardedTransitApi(fake, clock)

        // Tap, close, tap again within 30 s: one trip request.
        TripDetailsSession(api, userLeg()).open(clock.now)
        clock.now = clock.now.plusSeconds(20)
        val session = TripDetailsSession(api, userLeg())
        session.open(clock.now)
        assertEquals(listOf("trip"), fake.calls)

        // Two minutes in front, ticking every second: a refresh at 0, 30, 60 and 90 s.
        val marks = ArrayList<LatLon?>()
        repeat(120) {
            marks += session.tick(clock.now, visible = true)?.at
            clock.now = clock.now.plusSeconds(1)
        }
        assertEquals(4, fake.calls.count { it == "mapTrips" })
        // The mark moved between refreshes (interpolated), not only on them.
        assertTrue(marks[1] != null && marks[1] != marks[2])

        // In the background: nothing asked, however long.
        repeat(300) {
            session.tick(clock.now, visible = false)
            clock.now = clock.now.plusSeconds(1)
        }
        assertEquals(4, fake.calls.count { it == "mapTrips" })

        // Back in front: one refresh straight away.
        session.tick(clock.now, visible = true)
        assertEquals(5, fake.calls.count { it == "mapTrips" })

        // A re-tap after 30 s asks for the trip again.
        TripDetailsSession(api, userLeg()).open(clock.now)
        assertEquals(2, fake.calls.count { it == "trip" })
    }

    @Test fun `no map trips request when the timetable puts the vehicle nowhere`() = runTest {
        val fake = FakeTransitApi().apply { onTrip = { trip470 } }
        val session = TripDetailsSession(fake, userLeg())
        session.open(at("23:00:00"))
        assertNull(session.tick(at("23:00:00"), visible = true))
        assertEquals(listOf("trip"), fake.calls)
    }

    @Test fun `a failed trip request falls back to the leg, a failed refresh keeps the last hops`() = runTest {
        var fail = true
        val fake = FakeTransitApi().apply {
            onTrip = { error("offline") }
            onMapTrips = { _, _, _, _ -> if (fail) error("offline") else segments }
        }
        val session = TripDetailsSession(fake, userLeg())
        // Only the user's stops are known (Soroka 20:37 on), so the box starts 2 min before that.
        assertEquals(3, session.open(at("20:33:00")).rows.size)
        assertNull(session.tick(at("20:33:00"), visible = true))
        assertEquals(listOf("trip"), fake.calls)
        assertNull(session.tick(at("20:35:00"), visible = true))
        assertEquals(1, fake.calls.count { it == "mapTrips" })
        // A failed refresh still waits 30 s before the next one.
        fail = false
        assertNull(session.tick(at("20:35:20"), visible = true))
        assertNotNull(session.tick(at("20:35:30"), visible = true)) // on the 470's first hop
        fail = true
        assertNotNull(session.tick(at("20:36:00"), visible = true))
        assertEquals(3, fake.calls.count { it == "mapTrips" })
    }
}
