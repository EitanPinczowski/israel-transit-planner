package il.transit.core

import il.transit.core.api.BudgetedTransitApi
import il.transit.core.api.Endpoint
import il.transit.core.api.Itinerary
import il.transit.core.api.MotisJson
import il.transit.core.api.Place
import il.transit.core.api.PlanResponse
import il.transit.core.api.StreetModes
import il.transit.core.api.parseTime
import il.transit.core.features.BetterStartPlanner
import il.transit.core.features.BetterStartQuery
import il.transit.core.features.DropOffPlanner
import il.transit.core.features.DropOffQuery
import il.transit.core.features.Option
import il.transit.core.features.ParkRidePlanner
import il.transit.core.features.ParkRideQuery
import il.transit.core.features.PickUpPlanner
import il.transit.core.features.PickUpQuery
import il.transit.core.features.TrafficProfile
import il.transit.core.features.paretoFront
import il.transit.core.fare.FareEstimator
import il.transit.core.fare.FareProfile
import il.transit.core.geo.LatLon
import il.transit.core.geo.RailStations
import il.transit.core.present.RideDetails
import il.transit.core.present.minutes
import il.transit.core.present.rideDetails
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.util.Random

/**
 * Phase 10 D4: things that must hold for ANY answer, checked over every recorded plan and over what each
 * special feature makes of its recordings. A new fixture or a new feature is covered the day it lands;
 * a break here means a rule about time, money or request count was broken, whatever the numbers were.
 */
class RoutingInvariantsTest {
    private fun text(name: String) = javaClass.getResource("/fixtures/$name.json")!!.readText()
    private fun plan(name: String) = MotisJson.decodeFromString(PlanResponse.serializer(), text(name))

    /** Every `plan_*` fixture, found on disk, so a newly recorded one is checked without editing this file. */
    private val planFixtures: List<String> by lazy {
        val dir = File(javaClass.getResource("/fixtures/plan_bgu_telaviv.json")!!.toURI()).parentFile
        dir.listFiles { f -> f.name.startsWith("plan_") && f.name.endsWith(".json") }!!
            .map { it.name.removeSuffix(".json") }.sorted()
    }

    private val tripFixtures = listOf("trip_bus_470", "trip_train_bs_telaviv")

    private val traffic = TrafficProfile()
    private val meitar = LatLon(31.3236, 34.9381)
    private val telAviv = LatLon(32.0839, 34.7983)
    private val monday8 = Instant.parse("2026-10-05T05:00:00Z")
    private val monday17 = Instant.parse("2026-10-05T14:00:00Z")
    private val sunday0730 = Instant.parse("2026-10-11T04:30:00Z")

    // ---- one itinerary --------------------------------------------------------------------------

    private fun times(p: Place): List<Instant> = listOfNotNull(p.arrival, p.departure).map(::parseTime)

    private fun checkItinerary(where: String, it: Itinerary) {
        assertTrue("$where: arrival before departure", !it.end.isBefore(it.start))
        assertTrue("$where: negative duration", it.duration >= 0)
        assertTrue("$where: transfers < 0", it.transfers >= 0)
        assertTrue("$where: no legs", it.legs.isNotEmpty())
        it.legs.forEachIndexed { i, leg ->
            val at = "$where leg $i (${leg.mode})"
            assertTrue("$at ends before it starts", !leg.end.isBefore(leg.start))
            assertTrue("$at: negative duration", leg.duration >= 0)
            // A leg's stops, in time order: from → intermediate stops → to. (A stop's arrival ≤ its departure.)
            val stops = listOf(leg.from) + leg.intermediateStops + leg.to
            stops.forEach { s ->
                if (s.arrival != null && s.departure != null && s !== leg.from && s !== leg.to) {
                    assertTrue("$at: stop '${s.name}' departs before it arrives", !parseTime(s.departure).isBefore(parseTime(s.arrival)))
                }
            }
            val sequence = listOfNotNull(leg.from.departure?.let(::parseTime)) +
                leg.intermediateStops.flatMap(::times) + listOfNotNull(leg.to.arrival?.let(::parseTime))
            sequence.zipWithNext().forEachIndexed { k, (a, b) ->
                assertTrue("$at: stop times go back in time at #$k ($a > $b)", !a.isAfter(b))
            }
            if (i > 0) {
                assertTrue("$where: leg $i starts before leg ${i - 1} ends", !leg.start.isBefore(it.legs[i - 1].end))
            }
        }
        assertTrue("$where: itinerary starts after its first leg", !it.start.isAfter(it.legs.first().start))
        assertTrue("$where: itinerary ends before its last leg", !it.end.isBefore(it.legs.last().end))
        checkRideDetails(where, it)
        checkFare(where, it)
    }

