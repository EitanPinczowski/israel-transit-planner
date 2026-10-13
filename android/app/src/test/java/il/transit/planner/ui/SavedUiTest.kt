package il.transit.planner.ui

import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.geo.LatLon
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

    @Test fun `only the plain Trip tab with options keeps its cache key`() {
        val results = TripResult(plan.itineraries, null)
        val trip = UiState(from = bgu, to = savidor, results = results, selected = 1)
        assertEquals("k", SavedUi.of(trip, "k", null, null).cacheKey)
        assertNull("closed results", SavedUi.of(trip.copy(results = null), "k", null, null).cacheKey)
        assertNull("a car tab is never cached", SavedUi.of(trip.copy(mode = AppMode.PICK_UP), "k", null, null).cacheKey)
        assertEquals(SavedUi.Sheet.Ride(1), SavedUi.of(trip, "k", 1, null).sheet)
        val stop = trip.copy(stopSheet = StopSheet("s1", "Stop", loading = false, rows = emptyList(), failed = false))
        assertEquals(SavedUi.Sheet.Stop("s1", "Stop"), SavedUi.of(stop, "k", null, null).sheet)
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
