package il.transit.core

import il.transit.core.api.Itinerary
import il.transit.core.api.PlanResponse
import il.transit.core.geo.LatLon
import il.transit.core.history.TripRecord
import il.transit.core.plan.LastRide
import il.transit.core.plan.LastRideFinder
import il.transit.core.plan.LastTripChecker
import il.transit.core.plan.LastTripHome
import il.transit.core.plan.LastTripHome.Message
import il.transit.core.plan.LastTripHome.Seen
import il.transit.core.user.UserJson
import il.transit.core.user.UserSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Times in comments are Israel time (UTC+3 in October 2026). */
class LastTripHomeTest {
    private val home = LatLon(31.2790, 34.8200) // Be'er Sheva, Ramot
    private val nearHome = LatLon(31.2820, 34.8230) // ~430 m away
    private val telAviv = LatLon(32.0839, 34.7983)
    private val central = LatLon(32.0565, 34.7794)

    /** History keeps ends rounded to ~100 m: an origin from a record is that cell. */
    private val telAvivCell = TripRecord.parseCell(TripRecord.cellOf(telAviv))!!

    private fun t(iso: String) = Instant.parse(iso)

    private fun record(startedAt: String, to: LatLon?, totalMin: Int? = 60) = TripRecord(
        startedAtEpoch = t(startedAt).epochSecond, from = "", to = "x", mode = "TRIP",
        transitMin = 40, walkMin = 10, transfers = 0, totalMin = totalMin,
        toCell = to?.let(TripRecord::cellOf),
    )

    /** Walk 8 min to the stop, then a 90-min bus home. */
    private fun tripHome(walkStartUtc: String): Itinerary {
        val w = t(walkStartUtc)
        val b = w.plusSeconds(8 * 60)
        return itinerary(
            leg("WALK", place("START", telAviv), place("תחנה מרכזית תל אביב", central), w, b),
            leg("BUS", place("תחנה מרכזית תל אביב", central), place("END", home), b, b.plusSeconds(90 * 60)).copy(routeShortName = "370"),
        )
    }

    // --- when --------------------------------------------------------------------------------

    @Test fun `check times - 19 Sun-Thu, 12 Friday, 20 Saturday`() {
        assertEquals(LocalTime.of(19, 0), LastTripHome.checkTime(LocalDate.of(2026, 10, 11))) // Sunday
        assertEquals(LocalTime.of(19, 0), LastTripHome.checkTime(LocalDate.of(2026, 10, 8))) // Thursday
        assertEquals(LocalTime.of(12, 0), LastTripHome.checkTime(LocalDate.of(2026, 10, 9))) // Friday
        assertEquals(LocalTime.of(20, 0), LastTripHome.checkTime(LocalDate.of(2026, 10, 10))) // Saturday
    }

    @Test fun `next check - ahead today, at once when missed, else tomorrow`() {
        // Wednesday 10:00 → today 19:00.
        assertEquals(t("2026-10-07T16:00:00Z"), LastTripHome.nextCheck(t("2026-10-07T07:00:00Z"), null))
        // Wednesday 21:00, rebooted and not checked yet → now.
        assertEquals(t("2026-10-07T18:00:00Z"), LastTripHome.nextCheck(t("2026-10-07T18:00:00Z"), null))
        // Already checked → Thursday 19:00.
        assertEquals(t("2026-10-08T16:00:00Z"), LastTripHome.nextCheck(t("2026-10-07T18:00:00Z"), LocalDate.of(2026, 10, 7)))
        // Thursday evening checked → Friday 12:00.
        assertEquals(t("2026-10-09T09:00:00Z"), LastTripHome.following(t("2026-10-08T17:00:00Z")))
    }

    @Test fun `the 04 00 service-day edge`() {
        val wed = LocalDate.of(2026, 10, 7)
        // Thursday 03:59 still belongs to Wednesday, which was checked → Thursday 19:00.
        assertEquals(t("2026-10-08T16:00:00Z"), LastTripHome.nextCheck(t("2026-10-08T00:59:00Z"), wed))
        // Thursday 04:00 is Thursday: its own check is ahead.
        assertEquals(t("2026-10-08T16:00:00Z"), LastTripHome.nextCheck(t("2026-10-08T01:00:00Z"), wed))
        // A trip started Wednesday 23:30 counts for a check at Thursday 00:30, not for Thursday 05:00.
        val late = listOf(record("2026-10-07T20:30:00Z", telAviv, totalMin = 30))
        assertEquals(telAvivCell, LastTripHome.origin(late, null, home, t("2026-10-07T21:30:00Z")))
        assertNull(LastTripHome.origin(late, null, home, t("2026-10-08T02:00:00Z")))
    }

    // --- where -------------------------------------------------------------------------------