    /** Ride details add up to the legs: stops, minutes, count, and what the card shows of them. */
    private fun checkRideDetails(where: String, it: Itinerary) {
        val d = rideDetails(it)
        val transit = it.legs.filter { l -> l.isTransit }
        assertEquals("$where: ride legs", transit.size, d.legs.size)
        d.legs.forEach { r ->
            val leg = it.legs[r.legIndex]
            assertTrue("$where: ride leg points at a walk", leg.isTransit)
            assertEquals("$where: stops of leg ${r.legIndex}", leg.intermediateStops.size + 1, r.stops)
            assertEquals("$where: minutes of leg ${r.legIndex}", minutes((leg.end.epochSecond - leg.start.epochSecond).toInt()), r.minutes)
            assertEquals("$where: get-off of leg ${r.legIndex}", leg.to.name, r.getOff)
            assertTrue("$where: ride minutes < 0", r.minutes >= 0 && r.stops >= 1)
        }
        assertEquals("$where: shown + more", d.legs.size, d.shown.size + d.more)
        assertTrue("$where: too many lines", d.shown.size <= RideDetails.MAX_LINES)
        val total = d.total
        if (transit.isEmpty()) {
            assertEquals("$where: total without a ride", null, total)
        } else {
            assertEquals("$where: total stops", d.legs.sumOf { r -> r.stops }, total!!.stops)
            assertEquals("$where: total minutes", d.legs.sumOf { r -> r.minutes }, total.minutes)
            assertEquals("$where: total get-off", transit.last().to.name, total.getOff)
            // Time on board can't exceed the whole trip.
            assertTrue("$where: on board longer than the trip", total.minutes <= minutes(it.duration) + d.legs.size)
        }
    }

    private fun checkFare(where: String, it: Itinerary) {
        val regular = FareEstimator.estimate(it)
        val half = FareEstimator.estimate(it, FareProfile.HALF)
        val free = FareEstimator.estimate(it, FareProfile.FREE)
        if (it.legs.none { l -> l.isTransit }) {
            assertEquals("$where: fare without a ride", null, regular)
            return
        }
        assertTrue("$where: fare < 0 (${regular?.agorot})", regular != null && regular.agorot >= 0)
        assertTrue("$where: half fare dearer than full", half!!.agorot in 0..regular!!.agorot)
        assertEquals("$where: free fare", 0, free!!.agorot)
    }

    // ---- every recorded answer ------------------------------------------------------------------

    @Test fun `there are recorded plans to check, and they are all parsed`() {
        assertTrue("fixtures: $planFixtures", planFixtures.size >= 10)
    }

    @Test fun `every recorded plan - times, order, ride details and fares hold`() {
        var checked = 0
        for (name in planFixtures) {
            val r = plan(name)
            (r.itineraries + r.direct).forEachIndexed { i, it -> checkItinerary("$name #$i", it); checked++ }
        }
        assertTrue("only $checked itineraries", checked >= 30)
    }

    @Test fun `every recorded trip - stops are in time order`() {
        for (name in tripFixtures) {
            checkItinerary(name, MotisJson.decodeFromString(Itinerary.serializer(), text(name)))
        }
    }

    // ---- traffic ---------------------------------------------------------------------------------

    @Test fun `the traffic factor is never below 1 - at any hour of any day`() {
        val start = Instant.parse("2026-10-04T00:00:00Z") // a Sunday
        for (profile in listOf(TrafficProfile(), TrafficProfile(peakFactor = 1.0), TrafficProfile(peakFactor = 1.6))) {
            for (h in 0 until 24 * 7) {
                val t = start.plusSeconds(h * 3600L)
                assertTrue("factor ${profile.factorAt(t)} at $t", profile.factorAt(t) >= 1.0)
                for (free in listOf(0, 1, 59, 600, 3_661, 18_000)) {
                    assertTrue("$free s became ${profile.adjust(free, t)}", profile.adjust(free, t) >= free)
                }
            }
        }
        // Israel's work week: Sunday–Thursday peaks, never Friday or Saturday.
        assertEquals(1.3, traffic.factorAt(monday8), 0.0)
        assertEquals(1.0, traffic.factorAt(Instant.parse("2026-10-09T05:00:00Z")), 0.0) // Friday 08:00
        assertEquals(DayOfWeek.FRIDAY, Instant.parse("2026-10-09T05:00:00Z").atZone(il.transit.core.features.ISRAEL).dayOfWeek)
    }

    // ---- the Pareto front --------------------------------------------------------------------------

    private fun <T> dominates(a: Option<T>, b: Option<T>) =
        a.driverCostSec <= b.driverCostSec && !a.arrival.isAfter(b.arrival) && a.transfers <= b.transfers &&
            (a.driverCostSec < b.driverCostSec || a.arrival.isBefore(b.arrival) || a.transfers < b.transfers)

