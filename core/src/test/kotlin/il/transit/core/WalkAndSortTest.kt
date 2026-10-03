package il.transit.core

import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.api.StepInstruction
import il.transit.core.geo.LatLon
import il.transit.core.plan.TripCacheJson
import il.transit.core.plan.TripSort
import il.transit.core.plan.sortOptions
import il.transit.core.plan.walkSec
import il.transit.core.present.Turn
import il.transit.core.present.WalkStep
import il.transit.core.present.walkSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WalkAndSortTest {
    private fun recorded(name: String): PlanResponse =
        MotisJson.decodeFromString(PlanResponse.serializer(), javaClass.getResource("/fixtures/$name.json")!!.readText())

    @Test fun `street walk in Be'er Sheva - turns worked out from the geometry`() {
        // Recorded 2026-10-03: 33 steps, all CONTINUE, streets named on most of them.
        val leg = recorded("plan_walk_beersheva_streets").direct.first().legs.single()
        assertEquals(33, leg.steps.size)
        assertEquals(
            listOf(
                WalkStep(Turn.START, 60, null),
                WalkStep(Turn.STRAIGHT, 180, "חיים נחמן ביאליק"),
                WalkStep(Turn.SLIGHT_LEFT, 150, "בזל"), // the 7 m roundabout before it is folded in
                WalkStep(Turn.RIGHT, 160, "אוסישקין"),
                WalkStep(Turn.LEFT, 480, "ויצמן"),
                WalkStep(Turn.STRAIGHT, 240, "דוד וולפסון"),
                WalkStep(Turn.RIGHT, 210, "התקווה"),
            ),
            walkSteps(leg),
        )
    }

    @Test fun `campus walk keeps its stairs`() {
        val leg = recorded("plan_bgu_telaviv").itineraries.first().legs.first { it.mode == "WALK" }
        val turns = walkSteps(leg).map { it.turn }
        assertEquals(listOf(Turn.START, Turn.STAIRS, Turn.STRAIGHT, Turn.STAIRS, Turn.STRAIGHT), turns)
    }

    @Test fun `no steps, unknown directions and old caches`() {
        val a = LatLon(31.25, 34.79)
        val bare = leg("WALK", place("a", a), place("b", a), Instant.EPOCH, Instant.EPOCH.plusSeconds(60))
        assertTrue(walkSteps(bare).isEmpty())
        val odd = bare.copy(steps = listOf(StepInstruction("CIRCLE_CLOCKWISE", 40.0, "רגר")))
        assertEquals(listOf(WalkStep(Turn.START, 40, "רגר")), walkSteps(odd))
        // A trip cached on disk before `steps` existed still loads.
        val old = """[{"key":"k","savedAtEpoch":1,"result":{"itineraries":[{"duration":60,"startTime":"2026-10-05T05:00:00Z",
            "endTime":"2026-10-05T05:01:00Z","transfers":0,"legs":[{"mode":"WALK","from":{"name":"a","lat":1.0,"lon":1.0},
            "to":{"name":"b","lat":1.0,"lon":1.0},"duration":60,"startTime":"2026-10-05T05:00:00Z","endTime":"2026-10-05T05:01:00Z"}]}],"walkOnly":null}}]"""
        assertTrue(TripCacheJson.decode(old).single().value.itineraries.single().legs.single().steps.isEmpty())
    }

    @Test fun `sort chips reorder the same answers`() {
        val a = LatLon(31.25, 34.79)
        val t0 = Instant.parse("2026-10-05T05:00:00Z")
        fun opt(arriveMin: Long, transfers: Int, walkMin: Long) = itinerary(
            leg("WALK", place("a", a), place("s", a), t0, t0.plusSeconds(walkMin * 60)),
            leg("BUS", place("s", a), place("b", a), t0.plusSeconds(walkMin * 60), t0.plusSeconds(arriveMin * 60)),
            transfers = transfers,
        )
        val fast = opt(40, 2, 12)
        val direct = opt(50, 0, 9)
        val noWalk = opt(55, 1, 2)
        val planned = listOf(fast, direct, noWalk) // the planner's order: earliest arrival
        assertEquals(planned, sortOptions(planned, TripSort.FASTEST))
        assertEquals(listOf(direct, noWalk, fast), sortOptions(planned, TripSort.FEWEST_TRANSFERS))
        assertEquals(listOf(noWalk, direct, fast), sortOptions(planned, TripSort.LEAST_WALKING))
        assertEquals(120, noWalk.walkSec)
    }
}
