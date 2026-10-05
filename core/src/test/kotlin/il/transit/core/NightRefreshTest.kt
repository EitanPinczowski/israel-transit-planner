package il.transit.core

import il.transit.core.api.PlanResponse
import il.transit.core.api.StreetModes
import il.transit.core.features.ISRAEL
import il.transit.core.geo.LatLon
import il.transit.core.history.TripRecord
import il.transit.core.plan.NightRefresh
import il.transit.core.plan.NightRefresher
import il.transit.core.plan.PlanCache
import il.transit.core.plan.TripCacheJson
import il.transit.core.plan.TripResult
import il.transit.core.plan.stillAhead
import il.transit.core.user.Home
import il.transit.core.user.PlaceRoutine
import il.transit.core.user.SavedPlace
import il.transit.core.user.SavedTrip
import il.transit.core.user.UserJson
import il.transit.core.user.UserSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class NightRefreshTest {
    private val sunToThu = listOf(7, 1, 2, 3, 4)
    private val home = SavedPlace("Home", 31.279, 34.82)
    private val uni = SavedPlace("University", 31.262, 34.8015, PlaceRoutine(sunToThu, 7 * 60, 10 * 60))
    private val gym = SavedPlace("Gym", 31.25, 34.79, PlaceRoutine(listOf(1, 3), 18 * 60, 20 * 60)) // Mon, Wed
    private val shul = SavedPlace("Shul", 31.27, 34.81, PlaceRoutine(listOf(6), 9 * 60, 12 * 60)) // Saturday
    private val mom = SavedPlace("Mom", 31.9, 34.8)
    private val places = listOf(home, uni, gym, shul, mom)

    /** A local Israel time, e.g. "2026-10-04T23:50". */
    private fun il(local: String): Instant = LocalDateTime.parse(local).atZone(ISRAEL).toInstant()

    private val monday = LocalDate.parse("2026-10-05")
    private val sunday = LocalDate.parse("2026-10-04")
    private val saturday = LocalDate.parse("2026-10-10")

    // --- which day ------------------------------------------------------------------------

    @Test fun `23-50 and 00-10 plan the same service day`() {
        assertEquals(monday, NightRefresh.tomorrow(il("2026-10-04T23:50")))
        assertEquals(monday, NightRefresh.tomorrow(il("2026-10-05T00:10")))
        assertEquals(monday, NightRefresh.tomorrow(il("2026-10-05T03:59")))
        assertEquals(monday, NightRefresh.tomorrow(il("2026-10-05T04:00"))) // this morning
        assertEquals(monday.plusDays(1), NightRefresh.tomorrow(il("2026-10-05T06:00"))) // daytime: the next day
    }

    @Test fun `a run at 04-30 plans today's morning, not the day after`() {
        assertEquals(sunday, NightRefresh.tomorrow(il("2026-10-04T04:30")))
    }

    @Test fun `a 04-30 run after a 00-10 run the same night sends nothing`() = runBlocking {
        val api = FakeTransitApi().also(::answering)
        val first = NightRefresher(api).run({ eightTrips() }, null, il("2026-10-05T00:10"), UserSettings(), "he") { _, _ -> }!!
        assertEquals(6, api.calls.size)
        assertNull(NightRefresher(api).run({ eightTrips() }, first.day, il("2026-10-05T04:30"), UserSettings(), "he") { _, _ -> error("no") })
        assertEquals(6, api.calls.size)
    }

    @Test fun `a Sunday routine is seen from Saturday night`() {
        val day = NightRefresh.tomorrow(il("2026-10-03T23:30")) // Saturday night
        assertEquals(sunday, day)
        assertEquals(listOf("University"), NightRefresh.jobs(places, emptyList(), home, emptyList(), day).map { it.label })
    }

    @Test fun `the worker only runs between 22-00 and 06-00`() {
        assertTrue(NightRefresh.inWindow(il("2026-10-04T22:00")))
        assertTrue(NightRefresh.inWindow(il("2026-10-05T02:14")))
        assertTrue(NightRefresh.inWindow(il("2026-10-05T05:59")))
        assertFalse(NightRefresh.inWindow(il("2026-10-05T06:00")))
        assertFalse(NightRefresh.inWindow(il("2026-10-05T14:00")))
        assertFalse(NightRefresh.inWindow(il("2026-10-04T21:59")))
    }

    @Test fun `the flex window opens at midnight Israel time`() {
        for (now in listOf("2026-10-05T14:00", "2026-10-05T03:00", "2026-10-05T23:59", "2026-10-05T05:30")) {
            val start = il(now)
            val delay = NightRefresh.initialDelay(start)
            assertFalse(delay.isNegative)
            assertTrue(delay < NightRefresh.PERIOD)
            // The first run lands in [delay + 19 h, delay + 24 h): 00:00–05:00.
            val flexOpens = start.plus(delay).plus(NightRefresh.PERIOD.minus(NightRefresh.FLEX)).atZone(ISRAEL)
            assertEquals(now, 0, flexOpens.hour)
            assertEquals(now, 0, flexOpens.minute)
        }
    }

    // --- which trips ----------------------------------------------------------------------

    @Test fun `the job is enqueued only when some night has a trip`() {
        val now = il("2026-10-05T12:00")
        assertTrue(NightRefresh.anyJobs(listOf(home, shul), emptyList(), home, emptyList(), now)) // Saturday only
        assertFalse(NightRefresh.anyJobs(listOf(home, shul), emptyList(), null, emptyList(), now)) // no Home
        assertFalse(NightRefresh.anyJobs(listOf(home, mom), emptyList(), home, emptyList(), now)) // no routine
        assertFalse(NightRefresh.anyJobs(emptyList(), listOf(SavedTrip("To Mom", home, mom)), home, emptyList(), now)) // no time
    }

    @Test fun `routine places from Home at the routine start`() {
        val jobs = NightRefresh.jobs(places, emptyList(), home, emptyList(), monday)
        assertEquals(listOf("University", "Gym"), jobs.map { it.label }) // earliest first
        assertEquals(il("2026-10-05T07:00"), jobs[0].at)
        assertEquals(home.latLon, jobs[0].from)
        assertEquals(il("2026-10-05T18:00"), jobs[1].at)
    }

    @Test fun `a 07-00 routine on the day DST ends is still 07-00 local`() {
        val uni = SavedPlace("Uni", 31.26, 34.80, PlaceRoutine(listOf(7), 7 * 60, 10 * 60))
        val job = NightRefresh.jobs(listOf(home, uni), emptyList(), home, emptyList(), LocalDate.parse("2026-10-25")).single()
        assertEquals(LocalTime.of(7, 0), job.at.atZone(ISRAEL).toLocalTime())
    }

    @Test fun `a 07-00 routine on the day DST starts is still 07-00 local`() {
        val uni = SavedPlace("Uni", 31.26, 34.80, PlaceRoutine(listOf(5), 7 * 60, 10 * 60))
        val job = NightRefresh.jobs(listOf(home, uni), emptyList(), home, emptyList(), LocalDate.parse("2026-03-27")).single()
        assertEquals(LocalTime.of(7, 0), job.at.atZone(ISRAEL).toLocalTime())
    }

    @Test fun `Shabbat is planned anyway`() {
        assertEquals(listOf("Shul"), NightRefresh.jobs(places, emptyList(), home, emptyList(), saturday).map { it.label })
    }

    @Test fun `no Home skips routine places and trips from my location`() {
        val trips = listOf(SavedTrip("To uni", null, uni), SavedTrip("Mom to uni", mom, uni))
        val jobs = NightRefresh.jobs(places, trips, null, emptyList(), monday)
        assertEquals(listOf("Mom to uni"), jobs.map { it.label })
        assertEquals(mom.latLon, jobs[0].from)
    }

    @Test fun `a saved trip takes the routine time, else the last history time, else nothing`() {
        // saveTrip copies the place without its routine: the routine is looked up by name.
        val toUni = SavedTrip("Uni from Mom", mom, uni.copy(routine = null))
        val toMom = SavedTrip("To Mom", null, mom)
        val noTime = SavedTrip("Mom to gym", mom, SavedPlace("Pool", 31.0, 34.0))
        val history = listOf(
            record(il("2026-09-30T16:45"), "", "Mom"),
            record(il("2026-10-01T17:20"), "Home", "Mom"), // the latest
            record(il("2026-10-02T09:00"), "Elsewhere", "Mom"), // another trip
        )
        val jobs = NightRefresh.jobs(places, listOf(toUni, toMom, noTime), home, history, monday).associateBy { it.label }
        assertEquals(il("2026-10-05T07:00"), jobs.getValue("Uni from Mom").at)
        assertEquals(il("2026-10-05T17:20"), jobs.getValue("To Mom").at)
        assertEquals(home.latLon, jobs.getValue("To Mom").from)
        assertFalse("Mom to gym" in jobs)
    }

    @Test fun `dedupe by cache key, earliest first, at most six`() {
        // Home → University twice (routine and a saved trip from Home): one job.
        val dup = SavedTrip("Uni", home, uni)
        val many = (1..8).map { i -> SavedPlace("P$i", 31.0 + i / 100.0, 34.7, PlaceRoutine(listOf(1), 6 * 60 + i, 9 * 60)) }
        val jobs = NightRefresh.jobs(listOf(home, uni) + many, listOf(dup), home, emptyList(), monday)
        assertEquals(NightRefresh.MAX_TRIPS, jobs.size)
        assertEquals(jobs.sortedBy { it.at }, jobs)
        assertEquals(jobs.size, jobs.map { it.key }.toSet().size)
        assertEquals(listOf("P1", "P2", "P3", "P4", "P5", "P6"), jobs.map { it.label })

        val two = NightRefresh.jobs(listOf(home, uni), listOf(dup), home, emptyList(), monday)
        assertEquals(1, two.size)
    }

    @Test fun `the cache key is the Trip tab's NOW key`() {
        val job = NightRefresh.jobs(places, emptyList(), home, emptyList(), monday).first()
        assertEquals(PlanCache.key("TRIP-NOW", home.latLon, uni.latLon), job.key)
        // "My location" a few steps from the saved Home still hits.
        assertEquals(job.key, PlanCache.key("TRIP-NOW", LatLon(home.lat + 0.0002, home.lon - 0.0002), uni.latLon))
    }

    // --- the run --------------------------------------------------------------------------

    private fun eightTrips(): List<NightRefresh.Job> =
        (1..8).map { i -> NightRefresh.Job(home.latLon, LatLon(31.0 + i / 100.0, 34.7), il("2026-10-05T07:00").plusSeconds(60L * i), "T$i") }

    private fun answering(api: FakeTransitApi) {
        api.onPlan = { req ->
            val a = place("A", (req.from as il.transit.core.api.Endpoint.Coord).at, "s1")
            val b = place("B", (req.to as il.transit.core.api.Endpoint.Coord).at, "s2")
            PlanResponse(listOf(itinerary(leg("BUS", a, b, req.time, req.time.plusSeconds(1800)))))
        }
    }

    @Test fun `eight eligible trips send six requests, a second run that night sends none`() = runBlocking {
        val api = FakeTransitApi().also(::answering)
        val saved = mutableMapOf<String, TripResult>()
        val first = NightRefresher(api).run({ eightTrips() }, null, il("2026-10-05T02:14"), UserSettings(), "he") { k, r -> saved[k] = r }!!
        assertEquals(6, api.calls.size)
        assertEquals(6, first.requests)
        assertEquals(6, first.planned)
        assertEquals(6, saved.size)
        assertEquals(monday, first.day)
        // Departure times are tomorrow's, not "now".
        assertEquals(il("2026-10-05T07:01"), api.planRequests[0].time)

        val again = NightRefresher(api).run({ eightTrips() }, first.day, il("2026-10-05T03:30"), UserSettings(), "he") { _, _ -> error("no") }
        assertNull(again)
        assertEquals(6, api.calls.size)
    }

    @Test fun `outside the window nothing is sent`() = runBlocking {
        val api = FakeTransitApi().also(::answering)
        assertNull(NightRefresher(api).run({ eightTrips() }, null, il("2026-10-05T14:00"), UserSettings(), "he") { _, _ -> })
        assertTrue(api.calls.isEmpty())
    }

    @Test fun `a failed trip is skipped, an empty answer is not cached`() = runBlocking {
        val api = FakeTransitApi()
        var n = 0
        api.onPlan = { req ->
            n++
            when (n) {
                1 -> throw java.io.IOException("no signal")
                2 -> PlanResponse() // Shabbat: nothing
                else -> {
                    val a = place("A", (req.from as il.transit.core.api.Endpoint.Coord).at, "s1")
                    PlanResponse(listOf(itinerary(leg("BUS", a, a, req.time, req.time.plusSeconds(600)))))
                }
            }
        }
        val saved = mutableListOf<String>()
        val out = NightRefresher(api).run({ eightTrips().take(3) }, null, il("2026-10-05T01:00"), UserSettings(), "he") { k, _ -> saved += k }!!
        assertEquals(3, api.calls.size) // no retry
        assertEquals(1, out.planned)
        assertEquals(listOf(eightTrips()[2].key), saved)
    }

    // --- the cache ------------------------------------------------------------------------

    @Test fun `past options are dropped from an offline entry`() {
        val a = place("A", home.latLon, "s1")
        val b = place("B", uni.latLon, "s2")
        val t0 = il("2026-10-05T07:00")
        fun option(walkMin: Long, busAt: Instant) = itinerary(
            leg(StreetModes.WALK, a, a, busAt.minusSeconds(walkMin * 60), busAt),
            leg("BUS", a, b, busAt, busAt.plusSeconds(1800)),
        )
        val r = TripResult(listOf(option(5, t0), option(5, t0.plusSeconds(900)), option(5, t0.plusSeconds(1800))), walkOnly = null)
        // 07:12: the 07:00 bus has left; the 07:15 one is still ahead though its walk began at 07:10.
        assertEquals(2, r.stillAhead(il("2026-10-05T07:12"))!!.itineraries.size)
        assertNull(r.stillAhead(il("2026-10-05T08:00")))
        val walk = itinerary(leg(StreetModes.WALK, a, b, t0, t0.plusSeconds(2400)))
        assertEquals(walk, r.copy(walkOnly = walk).stillAhead(il("2026-10-05T08:00"))!!.walkOnly)
    }

    @Test fun `night entries are marked and old files still decode`() {
        val cache = PlanCache<TripResult>()
        val r = TripResult(emptyList(), null)
        cache.put("a", r, Instant.ofEpochSecond(100))
        cache.put("b", r, Instant.ofEpochSecond(200), night = true)
        val back = TripCacheJson.decode(TripCacheJson.encode(cache.all))
        assertEquals(listOf(true, false), back.map { it.night })
        val old = """[{"key":"a","savedAtEpoch":100,"result":{"itineraries":[]}}]"""
        assertEquals(false, TripCacheJson.decode(old).single().night)
    }

    @Test fun `last night is only last night`() {
        val e = PlanCache.Entry("k", il("2026-10-05T02:14"), 0, night = true)
        assertTrue(e.isLastNight(il("2026-10-05T07:30")))
        assertFalse(e.isLastNight(il("2026-10-06T07:30"))) // the next night was missed
        assertFalse(e.copy(night = false).isLastNight(il("2026-10-05T07:30")))
    }

    @Test fun `six night entries leave the user's ten searches in the cache`() {
        val cache = PlanCache<Int>()
        repeat(10) { cache.put("user$it", it, Instant.ofEpochSecond(it.toLong())) }
        repeat(NightRefresh.MAX_TRIPS) { cache.put("night$it", it, Instant.ofEpochSecond(100L + it), night = true) }
        assertEquals(16, cache.all.size)
        assertTrue((0 until 10).all { cache.get("user$it") != null })
    }

    // --- settings and Home -----------------------------------------------------------------

    @Test fun `settings round-trip, and old settings get the defaults`() {
        val s = UserSettings(homePlace = "בית", homeOffered = true, nightRefresh = false)
        assertEquals(s, UserJson.decodeSettings(UserJson.encodeSettings(s)))
        val old = UserJson.decodeSettings("""{"maxWalkMin":10}""")
        assertNull(old.homePlace)
        assertTrue(old.nightRefresh)
        assertFalse(old.homeOffered)
    }

    @Test fun `Home is a saved place, offered once by name, cleared on delete`() {
        val bayit = SavedPlace("בית", 31.0, 34.0)
        val s = UserSettings()
        assertNull(Home.of(s, listOf(bayit)))
        assertEquals(bayit, Home.suggestion(s, listOf(mom, bayit)))
        assertEquals(home, Home.suggestion(s, listOf(home)))
        assertNull(Home.suggestion(s.copy(homeOffered = true), listOf(bayit)))
        assertNull(Home.suggestion(s, listOf(mom)))

        val set = s.copy(homePlace = "Mom")
        assertEquals(mom, Home.of(set, places))
        assertNull(Home.suggestion(set, places)) // a Home is set: nothing to offer
        assertNull(Home.of(set, listOf(home))) // the place is gone
        assertNull(Home.afterDelete(set, mom).homePlace)
        assertEquals("Mom", Home.afterDelete(set, uni).homePlace)
        assertEquals("Ima", Home.afterRename(set, "Mom", "Ima").homePlace)
    }

    private fun record(at: Instant, from: String, to: String) =
        TripRecord(at.epochSecond, from, to, "TRIP", transitMin = 20, walkMin = 5, transfers = 0)

}
