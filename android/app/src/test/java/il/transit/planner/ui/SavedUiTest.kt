package il.transit.planner.ui

import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.features.PickUpResult
import il.transit.core.geo.LatLon
import il.transit.core.plan.ChainResult
import il.transit.core.plan.ChainStop
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Phase 10 D11: what survives process death, through a map standing in for SavedStateHandle. */
class SavedUiTest {
    private fun roundTrip(s: SavedUi): SavedUi? {
        val bundle = HashMap<String, Any?>()
        s.write { k, v -> bundle[k] = v }
        // Only types a Bundle keeps (SavedStateHandle refuses the rest at runtime).
        bundle.values.filterNotNull().forEach { v -> assertTrue("$v", v is String || v is Int || v is Long || v is Double) }
        return SavedUi.read { bundle[it] }
    }

    private val bgu = PlaceRef.Point("אוניברסיטת בן גוריון", LatLon(31.262, 34.801))
    private val savidor = PlaceRef.Point("ת. רכבת תל אביב - סבידור", LatLon(32.08, 34.79))

    @Test fun `search, tab, chosen option and trip sheet come back`() {
        val s = SavedUi(
            mode = AppMode.TRIP, from = PlaceRef.MyLocation, to = savidor, timeMode = TimeMode.ARRIVE_BY,
            time = Instant.parse("2026-10-05T06:30:00Z"), selected = 2, optionId = "1-2-3", cacheKey = "TRIP-ARRIVE_BY|31.26,34.8|32.08,34.79",
            sheet = SavedUi.Sheet.Ride(1),
        )
        assertEquals(s, roundTrip(s))
    }

    @Test fun `a car tab with its third field and a stop sheet`() {
        val s = SavedUi(
            mode = AppMode.DROP_OFF, from = bgu, to = savidor, driverTo = PlaceRef.Point(null, LatLon(32.08, 34.81)),
            sheet = SavedUi.Sheet.Stop("il-Israel-MOT_37314", "באר שבע צפון"),
        )
        assertEquals(s, roundTrip(s))
    }

    @Test fun `nothing saved is a fresh start`() {
        assertNull(SavedUi.read { null })
    }

    @Test fun `a value cleared since the last save is cleared there too`() {
        val bundle = HashMap<String, Any?>()
        SavedUi(to = savidor, cacheKey = "k", sheet = SavedUi.Sheet.Ride(1)).write { k, v -> bundle[k] = v }
        SavedUi(to = null).write { k, v -> bundle[k] = v }
        assertEquals(SavedUi(to = null), SavedUi.read { bundle[it] })
    }

    @Test fun `a key is kept only for what this tab shows`() {
        val results = TripResult(plan.itineraries, null)
        val trip = UiState(from = bgu, to = savidor, results = results, selected = 1)
        val tripKey = "TRIP-NOW:31.262,34.801|32.08,34.79"
        assertEquals(tripKey, SavedUi.of(trip, tripKey, null, null).cacheKey)
        assertNull("closed results", SavedUi.of(trip.copy(results = null), tripKey, null, null).cacheKey)
        assertNull("another tab's key", SavedUi.of(trip.copy(mode = AppMode.PICK_UP), tripKey, null, null).cacheKey)
        val pickUp = trip.copy(mode = AppMode.PICK_UP, results = null, pickUp = PickUpResult(plan.itineraries.first(), emptyList()))
        assertEquals("PICK_UP-NOW-15:x", SavedUi.of(pickUp, "PICK_UP-NOW-15:x", null, null).cacheKey)
        assertNull("a car tab with no answer", SavedUi.of(pickUp.copy(pickUp = null), "PICK_UP-NOW-15:x", null, null).cacheKey)
        val errands = trip.copy(results = null, chain = ChainResult(plan.itineraries.take(2), null))
        assertEquals("ERRANDS-NOW:x", SavedUi.of(errands, "ERRANDS-NOW:x", null, null).cacheKey)
        assertNull("a chain is not the plain Trip", SavedUi.of(errands, tripKey, null, null).cacheKey)
        assertEquals(SavedUi.Sheet.Ride(1), SavedUi.of(trip, tripKey, 1, null).sheet)
        val stop = trip.copy(stopSheet = StopSheet("s1", "Stop", loading = false, rows = emptyList(), failed = false))
        assertEquals(SavedUi.Sheet.Stop("s1", "Stop"), SavedUi.of(stop, tripKey, null, null).sheet)
    }

