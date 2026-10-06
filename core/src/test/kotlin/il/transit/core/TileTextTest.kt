package il.transit.core

import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.features.ISRAEL
import il.transit.core.geo.LatLon
import il.transit.core.plan.CacheLookup
import il.transit.core.plan.PlanCache
import il.transit.core.plan.TripPages
import il.transit.core.plan.TripResult
import il.transit.core.present.TileText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

/** The tile's label cases (Phase 9 C4), on a recorded Saturday-night plan home. */
class TileTextTest {
    private fun il(local: String): Instant = LocalDateTime.parse(local).atZone(ISRAEL).toInstant()

    private val result: TripResult = run {
        val resp = MotisJson.decodeFromString(PlanResponse.serializer(), javaClass.getResource("/fixtures/plan_now_bs_hahagana.json")!!.readText())
        TripResult(TripPages.order(TripPages.sane(resp.itineraries), false), null)
    }
    private val home = LatLon(32.053978, 34.78484)
    private val cache = listOf(PlanCache.Entry(PlanCache.key("TRIP-NOW", LatLon(31.262, 34.801), home), il("2026-10-03T23:20"), result))

    private fun text(now: Instant, homeSet: Boolean = true, entries: List<PlanCache.Entry<TripResult>> = cache) =
        TileText.of(homeSet, CacheLookup.tripHome(entries, home, now))

    @Test fun `no Home - Set Home, whatever is cached`() {
        assertEquals(TileText.NoHome, text(il("2026-10-03T23:30"), homeSet = false))
    }

    @Test fun `no cache - tap to plan`() {
        assertEquals(TileText.TapToPlan, text(il("2026-10-03T23:30"), entries = emptyList()))
    }

    @Test fun `the next option home, past midnight, with an en dash`() {
        val t = text(il("2026-10-03T23:30")) as TileText.Next
        val first = result.itineraries.first()
        assertEquals(il.transit.core.present.hhmm(first.start), t.leave)
        assertEquals("${t.leave}–${t.arrive}", t.times)
        assertEquals(true, t.arrive.startsWith("0")) // arrives after midnight
    }

    @Test fun `an option whose bus just left gives way to the next one`() {
        val firstBus = result.itineraries.first().firstTransitLeg!!.start
        val before = text(firstBus) as TileText.Next
        val after = text(firstBus.plusSeconds(60)) as TileText.Next
        assertEquals(il.transit.core.present.hhmm(result.itineraries.first().start), before.leave)
        org.junit.Assert.assertNotEquals(before, after)
    }

    @Test fun `stale cache - every option gone - tap to plan (Saturday afternoon)`() {
        assertEquals(TileText.TapToPlan, text(il("2026-10-04T15:00")))
    }

    @Test fun `the 04-00 service-day edge - a night entry for the morning shows before and after 04-00`() {
        val morning = il("2026-10-05T05:10")
        val bus = leg("BUS", place("Home", LatLon(31.262, 34.801)), place("Office", home), morning, morning.plusSeconds(3000))
        val night = listOf(PlanCache.Entry(PlanCache.key("TRIP-NOW", LatLon(31.262, 34.801), home), il("2026-10-05T02:14"), TripResult(listOf(itinerary(bus)), null), night = true))
        assertEquals(TileText.Next("05:10", "06:00"), text(il("2026-10-05T03:59"), entries = night))
        assertEquals(TileText.Next("05:10", "06:00"), text(il("2026-10-05T04:01"), entries = night))
        assertEquals(TileText.TapToPlan, text(il("2026-10-05T05:11"), entries = night))
    }
}
