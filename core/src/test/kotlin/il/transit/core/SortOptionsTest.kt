package il.transit.core

import il.transit.core.api.Itinerary
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanRequest
import il.transit.core.api.PlanResponse
import il.transit.core.features.BetterStartOption
import il.transit.core.features.BetterStartPlanner
import il.transit.core.features.BetterStartQuery
import il.transit.core.features.DropOffKind
import il.transit.core.features.DropOffOption
import il.transit.core.features.DropOffPlanner
import il.transit.core.features.DropOffQuery
import il.transit.core.features.Option
import il.transit.core.features.ParkRideOption
import il.transit.core.features.ParkRidePlanner
import il.transit.core.features.ParkRideQuery
import il.transit.core.features.PickUpOption
import il.transit.core.features.PickUpPlanner
import il.transit.core.features.PickUpQuery
import il.transit.core.api.Endpoint
import il.transit.core.geo.LatLon
import il.transit.core.plan.ChainPlanner
import il.transit.core.plan.ChainStop
import il.transit.core.plan.LastRideFinder
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPages
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripQuery
import il.transit.core.plan.TripResult
import il.transit.core.plan.TripSort
import il.transit.core.plan.arrangeFront
import il.transit.core.plan.sortOptions
import il.transit.core.user.UserJson
import il.transit.core.user.UserSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** D12: the sort chips in every tab, the chain rule, and 1 minute to spare at every change. */
class SortOptionsTest {
    private val a = LatLon(31.25, 34.79)
    private val stop = place("s", a)
    private val t0 = Instant.parse("2026-10-13T05:00:00Z")

    /** Leaves at [startMin], walks [walkMin], rides until [endMin]; minutes after [t0]. */
    private fun itin(startMin: Long, endMin: Long, transfers: Int, walkMin: Long = 0): Itinerary {
        val s = t0.plusSeconds(startMin * 60)
        val board = s.plusSeconds(walkMin * 60)
        val legs = listOfNotNull(
            if (walkMin > 0) leg("WALK", stop, stop, s, board) else null,
            leg("BUS", stop, stop, board, t0.plusSeconds(endMin * 60)),
        )
        return itinerary(*legs.toTypedArray(), transfers = transfers)
    }

    // --- the chain rule -------------------------------------------------------------------

    @Test fun `X goes when Y has 2 fewer changes and arrives at most 10 min later - exactly 10 included`() {
        val chain = itin(0, 100, 4)
        val at10 = itin(0, 110, 2)
        assertEquals(listOf(at10), TripPages.sane(listOf(chain, at10)))
        val at11 = itin(0, 111, 2)
        assertEquals(listOf(chain, at11), TripPages.sane(listOf(chain, at11)))
        val oneFewer = itin(0, 101, 3)
        assertEquals(listOf(chain, oneFewer), TripPages.sane(listOf(chain, oneFewer)))
    }

    @Test fun `arrive-by reading - Y leaving at most 10 min before X, exactly 10 included`() {
        val chain = itin(30, 100, 4)
        val leaves10Earlier = itin(20, 95, 2)
        assertEquals(listOf(leaves10Earlier), TripPages.sane(listOf(chain, leaves10Earlier)))
        val leaves11Earlier = itin(19, 95, 2)
        assertEquals(listOf(chain, leaves11Earlier), TripPages.sane(listOf(chain, leaves11Earlier)))
    }

    @Test fun `an early direct bus does not wipe out later departures`() {
        val direct = itin(0, 60, 0)
        val later = itin(180, 250, 2)
        assertEquals(listOf(direct, later), TripPages.sane(listOf(direct, later)))
    }

    @Test fun `5 or more changes go whenever an option with 3 or fewer exists at all`() {
        val five = itin(0, 60, 5)
        val three = itin(0, 100, 3)
        assertEquals(listOf(three), TripPages.sane(listOf(five, three)))
        val four = itin(0, 100, 4)
        assertEquals(listOf(five, four), TripPages.sane(listOf(five, four)))
    }

    @Test fun `the last option is never dropped`() {
        val lone = itin(0, 60, 7)
        assertEquals(listOf(lone), TripPages.sane(listOf(lone)))
        assertEquals(emptyList<Itinerary>(), TripPages.sane(emptyList()))
        // Two equal long chains: neither has fewer changes, both stay.
        val other = itin(5, 65, 7)
        assertEquals(listOf(lone, other), TripPages.sane(listOf(lone, other)))
    }

    @Test fun `the constants are the owner's`() {
        assertEquals(2, TripPlanner.CHAIN_FEWER_CHANGES)
        assertEquals(600, TripPlanner.CHAIN_WINDOW_SEC)
        assertEquals(5, TripPlanner.LONG_CHAIN_CHANGES)
        assertEquals(3, TripPlanner.SHORT_CHAIN_CHANGES)
    }

