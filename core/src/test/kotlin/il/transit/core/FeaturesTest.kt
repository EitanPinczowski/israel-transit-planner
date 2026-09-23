package il.transit.core

import il.transit.core.api.BudgetedTransitApi
import il.transit.core.api.Endpoint
import il.transit.core.api.PlanResponse
import il.transit.core.api.StreetModes
import il.transit.core.api.TransitModes
import il.transit.core.features.BetterStartPlanner
import il.transit.core.features.BetterStartQuery
import il.transit.core.features.DropOffKind
import il.transit.core.features.DropOffPlanner
import il.transit.core.features.DropOffQuery
import il.transit.core.features.PickUpPlanner
import il.transit.core.features.PickUpQuery
import il.transit.core.geo.LatLon
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DropOffTest {
    // Be'er Sheva → Tel Aviv along a straight meridian-ish line; C near the northern end.
    private val a = LatLon(31.25, 34.80)
    private val b = LatLon(32.08, 34.80)
    private val c = LatLon(31.90, 34.83)
    private val s1 = place("S1", LatLon(31.50, 34.801), "s1", listOf("RAIL"), 0.9)
    private val s1b = place("S1-twin", LatLon(31.505, 34.801), "s1b", listOf("RAIL"), 0.5) // <2 km from S1
    private val s2 = place("S2", LatLon(31.89, 34.802), "s2", listOf("RAIL"), 0.8)
    private val s5 = place("S5", LatLon(31.70, 34.799), "s5", listOf("RAIL"), 0.7)
    private val far = place("Far", LatLon(31.70, 35.20), "far", listOf("RAIL"), 1.0) // ~38 km off the route

    private fun scripted(): FakeTransitApi = FakeTransitApi().apply {
        val route = itinerary(
            leg(StreetModes.CAR, place("A", a), place("B", b), NOON, NOON.plusSeconds(5400), encodePolyline(listOf(a, b))),
        )
        val toStop = mapOf(s1.latLon to 1800, s2.latLon to 4400, s5.latLon to 3000)
        val stopToB = mapOf(s1.latLon to 3700, s2.latLon to 1100, s5.latLon to 3000) // S5 detour = 11 min
        onPlan = { req ->
            val from = (req.from as Endpoint.Coord).at
            when {
                StreetModes.CAR in req.directModes -> PlanResponse(direct = listOf(route))
                from == s1.latLon -> transit(s1, 5000, transfers = 1)
                from == s2.latLon -> transit(s2, 4700, transfers = 0)
                from == b -> transit(place("B", b), 7800, transfers = 0)
                from == a -> transit(place("A", a), 6000, transfers = 1)
                else -> error("unexpected plan from $from")
            }
        }
        onStops = { _, modes ->
            assertEquals(TransitModes.RAIL_LIKE, modes) // long drive → rail only
            listOf(s1, s1b, s2, s5, far)
        }
        onOneToMany = { _, many, arriveBy -> many.map { (if (arriveBy) stopToB else toStop)[it] } }
    }

    private fun transit(from: il.transit.core.api.Place, arriveAfterNoonSec: Long, transfers: Int) = PlanResponse(
        itineraries = listOf(
            itinerary(
                leg("RAIL", from, place("C", c), NOON.plusSeconds(arriveAfterNoonSec - 1200), NOON.plusSeconds(arriveAfterNoonSec)),
                transfers = transfers,
            ),
        ),
    )

    @Test fun `ranks drop-off stops by detour and arrival, with both baselines`() = runTest {
        val fake = scripted()
        val result = DropOffPlanner(fake).plan(DropOffQuery(a, b, c, NOON, maxDetourMin = 10))

        assertEquals(5400, result.directDriveSec)
        assertEquals(3, result.candidatesConsidered) // S1, S2, S5 — twin too close, Far outside the corridor
        val kinds = result.options.map { it.payload.kind to it.payload.stop?.name }
        assertEquals(
            listOf(
                DropOffKind.STOP_ON_THE_WAY to "S2", // S2 beats S1: same detour, earlier, fewer transfers
                DropOffKind.TRANSIT_FROM_START to null,
                DropOffKind.RIDE_TO_END to null,
            ),
            kinds,
        )
        val s2opt = result.options.first().payload
        // The car part runs along the route from A and ends at the stop.
        assertEquals(a, s2opt.carPath.first())
        assertEquals(s2.latLon, s2opt.carPath.last())
        assertEquals(listOf(a, b), result.options.last().payload.carPath) // ride to the end: the whole route
        assertTrue(result.options[1].payload.carPath.isEmpty()) // transit from the start: no car
        assertEquals(100 + 60, s2opt.detourSec) // 4400 + 1100 − 5400, plus the stop penalty
        assertEquals(4400, s2opt.rideSec)
        // The car route is a street-only request: no transit search on the server.
        assertTrue(fake.planRequests.first().directOnly)
        // S5 (11-min detour) must never have been planned.
        assertFalse(fake.planRequests.any { (it.from as Endpoint.Coord).at == s5.latLon })
    }

    @Test fun `passes the UI language to every transit plan`() = runTest {
        val fake = scripted()
        DropOffPlanner(fake).plan(DropOffQuery(a, b, c, NOON, language = "en"))
        val transitPlans = fake.planRequests.filterNot { it.directOnly }
        assertTrue(transitPlans.isNotEmpty())
        assertTrue(transitPlans.all { it.language == "en" })
    }

    @Test fun `rows show the stop, detour and arrival`() = runTest {
        val result = DropOffPlanner(scripted()).plan(DropOffQuery(a, b, c, NOON))
        val row = il.transit.core.present.dropOffRow(result.options.first().payload)
        assertEquals("S2", row.stop)
        assertEquals(s2.latLon, row.stopAt)
        assertEquals(3, row.detourMin) // 160 s
        assertEquals(73, row.rideMin) // 4400 s
        assertEquals("13:18", row.arrive) // NOON + 4700 s, Israel time
        val baseline = il.transit.core.present.dropOffRow(result.options.last().payload)
        assertEquals(DropOffKind.RIDE_TO_END, baseline.kind)
        assertEquals(null, baseline.stop)
        assertEquals(0, baseline.detourMin)
    }

    @Test fun `stays inside its request budget`() = runTest {
        val fake = scripted()
        val budgeted = BudgetedTransitApi(fake, DropOffPlanner.BUDGET)
        DropOffPlanner(budgeted).plan(DropOffQuery(a, b, c, NOON))
        assertTrue("used ${budgeted.used}", budgeted.used <= DropOffPlanner.BUDGET)
    }

    @Test fun `worst case with every candidate viable is exactly the budget`() = runTest {
        val stops = (0 until 12).map { i ->
            place("R$i", LatLon(31.30 + i * 0.06, 34.80), "r$i", listOf("RAIL"), 1.0 - i * 0.01)
        }
        val fake = scripted().apply {
            onStops = { _, _ -> stops }
            onOneToMany = { _, many, _ -> many.map { 2700 } } // detour 0 + 60 s
            val inner = onPlan
            onPlan = { req ->
                val from = (req.from as Endpoint.Coord).at
                if (stops.any { it.latLon == from }) transit(stops.first { it.latLon == from }, 6500, 0) else inner(req)
            }
        }
        val budgeted = BudgetedTransitApi(fake, DropOffPlanner.BUDGET)
        val result = DropOffPlanner(budgeted).plan(DropOffQuery(a, b, c, NOON))
        assertEquals(4, result.candidatesConsidered)
        assertEquals(DropOffPlanner.BUDGET, budgeted.used)
    }

    @Test fun `failed car route still returns the transit-only baselines`() = runTest {
        val fake = scripted().apply {
            val inner = onPlan
            onPlan = { req -> if (StreetModes.CAR in req.directModes) PlanResponse() else inner(req) }
        }
        val result = DropOffPlanner(fake).plan(DropOffQuery(a, b, c, NOON))
        assertNull(result.directDriveSec)
        assertEquals(listOf(DropOffKind.TRANSIT_FROM_START), result.options.map { it.payload.kind })
    }
}

