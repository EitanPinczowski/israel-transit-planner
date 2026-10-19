package il.transit.planner.ui

import il.transit.core.features.ISRAEL
import il.transit.core.plan.TimeMode
import il.transit.planner.ui.screens.TimeFlow
import il.transit.planner.ui.screens.TimeStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Phase 11 E7: what the clock's OK and the "Tomorrow at …?" answers lead to (the wiring of
 * `TimeSteps`). A past time today is asked about and nothing is set until the answer; Yes sets the
 * next day in the same mode; Change goes back to the clock on the same day.
 */
class TimeFlowTest {
    private fun il(s: String): Instant = LocalDateTime.parse(s).atZone(ISRAEL).toInstant()
    private val now = il("2026-10-19T10:00:30")
    private val today = LocalDate.parse("2026-10-19")

    @Test fun `a time still ahead is set at once, in its mode`() {
        for (mode in listOf(TimeMode.DEPART_AT, TimeMode.ARRIVE_BY)) {
            val next = TimeFlow.clock(TimeStep.Clock(mode, today), LocalTime.of(10, 0), now) // this minute is not past
            assertNull(next.step)
            assertEquals(mode to il("2026-10-19T10:00"), next.set)
            assertEquals(mode to il("2026-10-20T08:00"), TimeFlow.clock(TimeStep.Clock(mode, today.plusDays(1)), LocalTime.of(8, 0), now).set)
        }
    }

    @Test fun `a past time today asks, and nothing is set before the answer`() {
        for (mode in listOf(TimeMode.DEPART_AT, TimeMode.ARRIVE_BY)) {
            val next = TimeFlow.clock(TimeStep.Clock(mode, today), LocalTime.of(6, 10), now)
            assertEquals(TimeStep.Past(mode, il("2026-10-19T06:10")), next.step)
            assertNull(next.set)
        }
    }

    @Test fun `Yes sets the same time tomorrow in the same mode, and closes`() {
        for (mode in listOf(TimeMode.DEPART_AT, TimeMode.ARRIVE_BY)) {
            val asked = TimeFlow.clock(TimeStep.Clock(mode, today), LocalTime.of(6, 10), now).step as TimeStep.Past
            val yes = TimeFlow.yes(asked)
            assertNull(yes.step)
            assertEquals(mode to il("2026-10-20T06:10"), yes.set)
        }
    }

    @Test fun `Change reopens the clock on the same day and sets nothing`() {
        for (mode in listOf(TimeMode.DEPART_AT, TimeMode.ARRIVE_BY)) {
            val asked = TimeFlow.clock(TimeStep.Clock(mode, today), LocalTime.of(6, 10), now).step as TimeStep.Past
            val change = TimeFlow.change(asked)
            assertEquals(TimeStep.Clock(mode, today), change.step)
            assertNull(change.set)
        }
    }

    @Test fun `Yes across the autumn clock change keeps the clock time`() {
        val sat = LocalDate.parse("2026-10-24")
        val asked = TimeFlow.clock(TimeStep.Clock(TimeMode.DEPART_AT, sat), LocalTime.of(8, 0), il("2026-10-24T09:00")).step as TimeStep.Past
        assertEquals(TimeMode.DEPART_AT to il("2026-10-25T08:00"), TimeFlow.yes(asked).set) // 25 h later in UTC
    }
}
