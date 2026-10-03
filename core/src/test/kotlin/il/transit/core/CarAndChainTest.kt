package il.transit.core

import il.transit.core.api.BudgetedTransitApi
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.features.TrafficProfile
import il.transit.core.geo.LatLon
import il.transit.core.plan.CarCompare
import il.transit.core.plan.ChainPlanner
import il.transit.core.plan.ChainStop
import il.transit.core.user.UserSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CarAndChainTest {
    private val monday8 = Instant.parse("2026-10-05T05:00:00Z") // peak: ×1.3

    @Test fun `car time from the recorded drive, with the rush-hour factor`() = runBlocking {
        // Meitar → Tel Aviv, recorded: 4,383 s free-flow (73 min), 112.8 km.
        val api = FakeTransitApi()
        api.onPlan = { req ->
            assertTrue(req.directOnly)
            MotisJson.decodeFromString(PlanResponse.serializer(), javaClass.getResource("/fixtures/plan_direct_car.json")!!.readText())
        }
        val car = CarCompare(BudgetedTransitApi(api, CarCompare.BUDGET))
            .drive(LatLon(31.28, 34.94), LatLon(32.08, 34.80), monday8, TrafficProfile())!!
        assertEquals(95, car.minutes) // ceil(4383 × 1.3 / 60)
        assertEquals(1.3, car.factor, 1e-9)
        assertEquals(112.8, car.km, 0.1)
        api.onPlan = { PlanResponse() }
        assertNull(CarCompare(api).drive(LatLon(0.0, 0.0), LatLon(0.0, 0.1), monday8, TrafficProfile()))
    }

    private val home = LatLon(31.279, 34.82)
    private val post = LatLon(31.252, 34.791)
    private val uni = LatLon(31.262, 34.8015)

    /** Every leg is a 20-minute bus leaving at the asked time; records the asked times. */
    private fun busEverywhere(api: FakeTransitApi, asked: MutableList<Instant>, failFrom: LatLon? = null) {
        api.onPlan = { req ->
            asked += req.time
            val from = (req.from as il.transit.core.api.Endpoint.Coord).at
            if (from == failFrom) PlanResponse() else {
                val to = (req.to as il.transit.core.api.Endpoint.Coord).at
                PlanResponse(listOf(itinerary(leg("BUS", place("a", from), place("b", to), req.time, req.time.plusSeconds(1200)))))
            }
        }
    }

    @Test fun `errands chain - each leg leaves after the previous arrival plus the stay`() = runBlocking {
        val api = FakeTransitApi()
        val asked = mutableListOf<Instant>()
        busEverywhere(api, asked)
        val r = ChainPlanner(BudgetedTransitApi(api, ChainPlanner.BUDGET))
            .plan(home, listOf(ChainStop(post, "Post office", stayMin = 30)), uni, monday8, UserSettings(), "he")
        assertNull(r.failedAt)
        assertEquals(2, r.legs.size)
        // Leg 1 08:00–08:20; 30 min at the post office; leg 2 leaves 08:50, arrives 09:10.
        assertEquals(listOf(monday8, monday8.plusSeconds(50 * 60)), asked)
        assertEquals(monday8, r.leaveAt)
        assertEquals(monday8.plusSeconds(70 * 60), r.arriveAt)
    }

    @Test fun `a leg with no way to make it stops the chain`() = runBlocking {
        val api = FakeTransitApi()
        val asked = mutableListOf<Instant>()
        busEverywhere(api, asked, failFrom = post)
        val r = ChainPlanner(api).plan(home, listOf(ChainStop(post, "Post office", 15)), uni, monday8, UserSettings(), "he")
        assertEquals(1, r.failedAt)
        assertEquals(1, r.legs.size)
        assertNull(r.arriveAt)
        assertEquals(2, asked.size)
    }

    @Test fun `the budget is one request per leg`() = runBlocking {
        val api = FakeTransitApi()
        busEverywhere(api, mutableListOf())
        val stops = List(ChainPlanner.MAX_STOPS) { ChainStop(post, "s$it", 0) }
        val budgeted = BudgetedTransitApi(api, ChainPlanner.BUDGET)
        ChainPlanner(budgeted).plan(home, stops, uni, monday8, UserSettings(), "he")
        assertEquals(ChainPlanner.BUDGET, budgeted.used)
    }
}