class BetterStartTest {
    private val origin = LatLon(31.265, 34.785)
    private val dest = LatLon(32.08, 34.78)
    private val station = place("Be'er Sheva North", LatLon(31.262, 34.809), "bsn", listOf("RAIL"))

    private fun viaStation(carSec: Long, transitStartSec: Long, arriveSec: Long, transfers: Int, start: java.time.Instant = NOON) =
        itinerary(
            leg(StreetModes.CAR_DROPOFF, place("origin", origin), station, start, start.plusSeconds(carSec)),
            leg("RAIL", station, place("dest", dest), start.plusSeconds(transitStartSec), start.plusSeconds(arriveSec)),
            transfers = transfers,
        )

    @Test fun `offers the station only when it clearly beats the baseline`() = runTest {
        val fake = FakeTransitApi().apply {
            onPlan = { req ->
                val cap = req.maxPreTransitSec
                when {
                    cap == null -> PlanResponse(listOf(itinerary(leg("BUS", place("origin", origin), place("dest", dest), NOON, NOON.plusSeconds(4000)), transfers = 2)))
                    cap >= 400 -> PlanResponse(listOf(viaStation(350, 500, 3000, 0)))
                    else -> PlanResponse(listOf(viaStation(150, 300, 3900, 2))) // only 100 s better: not worth a car
                }
            }
        }
        val result = BetterStartPlanner(fake).plan(BetterStartQuery(origin, dest, NOON, maxDriveMin = 10))
        assertEquals(4, fake.calls.size) // baseline + 3 caps
        assertEquals(StreetModes.CAR_DROPOFF, fake.planRequests.last().preTransitModes.single())
        assertEquals(1, result.options.size)
        val opt = result.options.single().payload
        assertEquals("Be'er Sheva North", opt.dropOffStopName)
        assertEquals(350, opt.driveSec)
        assertFalse(opt.tight)
        assertEquals(StreetModes.CAR_DROPOFF, result.usedMode)
    }

