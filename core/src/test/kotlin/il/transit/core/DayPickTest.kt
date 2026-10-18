package il.transit.core

import il.transit.core.api.Endpoint
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.calendar.DayKind
import il.transit.core.calendar.Holiday
import il.transit.core.calendar.Holidays
import il.transit.core.features.ISRAEL
import il.transit.core.geo.LatLon
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripQuery
import il.transit.core.present.DayPick
import il.transit.core.present.DayPick.Mark
import il.transit.core.present.DayPick.Word
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/** Picking the day of a search (Phase 11 E7). Times in names and comments are Israel time. */
class DayPickTest {
    private fun il(s: String): Instant = LocalDateTime.parse(s).atZone(ISRAEL).toInstant()
    private fun d(s: String): LocalDate = LocalDate.parse(s)
    private fun at(date: String, time: String) = DayPick.at(d(date), LocalTime.parse(time))

    private val monMorning = il("2026-10-19T10:00")

    @Test fun `picking a day sends nothing, and the search sends its usual one plan at that day`() = runBlocking {
        val api = FakeTransitApi()
        api.onPlan = { recorded }
        val now = monMorning
        DayPick.days(now)
        val tomorrow8 = DayPick.at(DayPick.days(now)[1].date, LocalTime.of(8, 0))
        DayPick.label(tomorrow8, now)
        DayPick.past(tomorrow8, now)
        assertTrue(api.calls.isEmpty()) // DayPick takes no TransitApi
        TripPlanner(api).plan(TripQuery(Endpoint.Coord(LatLon(31.262, 34.801)), Endpoint.Coord(LatLon(32.054, 34.785)), TimeMode.DEPART_AT, tomorrow8), now)
        assertEquals(listOf("plan"), api.calls)
        assertEquals(il("2026-10-20T08:00"), api.planRequests.single().time)
    }

    @Test fun `the day list - today, tomorrow and five named days`() {
        val days = DayPick.days(monMorning)
        assertEquals(DayPick.NAMED_DAYS + 2, days.size)
        assertEquals((0..6).toList(), days.map { it.offset })
        assertEquals(d("2026-10-19"), days.first().date)
        assertEquals(d("2026-10-25"), days.last().date)
        assertEquals(Mark.Friday, days[4].mark) // Fri 23
        assertEquals(Mark.Shabbat, days[5].mark) // Sat 24
        assertNull(days[0].mark)
    }

    @Test fun `labels - today has no day, then tomorrow, then the date`() {
        assertEquals(DayPick.Label(Word.TODAY, d("2026-10-19"), "14:00"), DayPick.label(at("2026-10-19", "14:00"), monMorning))
        assertEquals(DayPick.Label(Word.TOMORROW, d("2026-10-20"), "08:15"), DayPick.label(at("2026-10-20", "08:15"), monMorning))
        assertEquals(DayPick.Label(Word.DATE, d("2026-10-23"), "17:30"), DayPick.label(at("2026-10-23", "17:30"), monMorning))
        // A saved search from an earlier day is named by its date, never "today".
        assertEquals(Word.DATE, DayPick.label(at("2026-10-18", "09:00"), monMorning).word)
    }

    @Test fun `date texts in English and Hebrew`() {
        val fri = d("2026-10-23")
        assertEquals("Fri 23 Oct", DayPick.dateText(fri, Locale.ENGLISH))
        assertEquals("Fri 23", DayPick.chipText(fri, Locale.ENGLISH))
        for (he in listOf(Locale.forLanguageTag("he"), Locale.forLanguageTag("iw"))) {
            val full = DayPick.dateText(fri, he)
            assertTrue(full, full.startsWith("יום ו׳ 23 "))
            assertTrue(full, full.endsWith("אוק׳")) // Android's ICU writes "באוק׳", the JDK "אוק׳"
            assertEquals("יום ו׳ 23", DayPick.chipText(fri, he))
        }
    }

    @Test fun `the 04 00 edge - late at night tomorrow is the calendar date, named by its date`() {
        val monLate = il("2026-10-19T23:30")
        // "Tomorrow" 01:30, picked at 23:30: Tuesday 01:30, the calendar date picked. Named by date, not "tomorrow".
        val tue0130 = DayPick.at(DayPick.days(monLate)[1].date, LocalTime.of(1, 30))
        assertEquals(il("2026-10-20T01:30"), tue0130)
        assertEquals(DayPick.Label(Word.DATE, d("2026-10-20"), "01:30"), DayPick.label(tue0130, monLate))
        // A daytime tomorrow is still "tomorrow" before midnight.
        assertEquals(Word.TOMORROW, DayPick.label(at("2026-10-20", "08:00"), monLate).word)
        assertFalse(DayPick.night(monLate))

        val tueNight = il("2026-10-20T00:30") // still Monday's service day
        assertTrue(DayPick.night(tueNight))
        assertEquals(d("2026-10-20"), DayPick.days(tueNight).first().date)
        assertEquals(Word.TODAY, DayPick.label(at("2026-10-20", "01:30"), tueNight).word) // later tonight
        assertEquals(Word.DATE, DayPick.label(at("2026-10-20", "08:00"), tueNight).word) // "this morning" or "tomorrow"? the date says
        assertEquals(Word.DATE, DayPick.label(at("2026-10-21", "08:00"), tueNight).word)
        // From 04:00 the words come back.
        assertEquals(Word.TOMORROW, DayPick.label(at("2026-10-21", "08:00"), il("2026-10-20T04:00")).word)
    }

