package il.transit.core

import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.geo.LatLon
import il.transit.core.present.StopPlatform
import il.transit.core.present.parseStopDescription
import il.transit.core.present.stopPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Platform 12 · floor 6 · stop 47899" from the stop description (Phase 9 C1). */
class StopPlatformTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name.json")!!.readText()

    @Test fun `platform and floor from the MOT text`() {
        assertEquals(
            StopPlatform("626", "6"),
            parseStopDescription("רחוב: תחנה מרכזית קומה 6 עיר: תל אביב יפו רציף: 626 קומה: 6"),
        )
        assertEquals(StopPlatform("15"), parseStopDescription("רחוב: יהושע חנקין עיר: באר שבע רציף: 15  קומה:"))
        assertEquals(StopPlatform(), parseStopDescription("רחוב: שדרות דוד בן גוריון 11 עיר: באר שבע רציף:  קומה:"))
    }

    @Test fun `only a floor`() {
        assertEquals(StopPlatform(floor = "2"), parseStopDescription("רחוב: הרצל עיר: חיפה רציף:   קומה: 2"))
    }

    @Test fun `odd text gives nulls, never a crash`() {
        val odd = listOf(
            null, "", "   ", "Platform: 3", "Herzl St, Tel Aviv", "רציף:", ":", "רציף::", "קומה",
            "רחוב: א עיר: ב תת-רציף: 4", "רציף: ${"x".repeat(40)}", "גוש: 7 חלקה: 8",
        )
        for (t in odd) assertEquals(t, StopPlatform(), parseStopDescription(t))
        assertEquals(StopPlatform("3"), parseStopDescription("  שער: B רציף:3 ")) // unknown key, no space after ':'
    }

    @Test fun `stop code from the place, nothing for a train station`() {
        val bus = place("x", LatLon(31.0, 34.0)).copy(description = "רציף: 2", stopCode = "13907", modes = listOf("BUS"))
        assertEquals(StopPlatform("2", null, "13907"), stopPlatform(bus))
        val rail = bus.copy(stopCode = "17084", description = null, modes = listOf("REGIONAL_RAIL"))
        assertTrue(stopPlatform(rail).isEmpty)
        assertTrue(stopPlatform(place("y", LatLon(31.0, 34.0)).copy(stopCode = " ")).isEmpty)
    }

    @Test fun `recorded plans carry description and stop code`() {
        val plan = MotisJson.decodeFromString(PlanResponse.serializer(), fixture("plan_pages_first"))
        val bus370 = plan.itineraries[1].firstTransitLeg!!
        assertEquals("370", bus370.routeShortName)
        assertEquals(StopPlatform("2", null, "13907"), stopPlatform(bus370.from))
        val train = plan.itineraries[0].firstTransitLeg!!
        assertEquals("17084", train.from.stopCode)
        assertTrue(stopPlatform(train.from).isEmpty)
        assertTrue(train.from.track.isNullOrEmpty())
        // Every description in the recorded plans parses.
        val all = listOf("plan_pages_first", "plan_pages_later", "plan_pages_earlier", "plan_now_bs_hahagana", "plan_bgu_telaviv")
            .flatMap { MotisJson.decodeFromString(PlanResponse.serializer(), fixture(it)).itineraries }
            .flatMap { it.legs }.flatMap { listOf(it.from, it.to) + it.intermediateStops }
        val withPlatform = all.mapNotNull { parseStopDescription(it.description).platform }.toSet()
        assertTrue(withPlatform.toString(), "2" in withPlatform)
    }

    @Test fun `trip sheet shows platform and code on the boarding and alighting rows only`() {
        val trip = MotisJson.decodeFromString(il.transit.core.api.Itinerary.serializer(), fixture("trip_bus_470"))
        val t = trip.legs.single()
        val stops = listOf(t.from) + t.intermediateStops + listOf(t.to)
        // Boarding at the first stop: Be'er Sheva central station, platform 10.
        val leg = t.copy(to = stops[3], intermediateStops = stops.subList(1, 3), endTime = stops[3].arrival!!)
        val d = il.transit.core.present.tripDetails(leg, trip, java.time.Instant.parse("2026-10-03T20:00:00Z"))
        assertEquals(StopPlatform("10", null, "13907"), d.rows[0].platform)
        assertEquals(StopPlatform(null, null, "34290"), d.rows[3].platform)
        assertTrue(d.rows.filterIndexed { i, _ -> i != 0 && i != 3 }.all { it.platform.isEmpty })
    }
}
