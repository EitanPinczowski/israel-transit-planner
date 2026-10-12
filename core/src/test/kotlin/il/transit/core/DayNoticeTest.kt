package il.transit.core

import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.calendar.DayKind
import il.transit.core.calendar.Holiday
import il.transit.core.present.DayNotice
import il.transit.core.present.DayNotice.Notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * The Trip tab's day notices (Phase 10 D3). Comments are Israel time: UTC+3 until 25 Oct 2026
 * and from late March, UTC+2 in between.
 */
class DayNoticeTest {
    private fun t(iso: String) = Instant.parse(iso)

    /** Some day well before every search below, so the horizon never matters unless a test wants it. */
    private fun before(search: String) = t(search).minusSeconds(86_400)

    @Test fun `building any notice sends 0 requests`() {
        val api = FakeTransitApi()
        DayNotice.banner(t("2027-04-22T07:00:00Z"))
        DayNotice.forEmpty(t("2027-04-22T07:00:00Z"), t("2027-04-20T07:00:00Z"))
        DayNotice.forEmpty(t("2026-11-12T06:00:00Z"), t("2026-10-12T07:00:00Z"))
        assertTrue(api.calls.isEmpty()) // DayNotice takes no TransitApi: only "Plan for <day>" asks, once, on tap
    }

    @Test fun `banner - holiday, eve, Yom Kippur eve, reduced, none`() {
        val pesach = DayNotice.banner(t("2027-04-22T05:00:00Z"))!! // Thu 22 Apr 08:00
        assertEquals(DayKind.NO_SERVICE, pesach.kind)
        assertEquals(Holiday.PESACH_1, pesach.holiday)
        assertEquals(LocalDate.of(2027, 4, 22), pesach.date)
        assertEquals(DayKind.EVE, DayNotice.banner(t("2026-09-11T06:00:00Z"))!!.kind) // Fri, Erev Rosh Hashana
        // A weekday holiday eve: Wed 21 Apr 2027, Erev Pesach, afternoon.
        assertEquals(Holiday.PESACH_1, DayNotice.banner(t("2027-04-21T12:00:00Z"))!!.holiday)
        // Yom Kippur eve, afternoon (Sun 20 Sep 2026 14:00).
        assertEquals(DayKind.YOM_KIPPUR_EVE, DayNotice.banner(t("2026-09-20T11:00:00Z"))!!.kind)
        assertEquals(DayKind.REDUCED, DayNotice.banner(t("2026-09-29T06:00:00Z"))!!.kind) // Chol HaMoed Sukkot
        assertNull(DayNotice.banner(t("2026-10-13T05:00:00Z"))) // an ordinary Tuesday
        assertNull(DayNotice.banner(t("2029-05-19T09:00:00Z"))) // an eve on a Saturday: Shabbat says it already
        // The holiday is over at 20:00 when the next day has service (Thu 22 Apr 2027, 21:30): no banner.
        assertNull(DayNotice.banner(t("2027-04-22T18:30:00Z")))
        assertEquals(Holiday.PESACH_1, DayNotice.banner(t("2027-04-22T16:30:00Z"))!!.holiday) // 19:30: still on
        // Rosh Hashana day 1 at 21:00: day 2 follows, so the banner stays.
        assertEquals(DayKind.NO_SERVICE, DayNotice.banner(t("2026-09-12T18:00:00Z"))!!.kind)
        // After midnight the holiday is the civil day's: Thu 22 Apr 01:30 is Pesach.
        assertEquals(Holiday.PESACH_1, DayNotice.banner(t("2027-04-21T22:30:00Z"))!!.holiday)
    }

    @Test fun `0 options on a holiday - no service, plan for the evening after`() {
        val s = "2027-04-22T07:00:00Z" // Thu 22 Apr 10:00, Pesach
        assertEquals(Notice.NoService(Holiday.PESACH_1, t("2027-04-22T17:00:00Z"), evening = false), DayNotice.forEmpty(t(s), before(s)))
        // Rosh Hashana on Sat–Sun (2026): from Friday evening to Sunday 20:00, one window.
        val fri = "2026-09-11T15:00:00Z" // Fri 18:00
        assertEquals(Notice.NoService(Holiday.ROSH_HASHANA_1, t("2026-09-13T17:00:00Z"), evening = true), DayNotice.forEmpty(t(fri), before(fri)))
        val satNight = "2026-09-12T18:00:00Z" // Sat 21:00: Shabbat is out, Rosh Hashana is not
        assertEquals(Notice.NoService(Holiday.ROSH_HASHANA_2, t("2026-09-13T17:00:00Z"), evening = false), DayNotice.forEmpty(t(satNight), before(satNight)))
        // Yom Kippur eve, 17:00: the fast has begun.
        val ykEve = "2026-09-20T14:00:00Z"
        assertEquals(Notice.NoService(Holiday.YOM_KIPPUR, t("2026-09-21T17:00:00Z"), evening = true), DayNotice.forEmpty(t(ykEve), before(ykEve)))
    }