    private fun <T> checkFront(where: String, front: List<Option<T>>) {
        front.forEach { o ->
            assertTrue("$where: driver cost ${o.driverCostSec} < 0", o.driverCostSec >= 0)
            assertTrue("$where: transfers < 0", o.transfers >= 0)
        }
        for (a in front) for (b in front) {
            if (a !== b) assertFalse("$where: ${b.driverCostSec}s/${b.arrival}/${b.transfers} is dominated", dominates(a, b))
        }
        assertEquals("$where: sorted by arrival", front.sortedBy { it.arrival }.map { it.arrival }, front.map { it.arrival })
    }

    @Test fun `paretoFront - no member dominated, nothing good dropped - over random options`() {
        val rnd = Random(20261012)
        repeat(300) { round ->
            val options = List(rnd.nextInt(12)) {
                Option(it, rnd.nextInt(40) * 60, NOON.plusSeconds(rnd.nextInt(30) * 300L), rnd.nextInt(4))
            }
            val front = paretoFront(options)
            checkFront("round $round", front)
            options.forEach { o ->
                // Everything left out is beaten by, or the same as, something kept.
                if (front.none { it.payload == o.payload }) {
                    assertTrue(
                        "round $round: option ${o.payload} dropped for nothing",
                        front.any { f -> dominates(f, o) || (f.driverCostSec == o.driverCostSec && f.arrival == o.arrival && f.transfers == o.transfers) },
                    )
                }
            }
        }
    }

    // ---- the four features on their recordings ----------------------------------------------------

    private fun checkOption(where: String, o: Option<*>, itin: Itinerary, departAt: Instant) {
        checkItinerary(where, itin)
        assertTrue("$where: arrives before it left", !o.arrival.isBefore(departAt))
        assertEquals("$where: option arrival is the itinerary's", itin.end, o.arrival)
    }

    @Test fun `better start - drive, leave time, front and budget`() = runTest {
        val fake = FakeTransitApi().apply {
            onPlan = { req -> if (req.maxPreTransitSec == null) PlanResponse() else plan("plan_car_pre_meitar") }
        }
        val api = BudgetedTransitApi(fake, BetterStartPlanner.BUDGET)
        val result = BetterStartPlanner(api).plan(BetterStartQuery(meitar, telAviv, monday8, maxDriveMin = 20))
        assertTrue("budget: ${api.used}", api.used in 1..BetterStartPlanner.BUDGET)
        assertTrue(result.options.isNotEmpty())
        checkFront("better start", result.options)
        result.baseline?.let { checkItinerary("better start baseline", it) }
        result.options.forEach { o ->
            val p = o.payload
            checkOption("better start", o, p.itinerary, monday8)
            assertTrue("drive ${p.driveSec} < 0", p.driveSec >= 0)
            assertEquals(p.driveSec, o.driverCostSec)
            assertTrue("leaves ${p.leaveAt} after the trip starts ${p.itinerary.start}", !p.leaveAt.isAfter(p.itinerary.start))
            assertTrue("leaves before asked", !p.leaveAt.isBefore(monday8.minusSeconds(3600)))
        }
    }

    @Test fun `pick-up - drive, driver's times, front and budget`() = runTest {
        val fake = FakeTransitApi().apply {
            onPlan = { req -> if (req.maxPostTransitSec == null) PlanResponse() else plan("plan_car_post_pickup") }
        }
        val api = BudgetedTransitApi(fake, PickUpPlanner.BUDGET)
        val result = PickUpPlanner(api).plan(PickUpQuery(telAviv, meitar, monday17, maxDriveMin = 20))
        assertTrue("budget: ${api.used}", api.used in 1..PickUpPlanner.BUDGET)
        assertTrue(result.options.isNotEmpty())
        checkFront("pick-up", result.options)
        result.baseline?.let { checkItinerary("pick-up baseline", it) }
        result.options.forEach { o ->
            val p = o.payload
            checkOption("pick-up", o, p.itinerary, monday17)
            assertTrue("drive ${p.driveSec} < 0", p.driveSec >= 0)
            assertEquals("the driver pays the round trip", 2 * p.driveSec, o.driverCostSec)
            assertTrue("driver leaves ${p.driverLeavesAt} after you arrive ${p.pickUpTime}", !p.driverLeavesAt.isAfter(p.pickUpTime))
        }
    }