    @Test fun `origin - today's latest trip, unless a trip home came after it`() {
        val now = t("2026-10-07T16:00:00Z") // Wednesday 19:00
        val out = record("2026-10-07T05:00:00Z", telAviv)
        assertEquals(telAvivCell, LastTripHome.origin(listOf(out), null, home, now))
        val back = record("2026-10-07T14:00:00Z", nearHome)
        assertNull(LastTripHome.origin(listOf(back, out), null, home, now)) // newest first, as stored
        assertNull(LastTripHome.origin(listOf(out, back), null, home, now)) // order does not matter
        // Yesterday's trip says nothing about tonight.
        assertNull(LastTripHome.origin(listOf(record("2026-10-06T05:00:00Z", telAviv)), null, home, now))
        // A record from before C3 has no coordinates: not an origin.
        assertNull(LastTripHome.origin(listOf(record("2026-10-07T05:00:00Z", null)), null, home, now))
        // No history at all, no fix.
        assertNull(LastTripHome.origin(emptyList(), null, home, now))
    }

    @Test fun `origin - the last foreground fix, today and under 3 h, when it is newer`() {
        val now = t("2026-10-07T16:00:00Z") // 19:00
        val trip = record("2026-10-07T05:00:00Z", telAviv) // arrived 09:00
        val herzliya = LatLon(32.1624, 34.8447)
        assertEquals(herzliya, LastTripHome.origin(listOf(trip), Seen(herzliya, t("2026-10-07T14:30:00Z")), home, now))
        // 3 h 30 old: ignored, the trip wins.
        assertEquals(telAvivCell, LastTripHome.origin(listOf(trip), Seen(herzliya, t("2026-10-07T12:30:00Z")), home, now))
        // Seen at home after the trip: at home.
        assertNull(LastTripHome.origin(listOf(trip), Seen(nearHome, t("2026-10-07T15:00:00Z")), home, now))
        // Seen on the way out, before the trip's arrival: the trip's destination wins.
        val onTheBus = record("2026-10-07T15:00:00Z", telAviv, totalMin = 90) // arrives 19:30
        assertEquals(telAvivCell, LastTripHome.origin(listOf(onTheBus), Seen(LatLon(31.6, 34.7), t("2026-10-07T15:50:00Z")), home, now))
        // A fix alone works too; one from yesterday evening does not (another service day).
        assertEquals(herzliya, LastTripHome.origin(emptyList(), Seen(herzliya, t("2026-10-07T15:00:00Z")), home, now))
        // Seen at 03:30, checked at 04:30: an hour old, but yesterday's service day.
        assertNull(LastTripHome.origin(emptyList(), Seen(herzliya, t("2026-10-07T00:30:00Z")), home, t("2026-10-07T01:30:00Z")))
    }

    // --- what --------------------------------------------------------------------------------

    @Test fun `alert - leave 30 min before, with the boarding time and stop`() {
        val last = tripHome("2026-10-07T20:02:00Z") // walk 23:02, bus 23:10
        val a = LastTripHome.alert(LastRide(last, tripHome("2026-10-08T01:30:00Z")), t("2026-10-07T16:05:00Z"))!! // next 04:38
        assertEquals("23:10", a.boardTime)
        assertEquals("תחנה מרכזית תל אביב", a.boardStop)
        assertEquals(t("2026-10-07T19:32:00Z"), a.notifyAt)
        assertEquals(LocalDate.of(2026, 10, 7), a.day)
        assertNull(a.nextDay) // an ordinary night: no "(next: …)"
        assertEquals(Message.LeaveIn(30), LastTripHome.message(a, a.notifyAt))
        // The check itself ran late (after the warning time): "leave now".
        assertEquals(Message.LeaveNow, LastTripHome.message(a, t("2026-10-07T19:50:00Z")))
        // An inexact alarm a little late still counts the minutes.
        assertEquals(Message.LeaveIn(27), LastTripHome.message(a, t("2026-10-07T19:35:00Z")))
        // Gone: nothing.
        assertNull(LastTripHome.message(a, t("2026-10-07T20:02:00Z")))
    }

    @Test fun `alert - none when it runs all night, none found, or already gone`() {
        val last = tripHome("2026-10-07T20:02:00Z")
        assertNull(LastTripHome.alert(LastRide(last, null, runsAllNight = true), t("2026-10-07T16:00:00Z")))
        assertNull(LastTripHome.alert(LastRide(null, null), t("2026-10-07T16:00:00Z")))
        assertNull(LastTripHome.alert(LastRide(last, null), t("2026-10-07T20:02:00Z")))
    }

