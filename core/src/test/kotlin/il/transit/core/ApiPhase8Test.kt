package il.transit.core

import il.transit.core.api.Alert
import il.transit.core.api.BudgetExceededException
import il.transit.core.api.BudgetedTransitApi
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.Itinerary
import il.transit.core.api.MotisClient
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.api.StopTimesResponse
import il.transit.core.api.StreetModes
import il.transit.core.api.TimeRange
import il.transit.core.api.TripSegment
import il.transit.core.features.leadingCarLegs
import il.transit.core.geo.BBox
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Phase 8 spike: answers recorded live from api.transitous.org on Saturday 2026-10-03 around
 * 23:20 Israel time (late Saturday-night service running), plus a Monday 08:00 `CAR_PARKING`
 * plan. What each fixture taught us is pinned here; the prose is in the transitous-api skill.
 */
class ApiPhase8Test {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name.json")!!.readText()
    private fun plan(name: String) = MotisJson.decodeFromString(PlanResponse.serializer(), fixture(name))
    private fun board(name: String) = MotisJson.decodeFromString(StopTimesResponse.serializer(), fixture(name))
    private fun tripFixture(name: String) = MotisJson.decodeFromString(Itinerary.serializer(), fixture(name))
    private fun segments() = MotisJson.decodeFromString(ListSerializer(TripSegment.serializer()), fixture("map_trips_beersheva"))

    private val bus470 = "20261003_23:30_il-Israel-MOT_585172431_031026"
    private val train = "20261003_23:32_il-Israel-MOT_1_520772"

    // --- real-time and alerts ---

    @Test fun `no real-time and no alerts in Israel on a Saturday night, at two busy stops`() {
        val beerSheva = board("stoptimes_now_beersheva_central").stopTimes
        val savidor = board("stoptimes_now_savidor").stopTimes
        assertEquals(36, beerSheva.size)
        assertEquals(20, savidor.size)
        // Buses leaving within 10 minutes, six operators in Tel Aviv: still all schedule.
        for (st in beerSheva + savidor) {
            assertFalse(st.realTime)
            assertEquals(st.place.scheduledDeparture, st.place.departure)
            assertFalse(st.cancelled || st.tripCancelled || st.place.cancelled)
            assertTrue(st.alerts.isEmpty() && st.place.alerts.isEmpty())
            assertTrue(st.tripId!!.contains("il-Israel-MOT_"))
        }
    }

    @Test fun `the same Saturday-night trip can be listed twice, once per service day`() {
        // Line 250 at 00:20: …_031026 and …_041026 — MOT GTFS carries both service days.
        val line250 = board("stoptimes_now_beersheva_central").stopTimes.filter { it.routeShortName == "250" }
        assertEquals(2, line250.size)
        assertEquals(line250[0].place.departure, line250[1].place.departure)
        assertTrue(line250[0].tripId != line250[1].tripId)
    }

    @Test fun `plans parse with alerts and cancelled defaulted, and no alert arrives`() {
        for (name in listOf("plan_now_bs_hahagana", "plan_car_parking_meitar", "plan_bgu_telaviv")) {
            val legs = plan(name).itineraries.flatMap { it.legs }
            assertTrue(legs.isNotEmpty())
            assertTrue(legs.all { l -> l.alerts.isEmpty() && l.from.alerts.isEmpty() && l.to.alerts.isEmpty() })
            assertTrue(legs.filter { it.isTransit }.none { it.cancelled || it.realTime })
        }
    }

    @Test fun `cancelled is set on some transfer walks - only transit legs mean it`() {
        val legs = plan("plan_now_bs_hahagana").itineraries.flatMap { it.legs }
        val flagged = legs.filter { it.cancelled }
        assertEquals(2, flagged.size)
        assertTrue(flagged.all { it.mode == StreetModes.WALK && it.from.cancelled && it.to.cancelled })
        assertEquals("צומת אל על", flagged[0].from.name)
    }

    @Test fun `alert model reads the MOTIS shape and knows its validity`() {
        val alert = MotisJson.decodeFromString(
            Alert.serializer(),
            """{"headerText":"שביתה","descriptionText":"אין שירות","cause":"STRIKE","effect":"NO_SERVICE",
               "impactPeriod":[{"start":"2026-10-05T04:00:00Z","end":"2026-10-05T10:00:00Z"}],"imageUrl":"x"}""",
        )
        assertEquals("STRIKE", alert.cause)
        assertEquals("NO_SERVICE", alert.effect)
        assertTrue(alert.inEffectAt(Instant.parse("2026-10-05T05:00:00Z")))
        assertFalse(alert.inEffectAt(Instant.parse("2026-10-05T10:00:00Z")))
        assertTrue(Alert(headerText = "always").inEffectAt(NOON))
        assertTrue(TimeRange(start = "2026-10-05T04:00:00+03:00").contains(Instant.parse("2026-10-05T01:00:00Z")))
    }