    @Test fun `Golan to Tabenkin 15 - the 5-change chain is gone, sorts see only what survives`() = runTest {
        // Recorded 2026-10-12 for Tue 2026-10-13 07:00, near Kela Alon → Tabenkin 15 Tel Aviv,
        // additionalTransferTime=1. MOTIS offers 59א+845+616 (2 changes, 11:50) and, a minute
        // earlier, 59א+541+969+184+124+171 (5 changes, 11:49), which Fastest listed first.
        val resp = MotisJson.decodeFromString(PlanResponse.serializer(), javaClass.getResource("/fixtures/plan_golan_tabenkin_tlv.json")!!.readText())
        assertEquals(listOf(2, 5, 2, 3, 4), resp.itineraries.map { it.transfers })
        val api = FakeTransitApi().apply { onPlan = { resp } }
        val r = TripPlanner(api).plan(TripQuery(Endpoint.Coord(LatLon(33.1308, 35.6825)), Endpoint.Coord(LatLon(32.1205, 34.803)), TimeMode.DEPART_AT, t0))
        assertTrue(r.itineraries.none { it.transfers >= 5 })
        assertEquals(listOf(2, 4, 3, 2), r.itineraries.map { it.transfers })
        assertEquals(listOf(2, 2, 3, 4), sortOptions(r, TripSort.FEWEST_TRANSFERS, false).map { it.transfers })
        assertEquals(1, api.calls.size)
    }

    // --- sorting the Trip tab -------------------------------------------------------------

    @Test fun `trip tab - walk-only sorts like any option, ties keep the planner's order`() {
        val fast = itin(0, 40, 2, walkMin = 12)
        val tie = itin(0, 40, 2, walkMin = 3)
        val direct = itin(0, 50, 0, walkMin = 9)
        val walk = itinerary(leg("WALK", stop, stop, t0, t0.plusSeconds(45 * 60)))
        val r = TripResult(listOf(fast, tie, direct), walk)
        assertEquals(listOf(fast, tie, walk, direct), sortOptions(r, TripSort.FASTEST, false))
        assertEquals(listOf(direct, walk, fast, tie), sortOptions(r, TripSort.FEWEST_TRANSFERS, false)) // walk-only comes after the planner's
        assertEquals(listOf(tie, direct, fast, walk), sortOptions(r, TripSort.LEAST_WALKING, false))
    }

    @Test fun `trip tab arrive-by - Fastest is the latest departure`() {
        val early = itin(0, 55, 0)
        val late = itin(20, 58, 1)
        val sameStart = itin(20, 59, 0)
        val r = TripResult(listOf(late, sameStart, early), null) // the planner's arrive-by order
        assertEquals(listOf(late, sameStart, early), sortOptions(r, TripSort.FASTEST, true))
        assertEquals(listOf(sameStart, early, late), sortOptions(r, TripSort.FEWEST_TRANSFERS, true))
    }

    // --- sorting the car + transit payloads ----------------------------------------------

    private fun <T> opt(payload: T, it: Itinerary, driverMin: Int = 0, arrival: Instant = it.end) =
        Option(payload, driverMin * 60, arrival, it.transfers)

    @Test fun `better start - each sort, ties by the front's order`() {
        fun bs(it: Itinerary) = BetterStartOption(it, 300, "s", a, it.start)
        val i1 = itin(0, 40, 2, walkMin = 10)
        val i2 = itin(0, 45, 1, walkMin = 2)
        val i3 = itin(0, 45, 1, walkMin = 5)
        val front = listOf(opt(bs(i1), i1, 3), opt(bs(i2), i2, 4), opt(bs(i3), i3, 6))
        fun order(s: TripSort) = arrangeFront(front, s) { it.itinerary }.map { it.payload.itinerary }
        assertEquals(listOf(i1, i2, i3), order(TripSort.FASTEST))
        assertEquals(listOf(i2, i3, i1), order(TripSort.FEWEST_TRANSFERS))
        assertEquals(listOf(i2, i3, i1), order(TripSort.LEAST_WALKING))
    }

    @Test fun `pick-up - Fastest is the home arrival, car part included`() {
        fun pu(it: Itinerary, home: Instant) = Option(PickUpOption(it, "s", a, it.end, 600, it.end), 1200, home, it.transfers)
        val earlyStop = itin(0, 40, 0)
        val lateStop = itin(0, 45, 0)
        // Reaching the stop first does not mean home first: the drive from there is longer.
        val front = listOf(pu(earlyStop, t0.plusSeconds(80 * 60)), pu(lateStop, t0.plusSeconds(60 * 60)))
        assertEquals(listOf(lateStop, earlyStop), arrangeFront(front, TripSort.FASTEST) { it.itinerary }.map { it.payload.itinerary })
    }

