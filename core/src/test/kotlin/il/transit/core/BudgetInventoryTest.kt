package il.transit.core

import il.transit.core.api.Endpoint
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.Itinerary
import il.transit.core.api.MotisJson
import il.transit.core.api.Place
import il.transit.core.api.PlanResponse
import il.transit.core.api.Preferences
import il.transit.core.api.ReplayTransitApi
import il.transit.core.api.StopTimesResponse
import il.transit.core.features.BetterStartPlanner
import il.transit.core.features.BetterStartQuery
import il.transit.core.features.DropOffPlanner
import il.transit.core.features.DropOffQuery
import il.transit.core.features.ParkRidePlanner
import il.transit.core.features.ParkRideQuery
import il.transit.core.features.PickUpPlanner
import il.transit.core.features.PickUpQuery
import il.transit.core.features.TrafficProfile
import il.transit.core.geo.LatLon
import il.transit.core.plan.BigStopChanges
import il.transit.core.plan.CarCompare
import il.transit.core.plan.ChainPlanner
import il.transit.core.plan.ChainStop
import il.transit.core.plan.LastRideFinder
import il.transit.core.plan.NightRefresh
import il.transit.core.plan.NightRefresher
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPages
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripQuery
import il.transit.core.present.DayPick
import il.transit.core.present.DayTimetable
import il.transit.core.present.TimetableLoader
import il.transit.core.search.BackupGeocoder
import il.transit.core.search.PlaceSearch
import il.transit.core.user.UserSettings
import il.transit.core.weather.GuardedWeather
import il.transit.core.weather.RainForecast
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase 11 T1: one row per feature entry point, run over its recorded fixtures through a counting
 * [TransitApi], asserting the number of requests it sends is at most its cap. The cap is written here
 * as a literal AND (where the code has one) as the constant it must agree with, so raising a budget is
 * a visible edit in two places, as the skill says it must be (a policy decision, not a code tweak).
 *
 * `tools/check_docs.py` fails CI when a `BUDGET` / `MAX_*` request constant in `core/src/main` is missing
 * from this table (the "constant" column) or from the budget section of the `transitous-api` skill.
 * Later packages add their row here.
 *
 * Counted: requests to Transitous (the [TransitApi]). Rain and the backup geocoders are other services and
 * are counted separately (their own rows say which).
 */
class BudgetInventoryTest {
    private val now = Instant.parse("2026-10-05T04:55:00Z") // Monday 07:55 Israel
    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name.json")!!.readText()
    private fun plan(name: String) = MotisJson.decodeFromString(PlanResponse.serializer(), fixture(name))
    private fun replay() = ReplayTransitApi(Clock.fixed(now, ZoneOffset.UTC)) { fixture(it) }

    private val bgu = LatLon(31.2622, 34.8013)
    private val telAviv = LatLon(32.0839, 34.7983)
    private val meitar = LatLon(31.3236, 34.9381)
    private val rehovot = LatLon(31.8947, 34.8093)

    /** [constant]: "Class.NAME" of the cap constant it must equal or stay under, or "-" when the code has none. */
    private class Row(val feature: String, val constant: String, val cap: Int, val sent: Int)

    private val rows = ArrayList<Row>()

    private fun record(feature: String, constant: String, cap: Int, sent: Int) {
        rows += Row(feature, constant, cap, sent)
        assertTrue("$feature sent $sent requests, its cap is $cap ($constant)", sent <= cap)
    }

    private suspend fun ReplayTransitApi.requests(block: suspend (ReplayTransitApi) -> Unit): Int {
        block(this)
        return calls.get()
    }