    @Test fun `falls back to CAR when the server refuses CAR_DROPOFF, within budget`() = runTest {
        val fake = FakeTransitApi().apply {
            onPlan = { req ->
                when {
                    req.maxPreTransitSec == null -> PlanResponse()
                    StreetModes.CAR_DROPOFF in req.preTransitModes ->
                        throw il.transit.core.api.TransitHttpException(400, "unknown mode CAR_DROPOFF")
                    else -> PlanResponse(listOf(viaStation(350, 500, 3000, 0)))
                }
            }
        }
        val budgeted = BudgetedTransitApi(fake, BetterStartPlanner.BUDGET)
        val result = BetterStartPlanner(budgeted).plan(BetterStartQuery(origin, dest, NOON))
        assertEquals(StreetModes.CAR, result.usedMode)
        assertEquals(1, result.options.size)
        assertEquals(station.latLon, result.options.single().payload.dropOffAt)
        assertEquals(BetterStartPlanner.BUDGET, budgeted.used) // baseline + refused probe + 3 rungs
        assertEquals(1, fake.planRequests.count { StreetModes.CAR_DROPOFF in it.preTransitModes })
    }

    @Test fun `other server errors are not mistaken for an unsupported mode`() = runTest {
        val fake = FakeTransitApi().apply {
            onPlan = { req ->
                if (req.maxPreTransitSec == null) PlanResponse() else throw il.transit.core.api.TransitHttpException(500, "boom")
            }
        }
        try {
            BetterStartPlanner(fake).plan(BetterStartQuery(origin, dest, NOON))
            org.junit.Assert.fail("expected the 500 to propagate")
        } catch (e: il.transit.core.api.TransitHttpException) {
            assertEquals(500, e.code)
        }
    }

