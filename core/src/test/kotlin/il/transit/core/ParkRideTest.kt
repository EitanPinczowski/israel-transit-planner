package il.transit.core

import il.transit.core.api.BudgetedTransitApi
import il.transit.core.api.Endpoint
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.api.StreetModes
import il.transit.core.features.ParkRidePlanner
import il.transit.core.features.ParkRideQuery
import il.transit.core.features.TrafficProfile
import il.transit.core.geo.LatLon
import il.transit.core.geo.RailStations
import il.transit.core.present.driveHomeMin
import il.transit.core.present.parkRideRow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ParkRideTest {
    private val origin = LatLon(31.3236, 34.9381) // Meitar
    private val dest = LatLon(32.0839, 34.7983) // Tel Aviv
    private val north = place("North", LatLon(31.262, 34.809), "north", listOf("RAIL"))
    private val center = place("Center", LatLon(31.243, 34.799), "center", listOf("RAIL"))
    private val twin = place("North-twin", LatLon(31.265, 34.810), "twin", listOf("RAIL")) // < 2 km from North
    private val lehavim = place("Lehavim", LatLon(31.370, 34.798), "lehavim", listOf("RAIL"))
    private val tram = place("Tram", LatLon(31.30, 34.90), "tram", listOf("TRAM"))
    private val walkable = place("Walkable", LatLon(31.325, 34.94), "walk", listOf("RAIL"))
    private val far = place("Far", LatLon(31.80, 34.70), "far", listOf("RAIL")) // ~57 km
    private val stations = listOf(north, center, twin, lehavim, tram, walkable, far)

    private fun byTrain(from: il.transit.core.api.Place, startSec: Long, arriveSec: Long, transfers: Int = 0, mode: String = "REGIONAL_RAIL") =
        itinerary(leg(mode, from, place("TA", dest), NOON.plusSeconds(startSec), NOON.plusSeconds(arriveSec)), transfers = transfers)

    private fun scripted(drives: Map<LatLon, Int?>): FakeTransitApi = FakeTransitApi().apply {
        onPlan = { req ->
            when (val from = req.from) {
                is Endpoint.Coord -> PlanResponse(listOf(byTrain(place("Meitar", origin), 600, 7200, transfers = 1, mode = "BUS")))
                is Endpoint.Stop -> when (from.stopId) {
                    "north" -> PlanResponse(
                        listOf(
                            byTrain(north, 2400, 6600), // 6 min earlier, direct
                            byTrain(north, 1800, 6000, transfers = 2, mode = "BUS"), // a bus from the forecourt
                        ),
                    )
                    "center" -> PlanResponse(listOf(byTrain(center, 2400, 7100, transfers = 1))) // only 100 s earlier
                    "lehavim" -> PlanResponse(listOf(byTrain(lehavim, 3000, 6900)))
                    else -> error("unexpected station ${from.stopId}")
                }
            }
        }
        onOneToMany = { _, many, arriveBy ->
            assertTrue(!arriveBy)
            many.map { drives[it] }
        }
    }

    @Test fun `pre-filter keeps trains in reach, not light rail, a walk away or too far`() {
        val picked = ParkRidePlanner.preFilter(stations, origin, capSec = 20 * 60)
        assertEquals(setOf("North-twin", "North", "Lehavim", "Center"), picked.map { it.name }.toSet())
        assertEquals("North-twin", picked.first().name) // nearest first
    }

    @Test fun `stations are ranked by where they lead and kept apart`() {
        val q = ParkRideQuery(origin, dest, NOON)
        val chosen = ParkRidePlanner.choose(listOf(north to 900, twin to 880, center to 950, lehavim to 1100), q)
        // Twin is < 2 km from North: one of the two. Lehavim is a longer drive but 12 km nearer Tel Aviv.
        assertEquals(3, chosen.size)
        assertTrue(chosen.none { it.first == north } || chosen.none { it.first == twin })
        assertEquals("Lehavim", chosen.first().first.name)
    }

    @Test fun `uses exactly the budget and only offers what beats the baseline`() = runTest {
        val fake = scripted(mapOf(north.latLon to 600, center.latLon to 700, lehavim.latLon to 1000, twin.latLon to null))
        val budgeted = BudgetedTransitApi(fake, ParkRidePlanner.BUDGET)
        val r = ParkRidePlanner(budgeted, stations = stations).plan(ParkRideQuery(origin, dest, NOON))
        assertEquals(ParkRidePlanner.BUDGET, budgeted.used) // baseline + one-to-many + 3 plans
        assertEquals(3, r.stationsInReach)
        assertEquals(listOf("oneToMany"), fake.calls.filter { it != "plan" })
        // Plans start from the station's stop id.
        assertEquals(setOf("north", "center", "lehavim"), fake.planRequests.mapNotNull { (it.from as? Endpoint.Stop)?.stopId }.toSet())
        // Center gains 100 s: not worth a car. The forecourt bus is not park & ride.
        val names = r.options.map { it.payload.station.name }
        assertTrue("Center" !in names)
        assertTrue(r.options.all { it.payload.itinerary.legs.first().mode == "REGIONAL_RAIL" })
        val n = r.options.single { it.payload.station.name == "North" }
        assertEquals(600, n.driverCostSec) // off-peak: no traffic
        assertEquals(NOON.plusSeconds(6600), n.arrival)
    }

    @Test fun `leaves home just in time for the train`() = runTest {
        val fake = scripted(mapOf(north.latLon to 600))
        val r = ParkRidePlanner(fake, stations = stations).plan(ParkRideQuery(origin, dest, NOON, parkSec = 300))
        // The plan is asked from when the car can be parked at the earliest…
        val stationReq = fake.planRequests.single { it.from is Endpoint.Stop }
        assertEquals(NOON.plusSeconds(900), stationReq.time)
        // …and the train at +2400 s means leaving home at +1500 s, not at the search time.
        val o = r.options.single().payload
        assertEquals(NOON.plusSeconds(2400), o.parkedBy)
        assertEquals(NOON.plusSeconds(1500), o.leaveAt)
        assertEquals(listOf(origin, north.latLon), o.carPath(origin))
    }

    @Test fun `rush hour shrinks the cap and lengthens the drive`() = runTest {
        var sentMax = -1
        val fake = scripted(mapOf(north.latLon to 600))
        val capturing = object : il.transit.core.api.TransitApi by fake {
            override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean): List<Int?> {
                sentMax = maxSeconds
                assertEquals(StreetModes.CAR, mode)
                return fake.oneToMany(one, many, mode, maxSeconds, arriveBy)
            }
        }
        val r = ParkRidePlanner(capturing, stations = stations).plan(ParkRideQuery(origin, dest, SUNDAY_8AM, maxDriveMin = 20))
        assertEquals((20 * 60 / 1.3).toInt(), sentMax)
        assertEquals(780, r.options.single().driverCostSec)
        assertEquals(TrafficProfile().adjust(600, SUNDAY_8AM), r.options.single().payload.driveSec)
    }

    @Test fun `nothing in reach costs one request and returns the baseline`() = runTest {
        val fake = scripted(emptyMap())
        val r = ParkRidePlanner(fake, stations = listOf(far, tram)).plan(ParkRideQuery(origin, dest, NOON))
        assertEquals(listOf("plan"), fake.calls)
        assertEquals(0, r.stationsInReach)
        assertTrue(r.options.isEmpty())
        assertEquals(NOON.plusSeconds(7200), r.baseline?.end)
    }

    @Test fun `drive home is the outbound drive at the return time's traffic`() = runTest {
        val r = ParkRidePlanner(scripted(mapOf(north.latLon to 600)), stations = stations).plan(ParkRideQuery(origin, dest, NOON))
        val o = r.options.single().payload
        assertEquals(10, driveHomeMin(o, NOON)) // off-peak
        val sunday17 = SUNDAY_8AM.plusSeconds(9 * 3600)
        assertEquals(13, driveHomeMin(o, sunday17)) // ×1.3 at the evening peak
    }

    @Test fun `the row shows leave, board and gain`() = runTest {
        val r = ParkRidePlanner(scripted(mapOf(north.latLon to 600)), stations = stations).plan(ParkRideQuery(origin, dest, NOON))
        val row = parkRideRow(r.options.single().payload, r.baseline)
        assertEquals("North", row.station)
        assertEquals(10, row.driveMin)
        assertEquals("12:25", row.leave)
        assertEquals("12:40", row.board)
        assertEquals("13:50", row.arrive)
        assertEquals(10, row.savedMin)
        assertEquals(1, row.transfersSaved)
    }
}

