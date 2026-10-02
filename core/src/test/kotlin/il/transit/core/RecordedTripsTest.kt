package il.transit.core

import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.api.StopTimesResponse
import il.transit.core.api.StreetModes
import il.transit.core.features.BetterStartPlanner
import il.transit.core.features.BetterStartQuery
import il.transit.core.features.DropOffPlanner
import il.transit.core.features.DropOffQuery
import il.transit.core.features.PickUpPlanner
import il.transit.core.features.PickUpQuery
import il.transit.core.features.TrafficProfile
import il.transit.core.geo.LatLon
import il.transit.core.present.LegKind
import il.transit.core.present.departureRow
import il.transit.core.present.summarize
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The features run over answers recorded from api.transitous.org (tools/record_fixture.py,
 * Monday 2026-10-05), so the leg shapes are the server's, not our guess of them.
 */
class RecordedTripsTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name.json")!!.readText()
    private fun plan(name: String) = MotisJson.decodeFromString(PlanResponse.serializer(), fixture(name))

    private val traffic = TrafficProfile()
    private val meitar = LatLon(31.3236, 34.9381)
    private val telAviv = LatLon(32.0839, 34.7983)

    /** Monday 08:00 Israel time. */
    private val monday8 = Instant.parse("2026-10-05T05:00:00Z")

    /** Monday 17:00 Israel time. */
    private val monday17 = Instant.parse("2026-10-05T14:00:00Z")

    @Test fun `better start with CAR drives to Be'er Sheva North`() = runTest {
        val fake = FakeTransitApi().apply {
            onPlan = { req -> if (req.maxPreTransitSec == null) PlanResponse() else plan("plan_car_pre_meitar") }
        }
        val result = BetterStartPlanner(fake).plan(BetterStartQuery(meitar, telAviv, monday8, maxDriveMin = 20))
        assertEquals(StreetModes.CAR, result.usedMode)
        val best = result.options.first()
        assertEquals("באר שבע צפון", best.payload.dropOffStopName)
        assertEquals(LatLon(31.262088999999996, 34.809287999999995), best.payload.dropOffAt)
        assertEquals(traffic.adjust(900, monday8), best.payload.driveSec)
        assertEquals(Instant.parse("2026-10-05T06:46:00Z"), best.arrival)
        // The same itineraries come back for every rung; the front keeps one of each.
        assertEquals(1, result.options.size)
    }

    @Test fun `CAR_DROPOFF answers skip the 0-second car stub and name the drop-off after the stop`() = runTest {
        // Shape: CAR 0′ (stub), WALK 5′, CAR 4′, WALK 4′, BUS… — or just the stub and a walk.
        val fake = FakeTransitApi().apply {
            onPlan = { req -> if (req.maxPreTransitSec == null) PlanResponse() else plan("plan_car_dropoff_meitar") }
        }
        val q = BetterStartQuery(meitar, telAviv, monday8, maxDriveMin = 20, carMode = StreetModes.CAR_DROPOFF)
        val options = BetterStartPlanner(fake).plan(q).options
        assertTrue(options.isNotEmpty())
        for (o in options) {
            assertEquals(traffic.adjust(240, monday8), o.payload.driveSec) // the 4-min leg, not the stub
            assertEquals("דרך חברון/מועצה מקומית", o.payload.dropOffStopName)
            assertTrue(o.payload.dropOffAt != meitar)
        }
        // Itineraries whose only car leg is the stub are walks, not drop-offs.
        assertTrue(options.none { it.arrival == Instant.parse("2026-10-05T07:54:00Z") })
    }

    @Test fun `pick-up at Lehavim-Rahat gets you home first`() = runTest {
        val fake = FakeTransitApi().apply {
            onPlan = { req -> if (req.maxPostTransitSec == null) PlanResponse() else plan("plan_car_post_pickup") }
        }
        val result = PickUpPlanner(fake).plan(PickUpQuery(telAviv, meitar, monday17, maxDriveMin = 20))
        val first = result.options.first().payload
        assertEquals("להבים רהט", first.pickUpStopName)
        assertEquals(Instant.parse("2026-10-05T15:31:00Z"), first.pickUpTime)
        assertEquals(traffic.adjust(1140, first.pickUpTime), first.driveSec)
        assertTrue(result.options.all { it.payload.pickUpStopName.isNotBlank() })
    }

    @Test fun `post-transit CAR_DROPOFF ends with a walk, so pick-up keeps CAR`() {
        // Transitous accepts it, but every answer ends …CAR, WALK: there is no trailing car leg.
        val its = plan("plan_cardropoff_post_probe").itineraries
        assertTrue(its.isNotEmpty())
        assertTrue(its.all { it.legs.last().mode == StreetModes.WALK && it.legs.any { l -> l.mode == StreetModes.CAR } })
        assertEquals(StreetModes.CAR, PickUpQuery(telAviv, meitar, monday17).carMode)
    }

    @Test fun `drop-off gets a car route longer than 30 minutes`() = runTest {
        val fake = FakeTransitApi().apply {
            onPlan = { req ->
                if (StreetModes.CAR in req.directModes) {
                    // Transitous returns an empty direct[] for this drive without maxDirectTime.
                    if (req.maxDirectSec == null) PlanResponse() else plan("plan_direct_car")
                } else {
                    PlanResponse()
                }
            }
            onStops = { _, _ -> MotisJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(il.transit.core.api.Place.serializer()), fixture("map_stops_beersheva_north")) }
            onOneToMany = { _, many, _ -> many.map { null } }
        }
        val result = DropOffPlanner(fake).plan(DropOffQuery(meitar, telAviv, LatLon(32.1, 34.85), monday8))
        assertEquals((4383 * traffic.factorAt(monday8)).toInt(), result.directDriveSec)
        assertEquals(DropOffPlanner.MAX_DRIVE_SEC, fake.planRequests.first().maxDirectSec)
    }

    @Test fun `the bundled station list is sane`() {
        val all = il.transit.core.geo.RailStations.ALL
        assertTrue(all.count { "RAIL" in it.modes.orEmpty() } >= 60)
        assertTrue(all.count { "TRAM" in it.modes.orEmpty() } >= 40)
        assertEquals(all.size, all.mapNotNull { it.stopId }.toSet().size)
        assertTrue(all.all { it.lat in 29.4..33.4 && it.lon in 34.2..35.9 })
        listOf("תל אביב מרכז", "קרית גת", "להבים רהט", "קרית מלאכי", "קרליבך").forEach { name ->
            assertTrue(name, all.any { it.name == name })
        }
    }

    @Test fun `drop-off candidates on the real Meitar to Tel Aviv drive reach the middle of the route`() {
        val leg = plan("plan_direct_car").direct.single().legs.single()
        val line = il.transit.core.geo.Geo.decodePolyline(leg.legGeometry!!.points, leg.legGeometry!!.precision)
        val q = DropOffQuery(meitar, telAviv, LatLon(31.8948, 34.8113), monday8)
        val picked = DropOffPlanner.pickCandidates(il.transit.core.geo.RailStations.ALL, line, q).map { it.name }
        // Before: the busiest stations won, all within a few km of Tel Aviv Center.
        // Now: trains in route order, km 61 to km 107 of 113. (Regenerating the station
        // list may legitimately change this; check the new list still spreads.)
        assertEquals(listOf("קרית מלאכי", "מזכרת בתיה", "נתב''ג", "צומת חולון", "תל אביב ההגנה"), picked)
        // The four that get planned span the drive too.
        assertEquals(listOf("קרית מלאכי", "מזכרת בתיה", "נתב''ג", "תל אביב ההגנה"), DropOffPlanner.spread(picked, q.maxPlans))
    }

    @Test fun `Friday afternoon drops the wait through Shabbat and marks Saturday-night trips`() = runTest {
        // Be'er Sheva → Tel Aviv, Friday 2026-10-09 15:00: two Friday buses, one that waits
        // 25 h through Shabbat, two on Saturday night.
        val fake = FakeTransitApi().apply { onPlan = { plan("plan_friday_bs_telaviv") } }
        val friday15 = Instant.parse("2026-10-09T12:00:00Z")
        val r = il.transit.core.plan.TripPlanner(fake).plan(
            il.transit.core.plan.TripQuery(
                il.transit.core.api.Endpoint.Coord(LatLon(31.252, 34.7915)),
                il.transit.core.api.Endpoint.Coord(telAviv),
                il.transit.core.plan.TimeMode.DEPART_AT,
                friday15,
            ),
        )
        assertEquals(4, r.itineraries.size)
        assertTrue(r.itineraries.none { it.duration > 24 * 3600 })

        val summaries = r.itineraries.map { summarize(it, searchedAt = friday15) }
        assertEquals(listOf(null, null, java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SATURDAY), summaries.map { it.departDay })
        assertEquals("18:17", summaries[2].depart)
        assertTrue(summaries.all { it.arriveDaysLater == 0 })
        assertNull(summarize(r.itineraries[2]).departDay) // no search time, no label
    }

    @Test fun `an overnight trip arrives a day later`() {
        val start = Instant.parse("2026-10-09T20:30:00Z") // 23:30 Israel time
        val night = itinerary(leg("BUS", place("a", meitar), place("b", telAviv), start, start.plusSeconds(3600)))
        val s = summarize(night, searchedAt = start)
        assertNull(s.departDay)
        assertEquals(1, s.arriveDaysLater)
        assertEquals("00:30", s.arrive)
    }

    @Test fun `Israel Railways legs and departures read well`() {
        val train = plan("plan_bgu_telaviv").itineraries[1]
        val chip = summarize(train).chips.single { it.kind == LegKind.TRAIN }
        assertNull(chip.label) // not "באר שבע מרכז-באר שבע<->כרמיאל-כרמיאל"

        val board = MotisJson.decodeFromString(StopTimesResponse.serializer(), fixture("stoptimes_beersheva_north"))
        val row = departureRow(board.stopTimes[2])
        assertEquals("", row.line) // the UI shows the train icon's name instead
        assertEquals("כרמיאל", row.headsign) // not the train number "406"
        assertEquals(LegKind.TRAIN, row.kind)
        assertEquals("08:34", row.time)
        assertNull(row.delayMin)
    }
}
