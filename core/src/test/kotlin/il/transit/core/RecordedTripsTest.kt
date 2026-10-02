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
