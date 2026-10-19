package il.transit.core

import il.transit.core.api.Endpoint
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.MotisJson
import il.transit.core.api.ReplayTransitApi
import il.transit.core.api.StreetModes
import il.transit.core.geo.LatLon
import il.transit.core.plan.MissedIt
import il.transit.core.plan.MissedIt.Fix
import il.transit.core.plan.MissedIt.Outcome
import il.transit.core.plan.MissedIt.Target
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripSort
import il.transit.core.remind.Reminder
import il.transit.core.user.UserSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Phase 11 E2: "Missed it? Plan from here" — 1 `plan` per tap, never more. */
class MissedItTest {
    private val now = Instant.parse("2026-10-05T04:46:00Z") // Monday 07:46 Israel
    private val bgu = LatLon(31.2622, 34.8013)
    private val telAviv = LatLon(32.0839, 34.7983)
    private val toTelAviv = Target("Tel Aviv", telAviv)
    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name.json")!!.readText()

    private class Setup(val replay: ReplayTransitApi, val missed: MissedIt)

    /** Through the guard, as in the app: a repeated query is answered from its cache. */
    private fun setup(clockAt: Instant = now): Setup {
        val replay = ReplayTransitApi(Clock.fixed(clockAt, ZoneOffset.UTC)) { fixture(it) }
        val guarded = GuardedTransitApi(replay, Clock.fixed(clockAt, ZoneOffset.UTC))
        return Setup(replay, MissedIt(TripPlanner(guarded)))
    }

    private val fresh = Fix(bgu, ageSec = 30, accuracyM = 20f)

    @Test fun `a fresh fix plans once, from here to the same destination, leaving now`() = runBlocking {
        val s = setup()
        val out = s.missed.tap(toTelAviv, fresh, now, UserSettings(), "he")
        assertEquals(1, s.replay.calls.get())
        out as Outcome.Planned
        assertEquals(bgu, out.from)
        assertEquals(now, out.at)
        assertFalse(out.wasArriveBy)
        assertTrue("options to show", out.result.itineraries.isNotEmpty())
        assertTrue("nothing that already left", out.result.itineraries.all { !it.start.isBefore(now) })
    }

    @Test fun `a stale or rough fix waits for a new one`() = runBlocking {
        val s = setup()
        var asked = 0
        val stale = Fix(LatLon(31.0, 34.0), ageSec = MissedIt.MAX_FIX_AGE_SEC + 1, accuracyM = 10f)
        val out = s.missed.tap(toTelAviv, stale, now, UserSettings(), "he") { asked++; fresh }
        assertEquals(1, asked)
        assertEquals(bgu, (out as Outcome.Planned).from)
        assertEquals(1, s.replay.calls.get())

        val rough = Fix(bgu, ageSec = 5, accuracyM = MissedIt.MAX_ACCURACY_M + 1)
        assertFalse(MissedIt.usable(rough))
        assertFalse(MissedIt.usable(Fix(bgu, ageSec = 5, accuracyM = null)))
        assertTrue(MissedIt.usable(Fix(bgu, ageSec = MissedIt.MAX_FIX_AGE_SEC, accuracyM = MissedIt.MAX_ACCURACY_M)))
    }

    @Test fun `no fix (no permission, no GPS) asks for the start and sends nothing`() = runBlocking {
        val s = setup()
        assertEquals(Outcome.NeedStart, s.missed.tap(toTelAviv, null, now, UserSettings(), "he"))
        // A stale fix and no new one either.
        assertEquals(Outcome.NeedStart, s.missed.tap(toTelAviv, fresh.copy(ageSec = 600), now, UserSettings(), "he") { null })
        // A new fix that is still too rough does not count.
        assertEquals(Outcome.NeedStart, s.missed.tap(toTelAviv, null, now, UserSettings(), "he") { fresh.copy(accuracyM = 500f) })
        assertEquals(0, s.replay.calls.get())
    }

    @Test fun `waiting for a fix gives up after the limit`() = runBlocking {
        val s = setup()
        kotlinx.coroutines.test.runTest {
            val r = s.missed.tap(toTelAviv, null, now, UserSettings(), "he") { kotlinx.coroutines.awaitCancellation() }
            assertEquals(MissedIt.WAIT_MS, testScheduler.currentTime)
            assertEquals(Outcome.NeedStart, r)
        }
        assertEquals(0, s.replay.calls.get())
    }

    @Test fun `at the destination - already there, no request`() = runBlocking {
        val s = setup()
        val near = Fix(LatLon(32.0849, 34.7983), ageSec = 10, accuracyM = 15f) // ~110 m
        assertEquals(Outcome.AlreadyThere, s.missed.tap(toTelAviv, near, now, UserSettings(), "he"))
        assertEquals(0, s.replay.calls.get())
        val further = Fix(LatLon(32.0839 + 0.0018, 34.7983), ageSec = 10, accuracyM = 15f) // ~200 m
        assertTrue(s.missed.tap(toTelAviv, further, now, UserSettings(), "he") is Outcome.Planned)
    }