    @Test fun `drop-off - baselines sort like any option, and re-sorting a sorted list is stable`() {
        fun d(kind: DropOffKind, it: Itinerary, detour: Int) = Option(DropOffOption(kind, null, detour * 60, 0, it), detour * 60, it.end, it.transfers)
        val onTheWay = itin(0, 70, 1, walkMin = 8)
        val rideToEnd = itin(0, 90, 0, walkMin = 4) // 15 min after: too late to drop the 2-change one
        val fromStart = itin(0, 75, 2, walkMin = 1)
        val front = listOf(d(DropOffKind.STOP_ON_THE_WAY, onTheWay, 6), d(DropOffKind.TRANSIT_FROM_START, fromStart, 0), d(DropOffKind.RIDE_TO_END, rideToEnd, 0))
        fun kinds(l: List<Option<DropOffOption>>) = l.map { it.payload.kind }
        val walking = arrangeFront(front, TripSort.LEAST_WALKING) { it.transit }
        assertEquals(listOf(DropOffKind.TRANSIT_FROM_START, DropOffKind.RIDE_TO_END, DropOffKind.STOP_ON_THE_WAY), kinds(walking))
        val fewest = arrangeFront(walking, TripSort.FEWEST_TRANSFERS) { it.transit }
        assertEquals(listOf(DropOffKind.RIDE_TO_END, DropOffKind.STOP_ON_THE_WAY, DropOffKind.TRANSIT_FROM_START), kinds(fewest))
        // Back to Fastest from any order gives the planner's order again.
        assertEquals(kinds(arrangeFront(front, TripSort.FASTEST) { it.transit }), kinds(arrangeFront(fewest, TripSort.FASTEST) { it.transit }))
        assertEquals(listOf(DropOffKind.STOP_ON_THE_WAY, DropOffKind.TRANSIT_FROM_START, DropOffKind.RIDE_TO_END), kinds(arrangeFront(fewest, TripSort.FASTEST) { it.transit }))
    }

    @Test fun `park and ride - the chain rule applies to the payloads, ties keep the front's order`() {
        val station = place("BS North", a, stopId = "x")
        fun pr(it: Itinerary, drive: Int) = Option(ParkRideOption(station, drive * 60, drive * 60, it, it.start, it.start), drive * 60, it.end, it.transfers)
        val chain = itin(0, 60, 4)
        val direct = itin(0, 68, 2)
        val sameArrival = itin(0, 68, 2, walkMin = 1)
        val front = listOf(pr(chain, 10), pr(direct, 12), pr(sameArrival, 15))
        val shown = arrangeFront(front, TripSort.FASTEST) { it.itinerary }
        assertEquals(listOf(direct, sameArrival), shown.map { it.payload.itinerary })
        assertEquals(listOf(direct, sameArrival), arrangeFront(front, TripSort.FEWEST_TRANSFERS) { it.itinerary }.map { it.payload.itinerary })
    }

    // --- 1 minute to spare in every mode --------------------------------------------------

    @Test fun `every mode's plan requests carry additionalTransferTime of at least 1`() = runTest {
        // An old settings file with the buffer Off.
        val settings = UserJson.decodeSettings("""{"transferBufferMin":null}""")
        val prefs = settings.preferences()
        val bs = LatLon(31.2622, 34.8014)
        val tlv = LatLon(32.0839, 34.7983)
        val bus = itin(0, 60, 0)
        val api = FakeTransitApi().apply {
            onPlan = { PlanResponse(itineraries = listOf(bus)) }
            onOneToMany = { _, many, _ -> many.map { 600 } }
            onStops = { _, _ -> emptyList() }
        }
        val modes = mutableListOf<Pair<String, List<PlanRequest>>>()
        suspend fun mode(name: String, run: suspend () -> Unit) {
            api.planRequests.clear()
            run()
            modes += name to api.planRequests.toList()
        }
        mode("trip") { TripPlanner(api).plan(TripQuery(Endpoint.Coord(bs), Endpoint.Coord(tlv), settings = settings), t0) }
        mode("errands") { ChainPlanner(api).plan(bs, listOf(ChainStop(LatLon(31.9, 34.8), "x", 10)), tlv, t0, settings, "he") }
        mode("better start") { BetterStartPlanner(api).plan(BetterStartQuery(bs, tlv, t0, preferences = prefs)) }
        mode("drop-off") { DropOffPlanner(api).plan(DropOffQuery(bs, tlv, LatLon(31.89, 34.81), t0, preferences = prefs)) }
        mode("pick-up") { PickUpPlanner(api).plan(PickUpQuery(tlv, bs, t0, preferences = prefs)) }
        mode("park & ride") { ParkRidePlanner(api).plan(ParkRideQuery(LatLon(31.2797, 34.9356), tlv, t0, preferences = prefs)) }
        mode("last trip") { LastRideFinder(api).find(Endpoint.Coord(tlv), Endpoint.Coord(bs), LocalDate.of(2026, 10, 13), prefs, "he") }
        for ((name, reqs) in modes) {
            val transit = reqs.filter { !it.directOnly }
            assertTrue("$name sent no transit plan", transit.isNotEmpty())
            transit.forEach { r ->
                val min = r.toQuery().firstOrNull { it.first == "additionalTransferTime" }?.second?.toInt()
                assertTrue("$name: additionalTransferTime=$min", min != null && min >= 1)
            }
        }
    }
}
