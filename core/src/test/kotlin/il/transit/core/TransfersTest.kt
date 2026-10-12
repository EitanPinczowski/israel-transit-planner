package il.transit.core

import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.MotisJson
import il.transit.core.api.Place
import il.transit.core.api.PlanRequest
import il.transit.core.api.PlanResponse
import il.transit.core.geo.LatLon
import il.transit.core.present.TIGHT_MIN
import il.transit.core.present.TIGHT_TRAIN_MIN
import il.transit.core.present.TransferWarning
import il.transit.core.present.tightTransfers
import il.transit.core.present.transferWarning
import il.transit.core.present.transfers
import il.transit.core.present.warnBelowMin
import il.transit.core.user.UserJson
import il.transit.core.user.UserSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Phase 10 D1: tight-transfer warning and the "keep transfers at least" buffer. No request. */
class TransfersTest {
    private fun stop(name: String, id: String = "s-$name") = Place(name = name, lat = 32.0, lon = 34.8, stopId = id)
    private fun at(min: Long, base: Instant = NOON) = base.plusSeconds(min * 60)

    private fun bus(from: Place, to: Place, s: Long, e: Long, base: Instant = NOON) = leg("BUS", from, to, at(s, base), at(e, base))
    private fun train(from: Place, to: Place, s: Long, e: Long) = leg("RAIL", from, to, at(s), at(e))
    private fun walk(from: Place, to: Place, s: Long, e: Long, base: Instant = NOON) = leg("WALK", from, to, at(s, base), at(e, base))

    private val savidor = stop("Savidor")

    @Test
    fun `same stop change, 1 min fires, 2 min does not`() {
        val itin = itinerary(bus(stop("A"), savidor, 0, 20), bus(savidor, stop("B"), 21, 40), transfers = 1)
        val t = transfers(itin).single()
        assertEquals(0, t.fromLeg)
        assertEquals(1, t.toLeg)
        assertEquals("Savidor", t.stop)
        assertEquals(1, t.slackMin)
        assertTrue(t.sameStop)
        assertEquals(0, t.walkMin)
        assertEquals(TransferWarning.CHANGE_IN, transferWarning(t, null))
        val two = itinerary(bus(stop("A"), savidor, 0, 20), bus(savidor, stop("B"), 22, 40))
        assertEquals(2, transfers(two).single().slackMin)
        assertTrue(tightTransfers(two, null).isEmpty())
    }

    @Test
    fun `a walk between, 4 min walk and 1 to spare`() {
        val other = stop("Arlozorov")
        val itin = itinerary(bus(stop("A"), savidor, 0, 20), walk(savidor, other, 20, 24), bus(other, stop("B"), 25, 40))
        val t = transfers(itin).single()
        assertEquals(4, t.walkMin)
        assertEquals(1, t.slackMin)
        assertFalse(t.sameStop)
        assertEquals("Arlozorov", t.stop)
        assertEquals(TransferWarning.WALK_SPARE, transferWarning(t, null))
    }

    @Test
    fun `a delay that eats the slack uses the live time`() {
        // Scheduled 20:00 arrival, live 24 → a 5-min change becomes 1.
        val late = bus(stop("A"), savidor, 0, 24).copy(realTime = true, scheduledEndTime = at(20).toString(), scheduledStartTime = at(0).toString())
        val itin = itinerary(late, bus(savidor, stop("B"), 25, 40))
        val (t, w) = tightTransfers(itin, null).single()
        assertEquals(1, t.slackMin)
        assertEquals(TransferWarning.CHANGE_IN, w)
    }

    @Test
    fun `zero slack is amber, below zero is tight`() {
        val zero = itinerary(bus(stop("A"), savidor, 0, 20), bus(savidor, stop("B"), 20, 40))
        val z = transfers(zero).single()
        assertEquals(0L, z.slackSec)
        assertEquals(TransferWarning.CHANGE_IN, transferWarning(z, null))
        val other = stop("Other")
        val missed = itinerary(bus(stop("A"), savidor, 0, 22), walk(savidor, other, 22, 25), bus(other, stop("B"), 24, 40))
        val m = transfers(missed).single()
        assertEquals(-1, m.slackMin)
        assertEquals(TransferWarning.TIGHT, transferWarning(m, null))
    }