    @Test fun `0 options on Shabbat and Friday - plan for Saturday 20 00`() {
        val sat = "2026-10-17T07:00:00Z" // Sat 10:00
        assertEquals(Notice.NoService(null, t("2026-10-17T17:00:00Z"), evening = false), DayNotice.forEmpty(t(sat), before(sat)))
        val fri = "2026-10-16T15:00:00Z" // Fri 18:00
        assertEquals(Notice.NoService(null, t("2026-10-17T17:00:00Z"), evening = true), DayNotice.forEmpty(t(fri), before(fri)))
        // Friday morning with 0 options is not Shabbat yet: the usual text.
        val friMorning = "2026-10-16T07:00:00Z"
        assertNull(DayNotice.forEmpty(t(friMorning), before(friMorning)))
    }

    @Test fun `Saturday night after service and the 04 00 service-day edge`() {
        val satNight = "2026-10-17T18:00:00Z" // Sat 21:00: service is back, 0 options is the usual text
        assertNull(DayNotice.forEmpty(t(satNight), before(satNight)))
        // Fri night 01:30 belongs to Friday's service day but is civil Saturday: Shabbat.
        val friNight = "2026-10-16T22:30:00Z"
        assertEquals(Notice.NoService(null, t("2026-10-17T17:00:00Z"), evening = false), DayNotice.forEmpty(t(friNight), before(friNight)))
        // Sat night 03:30 (civil Sunday) before the 04:00 start: an ordinary day, the usual text.
        val sunEarly = "2026-10-18T00:30:00Z"
        assertNull(DayNotice.forEmpty(t(sunEarly), before(sunEarly)))
    }

    @Test fun `beyond the horizon - the same weekday and time within it`() {
        val now = t("2026-10-12T07:00:00Z") // Mon 12 Oct 10:00
        // Thu 12 Nov 08:00 (UTC+2) → Thu 15 Oct 08:00 (UTC+3): the local time is kept across the clock change.
        assertEquals(
            Notice.BeyondHorizon(LocalDate.of(2026, 11, 12), t("2026-10-15T05:00:00Z")),
            DayNotice.forEmpty(t("2026-11-12T06:00:00Z"), now),
        )
        // A date in the horizon that still has 0 options: the usual text.
        assertNull(DayNotice.forEmpty(t("2026-10-13T05:00:00Z"), now))
        assertNull(DayNotice.forEmpty(t("2026-10-20T05:00:00Z"), now)) // the last day of the horizon
        // A holiday beyond the horizon says "no service" (the calendar is sure), not "may not reach".
        assertTrue(DayNotice.forEmpty(t("2027-04-22T07:00:00Z"), now) is Notice.NoService)
        // The same weekday that lands on a holiday goes back one more week: Thu 6 May → 29 Apr → 22 Apr (Pesach) → 15 Apr.
        assertEquals(
            Notice.BeyondHorizon(LocalDate.of(2027, 5, 6), t("2027-04-15T05:00:00Z")),
            DayNotice.forEmpty(t("2027-05-06T05:00:00Z"), t("2027-04-15T03:00:00Z")),
        )
    }

    @Test fun `the measured horizon - recorded plans`() {
        fun plan(name: String) = MotisJson.decodeFromString(PlanResponse.serializer(), javaClass.getResource("/fixtures/$name.json")!!.readText())
        // Recorded 2026-10-12, Be'er Sheva → Tel Aviv at 08:00: +30, +60, +90 days answer nothing.
        for (d in listOf("20261111", "20261210", "20270110")) assertTrue(d, plan("plan_horizon_$d").itineraries.isEmpty())
        // Recorded 2026-10-05 for Tue 13 Oct (+8 days): answered.
        assertTrue(plan("plan_pages_first").itineraries.isNotEmpty())
        assertEquals(8L, DayNotice.HORIZON_DAYS)
    }
}
