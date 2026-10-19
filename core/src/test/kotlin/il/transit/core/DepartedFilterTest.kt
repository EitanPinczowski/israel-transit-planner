package il.transit.core

import il.transit.core.api.Endpoint
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.api.parseTime
import il.transit.core.features.ISRAEL
import il.transit.core.geo.LatLon
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripQuery
import il.transit.core.plan.TripResult
import il.transit.core.present.hhmm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

/**
 * Phase 11 E7 (owner's "wrong trip results"): the live Trip search never shows an option whose
 * first boarding has already gone. The recorded BGU → Tel Aviv answer of Mon 5 Oct 2026 boards at
 * 08:04, 08:34, 08:37, 08:48 and 09:07 (Israel time); the walks before them start earlier.
 */
class DepartedFilterTest {
    private fun il(s: String): Instant = LocalDateTime.parse("2026-10-05T$s").atZone(ISRAEL).toInstant()

    private fun search(mode: TimeMode, time: Instant?, now: Instant): TripResult = runBlocking {
        val api = FakeTransitApi().apply { onPlan = { recorded } }
        val r = TripPlanner(api).plan(TripQuery(Endpoint.Coord(bgu), Endpoint.Coord(telAviv), mode, time), now)
        assertEquals("a filter, never a request", listOf("plan"), api.calls)
        r
    }

    private fun boardings(r: TripResult): List<String> {
        val times = r.itineraries.map { hhmm(parseTime(it.firstTransitLeg!!.startTime)) }
        return times.sorted()
    }

    @Test fun `Now - options that boarded before now minus a minute are dropped`() {
        assertEquals(listOf("08:48", "09:07"), boardings(search(TimeMode.NOW, null, il("08:40"))))
    }

    @Test fun `a boarding a minute ago is kept, and a walk that started earlier does not count`() {
        assertEquals(listOf("08:37", "08:48", "09:07"), boardings(search(TimeMode.NOW, null, il("08:38"))))
        // The 08:37 option starts walking at 08:27: at 08:30 it is still ahead.
        assertEquals(listOf("08:34", "08:37", "08:48", "09:07"), boardings(search(TimeMode.NOW, null, il("08:30"))))
    }

    @Test fun `Depart at within the next minutes is treated as now, later ones are left alone`() {
        assertEquals(listOf("08:48", "09:07"), boardings(search(TimeMode.DEPART_AT, il("08:50"), il("08:40"))))
        // A restored or past Depart at: same rule.
        assertEquals(listOf("08:48", "09:07"), boardings(search(TimeMode.DEPART_AT, il("08:00"), il("08:40"))))
        // An hour ahead the timetable answer is MOTIS's own: nothing dropped.
        assertEquals(5, search(TimeMode.DEPART_AT, il("09:40"), il("08:40")).itineraries.size)
    }

    @Test fun `the live window ends at 15 minutes - 14 59 is filtered, 15 00 and later are not`() {
        assertEquals(15 * 60L, TripPlanner.LIVE_WINDOW_SEC)
        assertEquals(60L, TripPlanner.DEPARTED_GRACE_SEC)
        val now = il("08:40")
        assertEquals(listOf("08:48", "09:07"), boardings(search(TimeMode.DEPART_AT, now.plusSeconds(14 * 60 + 59), now)))
        assertEquals(5, search(TimeMode.DEPART_AT, now.plusSeconds(15 * 60), now).itineraries.size)
        assertEquals(5, search(TimeMode.DEPART_AT, now.plusSeconds(15 * 60 + 1), now).itineraries.size)
    }

    @Test fun `an option with no ride (walk only) is never dropped`() = runBlocking {
        val walk = recorded.itineraries.first().let { it.copy(legs = it.legs.filter { l -> !l.isTransit }) }
        val api = FakeTransitApi().apply { onPlan = { recorded.copy(itineraries = recorded.itineraries + walk) } }
        val r = TripPlanner(api).plan(TripQuery(Endpoint.Coord(bgu), Endpoint.Coord(telAviv), TimeMode.NOW, null), il("09:30"))
        assertTrue("every ride has left at 09:30; the walk stays", r.itineraries.isNotEmpty() && r.itineraries.all { it.firstTransitLeg == null })
    }

    @Test fun `without the caller's clock (errands, the reminder re-check) MOTIS's answer is kept as it is`() = runBlocking {
        val api = FakeTransitApi().apply { onPlan = { recorded } }
        val r = TripPlanner(api).plan(TripQuery(Endpoint.Coord(bgu), Endpoint.Coord(telAviv), TimeMode.DEPART_AT, il("08:00")))
        assertEquals(5, r.itineraries.size)
    }

    @Test fun `Arrive by is untouched`() {
        assertEquals(5, search(TimeMode.ARRIVE_BY, il("10:30"), il("08:40")).itineraries.size)
    }

    @Test fun `no shown option boards in the past, at any minute of the morning`() {
        var now = il("07:30")
        while (now <= il("09:30")) {
            val r = search(TimeMode.NOW, null, now)
            val cutoff = now.minusSeconds(TripPlanner.DEPARTED_GRACE_SEC)
            r.itineraries.forEach { o -> assertTrue("$now: ${o.firstTransitLeg!!.startTime}", parseTime(o.firstTransitLeg!!.startTime) >= cutoff) }
            // Nothing still ahead is lost either.
            val ahead = recorded.itineraries.count { parseTime(it.firstTransitLeg!!.startTime) >= cutoff }
            assertTrue("$now: ${r.itineraries.size} shown, $ahead ahead", r.itineraries.size <= ahead)
            now = now.plusSeconds(60)
        }
    }

    companion object {
        private val bgu = LatLon(31.2622, 34.8013)
        private val telAviv = LatLon(32.0839, 34.7983)
        private val recorded: PlanResponse = MotisJson.decodeFromString(
            PlanResponse.serializer(),
            DepartedFilterTest::class.java.getResource("/fixtures/plan_bgu_telaviv.json")!!.readText(),
        )
    }
}