    @Test
    fun `a train change at 3 min warns, at 4 does not, a bus at 3 does not`() {
        val tr3 = transfers(itinerary(bus(stop("A"), savidor, 0, 20), train(savidor, stop("Haifa"), 23, 90))).single()
        assertTrue(tr3.train)
        assertEquals(TIGHT_TRAIN_MIN, warnBelowMin(tr3, null))
        assertEquals(TransferWarning.CHANGE_IN, transferWarning(tr3, null))
        val tr4 = transfers(itinerary(bus(stop("A"), savidor, 0, 20), train(savidor, stop("Haifa"), 24, 90))).single()
        assertNull(transferWarning(tr4, null))
        val b3 = transfers(itinerary(bus(stop("A"), savidor, 0, 20), bus(savidor, stop("B"), 23, 40))).single()
        assertEquals(TIGHT_MIN, warnBelowMin(b3, null))
        assertNull(transferWarning(b3, null))
    }

    @Test
    fun `a change past midnight`() {
        // 23:59 arrival, 00:00 departure next day (Israel time): 1 min, by instant not by clock.
        val night = Instant.parse("2026-09-23T20:59:00Z")
        val itin = itinerary(bus(stop("A"), savidor, -30, 0, night), bus(savidor, stop("B"), 1, 30, night))
        val t = transfers(itin).single()
        assertEquals(1, t.slackMin)
        assertEquals(TransferWarning.CHANGE_IN, transferWarning(t, null))
    }

    @Test
    fun `the buffer raises the threshold, never lowers it`() {
        val t = transfers(itinerary(bus(stop("A"), savidor, 0, 20), bus(savidor, stop("B"), 29, 40))).single()
        assertNull(transferWarning(t, null))
        assertNull(transferWarning(t, 3))
        assertEquals(TIGHT_MIN, warnBelowMin(t, 1))
        assertEquals(5, warnBelowMin(t, 5))
        assertEquals(10, warnBelowMin(t, 10))
        assertEquals(TransferWarning.CHANGE_IN, transferWarning(t, 10))
    }

    @Test
    fun `the results screenshot fixture shows a chip`() {
        // ScreensTest's results use this recording; its first option changes bus with 0 min to spare.
        val plan = MotisJson.decodeFromString(PlanResponse.serializer(), javaClass.getResource("/fixtures/plan_bgu_telaviv.json")!!.readText())
        assertTrue(tightTransfers(plan.itineraries.first(), null).isNotEmpty())
    }

    @Test
    fun `a direct ride has no transfers`() {
        assertTrue(transfers(itinerary(bus(stop("A"), savidor, 0, 20))).isEmpty())
    }

    @Test
    fun `the buffer setting adds additionalTransferTime, the cache key changes, no extra call`() = runTest {
        val req = PlanRequest(
            from = il.transit.core.api.Endpoint.Coord(LatLon(31.25, 34.79)),
            to = il.transit.core.api.Endpoint.Coord(LatLon(32.08, 34.78)),
            time = NOON,
        )
        assertTrue(req.copy(preferences = UserSettings().preferences()).toQuery().none { it.first == "additionalTransferTime" })
        val withBuffer = req.copy(preferences = UserSettings(transferBufferMin = 8).preferences())
        assertTrue(("additionalTransferTime" to "8") in withBuffer.toQuery())

        val fake = FakeTransitApi().apply { onPlan = { PlanResponse() } }
        val api = GuardedTransitApi(fake)
        api.plan(withBuffer)
        api.plan(withBuffer)
        assertEquals(1, fake.calls.size)
        api.plan(req.copy(preferences = UserSettings(transferBufferMin = 3).preferences()))
        assertEquals(listOf("plan", "plan"), fake.calls)
    }

    @Test
    fun `the buffer round-trips in JSON and old files still decode`() {
        val s = UserSettings(transferBufferMin = 5)
        assertEquals(s, UserJson.decodeSettings(UserJson.encodeSettings(s)))
        assertNull(UserJson.decodeSettings("""{"maxWalkMin":10}""").transferBufferMin)
        assertEquals(listOf(null, 3, 5, 8, 10), UserSettings.TRANSFER_BUFFER_CHOICES)
    }
}