/**
 * Golden trip 11 recorded on api.transitous.org (2026-10-04, for Sunday 2026-10-11 07:30, the
 * morning peak): Meitar → Tel Aviv, 20-min drive limit. Five real answers, five requests.
 */
class ParkRideRecordedTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name.json")!!.readText()
    private fun plan(name: String) = MotisJson.decodeFromString(PlanResponse.serializer(), fixture(name))

    private val meitar = LatLon(31.3236, 34.9381)
    private val telAviv = LatLon(32.0839, 34.7983)
    private val sunday0730 = Instant.parse("2026-10-11T04:30:00Z")

    @Test fun `Meitar parks at Be'er Sheva North and takes the direct train`() = runTest {
        val drives = MotisJson.decodeFromString(ListSerializer(JsonObject.serializer()), fixture("one_to_many_park_ride_meitar"))
            .map { it["duration"]?.jsonPrimitive?.content?.toDouble()?.toInt() }
        val fake = FakeTransitApi().apply {
            onOneToMany = { _, many, _ ->
                // The recording asked for exactly the stations the pre-filter keeps, in order.
                assertEquals(listOf("באר שבע צפון", "להבים רהט", "באר שבע מרכז"), many.map { at -> RailStations.ALL.first { it.latLon == at }.name })
                drives
            }
            onPlan = { req ->
                when (val from = req.from) {
                    is Endpoint.Coord -> plan("plan_park_ride_baseline_meitar")
                    is Endpoint.Stop -> when (from.stopId) {
                        "il-Israel-MOT_37314" -> plan("plan_park_ride_bs_north")
                        "il-Israel-MOT_37312" -> plan("plan_park_ride_bs_center")
                        else -> error("unexpected ${from.stopId}")
                    }
                }
            }
        }
        val budgeted = BudgetedTransitApi(fake, ParkRidePlanner.BUDGET)
        val r = ParkRidePlanner(budgeted).plan(ParkRideQuery(meitar, telAviv, sunday0730, maxDriveMin = 20))
        assertEquals(4, budgeted.used) // Lehavim is out of reach at peak: only 2 station plans
        assertEquals(2, r.stationsInReach)
        // Baseline: walk, bus 253, train — 09:46 with a transfer.
        assertEquals(Instant.parse("2026-10-11T06:46:00Z"), r.baseline?.end)
        assertEquals(1, r.baseline?.transfers)
        // Park at Be'er Sheva North (877 s free flow, ×1.3), the 08:34 direct train, 09:46.
        val best = r.options.first()
        assertEquals("באר שבע צפון", best.payload.station.name)
        assertEquals(Math.round(877 * 1.3).toInt(), best.driverCostSec)
        assertEquals(0, best.transfers)
        assertEquals(Instant.parse("2026-10-11T06:46:00Z"), best.arrival)
        assertEquals("08:10", il.transit.core.present.hhmm(best.payload.leaveAt))
        // Same train from Center costs a longer drive: dominated.
        assertTrue(r.options.none { it.payload.station.name == "באר שבע מרכז" && it.arrival == best.arrival })
    }
}