    @Test fun `park and ride - drives, times, front and budget`() = runTest {
        val drives = MotisJson.decodeFromString(ListSerializer(JsonObject.serializer()), text("one_to_many_park_ride_meitar"))
            .map { it["duration"]?.jsonPrimitive?.content?.toDouble()?.toInt() }
        val fake = FakeTransitApi().apply {
            onOneToMany = { _, _, _ -> drives }
            onPlan = { req ->
                when (val from = req.from) {
                    is Endpoint.Coord -> plan("plan_park_ride_baseline_meitar")
                    is Endpoint.Stop -> when (from.stopId) {
                        "il-Israel-MOT_37314" -> plan("plan_park_ride_bs_north")
                        "il-Israel-MOT_37312" -> plan("plan_park_ride_bs_center")
                        else -> PlanResponse()
                    }
                }
            }
        }
        val api = BudgetedTransitApi(fake, ParkRidePlanner.BUDGET)
        val result = ParkRidePlanner(api).plan(ParkRideQuery(meitar, telAviv, sunday0730, maxDriveMin = 20))
        assertTrue("budget: ${api.used}", api.used in 1..ParkRidePlanner.BUDGET)
        assertTrue(result.options.isNotEmpty())
        checkFront("park & ride", result.options)
        result.baseline?.let { checkItinerary("park & ride baseline", it) }
        result.options.forEach { o ->
            val p = o.payload
            checkOption("park & ride", o, p.itinerary, sunday0730)
            assertTrue("drive ${p.driveSec} < 0", p.driveSec >= 0)
            assertTrue("traffic made the drive shorter: ${p.driveSec} < ${p.freeFlowSec}", p.driveSec >= p.freeFlowSec)
            assertEquals(p.driveSec, o.driverCostSec)
            assertTrue("leaves after being parked", !p.leaveAt.isAfter(p.parkedBy))
            assertTrue("the station is not a rail stop", RailStations.ALL.any { it.stopId == p.station.stopId })
        }
    }

    @Test fun `drop-off - detours, rides, front and budget`() = runTest {
        val a = LatLon(31.25, 34.80)
        val b = LatLon(32.08, 34.80)
        val c = LatLon(31.90, 34.83)
        val s1 = place("S1", LatLon(31.50, 34.801), "s1", listOf("RAIL"), 0.9)
        val s2 = place("S2", LatLon(31.89, 34.802), "s2", listOf("RAIL"), 0.8)
        val s5 = place("S5", LatLon(31.70, 34.799), "s5", listOf("RAIL"), 0.7)
        val route = itinerary(
            leg(StreetModes.CAR, place("A", a), place("B", b), NOON, NOON.plusSeconds(5400), encodePolyline(listOf(a, b))),
        )
        val toStop = mapOf(s1.latLon to 1800, s2.latLon to 4400, s5.latLon to 3000)
        val stopToB = mapOf(s1.latLon to 3700, s2.latLon to 1100, s5.latLon to 3000)
        fun ride(from: Place, arriveSec: Long, transfers: Int) = PlanResponse(
            itineraries = listOf(
                itinerary(leg("RAIL", from, place("C", c), NOON.plusSeconds(arriveSec - 1200), NOON.plusSeconds(arriveSec)), transfers = transfers),
            ),
        )
        val fake = FakeTransitApi().apply {
            onPlan = { req ->
                val from = (req.from as Endpoint.Coord).at
                when {
                    StreetModes.CAR in req.directModes -> PlanResponse(direct = listOf(route))
                    from == s1.latLon -> ride(s1, 5000, 1)
                    from == s2.latLon -> ride(s2, 4700, 0)
                    from == s5.latLon -> ride(s5, 5200, 1)
                    from == b -> ride(place("B", b), 7800, 0)
                    else -> ride(place("A", a), 6000, 1)
                }
            }
            onStops = { _, _ -> listOf(s1, s2, s5) }
            onOneToMany = { _, many, arriveBy -> many.map { (if (arriveBy) stopToB else toStop)[it] } }
        }
        val api = BudgetedTransitApi(fake, DropOffPlanner.BUDGET)
        val result = DropOffPlanner(api, stations = emptyList()).plan(DropOffQuery(a, b, c, NOON))
        assertTrue("budget: ${api.used}", api.used in 1..DropOffPlanner.BUDGET)
        assertTrue(result.options.isNotEmpty())
        checkFront("drop-off", result.options)
        assertTrue("direct drive ${result.directDriveSec} < 0", (result.directDriveSec ?: 0) >= 0)
        result.options.forEach { o ->
            val p = o.payload
            checkOption("drop-off ${p.kind}", o, p.transit, NOON)
            assertTrue("detour ${p.detourSec} < 0", p.detourSec >= 0)
            assertTrue("ride ${p.rideSec} < 0", p.rideSec >= 0)
            assertEquals(p.detourSec, o.driverCostSec)
        }
    }
}