    @Test fun `autumn DST - 01 30 happens twice, the first is taken, and the next day keeps the clock time`() {
        // Sun 25 Oct 2026, 02:00 summer time becomes 01:00 winter time.
        assertEquals(Instant.parse("2026-10-24T22:30:00Z"), at("2026-10-25", "01:30")) // UTC+3, the first 01:30
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), at("2026-10-25", "02:30")) // UTC+2
        assertEquals(Instant.parse("2026-10-25T06:00:00Z"), at("2026-10-25", "08:00"))
        val sat8 = at("2026-10-24", "08:00")
        assertEquals(at("2026-10-25", "08:00"), DayPick.nextDay(sat8))
        assertEquals(Duration.ofHours(25), Duration.between(sat8, DayPick.nextDay(sat8)))
        assertEquals("08:00", DayPick.label(DayPick.nextDay(sat8), il("2026-10-24T20:00")).time)
    }

    @Test fun `spring DST - a time in the missing hour moves forward by the gap`() {
        // Fri 26 Mar 2027, 02:00 winter time becomes 03:00 summer time.
        val gap = at("2027-03-26", "02:30")
        assertEquals(Instant.parse("2027-03-26T00:30:00Z"), gap)
        assertEquals("03:30", DayPick.label(gap, il("2027-03-25T12:00")).time)
        val thu8 = at("2027-03-25", "08:00")
        assertEquals(Duration.ofHours(23), Duration.between(thu8, DayPick.nextDay(thu8)))
        assertEquals("08:00", DayPick.label(DayPick.nextDay(thu8), il("2027-03-25T12:00")).time)
    }

    @Test fun `holiday marks carry the D3 holiday day`() {
        val days = DayPick.days(il("2026-09-22T09:00")).associate { it.date to it.mark }
        val eve = days[d("2026-09-25")] as Mark.Holiday // Fri, Erev Sukkot
        assertEquals(DayKind.EVE, eve.day.kind)
        assertEquals(Holiday.SUKKOT, eve.day.holiday)
        val sukkot = days[d("2026-09-26")] as Mark.Holiday // Sat: the holiday wins over Shabbat
        assertEquals(DayKind.NO_SERVICE, sukkot.day.kind)
        assertEquals(DayKind.REDUCED, (days[d("2026-09-27")] as Mark.Holiday).day.kind) // Chol HaMoed
        assertNull(days[d("2026-09-24")])
        assertEquals(DayKind.YOM_KIPPUR_EVE, (DayPick.mark(d("2026-09-20")) as Mark.Holiday).day.kind)
        // An eve that falls on a Saturday is Shabbat: no service that day anyway.
        val saturdayEves = generateSequence(LocalDate.of(Holidays.FIRST_YEAR, 1, 1)) { it.plusDays(1) }
            .takeWhile { it.year <= Holidays.LAST_YEAR }
            .filter { it.dayOfWeek == DayOfWeek.SATURDAY && Holidays.day(it)?.kind.let { k -> k == DayKind.EVE || k == DayKind.YOM_KIPPUR_EVE } }
            .toList()
        assertTrue("the table has a Saturday eve to check", saturdayEves.isNotEmpty())
        saturdayEves.forEach { assertEquals(it.toString(), Mark.Shabbat, DayPick.mark(it)) }
    }

    @Test fun `the date picker reaches 60 days, never the past`() {
        val today = d("2026-10-19")
        assertTrue(DayPick.selectable(today, monMorning))
        assertFalse(DayPick.selectable(today.minusDays(1), monMorning))
        assertTrue(DayPick.selectable(today.plusDays(60), monMorning))
        assertFalse(DayPick.selectable(today.plusDays(61), monMorning))
        assertEquals(today.plusDays(DayPick.MAX_DAYS), DayPick.lastDay(monMorning))
        // Past the timetable horizon is allowed: the D3 notice speaks there.
        assertTrue(DayPick.selectable(today.plusDays(il.transit.core.present.DayNotice.HORIZON_DAYS + 1), monMorning))
        for (day in listOf(today, today.plusDays(60), d("1969-12-31"))) {
            assertEquals(day, DayPick.fromPicker(DayPick.toPicker(day)))
            assertEquals(day, DayPick.fromPicker(DayPick.toPicker(day) + 23 * 3_600_000L))
        }
    }

    @Test fun `a past time today is asked about, never moved`() {
        val now = il("2026-10-19T10:00:30")
        assertFalse(DayPick.past(at("2026-10-19", "10:00"), now)) // this minute
        assertTrue(DayPick.past(at("2026-10-19", "09:59"), now))
        assertTrue(DayPick.past(at("2026-10-19", "06:10"), now))
        assertEquals(at("2026-10-20", "06:10"), DayPick.nextDay(at("2026-10-19", "06:10")))
        assertFalse(DayPick.past(at("2026-10-20", "06:10"), now))
    }

    companion object {
        private val recorded: PlanResponse = MotisJson.decodeFromString(
            PlanResponse.serializer(),
            DayPickTest::class.java.getResource("/fixtures/plan_now_bs_hahagana.json")!!.readText(),
        )
    }
}
