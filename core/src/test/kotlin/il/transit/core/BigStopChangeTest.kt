package il.transit.core

import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.api.MotisJson
import il.transit.core.api.Place
import il.transit.core.api.PlanResponse
import il.transit.core.plan.BigStopChanges
import il.transit.core.plan.BigStopSession
import il.transit.core.present.BigStopHow
import il.transit.core.present.bigStopText
import il.transit.core.present.transfers
import il.transit.core.ride.RideEvent
import il.transit.core.ride.RideTracker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * Phase 10 D5: change at a big stop, same two vehicles. Recorded answers (2026-10-12, for Tue
 * 2026-10-13): `plan_bigstop_bgu_telaviv` and the `trip` of each bus around its changes
 * (`trip_bigstop_5`, `_370`, `_171`). Neither recorded change has a better big stop (the 5
 * meets the 370 going the other way; the 171 never reaches Tel Aviv's central station), so the
 * cases that find one use a second vehicle built here from the recorded 370's own stops.
 */
class BigStopChangeTest {
    private fun text(name: String) = javaClass.getResource("/fixtures/$name.json")!!.readText()
    private fun plan(name: String) = MotisJson.decodeFromString(PlanResponse.serializer(), text(name))
    private fun trip(name: String) = MotisJson.decodeFromString(Itinerary.serializer(), text(name))

    private val recorded = plan("plan_bigstop_bgu_telaviv")
    private val trips = listOf("trip_bigstop_5", "trip_bigstop_370", "trip_bigstop_171").map(::trip)
        .associateBy { it.legs.first { l -> l.isTransit }.tripId!! }

    private fun fakeWithRecordedTrips() = FakeTransitApi().apply { onTrip = { id -> trips[id] ?: error("no recording: $id") } }

    private fun itinWith(lines: List<String>): Itinerary =
        recorded.itineraries.first { it.legs.filter { l -> l.isTransit }.map { l -> l.routeShortName } == lines }

    // ---- recorded: nothing better, and when no request is sent ----------------------------------

    @Test fun `recorded street changes - both trips asked, no better stop`() = runTest {
        for (lines in listOf(listOf("370", "171"), listOf("5", "370", "171"))) {
            val api = fakeWithRecordedTrips()
            val itin = itinWith(lines)
            assertNotNull("$lines: a small-stop change to check", BigStopChanges.toCheck(itin))
            assertNull("$lines", BigStopChanges.find(itin, api, bufferMin = null))
            assertEquals("$lines: one trip per vehicle", listOf("trip", "trip"), api.calls)
        }
    }

    @Test fun `direct trip - no request`() = runTest {
        val api = fakeWithRecordedTrips()
        val train = recorded.itineraries.first { it.legs.count { l -> l.isTransit } == 1 }
        assertNull(BigStopChanges.find(train, api, null))
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test fun `change already at a big stop - no request`() = runTest {
        // 370 to Tel Aviv's central station, then a city bus from there (recorded 2026-10-05).
        val api = fakeWithRecordedTrips()
        val atCentral = plan("plan_pages_first").itineraries.first { itin ->
            transfers(itin).any { it.stop.contains("מרכזית") || itin.legs[it.fromLeg].to.name.contains("מרכזית") }
        }
        assertNull(BigStopChanges.toCheck(atCentral))
        assertNull(BigStopChanges.find(atCentral, api, null))
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test fun `big stops - station, central station, terminal, platform`() {
        fun p(name: String, desc: String? = null, modes: List<String>? = null, id: String? = null) =
            Place(name, 32.0, 34.8, stopId = id, description = desc, modes = modes)
        assertTrue(BigStopChanges.isBig(p("ת.מרכזית באר שבע/רציפים בינעירוני")))
        assertTrue(BigStopChanges.isBig(p("תחנה מרכזית חיפה")))
        assertTrue(BigStopChanges.isBig(p("מסוף אזורי חן")))
        assertTrue(BigStopChanges.isBig(p("Haifa Central Station")))
        assertTrue(BigStopChanges.isBig(p("Hof HaCarmel Terminal")))
        assertTrue(BigStopChanges.isBig(p("x", desc = "רחוב:   עיר: באר שבע רציף: 11   קומה:")))
        assertTrue(BigStopChanges.isBig(p("x", modes = listOf("TRAM"))))
        assertTrue(BigStopChanges.isBig(p("x", id = "il-Israel-MOT_37358"))) // Tel Aviv Merkaz, bundled list
        assertTrue(BigStopChanges.isBig(p("x"), vehicleMode = "REGIONAL_RAIL"))
        assertTrue(!BigStopChanges.isBig(p("מחלף לה גווארדייה", desc = "רחוב: לה גווארדיה עיר: תל אביב יפו רציף:   קומה:")))
        assertTrue(!BigStopChanges.isBig(p("ת. רכבת ההגנה/החרש")))
    }

    // ---- a better big stop: the recorded 370 and a second bus built on its stops -----------------

    private val bus370 = trips.values.first { it.legs.first { l -> l.isTransit }.routeShortName == "370" }
    private val stops370 = bus370.legs.first { it.isTransit }.let { listOf(it.from) + it.intermediateStops + it.to }
    private val soroka = stops370[1]
    private val laGuardia = stops370[3] // 06:16, a street stop on the Ayalon
    private val central = stops370[4] // Tel Aviv central station, floor 6: 06:20
    private val end = Place("קרליבך", 32.0653, 34.7828, stopId = "il-Israel-MOT_99001")

    private fun at(p: Place, time: String) = p.copy(arrival = time, departure = time, scheduledArrival = time, scheduledDeparture = time)

    /** Bus "18": [stops] in order with their times; the trip answer, first stop to last. */
    private fun bus18(vararg stops: Pair<Place, String>): Itinerary {
        val all = stops.map { (p, t) -> at(p, t) }
        val leg = Leg(
            mode = "BUS", from = all.first(), to = all.last(), duration = 0,
            startTime = stops.first().second, endTime = stops.last().second,
            routeShortName = "18", tripId = "T18", intermediateStops = all.subList(1, all.size - 1),
        )
        return Itinerary(0, leg.startTime, leg.endTime, 0, listOf(leg))
    }

    /** The planned option: 370 from Soroka to [change] on its trip, then bus 18 from its stop [board] to its last. */
    private fun option(second: Itinerary, change: Int, board: Int): Itinerary {
        val a = bus370.legs.first { it.isTransit }
        val b = second.legs.single()
        val bStops = listOf(b.from) + b.intermediateStops + b.to
        val first = a.copy(
            from = soroka, to = stops370[change], startTime = soroka.departure!!, endTime = stops370[change].arrival!!,
            intermediateStops = stops370.subList(2, change), legGeometry = null,
        )
        val ride = b.copy(from = bStops[board], startTime = bStops[board].departure!!, intermediateStops = bStops.subList(board + 1, bStops.lastIndex))
        val walkTo = Leg("WALK", soroka.copy(name = "start"), soroka, 300, "2026-10-13T05:02:00Z", soroka.departure!!)
        val sec = (ride.end.epochSecond - walkTo.start.epochSecond).toInt()
        return Itinerary(sec, walkTo.startTime, ride.endTime, 1, listOf(walkTo, first, ride))
    }

    private fun apiFor(second: Itinerary) = FakeTransitApi().apply {
        onTrip = { id -> if (id == "T18") second else bus370 }
    }

    // Bus 18 passes La Guardia 06:18 (2 min after the 370), then waits at the central station till 06:30.
    private val waits = bus18(laGuardia to "2026-10-13T06:18:00Z", central to "2026-10-13T06:30:00Z", end to "2026-10-13T06:40:00Z")

    @Test fun `a big stop with more slack - found, same arrival, re-cut legs pass the invariants`() = runTest {
        val itin = option(waits, change = 3, board = 0)
        val api = apiFor(waits)
        val c = BigStopChanges.find(itin, api, bufferMin = null)!!
        assertEquals(listOf("trip", "trip"), api.calls)
        assertEquals(central.name, c.stop)
        assertEquals(1, c.extraStops)
        assertEquals(10, c.slackMin)
        assertEquals(2, c.originalSlackMin)
        // Same departure, same arrival, same number of changes: never longer.
        assertEquals(itin.start, c.changed.start)
        assertEquals(itin.end, c.changed.end)
        assertEquals(itin.legs.last().end, c.changed.legs.last().end)
        assertEquals(itin.legs.first().start, c.changed.legs.first().start)
        assertEquals(1, transfers(c.changed).size)
        val t = transfers(c.changed).single()
        assertEquals(central.name, t.stop)
        assertEquals(600L, t.slackSec) // same stop id: no walk
        // The 370 now ends at the central station, La Guardia is a stop on the way.
        val first = c.changed.legs[c.fromLeg]
        assertEquals(central.stopId, first.to.stopId)
        assertEquals(laGuardia.stopId, first.intermediateStops.last().stopId)
        assertEquals(central.stopId, c.changed.legs[c.toLeg].from.stopId)
        RoutingInvariantsTest().checkItinerary("big-stop re-cut", c.changed)
        RoutingInvariantsTest().checkItinerary("original", itin)

        val text = bigStopText(c)
        assertEquals(BigStopHow.STAY_ON, text.how)
        assertEquals(1, text.stops)
        assertEquals(0, text.walkMin)
        assertEquals(central.name, text.boardStop)
        assertEquals("370", text.firstLine)
        assertEquals("18", text.secondLine)
        assertEquals(10, text.slackMin)
        assertEquals(2, text.originalSlackMin)
    }

    @Test fun `after Use this change the get-off alert is for the new stop`() = runTest {
        val c = BigStopChanges.find(option(waits, change = 3, board = 0), apiFor(waits), null)!!
        // At La Guardia (the old change, now the stop before the new one): "get off at the next stop" — the central station.
        val ride = RideTracker(c.changed)
        val now = Instant.parse("2026-10-13T06:16:00Z")
        val e = ride.update(laGuardia.latLon, now)
        assertEquals(RideEvent.Approaching(0, central.name, isLastLeg = false), e)
        // The original option alerted for La Guardia itself.
        assertEquals(RideEvent.Approaching(0, laGuardia.name, isLastLeg = false), RideTracker(c.original).update(laGuardia.latLon, now))
    }

    @Test fun `less slack than the original + 3 min - rejected`() = runTest {
        // Bus 18 leaves the central station 06:24: 4 min there vs 2 at La Guardia; not 3 more.
        val tight = bus18(laGuardia to "2026-10-13T06:18:00Z", central to "2026-10-13T06:24:00Z", end to "2026-10-13T06:40:00Z")
        val api = apiFor(tight)
        assertNull(BigStopChanges.find(option(tight, 3, 0), api, null))
        assertEquals(2, api.calls.size)
    }

    @Test fun `below the user's buffer - rejected`() = runTest {
        assertNull(BigStopChanges.find(option(waits, 3, 0), apiFor(waits), bufferMin = 12))
        assertNotNull(BigStopChanges.find(option(waits, 3, 0), apiFor(waits), bufferMin = 10))
    }

    @Test fun `a loop line passing the central station twice - only the lap after the 370 arrives`() = runTest {
        // Bus 18 starts at the central station 06:10 (before the 370 gets there), loops by La Guardia, and
        // passes the central station again at 06:30.
        val loop = bus18(
            central to "2026-10-13T06:10:00Z", laGuardia to "2026-10-13T06:18:00Z",
            central to "2026-10-13T06:30:00Z", end to "2026-10-13T06:40:00Z",
        )
        val itin = option(loop, change = 3, board = 1)
        val c = BigStopChanges.find(itin, apiFor(loop), null)!!
        val boarded = c.changed.legs[c.toLeg]
        assertEquals(central.stopId, boarded.from.stopId)
        assertEquals("2026-10-13T06:30:00Z", boarded.startTime)
        assertEquals(emptyList<Place>(), boarded.intermediateStops)
        assertEquals(itin.end, c.changed.end)
        RoutingInvariantsTest().checkItinerary("loop re-cut", c.changed)
    }

    // ---- owner decision B: a big stop on the other line within 300 m, the walk counted ----------

    /** Like Be'er Sheva central: the city platforms under another parent and name, [m] metres north of the 370's stop. */
    private fun cityPlatforms(m: Double) = Place(
        "ת.מרכזית/עירוניים לצפון", central.lat + m / 111_000.0, central.lon, stopId = "il-Israel-MOT_99002",
        description = "רחוב:   עיר: תל אביב יפו רציף: 11   קומה:",
    )

    @Test fun `platforms 200 m apart - matched, the walk counted and named`() = runTest {
        val city = cityPlatforms(200.0)
        val bus = bus18(laGuardia to "2026-10-13T06:18:00Z", city to "2026-10-13T06:32:00Z", end to "2026-10-13T06:40:00Z")
        val itin = option(bus, 3, 0)
        val c = BigStopChanges.find(itin, apiFor(bus), null)!!
        assertEquals(central.name, c.stop)
        assertEquals(city.name, c.boardStop)
        // 12 min between the two vehicles, ~200 s of it walking.
        assertEquals(8, c.slackMin)
        assertEquals(4, c.walkMin)
        val walk = c.changed.legs[c.fromLeg + 1]
        assertEquals("WALK", walk.mode)
        assertEquals(city.stopId, walk.to.stopId)
        assertEquals(1, transfers(c.changed).size)
        assertTrue(transfers(c.changed).single().slackSec in 480L..540L)
        assertEquals(itin.end, c.changed.end)
        RoutingInvariantsTest().checkItinerary("walk re-cut", c.changed)
        val text = bigStopText(c)
        assertEquals(4, text.walkMin)
        assertEquals(city.name, text.boardStop)
    }

    @Test fun `platforms 400 m apart - no match`() = runTest {
        val bus = bus18(laGuardia to "2026-10-13T06:18:00Z", cityPlatforms(400.0) to "2026-10-13T06:32:00Z", end to "2026-10-13T06:40:00Z")
        assertNull(BigStopChanges.find(option(bus, 3, 0), apiFor(bus), null))
    }

    @Test fun `a walk that eats the gain - no offer`() = runTest {
        // 8 min between the vehicles, minus ~200 s of walking: under the original 2 min + 3.
        val bus = bus18(laGuardia to "2026-10-13T06:18:00Z", cityPlatforms(200.0) to "2026-10-13T06:28:00Z", end to "2026-10-13T06:40:00Z")
        assertNull(BigStopChanges.find(option(bus, 3, 0), apiFor(bus), null))
    }

    @Test fun `same stop off the first bus, the second boarded at a terminal nearby - no stop count in the text`() = runTest {
        val terminal = Place("מסוף לה גווארדיה", laGuardia.lat + 100 / 111_000.0, laGuardia.lon, stopId = "il-Israel-MOT_99003")
        val bus = bus18(laGuardia to "2026-10-13T06:18:00Z", terminal to "2026-10-13T06:26:00Z", end to "2026-10-13T06:40:00Z")
        val c = BigStopChanges.find(option(bus, 3, 0), apiFor(bus), null)!!
        assertEquals(0, c.extraStops)
        assertEquals(laGuardia.name, c.stop)
        assertEquals(terminal.name, c.boardStop)
        val text = bigStopText(c)
        assertEquals(BigStopHow.SAME_STOP, text.how)
        assertEquals(0, text.stops)
        assertEquals(2, text.walkMin)
        RoutingInvariantsTest().checkItinerary("same-stop re-cut", c.changed)
    }

    @Test fun `ranking caps the slack - not sent off early to wait half an hour`() = runTest {
        // Bus 18 also passes a terminal by Merkaz Oren (one stop after boarding the 370) at 05:40:
        // 29 min to wait there, against 12 min at Tel Aviv's central station. Capped, the nearer one wins.
        val oren = stops370[2]
        val orenTerminal = Place("מסוף אורן", oren.lat + 50 / 111_000.0, oren.lon, stopId = "il-Israel-MOT_99004")
        val bus = bus18(
            orenTerminal to "2026-10-13T05:40:00Z", laGuardia to "2026-10-13T06:18:00Z",
            central to "2026-10-13T06:32:00Z", end to "2026-10-13T06:40:00Z",
        )
        val c = BigStopChanges.find(option(bus, 3, 1), apiFor(bus), null)!!
        assertEquals(central.name, c.stop)
        assertEquals(1, c.extraStops)
        assertEquals(12, c.slackMin)
    }

    @Test fun `session - an option opened again sends 0, found or not, until the results change`() = runTest {
        val fake = apiFor(waits) // no guard: as if its 30 s cache had expired
        val session = BigStopSession(fake)
        val results = Any()
        val a = option(waits, 3, 0)
        val none = itinWith(listOf("370", "171")) // recorded: nothing better
        val recordedFake = fakeWithRecordedTrips()
        val noneSession = BigStopSession(recordedFake)
        assertNotNull(session.check(results, a, null))
        assertNull(noneSession.check(results, none, null))
        assertNotNull(session.check(results, option(waits, 3, 0), null)) // another option (identity)
        assertNotNull(session.check(results, a, null))
        assertNull(noneSession.check(results, none, null))
        assertEquals(4, fake.calls.size) // A 2, B 2, A again 0
        assertEquals(2, recordedFake.calls.size) // found nothing, still 0 again
        assertTrue(session.known(results, a))
        // New results: checked afresh.
        assertNotNull(session.check(Any(), a, null))
        assertEquals(6, fake.calls.size)
    }

    @Test fun `session - a failed check is not remembered`() = runTest {
        var offline = true
        val fake = FakeTransitApi().apply { onTrip = { id -> if (offline) throw IOException("offline") else if (id == "T18") waits else bus370 } }
        val session = BigStopSession(fake)
        val results = Any()
        val a = option(waits, 3, 0)
        assertNull(session.check(results, a, null))
        offline = false
        assertNotNull(session.check(results, a, null))
    }

    // ---- requests -------------------------------------------------------------------------------

    @Test fun `budget - 2 for an opened option, 0 again through the guard's cache`() = runTest {
        val fake = apiFor(waits)
        val guarded = GuardedTransitApi(fake)
        val itin = option(waits, 3, 0)
        assertNotNull(BigStopChanges.find(itin, guarded, null))
        assertNotNull(BigStopChanges.find(itin, guarded, null))
        assertEquals(BigStopChanges.BUDGET, fake.calls.size)
        assertEquals(2, BigStopChanges.BUDGET)
    }

    @Test fun `no answer - no suggestion, no third request`() = runTest {
        val api = FakeTransitApi().apply { onTrip = { throw IOException("offline") } }
        assertNull(BigStopChanges.find(option(waits, 3, 0), api, null))
        assertEquals(1, api.calls.size)
    }
}