    @Test fun `an arrive-by trip becomes depart-now, and the banner says so`() = runBlocking {
        val api = FakeTransitApi().apply { onPlan = { MotisJson.decodeFromString(il.transit.core.api.PlanResponse.serializer(), fixture("plan_bgu_telaviv")) } }
        val out = MissedIt(TripPlanner(api)).tap(toTelAviv.copy(arriveBy = true), fresh, now, UserSettings(), "he")
        assertTrue((out as Outcome.Planned).wasArriveBy)
        val req = api.planRequests.single()
        assertFalse(req.arriveBy)
        assertEquals(now, req.time)
    }

    @Test fun `from a car tab it is a plain Trip to the final destination`() = runBlocking {
        val api = FakeTransitApi().apply { onPlan = { MotisJson.decodeFromString(il.transit.core.api.PlanResponse.serializer(), fixture("plan_bgu_telaviv")) } }
        MissedIt(TripPlanner(api)).tap(toTelAviv, fresh, now, UserSettings(), "he")
        val req = api.planRequests.single()
        assertTrue(req.preTransitModes.none { it in StreetModes.CAR_LIKE })
        assertTrue(req.postTransitModes.none { it in StreetModes.CAR_LIKE })
        assertFalse(req.directOnly)
        assertNull(req.pageCursor)
        val q = MissedIt.query(toTelAviv, bgu, UserSettings(), "he")
        assertEquals(TimeMode.NOW, q.timeMode)
        assertEquals(Endpoint.Coord(telAviv), q.to)
    }

    @Test fun `the same settings and sort as the user's Trip search`() = runBlocking {
        val mine = UserSettings(tripSort = TripSort.LEAST_WALKING, maxWalkMin = 7)
        val api = FakeTransitApi().apply { onPlan = { MotisJson.decodeFromString(il.transit.core.api.PlanResponse.serializer(), fixture("plan_bgu_telaviv")) } }
        MissedIt(TripPlanner(api)).tap(toTelAviv, fresh, now, mine, "en")
        // The very request the Trip tab sends for the same search, settings included.
        val trip = FakeTransitApi().apply { onPlan = api.onPlan }
        TripPlanner(trip).plan(il.transit.core.plan.TripQuery(Endpoint.Coord(bgu), Endpoint.Coord(telAviv), TimeMode.NOW, null, mine, "en"), now)
        assertEquals(trip.planRequests.single(), api.planRequests.single())
        assertEquals(mine, MissedIt.query(toTelAviv, bgu, mine, "en").settings)
    }

    @Test fun `a second tap within 60 s costs 0`() = runBlocking {
        val s = setup()
        s.missed.tap(toTelAviv, fresh, now, UserSettings(), "he")
        // 40 s later, a few metres on: the same query, answered by the guard's cache.
        val moved = fresh.copy(at = LatLon(bgu.lat + 0.0003, bgu.lon))
        val again = s.missed.tap(toTelAviv, moved, now.plusSeconds(40), UserSettings(), "he")
        assertEquals(1, s.replay.calls.get())
        assertEquals(bgu, (again as Outcome.Planned).from)
        assertEquals(now, again.at)
    }

    @Test fun `a later tap, or from elsewhere, plans again`() = runBlocking {
        val s = setup()
        s.missed.tap(toTelAviv, fresh, now, UserSettings(), "he")
        s.missed.tap(toTelAviv, fresh, now.plusSeconds(MissedIt.REUSE_SEC + 5), UserSettings(), "he")
        assertEquals(2, s.replay.calls.get())
        val far = fresh.copy(at = LatLon(bgu.lat + 0.01, bgu.lon)) // ~1.1 km
        val out = s.missed.tap(toTelAviv, far, now.plusSeconds(MissedIt.REUSE_SEC + 10), UserSettings(), "he")
        assertEquals(far.at, (out as Outcome.Planned).from)
        assertEquals(3, s.replay.calls.get())
    }

    @Test fun `only the missed reminder ends, never a later one`() {
        val r = Reminder(31.0, 34.0, 32.0, 34.7, UserSettings(), null, "5", "Stop", now.epochSecond, leaveAtEpoch = now.epochSecond + 600)
        assertFalse("still ahead: a later trip", MissedIt.endsReminder(r, now))
        assertFalse("tomorrow's", MissedIt.endsReminder(r.copy(leaveAtEpoch = now.epochSecond + 86_400), now))
        assertTrue("counting down", MissedIt.endsReminder(r.copy(counting = true), now))
        assertTrue("its leave time has passed", MissedIt.endsReminder(r.copy(leaveAtEpoch = now.epochSecond - 60), now))
        assertFalse(MissedIt.endsReminder(null, now))
    }
}