    @Test fun `Friday - checked at noon, last bus 15 40, next Saturday night`() {
        val last = tripHome("2026-10-09T12:32:00Z") // walk 15:32, bus 15:40
        val next = tripHome("2026-10-10T16:22:00Z") // Saturday, bus 19:30
        val lr = LastRide(last, next)
        assertTrue(lr.longGap) // the timetable knows: Shabbat (a holiday eve works the same way)
        val a = LastTripHome.alert(lr, t("2026-10-09T09:00:00Z"))!!
        assertEquals("15:40", a.boardTime)
        assertEquals(DayOfWeek.SATURDAY.value, a.nextDay)
        assertEquals("19:30", a.nextTime)
        assertEquals(t("2026-10-09T12:02:00Z"), a.notifyAt) // 15:02
    }

    @Test fun `settings - off by default, and it survives JSON`() {
        assertFalse(UserSettings().lastTripAlert)
        val s = UserSettings(lastTripAlert = true)
        assertEquals(s, UserJson.decodeSettings(UserJson.encodeSettings(s)))
        assertFalse(UserJson.decodeSettings("""{"nightRefresh":false}""").lastTripAlert)
    }

    @Test fun `the stored alert survives JSON`() {
        val a = LastTripHome.alert(LastRide(tripHome("2026-10-09T12:32:00Z"), tripHome("2026-10-10T16:22:00Z")), t("2026-10-09T09:00:00Z"))!!
        val json = kotlinx.serialization.json.Json.encodeToString(il.transit.core.plan.LastTripAlert.serializer(), a)
        assertEquals(a, kotlinx.serialization.json.Json.decodeFromString(il.transit.core.plan.LastTripAlert.serializer(), json))
    }

    // --- budget (pinned) --------------------------------------------------------------------

    /** Every plan answer offers trips home until 02:30 (a busy line that still ends). */
    private fun api(): FakeTransitApi {
        val evening = listOf(tripHome("2026-10-07T18:02:00Z"), tripHome("2026-10-07T19:02:00Z"), tripHome("2026-10-07T20:02:00Z"))
        val after = listOf(tripHome("2026-10-07T21:02:00Z"), tripHome("2026-10-07T22:02:00Z"), tripHome("2026-10-07T23:30:00Z"))
        return FakeTransitApi().apply {
            onPlan = { req -> PlanResponse(if (req.arriveBy) evening else after.filter { it.start >= req.time }) }
        }
    }

    private suspend fun check(api: FakeTransitApi, checked: LocalDate?, origin: LatLon?, now: Instant, marks: MutableList<LocalDate>) =
        LastTripChecker(api).check(checked, origin, home, now, UserSettings(), "he") { marks += it }

    @Test fun `a check sends at most 3, a second the same service day 0`() = runBlocking {
        val api = api()
        val marks = mutableListOf<LocalDate>()
        val now = t("2026-10-07T16:00:00Z")
        val out = check(api, null, telAviv, now, marks)!!
        assertTrue(api.calls.size <= LastRideFinder.BUDGET)
        assertEquals(LastRideFinder.BUDGET, 3)
        assertEquals(api.calls.size, out.requests)
        assertEquals(listOf(LocalDate.of(2026, 10, 7)), marks)
        // Night buses kept coming until the budget ran out: runs all night, no alert.
        assertNull(out.alert)

        val before = api.calls.size
        assertNull(check(api, marks.last(), telAviv, t("2026-10-07T19:00:00Z"), marks))
        // Past midnight is still the same service day.
        assertNull(check(api, marks.last(), telAviv, t("2026-10-07T23:00:00Z"), marks))
        assertEquals(before, api.calls.size)
    }

    @Test fun `no origin sends 0`() = runBlocking {
        val api = api()
        val marks = mutableListOf<LocalDate>()
        assertNull(check(api, null, null, t("2026-10-07T16:00:00Z"), marks))
        assertEquals(0, api.calls.size)
        assertTrue(marks.isEmpty())
    }

    @Test fun `an ordinary evening - 2 requests, an alert at 23 10`() = runBlocking {
        val last = tripHome("2026-10-07T20:02:00Z")
        val api = FakeTransitApi().apply {
            onPlan = { req -> PlanResponse(if (req.arriveBy) listOf(tripHome("2026-10-07T19:02:00Z"), last) else listOf(tripHome("2026-10-08T02:30:00Z")).filter { it.start >= req.time }) }
        }
        val marks = mutableListOf<LocalDate>()
        val out = check(api, null, telAviv, t("2026-10-07T16:00:00Z"), marks)!!
        assertEquals(2, out.requests)
        assertEquals("23:10", out.alert!!.boardTime)
    }

    @Test fun `a failed check leaves the day checked`() = runBlocking {
        val api = FakeTransitApi().apply { onPlan = { throw java.io.IOException("offline") } }
        val marks = mutableListOf<LocalDate>()
        val r = runCatching { check(api, null, telAviv, t("2026-10-07T16:00:00Z"), marks) }
        assertTrue(r.isFailure)
        assertEquals(listOf(LocalDate.of(2026, 10, 7)), marks)
    }
}
