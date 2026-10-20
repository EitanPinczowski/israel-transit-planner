package il.transit.core

import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.plan.TripCacheJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Phase 11 R1 made the Trip cache's codec generic (`CacheJson`). A phone that updates from v0.9.0
 * keeps its `trip_cache.json`: `upgrade/trip_cache_v0.9.0.json` is that file as v0.9.0 wrote it
 * (its `Row`, `TripResult` and `Leg` fields only, defaults and nulls left out, `plan_bgu_telaviv`'s
 * options), and it must still read.
 */
class TripCacheUpgradeTest {
    private val old = javaClass.getResource("/upgrade/trip_cache_v0.9.0.json")!!.readText()
    private val plan = MotisJson.decodeFromString(PlanResponse.serializer(), javaClass.getResource("/fixtures/plan_bgu_telaviv.json")!!.readText())

    @Test fun `a v0_9_0 trip cache still reads, its rows and their options whole`() {
        val entries = TripCacheJson.decode(old)
        assertEquals(listOf("TRIP-NOW:31.262,34.801|32.084,34.798", "TRIP-NOW:31.262,34.801|32.08,34.79"), entries.map { it.key })
        assertEquals(Instant.ofEpochSecond(1_791_262_500), entries[0].savedAt)
        assertEquals(listOf(false, true), entries.map { it.night })
        val day = entries[0].value
        assertEquals(3, day.itineraries.size)
        assertNull(day.walkOnly)
        day.itineraries.zip(plan.itineraries).forEach { (back, recorded) ->
            assertEquals(recorded.startTime, back.startTime)
            assertEquals(recorded.endTime, back.endTime)
            assertEquals(recorded.legs.map { it.mode to it.routeShortName }, back.legs.map { it.mode to it.routeShortName })
            assertEquals(recorded.legs.map { it.from.stopId }, back.legs.map { it.from.stopId })
            assertEquals(recorded.legs.map { it.legGeometry?.points }, back.legs.map { it.legGeometry?.points })
        }
        // Fields added since v0.9.0 read as their defaults.
        assertTrue(day.itineraries.flatMap { it.legs }.all { it.wheelchairAccessible == null && it.tripShortName == null })
        val night = entries[1].value
        assertEquals("LATER|1791262800", night.laterCursor)
        assertEquals(1_791_248_040L, night.searchEpoch)
        assertFalse(night.paged)
    }

    @Test fun `written again by this version it reads the same`() {
        val entries = TripCacheJson.decode(old)
        assertEquals(entries, TripCacheJson.decode(TripCacheJson.encode(entries)))
    }
}