    @Test fun `errand stops and the car sliders come back (R1)`() {
        val s = SavedUi(
            mode = AppMode.TRIP,
            to = savidor,
            cacheKey = "ERRANDS-NOW:x",
            stops = listOf(ChainStop(LatLon(31.2589, 34.7992), "סורוקה", 15), ChainStop(LatLon(31.25, 34.79), null, 0)),
            maxDriveMin = 7,
            maxDetourMin = 25,
            maxPickUpDriveMin = 30,
            maxParkRideMin = 35,
        )
        assertEquals(s, roundTrip(s))
        val st = UiState(chainStops = s.stops, maxDriveMin = 7, maxDetourMin = 25, maxPickUpDriveMin = 30, parkRide = ParkRideUi(maxDriveMin = 35))
        val of = SavedUi.of(st, null, null, null)
        assertEquals(s.stops, of.stops)
        assertEquals(listOf(7, 25, 30, 35), listOf(of.maxDriveMin, of.maxDetourMin, of.maxPickUpDriveMin, of.maxParkRideMin))
    }

    @Test fun `r1 keys of the wrong type fall back to the defaults, never throw`() {
        val bundle = HashMap<String, Any?>()
        val stops = listOf(ChainStop(LatLon(31.2589, 34.7992), "Soroka", 15), ChainStop(LatLon(31.25, 34.79), "Two", 5))
        SavedUi(mode = AppMode.PARK_RIDE, stops = stops, maxDriveMin = 7, maxDetourMin = 25, maxPickUpDriveMin = 30, maxParkRideMin = 35)
            .write { k, v -> bundle[k] = v }
        bundle["r1.maxDrive"] = "7"
        bundle["r1.maxDetour"] = 25L
        bundle["r1.maxPickUp"] = 30.0
        bundle["r1.maxParkRide"] = true
        bundle["r1.errand0.lat"] = "31.2589"
        bundle["r1.errand1.stay"] = "5"
        val back = SavedUi.read { bundle[it] }!!
        val defaults = SavedUi()
        assertEquals(
            listOf(defaults.maxDriveMin, defaults.maxDetourMin, defaults.maxPickUpDriveMin, defaults.maxParkRideMin),
            listOf(back.maxDriveMin, back.maxDetourMin, back.maxPickUpDriveMin, back.maxParkRideMin),
        )
        assertEquals("a stop with no readable place is left out; a bad stay is no stay", listOf(stops[1].copy(stayMin = 0)), back.stops)
        assertEquals(AppMode.PARK_RIDE, back.mode)
    }

    @Test fun `fewer errand stops than last time clear the old ones`() {
        val bundle = HashMap<String, Any?>()
        val three = (1..3).map { ChainStop(LatLon(31.0 + it / 100.0, 34.8), "s$it", it) }
        SavedUi(stops = three).write { k, v -> bundle[k] = v }
        SavedUi(stops = three.take(1)).write { k, v -> bundle[k] = v }
        assertEquals(three.take(1), SavedUi.read { bundle[it] }!!.stops)
    }

    @Test fun `the chosen option is found again by its times`() {
        val it = plan.itineraries[2]
        assertEquals("${it.start.epochSecond}-${it.end.epochSecond}-${it.legs.size}", MainViewModel.optionId(it))
        assertEquals(2, plan.itineraries.indexOfFirst { o -> MainViewModel.optionId(o) == MainViewModel.optionId(it) })
    }

    private val plan = MotisJson.decodeFromString(
        PlanResponse.serializer(),
        SavedUiTest::class.java.getResource("/fixtures/plan_bgu_telaviv.json")!!.readText(),
    )
}