    @Test fun `flags a connection that rush-hour traffic makes tight`() = runTest {
        val fake = FakeTransitApi().apply {
            onPlan = { req ->
                if (req.maxPreTransitSec == null) PlanResponse()
                else PlanResponse(listOf(viaStation(350, 410, 3000, 0, SUNDAY_8AM)))
            }
        }
        val opt = BetterStartPlanner(fake).plan(BetterStartQuery(origin, dest, SUNDAY_8AM)).options.single().payload
        assertEquals(455, opt.driveSec) // 350 × 1.3
        assertTrue(opt.tight) // 105 s of traffic against 60 s of slack
    }
}

class PickUpTest {
    @Test fun `a pick-up that saves under five minutes is not offered`() = runTest {
        val me = LatLon(32.08, 34.78)
        val home = LatLon(31.26, 34.79)
        val station = place("Be'er Sheva Center", LatLon(31.243, 34.798), "bsc", listOf("RAIL"))
        val fake = FakeTransitApi().apply {
            onPlan = { req ->
                if (req.maxPostTransitSec == null) {
                    PlanResponse(listOf(itinerary(leg("BUS", place("me", me), place("home", home), NOON, NOON.plusSeconds(3700)))))
                } else {
                    PlanResponse(
                        listOf(
                            itinerary(
                                leg("RAIL", place("me", me), station, NOON, NOON.plusSeconds(3000)),
                                leg(StreetModes.CAR, station, place("home", home), NOON.plusSeconds(3000), NOON.plusSeconds(3600)),
                            ),
                        ),
                    )
                }
            }
        }
        assertTrue(PickUpPlanner(fake).plan(PickUpQuery(me, home, NOON)).options.isEmpty())
    }

    @Test fun `driver cost is the round trip and leave time is worked back from the pick-up`() = runTest {
        val me = LatLon(32.08, 34.78)
        val home = LatLon(31.26, 34.79)
        val station = place("Be'er Sheva Center", LatLon(31.243, 34.798), "bsc", listOf("RAIL"))
        val fake = FakeTransitApi().apply {
            onPlan = { req ->
                if (req.maxPostTransitSec == null) {
                    PlanResponse(listOf(itinerary(leg("BUS", place("me", me), place("home", home), NOON, NOON.plusSeconds(6000)))))
                } else {
                    PlanResponse(
                        listOf(
                            itinerary(
                                leg("RAIL", place("me", me), station, NOON, NOON.plusSeconds(3000)),
                                leg(StreetModes.CAR, station, place("home", home), NOON.plusSeconds(3000), NOON.plusSeconds(3600)),
                            ),
                        ),
                    )
                }
            }
        }
        val budgeted = BudgetedTransitApi(fake, PickUpPlanner.BUDGET)
        val result = PickUpPlanner(budgeted).plan(PickUpQuery(me, home, NOON, maxDriveMin = 15, language = "en"))
        val opt = result.options.single()
        assertEquals(1200, opt.driverCostSec)
        assertEquals("Be'er Sheva Center", opt.payload.pickUpStopName)
        assertEquals(station.latLon, opt.payload.pickUpAt)
        assertEquals(NOON.plusSeconds(3000), opt.payload.pickUpTime)
        assertEquals(NOON.plusSeconds(2400), opt.payload.driverLeavesAt)
        assertEquals(NOON.plusSeconds(3600), opt.arrival) // off-peak: traffic factor 1.0
        assertEquals(NOON.plusSeconds(6000), result.baseline?.end)
        assertTrue(fake.planRequests.all { it.language == "en" })

        val row = il.transit.core.present.pickUpRow(opt, result.baseline)
        assertEquals("12:50", row.pickUpTime)
        assertEquals("12:40", row.driverLeaves)
        assertEquals(20, row.roundTripMin)
        assertEquals("13:00", row.arriveHome)
        assertEquals(40, row.savedMin)
        assertTrue(fake.planRequests.drop(1).all { it.postTransitModes == listOf(StreetModes.CAR) })
    }
}
