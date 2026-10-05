package il.transit.core

import il.transit.core.api.Endpoint
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.Itinerary
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.geo.LatLon
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPages
import il.transit.core.plan.TripPages.Direction.EARLIER
import il.transit.core.plan.TripPages.Direction.LATER
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripQuery
import il.transit.core.plan.TripResult
import il.transit.core.plan.reselect
import il.transit.core.plan.stillAhead
import il.transit.core.remind.Reminder
import il.transit.core.user.UserSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** "Earlier" / "Later" (Phase 9 C1), on three real pages of one search (Tue 13 Oct 2026,
 *  Be'er Sheva → Tel Aviv central station, 08:00). */
class TripPagesTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name.json")!!.readText()
    private fun page(name: String) = MotisJson.decodeFromString(PlanResponse.serializer(), fixture(name))

    private val first = page("plan_pages_first")
    private val later = page("plan_pages_later")
    private val earlier = page("plan_pages_earlier")
    private val time = Instant.parse("2026-10-13T05:00:00Z")
    private val query = TripQuery(
        Endpoint.Coord(LatLon(31.2437, 34.7976)), Endpoint.Coord(LatLon(32.0560, 34.7794)),
        TimeMode.DEPART_AT, time, UserSettings(),
    )

    private fun fake() = FakeTransitApi().apply {
        onPlan = { first }
        pages = mapOf(first.nextPageCursor!! to later, first.previousPageCursor!! to earlier, later.nextPageCursor!! to later)
    }

    @Test fun `recorded pages carry both cursors and the request sends one back`() {
        assertEquals("EARLIER|1791867600", first.previousPageCursor)
        assertEquals("LATER|1791871500", first.nextPageCursor)
        assertEquals("LATER|1791878700", later.nextPageCursor)
        assertEquals("EARLIER|1791863820", earlier.previousPageCursor)
        assertEquals(listOf(6, 8, 5), listOf(first, later, earlier).map { it.itineraries.size })
        val q = il.transit.core.api.PlanRequest(query.from, query.to, time, pageCursor = "LATER|1").toQuery()
        assertEquals("LATER|1", q.toMap()["pageCursor"])
        assertNull(il.transit.core.api.PlanRequest(query.from, query.to, time).toQuery().toMap()["pageCursor"])
    }

    @Test fun `first plan keeps its cursors and search time`() = runTest {
        val r = TripPlanner(fake()).plan(query)
        assertEquals(first.previousPageCursor, r.earlierCursor)
        assertEquals(first.nextPageCursor, r.laterCursor)
        assertEquals(time.epochSecond, r.searchEpoch)
    }

    @Test fun `later and earlier merge in order and only move their own cursor`() = runTest {
        val planner = TripPlanner(fake())
        val r0 = planner.plan(query)
        val r1 = planner.page(query, r0, LATER)
        assertEquals(14, r1.itineraries.size)
        assertEquals(r1.itineraries.sortedBy { it.end }, r1.itineraries)
        assertEquals(r0.earlierCursor, r1.earlierCursor)
        assertEquals(later.nextPageCursor, r1.laterCursor)
        assertTrue(r1.paged)
        val r2 = planner.page(query, r1, EARLIER)
        assertEquals(19, r2.itineraries.size)
        assertEquals("2026-10-13T03:57:00Z", r2.itineraries.first().startTime)
        assertEquals(earlier.previousPageCursor, r2.earlierCursor)
        assertEquals(later.nextPageCursor, r2.laterCursor)
    }

    @Test fun `a page already loaded adds no duplicates`() {
        val r0 = TripResult(first.itineraries, null, first.previousPageCursor, first.nextPageCursor)
        val again = TripPages.merge(r0, first, LATER, arriveBy = false)
        assertEquals(6, again.itineraries.size)
        assertEquals(6, again.itineraries.map(TripPages::key).toSet().size)
    }

    @Test fun `the list is capped at 20, dropping the far end`() {
        var r = TripResult(first.itineraries, null)
        r = TripPages.merge(r, later, LATER, false)
        r = TripPages.merge(r, earlier, EARLIER, false) // 19
        val shifted = later.copy(itineraries = later.itineraries.map { it.shift(3 * 3600) })
        r = TripPages.merge(r, shifted, LATER, false) // 27 → 20, the earliest go
        assertEquals(TripPages.MAX_OPTIONS, r.itineraries.size)
        assertTrue(r.itineraries.none { it.startTime == "2026-10-13T03:57:00Z" })
        assertTrue(r.itineraries.any { it.start == later.itineraries.last().start.plusSeconds(3 * 3600) })
    }

    @Test fun `a page that waits out Shabbat adds nothing`() {
        val r0 = TripResult(first.itineraries, null)
        val sat = later.itineraries.first().let { it.copy(duration = 25 * 3600, endTime = it.start.plusSeconds(25 * 3600L).toString()) }
        val r1 = TripPages.merge(r0, PlanResponse(listOf(sat), nextPageCursor = "LATER|9"), LATER, false)
        assertEquals(first.itineraries.toSet(), r1.itineraries.toSet())
        assertEquals("LATER|9", r1.laterCursor)
    }

    @Test fun `arrive-by keeps the latest departure first`() {
        val r = TripPages.merge(TripResult(first.itineraries, null), earlier, EARLIER, arriveBy = true)
        assertEquals(r.itineraries.sortedByDescending { it.start }.map { it.start }, r.itineraries.map { it.start })
    }

    @Test fun `arrive-by pages send the same arrive-by query with the cursor`() = runTest {
        val arrive = query.copy(timeMode = TimeMode.ARRIVE_BY)
        val api = fake()
        val planner = TripPlanner(api)
        val r0 = planner.plan(arrive)
        val r1 = planner.page(arrive, r0, EARLIER)
        val pageReq = api.planRequests.last()
        assertTrue(pageReq.arriveBy)
        assertEquals(first.previousPageCursor, pageReq.pageCursor)
        assertEquals(api.planRequests.first().copy(pageCursor = pageReq.pageCursor), pageReq) // only the cursor differs
        // Arrive-by lists the latest departure first; the Earlier page goes to the end.
        assertEquals(r1.itineraries.sortedByDescending { it.start }.map { it.start }, r1.itineraries.map { it.start })
        assertEquals("2026-10-13T03:57:00Z", r1.itineraries.last().startTime)
        assertEquals(earlier.previousPageCursor, r1.earlierCursor)
    }

    @Test fun `an Earlier page from yesterday's service day keeps the order across midnight`() {
        // The 05:19 search moved to 00:19 Israel time (21:19 UTC the day before); the Earlier
        // page shifted the same way lands before midnight, in the previous service day.
        val shift = -8L * 3600
        val r0 = TripResult(first.itineraries.map { it.shift(shift) }, null)
        val page = earlier.copy(itineraries = earlier.itineraries.map { it.shift(shift) })
        val r = TripPages.merge(r0, page, EARLIER, arriveBy = false)
        assertEquals(11, r.itineraries.size)
        assertEquals(r.itineraries.sortedBy { it.end }, r.itineraries)
        val first = r.itineraries.first().start.atZone(il.transit.core.features.ISRAEL)
        assertEquals(java.time.LocalDate.of(2026, 10, 12), first.toLocalDate()) // 22:57 the evening before
        val miss = TripPages.ifIMissIt(r.itineraries.first(), r.itineraries) as TripPages.Miss.Next
        assertTrue(miss.next.firstTransitLeg!!.start.isAfter(r.itineraries.first().firstTransitLeg!!.start))
    }

    @Test fun `offline, a failed tap keeps the list and says so`() = runTest {
        val api = fake()
        val planner = TripPlanner(api)
        val shown = planner.plan(query)
        api.pages = emptyMap()
        api.onPlan = { throw java.io.IOException("offline") }
        val merged = runCatching { planner.page(query, shown, LATER) }.getOrNull()
        assertNull(merged)
        assertEquals(TripPages.PageOutcome.Failed, TripPages.outcome(shown, shown, merged))
        // The list on screen is untouched: same options, same cursors (a later tap can retry).
        assertEquals(first.nextPageCursor, shown.laterCursor)
        // A new search replaced the list while the page loaded: the late answer is dropped.
        val newer = shown.copy(itineraries = shown.itineraries.drop(1))
        val late = TripPages.merge(shown, later, LATER, false)
        assertEquals(TripPages.PageOutcome.Stale, TripPages.outcome(shown, newer, late))
        assertEquals(TripPages.PageOutcome.Merged(late), TripPages.outcome(shown, shown, late))
    }

    @Test fun `no cursor that way sends nothing`() = runTest {
        val api = fake()
        val r = TripResult(first.itineraries, null)
        assertSame(r, TripPlanner(api).page(query, r, LATER))
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test fun `two Later taps send 2 requests and a repeated cursor sends 0`() = runTest {
        val fake = fake()
        val planner = TripPlanner(GuardedTransitApi(fake))
        val r0 = planner.plan(query)
        assertEquals(1, fake.calls.size)
        val r1 = planner.page(query, r0, LATER)
        val r2 = planner.page(query, r1, LATER)
        assertEquals(3, fake.calls.size)
        assertEquals(listOf(null, first.nextPageCursor, later.nextPageCursor), fake.planRequests.map { it.pageCursor })
        // Same cursor again (a second tap before the answer, or after a refresh): the guard answers.
        planner.page(query, r0, LATER)
        planner.page(query, r1, LATER)
        assertEquals(3, fake.calls.size)
        // Every page repeats the first search's time, so the cache key matches.
        assertEquals(setOf(time), fake.planRequests.map { it.time }.toSet())
        assertTrue(r2.itineraries.size >= r1.itineraries.size)
    }

    @Test fun `if I miss it names the next loaded departure`() {
        val r = TripPages.merge(TripResult(first.itineraries, null), later, LATER, false)
        val sel = r.itineraries.first() // 08:19 train, arrive 09:47
        val miss = TripPages.ifIMissIt(sel, r.itineraries) as TripPages.Miss.Next
        assertEquals("2026-10-13T05:27:00Z", miss.next.startTime) // walk to bus 370, which leaves 08:30
        assertEquals(14, miss.laterMin) // arrives 10:01
        // The last loaded option: nothing later is loaded, offer the Later page.
        val last = r.itineraries.maxBy { it.firstTransitLeg!!.start }
        assertEquals(TripPages.Miss.ShowLater, TripPages.ifIMissIt(last, r.itineraries))
    }

    @Test fun `if I miss it never says minus`() {
        val a = first.itineraries[4] // 09:04 train, arrive 10:55
        val b = first.itineraries[1].shift(2 * 3600) // leaves 10:27, arrives 12:01
        val fast = b.copy(endTime = a.end.minusSeconds(600).toString())
        assertEquals(0, (TripPages.ifIMissIt(a, listOf(a, fast)) as TripPages.Miss.Next).laterMin)
    }

    @Test fun `walking all the way has nothing to miss`() {
        val walk = itinerary(leg("WALK", place("a", LatLon(31.0, 34.0)), place("b", LatLon(31.01, 34.0)), time, time.plusSeconds(600)))
        assertNull(TripPages.ifIMissIt(walk, first.itineraries))
    }

    @Test fun `a refresh keeps the loaded pages and the cursors`() {
        val paged = TripPages.merge(TripResult(first.itineraries, null, "E0", "L0", time.epochSecond), later, LATER, false)
        val fresh = TripResult(first.itineraries.drop(1), null, "E1", "L1", time.epochSecond + 300)
        val now = first.itineraries[0].firstTransitLeg!!.start.plusSeconds(60) // the 08:19 train left
        val r = TripPages.refresh(paged, fresh, now, false)
        assertEquals(paged.itineraries.size - 1, r.itineraries.size)
        assertEquals("E0", r.earlierCursor)
        assertEquals(later.nextPageCursor, r.laterCursor)
        // Not paged: the refresh is the new answer, as before.
        assertSame(fresh, TripPages.refresh(TripResult(first.itineraries, null), fresh, now, false))
    }

    @Test fun `a reminder follows the option picked after paging`() {
        val r0 = TripResult(first.itineraries, null)
        val r1 = TripPages.merge(r0, earlier, EARLIER, false)
        val picked = r0.itineraries[2]
        val before = r0.itineraries.indexOf(picked)
        val now = reselect(picked, r1.itineraries, before)
        assertEquals(picked, r1.itineraries[now])
        assertTrue(now != before) // five earlier options moved it down the list
        val rem = Reminder.from(r1.itineraries[now], LatLon(31.2437, 34.7976), LatLon(32.0560, 34.7794), UserSettings())!!
        assertEquals(picked.firstTransitLeg!!.start, rem.scheduledBoarding)
    }

    @Test fun `old saved trips still decode and keep cursors offline`() {
        val old = """{"itineraries":[],"walkOnly":null}"""
        val r = kotlinx.serialization.json.Json.decodeFromString(TripResult.serializer(), old)
        assertNull(r.laterCursor)
        val saved = TripResult(first.itineraries, null, "E", "L", 1L)
        assertEquals("L", saved.stillAhead(time)!!.laterCursor)
    }

    private fun Itinerary.shift(sec: Long): Itinerary {
        fun s(t: String) = Instant.parse(t.replace(Regex("""\+00:00$"""), "Z")).plusSeconds(sec).toString()
        fun s(t: String?) = t?.let { s(it) }
        return copy(
            startTime = s(startTime), endTime = s(endTime),
            legs = legs.map { l -> l.copy(startTime = s(l.startTime), endTime = s(l.endTime), scheduledStartTime = s(l.scheduledStartTime), tripId = l.tripId?.let { "$it+$sec" }) },
        )
    }
}
