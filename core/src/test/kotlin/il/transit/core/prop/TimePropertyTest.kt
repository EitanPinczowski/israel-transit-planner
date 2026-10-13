package il.transit.core.prop

import il.transit.core.calendar.Holiday
import il.transit.core.calendar.Holidays
import il.transit.core.features.ISRAEL
import il.transit.core.features.TrafficProfile
import il.transit.core.plan.LastRideFinder
import il.transit.core.remind.Countdown
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Time and date math: the 04:00 service day, Israel's two clock changes, countdowns, holidays. */
class TimePropertyTest {
    // Israel's clock changes: DST ends Sun 25 Oct 2026 (02:00 -> 01:00), starts Fri 26 Mar 2027 (02:00 -> 03:00).
    private val dstEnds: Instant = Instant.parse("2026-10-24T23:00:00Z")
    private val dstStarts: Instant = Instant.parse("2027-03-25T23:00:00Z")

    @Test fun `the service day changes at 04 00 Israel time and nowhere else`() = runBlocking<Unit> {
        val from = Instant.parse("2026-01-01T00:00:00Z").epochSecond
        val to = Instant.parse("2027-12-31T00:00:00Z").epochSecond
        checkAll(cfg(1000), Arb.long(from, to)) { sec ->
            val t = Instant.ofEpochSecond(sec)
            val local = t.atZone(ISRAEL)
            val day = LastRideFinder.serviceDay(t)
            // Before 04:00 it is still yesterday's service day; from 04:00 it is today's.
            val expected = if (local.toLocalTime() < LocalTime.of(4, 0)) local.toLocalDate().minusDays(1) else local.toLocalDate()
            assertEquals("serviceDay($t = $local)", expected, day)
            // Never more than a day apart from the wall-clock date, never in the future of it.
            assertTrue(day <= local.toLocalDate() && day >= local.toLocalDate().minusDays(1))
        }
    }

    @Test fun `the service day changes at 04 00 on the two clock-change days too`() = runBlocking<Unit> {
        // Found by this test: the boundary used to be 4 ELAPSED hours after midnight (03:00 / 05:00 local on those days).
        checkAll(cfg(1000), Arb.int(-2 * 86400..2 * 86400)) { off ->
            for (change in listOf(dstEnds, dstStarts)) {
                val t = change.plusSeconds(off.toLong())
                val local = t.atZone(ISRAEL)
                val expected = if (local.toLocalTime() < LocalTime.of(4, 0)) local.toLocalDate().minusDays(1) else local.toLocalDate()
                assertEquals("serviceDay($t = $local)", expected, LastRideFinder.serviceDay(t))
            }
        }
    }

    @Test fun `the service day never goes back as time moves on, across both clock changes`() = runBlocking<Unit> {
        for (change in listOf(dstEnds, dstStarts)) {
            var last: LocalDate? = null
            // Every 10 minutes for 3 days around the change.
            var t = change.minus(Duration.ofDays(1))
            val end = change.plus(Duration.ofDays(2))
            while (t < end) {
                val d = LastRideFinder.serviceDay(t)
                if (last != null) assertTrue("serviceDay went back at $t: $last -> $d", d >= last)
                last = d
                t = t.plusSeconds(600)
            }
        }
        // And randomly around them.
        checkAll(cfg(500), Arb.int(-36 * 3600..36 * 3600), Arb.int(1..7200)) { off, step ->
            for (change in listOf(dstEnds, dstStarts)) {
                val a = change.plusSeconds(off.toLong())
                val b = a.plusSeconds(step.toLong())
                assertTrue(LastRideFinder.serviceDay(b) >= LastRideFinder.serviceDay(a))
            }
        }
    }

    @Test fun `the clock changes shift Israel local time by exactly one hour and the 04 00 edge stays at 04 00`() {
        // 25 Oct 2026: 04:00 local is UTC+2 = 02:00Z; the day before it was UTC+3.
        assertEquals(LocalDate.of(2026, 10, 24), LastRideFinder.serviceDay(Instant.parse("2026-10-25T01:59:59Z")))
        assertEquals(LocalDate.of(2026, 10, 25), LastRideFinder.serviceDay(Instant.parse("2026-10-25T02:00:00Z")))
        // 26 Mar 2027: DST starts at 02:00 -> 03:00 (23:00Z the day before); 04:00 local is UTC+3 = 01:00Z.
        assertEquals(LocalDate.of(2027, 3, 25), LastRideFinder.serviceDay(Instant.parse("2027-03-26T00:59:59Z")))
        assertEquals(LocalDate.of(2027, 3, 26), LastRideFinder.serviceDay(Instant.parse("2027-03-26T01:00:00Z")))
        // The offset really changes by one hour.
        val before = Instant.parse("2026-10-24T20:00:00Z").atZone(ISRAEL).offset.totalSeconds
        val after = Instant.parse("2026-10-25T20:00:00Z").atZone(ISRAEL).offset.totalSeconds
        assertEquals(3600, before - after)
    }