    @Test fun `every entry point stays within its cap`() = runBlocking<Unit> {
        // ---- Trip tab ----
        record("Trip plan", "-", 1, replay().requests {
            TripPlanner(it).plan(TripQuery(Endpoint.Coord(bgu), Endpoint.Coord(telAviv)), now)
        })
        // Phase 11 E7: a picked day only changes the time of that same plan (DayPickTest).
        val tomorrow8 = DayPick.at(DayPick.days(now)[1].date, LocalTime.of(8, 0))
        val onDay = TripQuery(Endpoint.Coord(bgu), Endpoint.Coord(telAviv), TimeMode.DEPART_AT, tomorrow8)
        record("Trip plan on a picked day", "-", 1, replay().requests { TripPlanner(it).plan(onDay, now) })

        run {
            val first = plan("plan_pages_first")
            val api = FakeTransitApi().apply {
                onPlan = { first }
                pages = mapOf(
                    first.nextPageCursor!! to plan("plan_pages_later"),
                    first.previousPageCursor!! to plan("plan_pages_earlier"),
                )
            }
            val q = TripQuery(Endpoint.Coord(bgu), Endpoint.Coord(telAviv), TimeMode.DEPART_AT, Instant.parse("2026-10-13T05:00:00Z"), UserSettings())
            val planner = TripPlanner(api)
            val r0 = planner.plan(q)
            val before = api.calls.size
            val r1 = planner.page(q, r0, TripPages.Direction.LATER)
            record("Later (one tap)", "-", 1, api.calls.size - before)
            val before2 = api.calls.size
            planner.page(q, r1, TripPages.Direction.EARLIER)
            record("Earlier (one tap)", "-", 1, api.calls.size - before2)
        }

        // ---- the four car + transit features ----
        record("Better start", "BetterStartPlanner.BUDGET", BetterStartPlanner.BUDGET, replay().requests {
            BetterStartPlanner(it).plan(BetterStartQuery(meitar, telAviv, now, maxDriveMin = 20))
        })
        record("Let me off on the way", "DropOffPlanner.BUDGET", DropOffPlanner.BUDGET, replay().requests {
            DropOffPlanner(it).plan(DropOffQuery(meitar, telAviv, rehovot, now))
        })
        record("Best pick-up point", "PickUpPlanner.BUDGET", PickUpPlanner.BUDGET, replay().requests {
            PickUpPlanner(it).plan(PickUpQuery(telAviv, meitar, now, maxDriveMin = 30))
        })
        run {
            val api = replay()
            val sent = try {
                ParkRidePlanner(api).plan(ParkRideQuery(meitar, telAviv, now)); api.calls.get()
            } catch (e: Exception) { api.calls.get() } // a replay without a station answer still counts what it was asked
            record("Park & ride", "ParkRidePlanner.BUDGET", ParkRidePlanner.BUDGET, sent)
        }

        // ---- errands, car comparison, last trip ----
        record("Errands: more stops than allowed (refused)", "ChainPlanner.MAX_STOPS", 0, replay().requests {
            val stops = List(ChainPlanner.MAX_STOPS + 1) { i -> ChainStop(rehovot, "s$i", 10) }
            try { ChainPlanner(it).plan(bgu, stops, telAviv, now, UserSettings(), "he"); org.junit.Assert.fail("should refuse") } catch (e: IllegalArgumentException) { }
        })
        record("Errands: one plan per leg", "ChainPlanner.BUDGET", ChainPlanner.BUDGET, replay().requests {
            ChainPlanner(it).plan(bgu, List(ChainPlanner.MAX_STOPS) { i -> ChainStop(rehovot, "s$i", 10) }, telAviv, now, UserSettings(), "he")
        })
        record("Car comparison", "CarCompare.BUDGET", CarCompare.BUDGET, replay().requests {
            CarCompare(it).drive(meitar, telAviv, now, TrafficProfile())
        })
        record("Last trip home / last ride", "LastRideFinder.BUDGET", LastRideFinder.BUDGET, replay().requests {
            LastRideFinder(it).find(Endpoint.Coord(bgu), Endpoint.Coord(telAviv), LocalDate.of(2026, 10, 9), Preferences(), "he")
        })

        // ---- background: night refresh trips and boards ----
        record("Night refresh: trips and boards", "NightRefresh.MAX_TRIPS", NightRefresh.MAX_TRIPS + NightRefresh.MAX_STOPS, replay().requests {
            val night = Instant.parse("2026-10-05T20:30:00Z") // Mon 23:30 Israel, inside the window
            val tomorrow = NightRefresh.tomorrow(night)
            val jobs = List(NightRefresh.MAX_TRIPS + 4) { i ->
                NightRefresh.Job(bgu, telAviv, tomorrow.atTime(6, 0).atZone(il.transit.core.features.ISRAEL).toInstant().plusSeconds(i * 600L), "job$i")
            }
            val out = NightRefresher(it).run(
                jobs = { jobs }, lastRunDay = null, now = night, settings = UserSettings(), language = "he",
                stops = List(NightRefresh.MAX_STOPS + 3) { i -> "il-Israel-MOT_$i" }, saveBoard = { _, _ -> }, save = { _, _ -> },
            )
            assertTrue("the run happened", out != null)
        })
        record("Night boards (alone)", "NightRefresh.MAX_STOPS", NightRefresh.MAX_STOPS, replay().requests {
            val night = Instant.parse("2026-10-05T20:30:00Z")
            NightRefresher(it).run(
                jobs = { emptyList() }, lastRunDay = null, now = night, settings = UserSettings(), language = "he",
                stops = List(NightRefresh.MAX_STOPS + 3) { i -> "il-Israel-MOT_$i" }, saveBoard = { _, _ -> }, save = { _, _ -> },
            )
        })

        // ---- big-stop change on opening an option ----
        run {
            val recorded = plan("plan_bigstop_bgu_telaviv")
            val trips = listOf("trip_bigstop_5", "trip_bigstop_370", "trip_bigstop_171")
                .map { MotisJson.decodeFromString(Itinerary.serializer(), fixture(it)) }
                .associateBy { it.legs.first { l -> l.isTransit }.tripId!! }
            val api = FakeTransitApi().apply { onTrip = { id -> trips[id] ?: error("no recording: $id") } }
            val itin = recorded.itineraries.first { it.legs.filter { l -> l.isTransit }.map { l -> l.routeShortName } == listOf("370", "171") }
            BigStopChanges.find(itin, api, bufferMin = null)
            record("Big-stop change (open an option)", "BigStopChanges.BUDGET", BigStopChanges.BUDGET, api.calls.size)
        }

        // ---- day timetable ----
        run {
            val p1 = MotisJson.decodeFromString(StopTimesResponse.serializer(), fixture("stoptimes_day_beersheva_central_p1"))
            val p2 = MotisJson.decodeFromString(StopTimesResponse.serializer(), fixture("stoptimes_day_beersheva_central_p2"))
            val api = FakeTransitApi().apply {
                onStopTimes = { c -> if (c.pageCursor == null) p1 else if (c.pageCursor == p1.nextPageCursor) p2 else p2.copy(nextPageCursor = "LATER|3") }
            }
            val loader = TimetableLoader(api)
            val page = loader.open("il-Israel-MOT_48114", "", "", LocalDate.parse("2026-10-14"), "he")
            record("Day timetable (open)", "DayTimetable.PAGES_PER_OPEN", DayTimetable.PAGES_PER_OPEN, api.calls.size)
            val before = api.calls.size
            loader.more(page, "he")
            record("Day timetable (More)", "-", 1, api.calls.size - before)
        }

        // ---- search ----
        run {
            val api = FakeTransitApi().apply { onGeocode = { listOf(GeocodeMatch("PLACE", "x", "x", 32.0, 34.8)) } }
            PlaceSearch(api, null).search("רגר", "he", null)
            record("Typing search (one search)", "-", 1, api.calls.size)
        }
        run {
            val api = FakeTransitApi()
            val backup = AtomicInteger()
            val counting = object : BackupGeocoder {
                override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
                    backup.incrementAndGet(); return emptyList()
                }
            }
            PlaceSearch(api, counting).search("טבנקין 15 רעננה", "he", null)
            record("Typing search: backup geocoders", "PlaceSearch.BACKUP_PER_SEARCH", PlaceSearch.BACKUP_PER_SEARCH, backup.get())
            assertTrue("Transitous geocode", api.calls.size <= 1)
        }
        run {
            val api = FakeTransitApi()
            val stop = Place("תחנה", 31.0, 34.0, stopId = "s", stopCode = "13907")
            PlaceSearch(api, null, knownStops = { listOf(stop) }).search("13907", "he", null)
            record("Stop code", "-", 0, api.calls.size)
        }
        record("Pin name (twice at one point)", "-", 1, replay().requests {
            val guarded = GuardedTransitApi(it)
            guarded.reverseGeocode(bgu, "he", 1)
            guarded.reverseGeocode(bgu, "he", 1)
        })