    // --- /api/v6/trip ---

    @Test fun `a train trip is one leg from the first to the last station, with the train number`() {
        val it = tripFixture("trip_train_bs_telaviv")
        val leg = it.legs.single()
        assertEquals(0, it.transfers)
        assertEquals("REGIONAL_RAIL", leg.mode)
        assertEquals(train, leg.tripId)
        assertEquals("באר שבע מרכז", leg.from.name)
        assertEquals("תל אביב מרכז", leg.to.name)
        assertEquals("", leg.routeShortName)
        assertEquals("7026", leg.headsign)
        assertEquals(
            listOf("באר שבע צפון", "להבים רהט", "קרית גת", "קרית מלאכי", "מזכרת בתיה", "רמלה", "לוד", "תל אביב ההגנה", "השלום"),
            leg.intermediateStops.map { s -> s.name },
        )
        // Every stop has both times, scheduled = live (no real-time), none skipped.
        assertTrue(leg.intermediateStops.all { s -> s.arrival != null && s.arrival == s.scheduledArrival && !s.cancelled })
        assertEquals(6, leg.legGeometry!!.precision)
        assertEquals(Instant.parse("2026-10-03T20:32:00Z"), it.start)
        assertEquals(Instant.parse("2026-10-03T22:03:00Z"), it.end)
    }

    @Test fun `a bus trip is one leg with its line number and every stop`() {
        val leg = tripFixture("trip_bus_470").legs.single()
        assertEquals("BUS", leg.mode)
        assertEquals(bus470, leg.tripId)
        assertEquals("470", leg.routeShortName)
        assertEquals("ת.מרכזית באר שבע/רציפים בינעירוני", leg.from.name)
        assertEquals("ת. מרכזית ירושלים/הורדה", leg.to.name)
        assertEquals(6, leg.intermediateStops.size)
        assertFalse(leg.realTime)
        assertTrue(Geo.decodePolyline(leg.legGeometry!!.points, 6).first().let { p -> p.lat in 31.0..31.5 })
    }

    // --- /api/v6/map/trips ---

    @Test fun `map trips are stop-to-stop hops with a precision-5 polyline`() {
        val all = segments()
        assertEquals(134, all.size)
        assertTrue(all.all { s -> s.trips.size == 1 && !s.realTime })
        val hop = all.single { s -> s.tripId == bus470 }
        assertEquals("470", hop.routeName)
        assertEquals("ת.מרכזית באר שבע/רציפים בינעירוני", hop.from.name)
        assertEquals("מרכז רפואי סורוקה/אוניברסיטת בן גוריון", hop.to.name)
        assertEquals(Instant.parse("2026-10-03T20:30:00Z"), hop.depart)
        assertEquals(Instant.parse("2026-10-03T20:37:00Z"), hop.arrive)
        val path = hop.path()
        assertTrue(path.size > 2)
        // Precision 5 lands in Be'er Sheva; precision 6 would put it off the coast of Africa.
        assertTrue(path.all { p -> p.lat in 31.2..31.3 && p.lon in 34.7..34.9 })
        assertTrue(Geo.distanceM(path.first(), hop.from.latLon) < 200)
        // A train's displayName is the long "A<->B" name, not the train number.
        assertEquals("באר שבע מרכז-באר שבע<->תל אביב מרכז-תל אביב יפו", all.single { s -> s.tripId == train }.routeName)
    }

    @Test fun `map trips can include a foreign trip whose hop crosses the box - select by trip id`() {
        val foreign = segments().filter { s -> !s.tripId.contains("il-Israel-MOT_") }
        assertEquals(1, foreign.size)
        assertEquals("ROMA TERMINI", foreign.single().from.name)
    }

    // --- CAR_PARKING verdict ---

