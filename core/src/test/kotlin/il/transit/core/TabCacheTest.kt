package il.transit.core

import il.transit.core.api.ReplayTransitApi
import il.transit.core.api.parseTime
import il.transit.core.features.BetterStartOption
import il.transit.core.features.BetterStartPlanner
import il.transit.core.features.BetterStartQuery
import il.transit.core.features.BetterStartResult
import il.transit.core.features.DropOffOption
import il.transit.core.features.DropOffPlanner
import il.transit.core.features.DropOffQuery
import il.transit.core.features.DropOffResult
import il.transit.core.features.ParkRideOption
import il.transit.core.features.ParkRidePlanner
import il.transit.core.features.ParkRideQuery
import il.transit.core.features.ParkRideResult
import il.transit.core.features.PickUpOption
import il.transit.core.features.PickUpPlanner
import il.transit.core.features.PickUpQuery
import il.transit.core.features.PickUpResult
import il.transit.core.geo.LatLon
import il.transit.core.plan.ChainPlanner
import il.transit.core.plan.ChainResult
import il.transit.core.plan.ChainStop
import il.transit.core.plan.PlanCache
import il.transit.core.plan.TabCacheJson
import il.transit.core.plan.TabResult
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.stillAhead
import il.transit.core.user.UserSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Phase 11 R1: the car tabs' and errands' answers survive the file, and what already left is dropped. */
class TabCacheTest {
    private val now = Instant.parse("2026-10-05T04:55:00Z")
    private val replay = ReplayTransitApi(Clock.fixed(now, ZoneOffset.UTC)) { javaClass.getResource("/fixtures/$it.json")!!.readText() }
    private val meitar = LatLon(31.3236, 34.9381)
    private val telAviv = LatLon(32.0839, 34.7983)
    private val bgu = LatLon(31.262, 34.801)
    private val soroka = LatLon(31.2589, 34.7992)

    /** Every kind, planned from the recordings as the app plans it. */
    private val all: List<TabResult> = runBlocking {
        listOf(
            TabResult.BetterStart(BetterStartPlanner(replay).plan(BetterStartQuery(meitar, telAviv, now))),
            TabResult.DropOff(DropOffPlanner(replay).plan(DropOffQuery(meitar, telAviv, LatLon(32.08, 34.81), now))),
            TabResult.PickUp(PickUpPlanner(replay).plan(PickUpQuery(telAviv, meitar, now))),
            TabResult.ParkRide(ParkRidePlanner(replay).plan(ParkRideQuery(meitar, telAviv, now)), meitar),
            TabResult.Errands(ChainPlanner(replay).plan(bgu, listOf(ChainStop(soroka, "Soroka", 15)), telAviv, now, UserSettings(), "en")),
        )
    }

    /** First boardings of every option and the baseline. */
    private fun boardings(r: TabResult): List<Instant> = when (r) {
        is TabResult.BetterStart -> r.result.options.map { it.payload.itinerary } + listOfNotNull(r.result.baseline)
        is TabResult.DropOff -> r.result.options.map { it.payload.transit }
        is TabResult.PickUp -> r.result.options.map { it.payload.itinerary } + listOfNotNull(r.result.baseline)
        is TabResult.ParkRide -> r.result.options.map { it.payload.itinerary } + listOfNotNull(r.result.baseline)
        is TabResult.Errands -> r.chain.legs
    }.mapNotNull { i -> i.firstTransitLeg?.let { parseTime(it.startTime) } }

    @Test fun `every kind comes back from the file as it was`() {
        all.forEach { r -> assertTrue("$r has something to show", boardings(r).isNotEmpty()) }
        val entries = all.mapIndexed { i, r -> PlanCache.Entry("k$i", now, r) }
        assertEquals(entries, TabCacheJson.decode(TabCacheJson.encode(entries)))
    }

    /** Each saved type on its own, through its own serializer: what a later field change must keep readable. */
    @Test fun `every part has a serializer of its own that round-trips`() {
        val json = kotlinx.serialization.json.Json
        fun <T> back(ser: kotlinx.serialization.KSerializer<T>, v: T) = assertEquals(v, json.decodeFromString(ser, json.encodeToString(ser, v)))
        val bs = all.filterIsInstance<TabResult.BetterStart>().single()
        val dr = all.filterIsInstance<TabResult.DropOff>().single()
        val pu = all.filterIsInstance<TabResult.PickUp>().single()
        val pr = all.filterIsInstance<TabResult.ParkRide>().single()
        val er = all.filterIsInstance<TabResult.Errands>().single()
        back(TabResult.BetterStart.serializer(), bs)
        back(TabResult.DropOff.serializer(), dr)
        back(TabResult.PickUp.serializer(), pu)
        back(TabResult.ParkRide.serializer(), pr)
        back(TabResult.Errands.serializer(), er)
        back(BetterStartResult.serializer(), bs.result)
        back(DropOffResult.serializer(), dr.result)
        back(PickUpResult.serializer(), pu.result)
        back(ParkRideResult.serializer(), pr.result)
        back(ChainResult.serializer(), er.chain)
        back(ChainStop.serializer(), ChainStop(soroka, "Soroka"))
        back(LatLon.serializer(), meitar)
        dr.result.options.forEach { back(DropOffOption.serializer(), it.payload) }
        pu.result.options.forEach { back(PickUpOption.serializer(), it.payload) }
        pr.result.options.forEach { back(ParkRideOption.serializer(), it.payload) }
        val start = BetterStartOption(bs.result.baseline!!, 300, "Stop", meitar, now)
        back(BetterStartOption.serializer(), start)
        assertEquals("\"2026-10-05T04:55:00Z\"", json.encodeToString(il.transit.core.plan.InstantIso, now))
    }

    @Test fun `unreadable data is no entry`() {
        assertTrue(TabCacheJson.decode("{not json").isEmpty())
        assertTrue(TabCacheJson.decode(null).isEmpty())
    }

    @Test fun `at the search time nothing is dropped`() {
        all.forEach { assertEquals(it, it.stillAhead(now)) }
    }

    @Test fun `an option whose vehicle left over a minute ago is dropped, the rest stay`() {
        all.filter { it !is TabResult.Errands }.forEach { r ->
            val times = boardings(r).sorted()
            val first = times.first()
            val after = r.stillAhead(first.plusSeconds(TripPlanner.DEPARTED_GRACE_SEC + 1))
            val left = after?.let(::boardings).orEmpty()
            assertTrue("$r", left.none { it < first.plusSeconds(1) })
            assertEquals("$r", times.count { it > first }, left.size)
            // Within the grace minute it is still shown.
            assertEquals(r, r.stillAhead(first.plusSeconds(TripPlanner.DEPARTED_GRACE_SEC)))
        }
    }

    @Test fun `an errand chain whose first leg left is gone as a whole`() {
        val chain = all.single { it is TabResult.Errands }
        val first = boardings(chain).min()
        assertNotNull(chain.stillAhead(first))
        assertNull(chain.stillAhead(first.plusSeconds(TripPlanner.DEPARTED_GRACE_SEC + 1)))
    }

    @Test fun `long after every boarding nothing is left to show`() {
        all.forEach { r -> assertNull("$r", r.stillAhead(now.plusSeconds(3 * 86_400))) }
    }
}
