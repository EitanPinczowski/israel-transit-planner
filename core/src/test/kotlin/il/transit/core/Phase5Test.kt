package il.transit.core

import il.transit.core.api.StreetModes
import il.transit.core.geo.LatLon
import il.transit.core.plan.PlanCache
import il.transit.core.plan.TripCacheJson
import il.transit.core.plan.TripResult
import il.transit.core.present.summarize
import il.transit.core.remind.Reminder
import il.transit.core.remind.ReminderLogic
import il.transit.core.remind.ReminderUpdate
import il.transit.core.user.UserJson
import il.transit.core.user.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val home = place("home", LatLon(31.26, 34.80))
private val stop = place("רגר/יצחק", LatLon(31.262, 34.801))
private val campus = place("BGU", LatLon(31.262, 34.803))

/** Walk 5 min, then bus 5 (trip "t5") scheduled at +7 min, [delaySec] late. */
private fun busTrip(delaySec: Long = 0, tripId: String? = "t5", realTime: Boolean = true, cancelled: Boolean = false) = itinerary(
    leg(StreetModes.WALK, home, stop, NOON.plusSeconds(120 + delaySec), NOON.plusSeconds(420 + delaySec)),
    leg("BUS", stop, campus, NOON.plusSeconds(420 + delaySec), NOON.plusSeconds(1200 + delaySec)).copy(
        routeShortName = "5",
        tripId = tripId,
        realTime = realTime,
        scheduledStartTime = NOON.plusSeconds(420).toString(),
        cancelled = cancelled,
    ),
)

class RealTimeSummaryTest {
    @Test fun `chips and boarding carry the delay only when the leg is real-time`() {
        val late = summarize(busTrip(delaySec = 180))
        val bus = late.chips.single { it.label == "5" }
        assertEquals(3, bus.delayMin)
        assertEquals(3, late.firstBoarding?.delayMin)
        assertTrue(late.hasRealTime)
        assertNull(late.chips.first().delayMin) // walking never has a delay

        val scheduled = summarize(busTrip(delaySec = 180, realTime = false))
        assertNull(scheduled.firstBoarding?.delayMin)
        assertFalse(scheduled.hasRealTime)

        assertEquals(0, summarize(busTrip()).firstBoarding?.delayMin)
    }
}

class ReminderTest {
    private val settings = UserSettings(maxTransfers = 1)
    private fun reminder() = Reminder.from(busTrip(), home.latLon, campus.latLon, settings)!!

    @Test fun `a reminder leaves when the itinerary starts and re-checks 15 min earlier`() {
        val r = reminder()
        assertEquals(NOON.plusSeconds(120), r.leaveAt)
        assertEquals(NOON.plusSeconds(120 - 900), r.recheckAt)
        assertEquals("5", r.line)
        assertEquals("12:07", r.boardingTime)
        assertEquals(NOON.plusSeconds(60), ReminderLogic.recheckTime(r, NOON.plusSeconds(60))) // past: re-check now
        assertNull(Reminder.from(itinerary(leg(StreetModes.WALK, home, campus, NOON, NOON.plusSeconds(900))), home.latLon, campus.latLon, settings))
    }

    @Test fun `a delay moves the leave time, matched by trip id`() {
        val update = ReminderLogic.update(reminder(), listOf(busTrip(delaySec = 240)))
        update as ReminderUpdate.Updated
        assertEquals(NOON.plusSeconds(360), update.reminder.leaveAt)
        assertEquals(4, update.shiftedByMin)
        assertEquals(4, update.reminder.boardingDelayMin)
        assertEquals("12:11", update.reminder.boardingTime)
    }

    @Test fun `without trip ids it matches line, stop and scheduled time`() {
        val r = reminder().copy(tripId = null)
        assertTrue(ReminderLogic.update(r, listOf(busTrip(delaySec = 60, tripId = null))) is ReminderUpdate.Updated)
        val otherBus = busTrip(tripId = null).let { it.copy(legs = it.legs.map { l -> if (l.isTransit) l.copy(routeShortName = "3") else l }) }
        assertEquals(ReminderUpdate.Gone, ReminderLogic.update(r, listOf(otherBus)))
    }

    @Test fun `a cancelled or missing trip is Gone`() {
        assertEquals(ReminderUpdate.Gone, ReminderLogic.update(reminder(), listOf(busTrip(cancelled = true))))
        assertEquals(ReminderUpdate.Gone, ReminderLogic.update(reminder(), listOf(busTrip(tripId = "other"))))
        assertEquals(ReminderUpdate.Gone, ReminderLogic.update(reminder(), emptyList()))
    }

    @Test fun `reminders round-trip through the user store codec`() {
        val r = reminder()
        assertEquals(r, UserJson.decodeReminder(UserJson.encodeReminder(r)))
        assertNull(UserJson.decodeReminder(UserJson.encodeReminder(null)))
        assertNull(UserJson.decodeReminder("garbage"))
    }
}

class PlanCacheTest {
    @Test fun `keys round to about 100 m so a few steps away still hits`() {
        val a = PlanCache.key("TRIP", LatLon(31.26211, 34.80149), LatLon(32.08, 34.78))
        val b = PlanCache.key("TRIP", LatLon(31.26239, 34.80121), LatLon(32.08, 34.78))
        assertEquals(a, b)
        assertFalse(a == PlanCache.key("TRIP", LatLon(31.2641, 34.8015), LatLon(32.08, 34.78)))
        assertFalse(a == PlanCache.key("DROP_OFF", LatLon(31.26211, 34.80149), LatLon(32.08, 34.78)))
    }

    @Test fun `least recently used entries fall out`() {
        val c = PlanCache<Int>(capacity = 2)
        c.put("a", 1, NOON)
        c.put("b", 2, NOON)
        c.put("a", 3, NOON) // refresh a
        c.put("c", 4, NOON) // evicts b
        assertEquals(listOf("c", "a"), c.all.map { it.key })
        assertEquals(3, c.get("a")?.value)
        assertNull(c.get("b"))
    }

    @Test fun `trip cache survives a round trip and garbage`() {
        val c = PlanCache<TripResult>()
        c.put("k", TripResult(listOf(busTrip(delaySec = 60)), walkOnly = null), NOON)
        val restored = TripCacheJson.decode(TripCacheJson.encode(c.all))
        assertEquals(c.all, restored)
        assertTrue(TripCacheJson.decode("{not json").isEmpty())
    }
}