    @Test fun `the traffic factor depends only on Israel wall-clock hour and weekday`() = runBlocking<Unit> {
        val traffic = TrafficProfile()
        val from = Instant.parse("2026-01-01T00:00:00Z").epochSecond
        val to = Instant.parse("2027-12-31T00:00:00Z").epochSecond
        checkAll(cfg(1000), Arb.long(from, to)) { sec ->
            val t = Instant.ofEpochSecond(sec)
            val f = traffic.factorAt(t)
            assertTrue(f == 1.0 || f == 1.3)
            val local = t.atZone(ISRAEL)
            val weekend = local.dayOfWeek.value in 5..6
            if (weekend) assertEquals(1.0, f, 0.0)
            assertTrue(traffic.adjust(600, t) >= 600)
        }
    }

    @Test fun `a countdown is never negative and never jumps back up`() = runBlocking<Unit> {
        checkAll(cfg(1000), Arb.int(0..4 * 3600), Arb.int(0..4 * 3600), Arb.int(0..3600)) { boardOff, a, step ->
            val boardAt = Instant.parse("2026-10-05T05:00:00Z").plusSeconds(boardOff.toLong())
            val end = boardAt.plus(Countdown.LINGER)
            val c = Countdown(null, false, "stop", null, boardAt, end)
            val t1 = boardAt.minusSeconds(a.toLong())
            val t2 = t1.plusSeconds(step.toLong())
            val m1 = c.timeoutMs(t1)
            val m2 = c.timeoutMs(t2)
            assertTrue("negative timeout", m1 >= 0 && m2 >= 0)
            assertTrue("countdown went up as time moved on ($m1 -> $m2)", m2 <= m1)
            // It falls by exactly the time that passed, until it reaches 0.
            assertEquals(maxOf(0L, m1 - step * 1000L), m2)
        }
    }

    @Test fun `every holiday appears in every year 2026 to 2031, one-day holidays exactly once`() {
        val oneDay = setOf(
            Holiday.ROSH_HASHANA_1, Holiday.ROSH_HASHANA_2, Holiday.YOM_KIPPUR, Holiday.SUKKOT, Holiday.SHMINI_ATZERET,
            Holiday.PESACH_1, Holiday.PESACH_7, Holiday.SHAVUOT,
            Holiday.YOM_HASHOAH, Holiday.YOM_HAZIKARON, Holiday.YOM_HAATZMAUT,
        )
        for (year in Holidays.FIRST_YEAR..Holidays.LAST_YEAR) {
            val counts = Holiday.values().associateWith { 0 }.toMutableMap()
            var d = LocalDate.of(year, 1, 1)
            while (d.year == year) {
                Holidays.holiday(d)?.let { counts[it] = counts.getValue(it) + 1 }
                d = d.plusDays(1)
            }
            for (h in Holiday.values()) {
                val n = counts.getValue(h)
                assertTrue("$h missing in $year", n >= 1)
                if (h in oneDay) assertEquals("$h in $year", 1, n)
            }
        }
    }

    @Test fun `holiday day kinds are consistent for every day 2026 to 2031`() {
        var d = LocalDate.of(Holidays.FIRST_YEAR, 1, 1)
        val end = LocalDate.of(Holidays.LAST_YEAR, 12, 31)
        while (d <= end) {
            // A rest day is never a short day; the day before a rest day is a short day or itself a rest day.
            if (Holidays.restDay(d)) assertTrue("$d", !Holidays.shortDay(d))
            if (Holidays.restDay(d.plusDays(1)) && !Holidays.restDay(d)) assertTrue("$d", Holidays.shortDay(d))
            assertTrue(Holidays.covers(d))
            d = d.plusDays(1)
        }
    }
}
