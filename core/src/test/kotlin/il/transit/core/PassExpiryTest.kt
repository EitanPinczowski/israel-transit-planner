package il.transit.core

import il.transit.core.remind.PassExpiry
import il.transit.core.remind.PassExpiry.Kind
import il.transit.core.remind.PassExpiry.Validity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** Phase 11 E1: Rav-Kav pass expiry, 3 days before and on the last day at 09:00 Israel time (owner decision 4). */
class PassExpiryTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun i(s: String) = Instant.parse(s)

    @Test fun `3 days before and on the last day, at 09 00 Israel time`() {
        val n = PassExpiry.notices(d("2026-11-01")) // a Sunday, winter time (UTC+2)
        assertEquals(listOf(Kind.SOON, Kind.LAST_DAY), n.map { it.kind })
        assertEquals(i("2026-10-29T07:00:00Z"), n[0].at)
        assertEquals(i("2026-11-01T07:00:00Z"), n[1].at)
        assertTrue(n.all { it.endsOn == d("2026-11-01") })
    }

    @Test fun `across the October clock change each notice is still 09 00 local`() {
        // Israel leaves summer time on Sun 25 Oct 2026: the first notice is UTC+3, the last day UTC+2.
        val n = PassExpiry.notices(d("2026-10-27"))
        assertEquals(i("2026-10-24T06:00:00Z"), n[0].at)
        assertEquals(i("2026-10-27T07:00:00Z"), n[1].at)
        // And into summer time (Fri 26 Mar 2027): UTC+2, then UTC+3.
        val spring = PassExpiry.notices(d("2027-03-28"))
        assertEquals(i("2027-03-25T07:00:00Z"), spring[0].at)
        assertEquals(i("2027-03-28T06:00:00Z"), spring[1].at)
    }

    @Test fun `29 February 2028 is a day like any other`() {
        assertEquals(i("2028-02-26T07:00:00Z"), PassExpiry.notices(d("2028-02-29"))[0].at)
        assertEquals(i("2028-02-29T07:00:00Z"), PassExpiry.notices(d("2028-03-03"))[0].at) // the reminder falls on the 29th
        assertEquals(Validity.OK, PassExpiry.check(d("2028-02-29"), d("2028-01-01")))
    }

    @Test fun `a last day on Shabbat still notifies at 09 00`() {
        val last = d("2026-10-31")
        assertEquals(DayOfWeek.SATURDAY, last.dayOfWeek)
        assertEquals(i("2026-10-31T07:00:00Z"), PassExpiry.notices(last)[1].at)
        assertEquals(Kind.LAST_DAY, PassExpiry.due(last, i("2026-10-31T07:00:30Z"))!!.kind)
    }

    @Test fun `next is the first notice still ahead, then none`() {
        val end = d("2026-11-01")
        assertEquals(Kind.SOON, PassExpiry.next(end, i("2026-10-20T10:00:00Z"))!!.kind)
        assertEquals(Kind.LAST_DAY, PassExpiry.next(end, i("2026-10-29T07:00:00Z"))!!.kind) // at the first: the next one
        assertEquals(Kind.LAST_DAY, PassExpiry.next(end, i("2026-10-30T12:00:00Z"))!!.kind)
        assertNull(PassExpiry.next(end, i("2026-11-01T07:00:00Z")))
        // Set on the last day before 09:00: that morning's notice is still ahead.
        assertEquals(i("2026-11-01T07:00:00Z"), PassExpiry.next(end, i("2026-11-01T05:00:00Z"))!!.at)
    }

    @Test fun `due posts the notice of its own day, late or not, and nothing from an earlier day`() {
        val end = d("2026-11-01")
        assertNull(PassExpiry.due(end, i("2026-10-29T06:59:00Z")))
        assertEquals(Kind.SOON, PassExpiry.due(end, i("2026-10-29T07:00:00Z"))!!.kind)
        assertEquals(Kind.SOON, PassExpiry.due(end, i("2026-10-29T20:00:00Z"))!!.kind) // Doze held it back
        assertNull(PassExpiry.due(end, i("2026-10-30T07:00:00Z"))) // the next day: too late to say "3 days"
        assertEquals(Kind.LAST_DAY, PassExpiry.due(end, i("2026-11-01T09:00:00Z"))!!.kind)
        assertNull(PassExpiry.due(end, i("2026-11-02T07:00:00Z")))
    }

    @Test fun `a past date is refused when typed and cleared once it has passed`() {
        val today = d("2026-10-14")
        assertEquals(Validity.PAST, PassExpiry.check(d("2026-10-13"), today))
        assertEquals(Validity.OK, PassExpiry.check(today, today))
        assertFalse(PassExpiry.isPast(d("2026-11-01"), i("2026-11-01T21:59:00Z"))) // 23:59 on the last day
        assertTrue(PassExpiry.isPast(d("2026-11-01"), i("2026-11-01T22:00:00Z"))) // 00:00 Israel the next day
    }

    @Test fun `more than 400 days ahead is refused`() {
        val today = d("2026-10-14")
        assertEquals(Validity.OK, PassExpiry.check(today.plusDays(400), today))
        assertEquals(Validity.TOO_FAR, PassExpiry.check(today.plusDays(401), today))
    }

    @Test fun `a changed date moves both notices, Off has none`() {
        val now = i("2026-10-14T10:00:00Z")
        assertEquals(i("2026-10-29T07:00:00Z"), PassExpiry.next(d("2026-11-01"), now)!!.at)
        assertEquals(i("2026-11-27T07:00:00Z"), PassExpiry.next(d("2026-11-30"), now)!!.at)
        // Off is null in the settings: nothing to parse, nothing to arm.
        assertNull(PassExpiry.parse(null))
        assertNull(PassExpiry.parse("not a date"))
        assertEquals(d("2026-11-30"), PassExpiry.parse("2026-11-30"))
    }

    @Test fun `the date reads Sun 1 Nov and says nothing about prices`() {
        assertEquals("Sun 1 Nov", PassExpiry.dateLabel(d("2026-11-01"), Locale.ENGLISH))
        assertEquals(d("2026-10-14"), PassExpiry.today(i("2026-10-13T21:30:00Z"))) // 00:30 Israel
    }

    @Test fun `the day boundary follows Israel's clock in summer too`() {
        assertEquals(d("2026-07-10"), PassExpiry.today(i("2026-07-10T20:59:00Z"))) // 23:59 IDT
        assertEquals(d("2026-07-11"), PassExpiry.today(i("2026-07-10T21:00:00Z"))) // 00:00 IDT
        assertFalse(PassExpiry.isPast(d("2026-07-10"), i("2026-07-10T20:59:00Z")))
        assertTrue(PassExpiry.isPast(d("2026-07-10"), i("2026-07-10T21:00:00Z")))
    }

    @Test fun `due across the October change - a summer notice, a winter last day`() {
        val end = d("2026-10-27") // notices Sat 24 Oct 09:00 IDT and Tue 27 Oct 09:00 IST
        assertEquals(Kind.SOON, PassExpiry.due(end, i("2026-10-24T20:59:00Z"))!!.kind) // 23:59 IDT, same day
        assertNull(PassExpiry.due(end, i("2026-10-24T21:00:00Z"))) // 00:00 Sun: the next day
        assertNull(PassExpiry.due(end, i("2026-10-26T12:00:00Z")))
        assertNull(PassExpiry.due(end, i("2026-10-27T06:59:00Z"))) // 08:59 IST
        assertEquals(Kind.LAST_DAY, PassExpiry.due(end, i("2026-10-27T07:00:00Z"))!!.kind)
    }

    @Test fun `step - an alarm posts what is due, arms the next, and clears after the last day`() {
        val end = "2026-11-01"
        // The first notice fires: post it, arm the last day.
        val first = PassExpiry.step(end, i("2026-10-29T07:00:05Z"), post = true)
        assertEquals(Kind.SOON, first.post!!.kind)
        assertEquals(i("2026-11-01T07:00:00Z"), first.armAt)
        assertFalse(first.clear)
        // The last day's fires: post it, nothing more to arm, the date goes back to Off.
        val last = PassExpiry.step(end, i("2026-11-01T07:00:05Z"), post = true)
        assertEquals(Kind.LAST_DAY, last.post!!.kind)
        assertNull(last.armAt)
        assertTrue(last.clear)
    }

    @Test fun `step - a boot posts a notice it slept through that day, a time-zone change or update only re-arms`() {
        val end = "2026-11-01"
        val bootLate = PassExpiry.step(end, i("2026-10-29T07:30:00Z"), post = true) // booted 09:30 on the first notice's day
        assertEquals(Kind.SOON, bootLate.post!!.kind)
        assertEquals(i("2026-11-01T07:00:00Z"), bootLate.armAt)
        val zone = PassExpiry.step(end, i("2026-10-29T07:30:00Z"), post = false)
        assertNull(zone.post)
        assertEquals(i("2026-11-01T07:00:00Z"), zone.armAt)
        assertFalse(zone.clear)
        // Before any notice: nothing to post, the first one armed (a reboot re-arms it).
        val early = PassExpiry.step(end, i("2026-10-20T10:00:00Z"), post = true)
        assertNull(early.post)
        assertEquals(i("2026-10-29T07:00:00Z"), early.armAt)
        // A boot the day after the first notice posts nothing (too late to say "3 days").
        assertNull(PassExpiry.step(end, i("2026-10-30T07:30:00Z"), post = true).post)
    }

    @Test fun `step - Off, a passed date and a broken value`() {
        assertEquals(PassExpiry.Step(null, null, clear = false), PassExpiry.step(null, i("2026-10-20T10:00:00Z"), post = true))
        assertEquals(PassExpiry.Step(null, null, clear = true), PassExpiry.step("2026-10-01", i("2026-10-20T10:00:00Z"), post = true))
        assertEquals(PassExpiry.Step(null, null, clear = true), PassExpiry.step("1 Nov", i("2026-10-20T10:00:00Z"), post = true))
    }
}
