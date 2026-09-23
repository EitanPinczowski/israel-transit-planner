package il.transit.core

import il.transit.core.api.Endpoint
import il.transit.core.api.EncodedPolyline
import il.transit.core.api.PlanRequest
import il.transit.core.api.PlanResponse
import il.transit.core.api.Preferences
import il.transit.core.api.StreetModes
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import il.transit.core.geo.MapData
import il.transit.core.geo.StopsViewport
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripQuery
import il.transit.core.present.LegKind
import il.transit.core.present.delayMin
import il.transit.core.present.legColor
import il.transit.core.present.legKind
import il.transit.core.present.minutesUntil
import il.transit.core.present.summarize
import il.transit.core.user.ModeFilter
import il.transit.core.user.SavedPlace
import il.transit.core.user.SavedTrip
import il.transit.core.user.UserJson
import il.transit.core.user.UserSettings
import il.transit.core.user.WalkSpeed
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlannerTest {
    private val from = Endpoint.Coord(LatLon(31.262, 34.801))
    private val to = Endpoint.Coord(LatLon(32.08, 34.78))
    private val a = place("A", LatLon(31.262, 34.801))
    private val b = place("B", LatLon(32.08, 34.78))

    @Test fun `now-search sorts by arrival and applies the user's settings`() = runTest {
        val fake = FakeTransitApi().apply {
            onPlan = {
                PlanResponse(
                    itineraries = listOf(
                        itinerary(leg("BUS", a, b, NOON, NOON.plusSeconds(5000)), transfers = 1),
                        itinerary(leg("RAIL", a, b, NOON.plusSeconds(600), NOON.plusSeconds(4000))),
                    ),
                    direct = listOf(itinerary(leg(StreetModes.WALK, a, b, NOON, NOON.plusSeconds(99_000)))),
                )
            }
        }
        val settings = UserSettings(maxTransfers = 1, maxWalkMin = 10, modeFilter = ModeFilter.TRAINS_ONLY)
        val r = TripPlanner(fake).plan(TripQuery(from, to, settings = settings), now = NOON)

        assertEquals(listOf(4000L, 5000L), r.itineraries.map { it.end.epochSecond - NOON.epochSecond })
        assertNull(r.walkOnly) // a 27-hour walk is not an option
        val q = fake.planRequests.single().toQuery().toMap()
        assertEquals(NOON.toString(), q["time"])
        assertEquals("false", q["arriveBy"])
        assertEquals("RAIL", q["transitModes"])
        assertEquals("1", q["maxTransfers"])
        assertEquals("600", q["maxPreTransitTime"])
        assertEquals("600", q["maxPostTransitTime"])
    }

    @Test fun `arrive-by sends the arrival time and prefers the latest departure`() = runTest {
        val arriveBy = NOON.plusSeconds(7200)
        val fake = FakeTransitApi().apply {
            onPlan = {
                PlanResponse(
                    itineraries = listOf(
                        itinerary(leg("BUS", a, b, NOON, NOON.plusSeconds(3000))),
                        itinerary(leg("RAIL", a, b, NOON.plusSeconds(1800), NOON.plusSeconds(6000))),
                    ),
                    direct = listOf(itinerary(leg(StreetModes.WALK, a, b, NOON, NOON.plusSeconds(1200)))),
                )
            }
        }
        val r = TripPlanner(fake).plan(TripQuery(from, to, TimeMode.ARRIVE_BY, arriveBy), now = NOON)
        assertEquals(NOON.plusSeconds(1800), r.itineraries.first().start)
        assertEquals(1200, r.walkOnly?.duration)
        val q = fake.planRequests.single().toQuery().toMap()
        assertEquals("true", q["arriveBy"])
        assertEquals(arriveBy.toString(), q["time"])
    }

    @Test fun `max walk applies to walk legs only, never overriding a car cap`() {
        val prefs = Preferences(maxWalkSec = 600)
        val car = PlanRequest(from, to, NOON, preTransitModes = listOf(StreetModes.CAR_DROPOFF), maxPreTransitSec = 900, preferences = prefs)
            .toQuery().toMap()
        assertEquals("900", car["maxPreTransitTime"])
        assertEquals("600", car["maxPostTransitTime"])
    }
}

class PresentationTest {
    private val a = place("באר שבע צפון", LatLon(31.262, 34.809))
    private val b = place("תל אביב סבידור", LatLon(32.08, 34.79))

    @Test fun `summary shows Israel-local times, boarding line and drops in-station walks`() {
        val it = itinerary(
            leg(StreetModes.WALK, place("home", LatLon(31.26, 34.80)), a, NOON, NOON.plusSeconds(540)),
            leg("REGIONAL_RAIL", a, b, NOON.plusSeconds(720), NOON.plusSeconds(5820)).copy(displayName = "רכבת ישראל", realTime = true),
            leg(StreetModes.WALK, b, b, NOON.plusSeconds(5820), NOON.plusSeconds(5850)), // 30 s: noise
        )
        val s = summarize(it)
        assertEquals("12:00", s.depart) // 09:00Z is 12:00 in Israel (IDT, UTC+3)
        assertEquals("13:37", s.arrive)
        assertEquals(98, s.durationMin)
        assertEquals(listOf(LegKind.WALK, LegKind.TRAIN), s.chips.map { c -> c.kind })
        assertEquals("רכבת ישראל", s.firstBoarding?.line)
        assertEquals("12:12", s.firstBoarding?.time)
        assertTrue(s.firstBoarding!!.realTime)
        assertEquals(10, s.walkMin)
    }

