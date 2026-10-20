package il.transit.core

import il.transit.core.api.Leg
import il.transit.core.api.MotisJson
import il.transit.core.api.Place
import il.transit.core.api.PlanResponse
import il.transit.core.present.AccessibleBadge
import il.transit.core.present.accessibleBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Phase 11 E3 item 8: the accessible-bus badge, and the sample that allowed it. No request. */
class AccessibleBadgeTest {
    private val a = Place(name = "A", lat = 31.25, lon = 34.79)
    private val b = Place(name = "B", lat = 31.26, lon = 34.80)
    private fun ride(mode: String, value: String?) = leg(mode, a, b, NOON, NOON.plusSeconds(600)).copy(wheelchairAccessible = value)

    @Test
    fun `each value on a bus, with the setting on`() {
        assertEquals(AccessibleBadge.ACCESSIBLE, accessibleBadge(ride("BUS", "ACCESSIBLE"), true))
        assertEquals(AccessibleBadge.MAY_NOT_BE, accessibleBadge(ride("BUS", "NOT_ACCESSIBLE"), true))
        assertEquals(AccessibleBadge.ACCESSIBLE, accessibleBadge(ride("COACH", "ACCESSIBLE"), true))
        assertNull(accessibleBadge(ride("BUS", "UNKNOWN"), true))
        assertNull(accessibleBadge(ride("BUS", null), true))
    }

    @Test
    fun `rail, light rail and street legs are ignored`() {
        listOf("REGIONAL_RAIL", "RAIL", "TRAM", "SUBWAY", "WALK", "CAR").forEach { m ->
            assertNull(m, accessibleBadge(ride(m, "ACCESSIBLE"), true))
            assertNull(m, accessibleBadge(ride(m, "NOT_ACCESSIBLE"), true))
        }
    }

    @Test
    fun `nothing with the Accessible-routes setting off`() {
        assertNull(accessibleBadge(ride("BUS", "ACCESSIBLE"), false))
        assertNull(accessibleBadge(ride("BUS", "NOT_ACCESSIBLE"), false))
    }

    @Test
    fun `answers without the fields (older servers) still decode, to null`() {
        // Every fixture recorded so far carries both fields, so strip them from a real one.
        val raw = javaClass.getResource("/fixtures/plan_bgu_telaviv.json")!!.readText()
        val stripped = raw.replace(Regex(""""(wheelchairAccessible|tripShortName)": *"[^"]*",?"""), "")
        assertTrue(stripped.length < raw.length)
        val legs = legsOf(MotisJson.decodeFromString(PlanResponse.serializer(), stripped))
        assertTrue(legs.isNotEmpty())
        assertTrue(legs.all { it.wheelchairAccessible == null && it.tripShortName == null })
    }

    private fun legsOf(p: PlanResponse) = p.itineraries.flatMap { it.legs }

    /** The go/no-go gate: ≥ 90 % of bus legs carry a value, and one route + operator never carries two. */
    @Test
    fun `the 2026-10-20 sample passes the gate`() {
        val dir = File(javaClass.getResource("/fixtures/plan_access_telaviv.json")!!.toURI()).parentFile
        val files = dir.listFiles { f -> f.name.startsWith("plan_access_") }!!
        assertEquals(9, files.size)
        val legs = files.flatMap { f -> legsOf(MotisJson.decodeFromString(PlanResponse.serializer(), f.readText())) }
        val buses = legs
            .filter { it.mode == "BUS" }
            .distinctBy { it.tripId to it.from.stopId }
        val known = buses.count { it.wheelchairAccessible == "ACCESSIBLE" || it.wheelchairAccessible == "NOT_ACCESSIBLE" }
        assertEquals(81, buses.size)
        assertTrue("$known of ${buses.size}", known >= buses.size * 0.9)
        val perRoute = buses.groupBy { it.agencyName to it.routeShortName }.mapValues { (_, l) -> l.map(Leg::wheelchairAccessible).toSet() }
        assertTrue(perRoute.filterValues { it.size > 1 }.toString(), perRoute.values.all { it.size == 1 })
    }
}
