package il.transit.core

import il.transit.core.geo.LatLon
import il.transit.core.remind.Countdown
import il.transit.core.remind.Reminder
import il.transit.core.remind.ReminderLogic
import il.transit.core.remind.ReminderUpdate
import il.transit.core.user.UserJson
import il.transit.core.user.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CountdownTest {
    private val home = LatLon(31.2790, 34.8200)
    private val stop = LatLon(31.2622, 34.8013)
    private val dest = LatLon(31.2525, 34.7915)

    /** Walk 6 min from 08:00 Israel, bus 5 from Rager/Oren at 08:08, off at 08:30. */
    private fun trip(walkStart: String = "2026-10-07T05:00:00Z", busStart: String = "2026-10-07T05:08:00Z", mode: String = "BUS", tripId: String = "t1"): il.transit.core.api.Itinerary {
        val w = Instant.parse(walkStart)
        val b = Instant.parse(busStart)
        return itinerary(
            leg("WALK", place("START", home), place("רגר/אורן", stop), w, w.plusSeconds(6 * 60)),
            leg(mode, place("רגר/אורן", stop), place("END", dest), b, b.plusSeconds(22 * 60))
                .copy(routeShortName = "5", tripId = tripId),
        )
    }

    private fun reminder(i: il.transit.core.api.Itinerary = trip()) =
        Reminder.from(i, home, dest, UserSettings(), fromName = "בית", toName = "אוניברסיטה")!!

    @Test fun `counts down to boarding and lingers two minutes`() {
        val r = reminder()
        val c = Countdown.of(r, Instant.parse("2026-10-07T05:00:00Z"))!!
        assertEquals("5", c.line)
        assertFalse(c.rail)
        assertEquals("רגר/אורן", c.boardStop)
        assertEquals(6, c.walkMin)
        assertEquals(Instant.parse("2026-10-07T05:08:00Z"), c.boardAt)
        assertEquals(Instant.parse("2026-10-07T05:10:00Z"), c.endAt)
        assertEquals(10 * 60_000L, c.timeoutMs(Instant.parse("2026-10-07T05:00:00Z")))
        assertEquals("Line 5 · board at X · walk 6 min", Countdown.text("Line 5", "board at X", "walk 6 min"))
        assertEquals("Line 5 · board at X", Countdown.text("Line 5", "board at X", null))
    }

    @Test fun `a known delay moves the boarding time`() {
        val r = reminder().copy(boardingDelayMin = 4)
        assertEquals(Instant.parse("2026-10-07T05:12:00Z"), Countdown.of(r, Instant.parse("2026-10-07T05:00:00Z"))!!.boardAt)
    }

    @Test fun `a re-check that moves the bus updates the countdown`() {
        val r = reminder().copy(counting = true)
        // Real time: the same bus, scheduled 08:08, now 08:13.
        val base = trip(walkStart = "2026-10-07T05:05:00Z", busStart = "2026-10-07T05:13:00Z")
        val bus = base.legs[1].copy(realTime = true, scheduledStartTime = "2026-10-07T05:08:00Z")
        val later = base.copy(legs = listOf(base.legs[0], bus))
        val u = ReminderLogic.update(r, listOf(later)) as ReminderUpdate.Updated
        assertTrue(u.reminder.counting)
        assertEquals(later, u.reminder.itinerary) // "Start trip" rides the fresh times
        val c = Countdown.of(u.reminder, Instant.parse("2026-10-07T05:05:00Z"))!!
        assertEquals(Instant.parse("2026-10-07T05:13:00Z"), c.boardAt)
        assertEquals(Instant.parse("2026-10-07T05:15:00Z"), c.endAt)
    }

    @Test fun `past the end - nothing to post`() {
        val r = reminder()
        assertNull(Countdown.of(r, Instant.parse("2026-10-07T05:10:00Z")))
        assertTrue(Countdown.of(r, Instant.parse("2026-10-07T05:09:59Z"))!!.timeoutMs(Instant.parse("2026-10-07T05:09:59Z")) > 0)
    }

    @Test fun `past midnight - the countdown crosses the day`() {
        // Leave 23:55 Israel, board 00:03: the end time is simply the next day.
        val r = reminder(trip(walkStart = "2026-10-07T20:55:00Z", busStart = "2026-10-07T21:03:00Z"))
        val c = Countdown.of(r, Instant.parse("2026-10-07T20:55:00Z"))!!
        assertEquals(Instant.parse("2026-10-07T21:05:00Z"), c.endAt)
        assertEquals(10 * 60_000L, c.timeoutMs(Instant.parse("2026-10-07T20:55:00Z")))
    }

    @Test fun `a train says so, a trip without a walk has no walk`() {
        val b = Instant.parse("2026-10-07T05:08:00Z")
        val train = itinerary(leg("RAIL", place("באר שבע מרכז", stop), place("END", dest), b, b.plusSeconds(3600)).copy(headsign = "406"))
        val r = Reminder.from(train, stop, dest, UserSettings())!!
        val c = Countdown.of(r, b.minusSeconds(60))!!
        assertTrue(c.rail)
        assertNull(c.walkMin)
    }

    @Test fun `an old stored reminder still counts down, without walk and ride`() {
        val r = reminder().copy(itinerary = null, fromName = null, toName = null)
        val c = Countdown.of(r, Instant.parse("2026-10-07T05:00:00Z"))!!
        assertNull(c.walkMin)
        assertFalse(c.rail)
        // A reminder JSON written before C5 decodes, with the new fields empty.
        val old = """{"fromLat":1.0,"fromLon":2.0,"toLat":3.0,"toLon":4.0,"settings":{},"tripId":null,"line":"5",""" +
            """"boardStop":"X","scheduledBoardingEpoch":100,"leaveAtEpoch":50}"""
        val decoded = UserJson.decodeReminder(old)!!
        assertNull(decoded.itinerary)
        assertFalse(decoded.counting)
    }

    @Test fun `the reminder keeps its option and names through JSON`() {
        val r = reminder().copy(counting = true)
        val back = UserJson.decodeReminder(UserJson.encodeReminder(r))!!
        assertEquals(r, back)
        assertEquals("בית", back.fromName)
        assertEquals(trip(), back.itinerary)
    }
}
