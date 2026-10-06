package il.transit.core

import il.transit.core.geo.LatLon
import il.transit.core.history.History
import il.transit.core.history.TripEnd
import il.transit.core.history.TripRecord
import il.transit.core.history.UsualTrip
import il.transit.core.user.PlaceRoutine
import il.transit.core.user.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZonedDateTime
import il.transit.core.features.ISRAEL

class UsualTripTest {
    private val now = Instant.parse("2026-10-06T09:00:00Z")
    private val home = LatLon(31.262, 34.801)
    private val work = LatLon(32.080, 34.790)
    private val homeEnd = TripEnd(home, "")
    private val workEnd = TripEnd(work, "Work")

    private fun rec(daysAgo: Long, min: Int, from: LatLon? = home, to: LatLon? = work, toName: String = "Work") = TripRecord(
        startedAtEpoch = now.minusSeconds(daysAgo * 86_400).epochSecond,
        from = "", to = toName, mode = "TRIP", transitMin = min - 10, walkMin = 10, transfers = 0,
        totalMin = min, fromCell = from?.let(TripRecord::cellOf), toCell = to?.let(TripRecord::cellOf),
    )

    /** A record started at an Israel wall-clock time. */
    private fun at(local: String, min: Int) = TripRecord(
        startedAtEpoch = ZonedDateTime.of(java.time.LocalDateTime.parse(local), ISRAEL).toEpochSecond(),
        from = "", to = "Work", mode = "TRIP", transitMin = min, walkMin = 0, transfers = 0,
        totalMin = min, toCell = TripRecord.cellOf(work),
    )

    @Test fun `no history, no usual`() {
        assertNull(UsualTrip.usual(emptyList(), homeEnd, workEnd, now))
    }

    @Test fun `exactly three records are enough, two are not`() {
        val three = listOf(rec(1, 50), rec(2, 52), rec(3, 60))
        assertEquals(52, UsualTrip.usual(three, homeEnd, workEnd, now)!!.medianMin)
        assertNull(UsualTrip.usual(three.take(2), homeEnd, workEnd, now))
    }

    @Test fun `a 25-hour Shabbat record does not move the median`() {
        val r = listOf(rec(1, 50), rec(2, 52), rec(3, 54), rec(4, 25 * 60))
        assertEquals(53, UsualTrip.usual(r, homeEnd, workEnd, now)!!.medianMin)
    }

    @Test fun `only the last 90 days count`() {
        val r = listOf(rec(1, 50), rec(2, 52), rec(91, 60))
        assertNull(UsualTrip.usual(r, homeEnd, workEnd, now))
    }

    @Test fun `same trip means both ends within about 300 m`() {
        val near = LatLon(work.lat + 0.002, work.lon) // ~220 m
        val far = LatLon(work.lat + 0.004, work.lon) // ~450 m
        val r = listOf(rec(1, 50), rec(2, 52, to = near), rec(3, 54, to = far))
        assertNull(UsualTrip.usual(r, homeEnd, workEnd, now))
        assertEquals(51, UsualTrip.usual(r + rec(4, 51), homeEnd, workEnd, now)!!.medianMin)
        // The reverse direction is another trip.
        assertNull(UsualTrip.usual(listOf(rec(1, 50, home, work), rec(2, 50, home, work), rec(3, 50, home, work)), workEnd, homeEnd, now))
    }

    @Test fun `old records without a door-to-door time are left out`() {
        // Riding + walking only, no waits: three at 40 min would make a 95 min option read "+55".
        val old = { d: Long -> TripRecord(now.minusSeconds(d * 86_400).epochSecond, "Home", "Work", "TRIP", 35, 5, 0) }
        assertNull(UsualTrip.usual(listOf(old(1), old(2), old(3)), TripEnd(home, "Home"), workEnd, now))
    }

    @Test fun `a record with no coordinates matches by saved place names, never by blank ones`() {
        val named = { d: Long, m: Int -> TripRecord(now.minusSeconds(d * 86_400).epochSecond, "Home", "Work", "TRIP", m - 5, 5, 0, totalMin = m) }
        val r = listOf(named(1, 40), named(2, 44), named(3, 48))
        assertEquals(44, UsualTrip.usual(r, TripEnd(home, "Home"), workEnd, now)!!.medianMin)
        assertNull(UsualTrip.usual(r, homeEnd, workEnd, now))
    }

    @Test fun `best leave time is the slot with the shortest median, at least two records each`() {
        val routine = PlaceRoutine(listOf(7, 1, 2, 3, 4), 7 * 60, 10 * 60)
        val place = SavedPlace("Work", work.lat, work.lon, routine)
        val r = listOf(
            at("2026-10-04T07:16", 41), at("2026-10-05T07:20", 43), at("2026-09-28T07:29", 40), // Sun, Mon, Mon: 07:15
            at("2026-10-04T07:50", 55), at("2026-10-05T07:46", 58), // 07:45
            at("2026-10-01T08:05", 30), // a lone 08:00 does not count
            at("2026-10-03T07:20", 20), at("2026-10-03T07:25", 20), // Saturday is not a routine day
        )
        val best = UsualTrip.bestLeave(r, routine, place)!!
        assertEquals(7 * 60 + 15, best.leaveMin)
        assertEquals(41, best.medianMin)
        assertEquals(3, best.trips)
        assertNull(UsualTrip.bestLeave(r.take(1), routine, place))
    }

    @Test fun `old history json still decodes, with the new fields null`() {
        val old = """[{"startedAtEpoch":1790000000,"from":"","to":"BGU","mode":"TRIP","transitMin":30,"walkMin":8,"transfers":1,"savedMin":null}]"""
        val r = History.decode(old).single()
        assertEquals("BGU", r.to)
        assertNull(r.totalMin)
        assertNull(r.fareAgorot)
        assertNull(r.toCell)
        assertEquals(r, History.decode(History.encode(listOf(r))).single())
    }

    @Test fun `a record from an itinerary fills the new fields`() {
        val bus = leg("BUS", place("a", home), place("b", LatLon(home.lat + 0.05, home.lon)), now, now.plusSeconds(1200))
        val r = TripRecord.from(itinerary(bus), "", "Work", "TRIP", now, null)
        assertEquals(800, r.fareAgorot)
        assertEquals(0, r.fareBand)
        assertEquals(false, r.withTrain)
        assertEquals("31.262,34.801", r.fromCell)
        assertEquals(TripRecord.parseCell(r.toCell), LatLon(31.312, 34.801))
    }
}