        // ---- rain: Open-Meteo, never Transitous ----
        run {
            val weather = AtomicInteger()
            val guard = GuardedWeather({ weather.incrementAndGet(); RainForecast(emptyList()) }, Clock.fixed(now, ZoneOffset.UTC))
            guard.forTrip(now.plusSeconds(3600), bgu)
            guard.forTrip(now.plusSeconds(7200), LatLon(bgu.lat + 0.01, bgu.lon)) // same 0.1° cell: cached
            record("Rain on the walks (Open-Meteo, two searches in one cell)", "GuardedWeather.cell", 1, weather.get())
        }

        println("Request-budget inventory (${rows.size} rows):\n" + rows.joinToString("\n") { "  %-62s %-32s sent %2d / cap %2d".format(it.feature, it.constant, it.sent, it.cap) })
        assertEquals("every row ran", 22, rows.size)
    }

    /** The caps are policy: this fails when one moves, so the change is a reviewed edit (skill `transitous-api`). */
    @Test fun `the caps are the documented numbers`() {
        assertEquals(5, BetterStartPlanner.BUDGET)
        assertEquals(10, DropOffPlanner.BUDGET)
        assertEquals(4, PickUpPlanner.BUDGET)
        assertEquals(5, ParkRidePlanner.BUDGET)
        assertEquals(3, LastRideFinder.BUDGET)
        assertEquals(2, BigStopChanges.BUDGET)
        assertEquals(1, CarCompare.BUDGET)
        assertEquals(3, ChainPlanner.MAX_STOPS)
        assertEquals(4, ChainPlanner.BUDGET)
        assertEquals(6, NightRefresh.MAX_TRIPS)
        assertEquals(3, NightRefresh.MAX_STOPS)
        assertEquals(2, DayTimetable.PAGES_PER_OPEN)
        assertEquals(1, PlaceSearch.BACKUP_PER_SEARCH)
    }
}
