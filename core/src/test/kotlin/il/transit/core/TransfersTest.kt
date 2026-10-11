package il.transit.core

import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.Place
import il.transit.core.api.PlanRequest
import il.transit.core.api.PlanResponse
import il.transit.core.geo.LatLon
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
    fun `same stop change, 3 min`() {
        val itin = itinerary(bus(stop("A"), savidor, 0, 20), bus(savidor, stop("B"), 23, 40), transfers = 1)
        val t = transfers(itin).single()
        assertEquals(0, t.fromLeg)
        assertEquals(1, t.toLeg)
        assertEquals("Savidor", t.stop)
        assertEquals(3, t.slackMin)
        assertTrue(t.sameStop)
        assertEquals(0, t.walkMin)
        assertEquals(TransferWarning.CHANGE_IN, transferWarning(t, null))
    }

    @Test
    fun `a walk between, 4 min walk and 2 to spare`() {
        val other = stop("Arlozorov")
        val itin = itinerary(bus(stop("A"), savidor, 0, 20), walk(savidor, other, 20, 24), bus(other, stop("B"), 26, 40))
        val t = transfers(itin).single()
        assertEquals(4, t.walkMin)
        assertEquals(2, t.slackMin)
        assertFalse(t.sameStop)
        assertEquals("Arlozorov", t.stop)
        assertEquals(TransferWarning.WALK_SPARE, transferWarning(t, null))
    }

    @Test
    fun `room enough gets no chip`() {
        val itin = itinerary(bus(stop("A"), savidor, 0, 20), bus(savidor, stop("B"), 25, 40))
        assertEquals(5, transfers(itin).single().slackMin)
        assertTrue(tightTransfers(itin, null).isEmpty())
    }

    @Test
    fun `a delay that eats the slack uses the live time`() {
        // Scheduled 20:00 arrival, live 24 → a 6-min change becomes 2.
        val late = bus(stop("A"), savidor, 0, 24).copy(realTime = true, scheduledEndTime = at(20).toString(), scheduledStartTime = at(0).toString())
        val itin = itinerary(late, bus(savidor, stop("B"), 26, 40))
        val (t, w) = tightTransfers(itin, null).single()
        assertEquals(2, t.slackMin)
        assertEquals(TransferWarning.CHANGE_IN, w)
    }

    @Test
    fun `slack of zero or less is tight`() {
        val other = stop("Other")
        val missed = itinerary(bus(stop("A"), savidor, 0, 22), walk(savidor, other, 22, 25), bus(other, stop("B"), 24, 40))
        val t = transfers(missed).single()
        assertTrue(t.slackSec < 0)
        assertEquals(TransferWarning.TIGHT, transferWarning(t, null))
        val zero = itinerary(bus(stop("A"), savidor, 0, 20), bus(savidor, stop("B"), 20, 40))
        assertEquals(TransferWarning.TIGHT, transferWarning(transfers(zero).single(), null))
    }

    @Test
    fun `a train change at 6 min warns, a bus change at 6 does not`() {
        val tr = transfers(itinerary(bus(stop("A"), savidor, 0, 20), train(savidor, stop("Haifa"), 26, 90))).single()
        assertTrue(tr.train)
        assertEquals(8, warnBelowMin(tr, null))
        assertEquals(TransferWarning.CHANGE_IN, transferWarning(tr, null))
        val b = transfers(itinerary(bus(stop("A"), savidor, 0, 20), bus(savidor, stop("B"), 26, 40))).single()
        assertNull(transferWarning(b, null))
    }

    @Test
    fun `a change past midnight`() {
        // 23:55 arrival, 00:02 departure next day (Israel time): 7 min, by instant not by clock.
        val night = Instant.parse("2026-09-23T20:55:00Z")
        val itin = itinerary(bus(stop("A"), savidor, -30, 0, night), bus(savidor, stop("B"), 7, 30, night))
        val t = transfers(itin).single()
        assertEquals(7, t.slackMin)
        assertNull(transferWarning(t, null))
        assertEquals(TransferWarning.CHANGE_IN, transferWarning(t, 8))
    }

    @Test
    fun `the buffer raises the threshold, never lowers it`() {
        val t = transfers(itinerary(bus(stop("A"), savidor, 0, 20), bus(savidor, stop("B"), 29, 40))).single()
        assertNull(transferWarning(t, null))
        assertNull(transferWarning(t, 3))
        assertEquals(5, warnBelowMin(t, 3))
        assertEquals(10, warnBelowMin(t, 10))
        assertEquals(TransferWarning.CHANGE_IN, transferWarning(t, 10))
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