    @Test fun `CAR_PARKING parks at unnamed lots near bus stops, never at a station - and arrives later than CAR`() {
        val parking = plan("plan_car_parking_meitar").itineraries
        assertEquals(5, parking.size)
        for (it in parking) {
            val car = it.legs.first()
            assertEquals(StreetModes.CAR, car.mode)
            assertEquals("", car.to.name) // an OSM parking lot, not a stop
            assertNull(car.to.stopId)
            assertEquals(StreetModes.WALK, it.legs[1].mode)
            assertEquals("BUS", it.firstTransitLeg!!.mode) // never a train straight from the car
            assertEquals(1, it.leadingCarLegs().size)
        }
        // Best: 9′ drive west, 11′ walk to Hilfi Lakiya junction, bus 425 to Lehavim, train.
        val best = parking.minBy { it.end }
        assertEquals(listOf(540, 660), best.legs.take(2).map { l -> l.duration })
        assertEquals("חלפי לקייה", best.legs[1].to.name)
        assertEquals(Instant.parse("2026-10-05T07:06:00Z"), best.end)
        // CAR (same Monday 08:00, 20-min cap) drives 15′ to Be'er Sheva North and is in Tel Aviv
        // Center — one stop past HaHagana — at 09:46, 20 min earlier.
        val car = plan("plan_car_pre_meitar").itineraries.minBy { it.end }
        assertEquals("באר שבע צפון", car.legs.first().to.name)
        assertEquals(Instant.parse("2026-10-05T06:46:00Z"), car.end)
    }

    // --- client, guards, budget ---

    @Test fun `client sends trip and map-trips queries and parses the recorded answers`() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            val client = MotisClient(server.url("/").toString())
            server.enqueue(MockResponse().setBody(fixture("trip_bus_470")))
            assertEquals("470", client.trip(bus470).legs.single().routeShortName)
            val tripUrl = server.takeRequest().requestUrl!!
            assertEquals("/api/v6/trip", tripUrl.encodedPath)
            assertEquals(bus470, tripUrl.queryParameter("tripId"))
            assertEquals("he", tripUrl.queryParameter("language"))

            server.enqueue(MockResponse().setBody(fixture("map_trips_beersheva")))
            val box = BBox(LatLon(31.23, 34.77), LatLon(31.28, 34.83))
            val start = Instant.parse("2026-10-03T20:33:00Z")
            val segs = client.mapTrips(box, start, start.plusSeconds(60), zoom = 14.0)
            assertEquals(134, segs.size)
            val req = server.takeRequest()
            val url = req.requestUrl!!
            assertEquals("/api/v6/map/trips", url.encodedPath)
            assertEquals("31.23,34.77", url.queryParameter("min"))
            assertEquals("31.28,34.83", url.queryParameter("max"))
            assertEquals("14.0", url.queryParameter("zoom"))
            assertEquals("2026-10-03T20:33:00Z", url.queryParameter("startTime"))
            assertEquals("2026-10-03T20:34:00Z", url.queryParameter("endTime"))
            assertTrue(req.getHeader("User-Agent")!!.contains("github.com/EitanPinczowski/israel-transit-planner"))
        } finally {
            server.shutdown()
        }
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }

    @Test fun `guard caches a trip for 30 s and map trips for 20 s`() = runTest {
        val fake = FakeTransitApi().apply {
            onTrip = { tripFixture("trip_bus_470") }
            onMapTrips = { _, _, _, _ -> segments() }
        }
        val clock = MutableClock(NOON)
        val api = GuardedTransitApi(fake, clock)
        val box = BBox(LatLon(31.23, 34.77), LatLon(31.28, 34.83))
        suspend fun both() {
            api.trip(bus470)
            api.mapTrips(box, NOON, NOON.plusSeconds(60), 14.0)
        }
        both()
        clock.now = NOON.plusSeconds(19)
        both()
        assertEquals(listOf("trip", "mapTrips"), fake.calls)
        clock.now = NOON.plusSeconds(21) // map trips expired, trip still fresh
        both()
        assertEquals(listOf("trip", "mapTrips", "mapTrips"), fake.calls)
        clock.now = NOON.plusSeconds(31) // trip expired; map trips re-fetched at 21 s still fresh
        both()
        assertEquals(listOf("trip", "mapTrips", "mapTrips", "trip"), fake.calls)
        assertEquals(30L, GuardedTransitApi.TRIP_TTL.seconds)
        assertEquals(20L, GuardedTransitApi.MAP_TRIPS_TTL.seconds)
    }

    @Test fun `budget counts trip and map trips`() = runTest {
        val fake = FakeTransitApi().apply {
            onTrip = { tripFixture("trip_train_bs_telaviv") }
            onMapTrips = { _, _, _, _ -> emptyList() }
        }
        val api = BudgetedTransitApi(fake, budget = 2)
        api.trip(train)
        api.mapTrips(BBox(LatLon(31.0, 34.0), LatLon(31.1, 34.1)), NOON, NOON.plusSeconds(60), 14.0)
        assertEquals(2, api.used)
        try {
            api.trip(train)
            fail("third request must exceed the budget")
        } catch (e: BudgetExceededException) {
            assertEquals(2, e.budget)
        }
    }
}
