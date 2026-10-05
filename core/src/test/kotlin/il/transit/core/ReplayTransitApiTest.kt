package il.transit.core

import il.transit.core.api.BudgetedTransitApi
import il.transit.core.api.Endpoint
import il.transit.core.api.MotisJson
import il.transit.core.api.ReplayTransitApi
import il.transit.core.features.BetterStartPlanner
import il.transit.core.features.BetterStartQuery
import il.transit.core.features.DropOffKind
import il.transit.core.features.DropOffPlanner
import il.transit.core.features.DropOffQuery
import il.transit.core.features.PickUpPlanner
import il.transit.core.features.PickUpQuery
import il.transit.core.geo.LatLon
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripQuery
import il.transit.core.present.hhmm
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The on-device UI tests and crawlers see the app through [ReplayTransitApi]. These pin
 * that every screen they drive gets real-looking results, at the uitest build's fixed clock.
 */
class ReplayTransitApiTest {
    /** Monday 2026-10-05 07:55 Israel: the uitest build's clock. */
    private val now = Instant.parse("2026-10-05T04:55:00Z")
    private val replay = ReplayTransitApi(Clock.fixed(now, ZoneOffset.UTC)) { name ->
        javaClass.getResource("/fixtures/$name.json")!!.readText()
    }
    private val bgu = LatLon(31.2622, 34.8013)
    private val telAviv = LatLon(32.0839, 34.7983)
    private val meitar = LatLon(31.3236, 34.9381)
    private val rehovot = LatLon(31.8947, 34.8093)

    @Test fun `every fixture it replays exists and is JSON`() {
        for (name in ReplayTransitApi.FIXTURES) {
            val text = javaClass.getResource("/fixtures/$name.json")?.readText() ?: fail("missing fixture $name")
            MotisJson.parseToJsonElement(text as String)
        }
    }

    @Test fun `a trip from Be'er Sheva leaves five minutes after the search`() = runTest {
        val r = TripPlanner(replay).plan(TripQuery(Endpoint.Coord(bgu), Endpoint.Coord(telAviv)), now)
        assertTrue(r.itineraries.size >= 3)
        assertEquals("08:00", hhmm(r.itineraries.minOf { it.start }))
    }

    @Test fun `arrive-by answers end before the time asked`() = runTest {
        val by = Instant.parse("2026-10-05T06:00:00Z")
        val r = TripPlanner(replay).plan(TripQuery(Endpoint.Coord(bgu), Endpoint.Coord(telAviv), TimeMode.ARRIVE_BY, by), now)
        assertTrue(r.itineraries.isNotEmpty())
        assertTrue(r.itineraries.all { !it.end.isAfter(by) })
    }

    @Test fun `better start from Meitar has options and a baseline at 20 minutes, none at 10`() = runTest {
        val api = BudgetedTransitApi(replay, BetterStartPlanner.BUDGET)
        val r = BetterStartPlanner(api).plan(BetterStartQuery(meitar, telAviv, now, maxDriveMin = 20))
        assertNotNull(r.baseline)
        assertTrue(r.options.isNotEmpty())
        val none = BetterStartPlanner(replay).plan(BetterStartQuery(meitar, telAviv, now, maxDriveMin = 10))
        assertTrue(none.options.isEmpty())
    }

    @Test fun `pick-up from Tel Aviv to Meitar has options and a baseline at 30 minutes`() = runTest {
        val api = BudgetedTransitApi(replay, PickUpPlanner.BUDGET)
        val r = PickUpPlanner(api).plan(PickUpQuery(telAviv, meitar, now, maxDriveMin = 30))
        assertNotNull(r.baseline)
        assertTrue(r.options.isNotEmpty())
        assertTrue(r.options.all { it.payload.pickUpStopName.isNotBlank() })
    }

    @Test fun `drop-off Meitar to Tel Aviv for Rehovot offers a stop and transit from the start`() = runTest {
        val api = BudgetedTransitApi(replay, DropOffPlanner.BUDGET)
        val r = DropOffPlanner(api).plan(DropOffQuery(meitar, telAviv, rehovot, now))
        assertNotNull(r.directDriveSec)
        val kinds = r.options.map { it.payload.kind }.toSet()
        val got = "${r.candidatesConsidered} candidates, " + r.options.joinToString { "${it.payload.kind} ${it.payload.stop?.name} cost ${it.driverCostSec} at ${hhmm(it.arrival)}" }
        assertTrue(got, DropOffKind.STOP_ON_THE_WAY in kinds)
        assertTrue(got, DropOffKind.TRANSIT_FROM_START in kinds)
        // A real trade-off on screen: the stop costs the driver minutes and gets you there sooner.
        assertTrue(got, r.options.any { it.payload.kind == DropOffKind.STOP_ON_THE_WAY && it.driverCostSec in 60..600 })
        println("drop-off replay: $got")
    }

    @Test fun `departures board starts two minutes from now`() = runTest {
        val board = replay.stopTimes("il-Israel-MOT_37314", null, 5, "he")
        assertEquals(5, board.stopTimes.size)
        assertEquals("07:57", hhmm(il.transit.core.api.parseTime(board.stopTimes.first().place.departure ?: board.stopTimes.first().place.arrival!!)))
    }

    @Test fun `failing makes every call throw like a dead network`() = runTest {
        replay.failing = true
        try {
            replay.geocode("רגר", "he", null, 8)
            fail("expected an IOException")
        } catch (e: java.io.IOException) {
            assertTrue(replay.calls.get() == 1)
        }
    }

    @Test fun `a train leg's trip sheet replays the train, anything else the bus, five minutes from now`() = runTest {
        val r = TripPlanner(replay).plan(TripQuery(Endpoint.Coord(bgu), Endpoint.Coord(telAviv)), now)
        val rail = r.itineraries.flatMap { it.legs }.first { it.mode.contains("RAIL") }
        val train = replay.trip(rail.tripId!!)
        assertTrue(train.legs.single().mode.contains("RAIL"))
        assertEquals("08:00", hhmm(train.start))
        assertEquals("BUS", replay.trip("no-such-trip").legs.single().mode)
    }

    @Test fun `vehicles on the map start at the window asked`() = runTest {
        val segs = replay.mapTrips(il.transit.core.geo.BBox(bgu, telAviv), now, now.plusSeconds(600), 14.0)
        assertTrue(segs.isNotEmpty())
        assertEquals(now, segs.minOf { it.depart })
    }
}