    @Test fun `leg kinds and colours`() {
        assertEquals(LegKind.LIGHT_RAIL, legKind("TRAM"))
        assertEquals(LegKind.CAR, legKind(StreetModes.CAR_DROPOFF))
        assertEquals(LegKind.TRAIN, legKind("SUBURBAN"))
        val l = leg("BUS", a, b, NOON, NOON.plusSeconds(60))
        assertEquals("#1E88E5", legColor(l))
        assertEquals("#00AA33", legColor(l.copy(routeColor = "00aa33")))
        assertEquals("#1E88E5", legColor(l.copy(routeColor = "zzz")))
    }

    @Test fun `departure rows show line, local time and delay only with real-time data`() {
        val stop = place("רגר", LatLon(31.26, 34.80)).copy(
            departure = "2026-09-23T09:05:00Z",
            scheduledDeparture = "2026-09-23T09:02:00Z",
        )
        val live = il.transit.core.api.StopTime(place = stop, mode = "BUS", realTime = true, headsign = "מרכזית", routeShortName = "5")
        val row = il.transit.core.present.departureRow(live)
        assertEquals("5", row.line)
        assertEquals("12:05", row.time)
        assertEquals(3, row.delayMin)
        assertEquals(LegKind.BUS, row.kind)
        val scheduledOnly = il.transit.core.present.departureRow(live.copy(realTime = false, routeShortName = "", displayName = "רכבת"))
        assertNull(scheduledOnly.delayMin)
        assertEquals("רכבת", scheduledOnly.line)
    }

    @Test fun `delay and countdown`() {
        assertEquals(3, delayMin("2026-09-23T09:03:00Z", "2026-09-23T09:00:00Z"))
        assertEquals(-1, delayMin("2026-09-23T08:59:00Z", "2026-09-23T09:00:00Z"))
        assertNull(delayMin(null, "2026-09-23T09:00:00Z"))
        assertEquals(7, minutesUntil(NOON.plusSeconds(7 * 60 + 30), NOON))
        assertEquals(0, minutesUntil(NOON.minusSeconds(60), NOON))
    }
}

class MapDataTest {
    @Test fun `itinerary GeoJSON has a line per leg in lon-lat order plus stop points`() {
        val a = place("A", LatLon(31.0, 34.0))
        val b = place("B", LatLon(31.5, 34.5))
        val geom: EncodedPolyline = encodePolyline(listOf(LatLon(31.0, 34.0), LatLon(31.2, 34.1), LatLon(31.5, 34.5)))
        val it = itinerary(
            leg(StreetModes.WALK, place("home", LatLon(30.99, 34.0)), a, NOON, NOON.plusSeconds(120)),
            leg("BUS", a, b, NOON.plusSeconds(200), NOON.plusSeconds(900), geom),
        )
        val fc = Json.parseToJsonElement(MapData.itinerary(it)).jsonObject
        val features = fc["features"]!!.jsonArray
        assertEquals(4, features.size) // 2 lines + board/alight points
        val bus = features[1].jsonObject
        val coords = bus["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        assertEquals(3, coords.size)
        assertEquals("34.1", coords[1].jsonArray[0].jsonPrimitive.content) // lon first
        assertEquals("BUS", bus["properties"]!!.jsonObject["kind"]!!.jsonPrimitive.content)
        assertEquals(5, MapData.bounds(it).size) // walk has no geometry (2 points) + bus polyline (3)
    }

    @Test fun `stops viewport fetches only when zoomed in and outside what is loaded`() {
        val view = BBox(LatLon(31.25, 34.79), LatLon(31.26, 34.80))
        assertFalse(StopsViewport.needsFetch(view, 14.0, null))
        assertTrue(StopsViewport.needsFetch(view, 15.5, null))
        val loaded = StopsViewport.fetchBox(view)
        assertFalse(StopsViewport.needsFetch(view, 16.0, loaded))
        val panned = BBox(LatLon(31.27, 34.79), LatLon(31.28, 34.80))
        assertTrue(StopsViewport.needsFetch(panned, 16.0, loaded))
    }
}

class UserDataTest {
    @Test fun `settings map to MOTIS preferences`() {
        val p = UserSettings(maxTransfers = 0, maxWalkMin = 5, modeFilter = ModeFilter.NO_BUSES, walkSpeed = WalkSpeed.SLOW).preferences()
        assertEquals(0, p.maxTransfers)
        assertEquals(300, p.maxWalkSec)
        assertEquals(1.0, p.pedestrianSpeedMps!!, 1e-9)
        assertFalse("BUS" in p.transitModes!!)
        assertNull(UserSettings().preferences().transitModes)
        assertEquals(1.5, UserSettings(peakFactor = 1.5).traffic().peakFactor, 1e-9)
    }

    @Test fun `saved data round-trips and survives garbage or new fields`() {
        val home = SavedPlace("בית", 31.26, 34.80)
        val trips = listOf(SavedTrip("לאוניברסיטה", null, SavedPlace("BGU", 31.262, 34.801)))
        assertEquals(listOf(home), UserJson.decodePlaces(UserJson.encodePlaces(listOf(home))))
        assertEquals(trips, UserJson.decodeTrips(UserJson.encodeTrips(trips)))
        val s = UserSettings(maxTransfers = 2, peakFactor = 1.4)
        assertEquals(s, UserJson.decodeSettings(UserJson.encodeSettings(s)))
        assertEquals(UserSettings(), UserJson.decodeSettings("not json"))
        assertEquals(emptyList<SavedPlace>(), UserJson.decodePlaces(null))
        assertEquals(UserSettings(maxWalkMin = 20), UserJson.decodeSettings("""{"maxWalkMin":20,"aFieldFromTheFuture":1}"""))
    }
}
