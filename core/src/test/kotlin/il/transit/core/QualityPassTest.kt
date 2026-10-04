package il.transit.core

import il.transit.core.api.Itinerary
import il.transit.core.geo.LatLon
import il.transit.core.plan.TripSort
import il.transit.core.plan.reselect
import il.transit.core.plan.sortOptions
import il.transit.core.remind.Reminder
import il.transit.core.remind.ReminderLogic
import il.transit.core.remind.ReminderUpdate
import il.transit.core.user.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bugs found in the v0.6.1 quality pass, pinned so they stay fixed. */
class QualityPassTest {
    private val home = LatLon(31.250, 34.790)
    private val stopA = place("Stop A", LatLon(31.252, 34.792), stopId = "A")
    private val stopB = place("Stop B", LatLon(31.262, 34.801), stopId = "B")
    private val post = LatLon(31.262, 34.802)

    /** Walk to stop A, ride [line] (trip [tripId]) leaving [min] minutes after noon, walk on. */
    private fun bus(line: String, tripId: String?, min: Long, transfers: Int = 0, walkMin: Long = 4): Itinerary {
        val board = NOON.plusSeconds(min * 60)
        val walk = leg("WALK", place("Home", home), stopA, board.minusSeconds(walkMin * 60), board)
        val ride = leg("BUS", stopA, stopB, board, board.plusSeconds(600)).copy(routeShortName = line, tripId = tripId)
        val off = leg("WALK", stopB, place("Uni", post), board.plusSeconds(600), board.plusSeconds(840))
        return itinerary(walk, ride, off, transfers = transfers)
    }

    private val walkOnly = itinerary(leg("WALK", place("Home", home), place("Uni", post), NOON, NOON.plusSeconds(2400)))

    @Test fun `a refresh keeps the bus the user picked, not its position in the list`() {
        val before = listOf(bus("5", "t1", 2), bus("12", "t2", 8), bus("5", "t3", 17))
        // The user picked the 12. Two minutes later the first bus has left and a new one is added.
        val after = listOf(bus("12", "t2", 8), bus("5", "t3", 17), bus("12", "t4", 25))
        assertEquals(0, reselect(before[1], after, previousIndex = 1))
    }

    @Test fun `the walk-only option stays selected through a refresh`() {
        val before = listOf(bus("5", "t1", 2), bus("12", "t2", 8)) + walkOnly
        val after = listOf(bus("12", "t2", 8), bus("5", "t3", 17)) + walkOnly
        // The old code clamped to itineraries.size - 1 and so always fell off the walk-only option.
        assertEquals(2, reselect(before[2], after, previousIndex = 2))
    }

    @Test fun `with no trip ids the same line, stop and scheduled time is the same bus`() {
        val before = listOf(bus("5", null, 2), bus("12", null, 8))
        val after = listOf(bus("12", null, 8), bus("5", null, 17))
        assertEquals(0, reselect(before[1], after, previousIndex = 1))
    }

    @Test fun `a sort is applied to the refreshed list before the selection is found`() {
        val picked = bus("12", "t2", 8, transfers = 0)
        val refreshed = listOf(bus("5", "t3", 3, transfers = 1), picked)
        val shown = sortOptions(refreshed, TripSort.FEWEST_TRANSFERS)
        assertEquals(0, reselect(picked, shown, previousIndex = 1))
    }

    @Test fun `when the picked bus has left, the old index is kept but clamped`() {
        val gone = bus("5", "t1", 2)
        assertEquals(1, reselect(gone, listOf(bus("12", "t2", 8), bus("5", "t3", 17)), previousIndex = 4))
        assertEquals(0, reselect(gone, emptyList(), previousIndex = 3))
    }

    @Test fun `a reminder on one errand leg re-checks that leg, not the whole trip`() {
        // The errand leg's own places, not the A and B of the whole chain the screen shows.
        val leg2 = bus("12", "t2", 8)
        val r = Reminder.forOwnEndpoints(leg2, UserSettings())!!
        assertEquals(home, r.from)
        assertEquals(post, r.to)
        // A fresh plan of that same leg finds the same bus, so the reminder survives the re-check.
        assertTrue(ReminderLogic.update(r, listOf(bus("12", "t2", 8))) is ReminderUpdate.Updated)
    }
}
