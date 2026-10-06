package il.transit.core

import il.transit.core.fare.FareProfile
import il.transit.core.fare.PassAdvisor
import il.transit.core.features.ISRAEL
import il.transit.core.history.TripRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZonedDateTime

class PassAdvisorTest {
    private val october = Instant.parse("2026-10-06T09:00:00Z")
    private val september = YearMonth.of(2026, 9)

    /** A trip started at an Israel wall-clock time, with its full fare. */
    private fun trip(local: String, agorot: Int, band: Int = 0, train: Boolean = false) = TripRecord(
        startedAtEpoch = ZonedDateTime.of(LocalDateTime.parse(local), ISRAEL).toEpochSecond(),
        from = "", to = "Work", mode = "TRIP", transitMin = 30, walkMin = 5, transfers = 0,
        totalMin = 40, fareAgorot = agorot, fareBand = band, withTrain = train,
    )

    /** Two yellow rides a day on [days] working days of September. */
    private fun commute(days: Int) = (1..days).flatMap { d ->
        val date = "2026-09-%02d".format(d)
        listOf(trip("${date}T07:30", 800), trip("${date}T17:30", 800))
    }

    @Test fun `no history, no advice`() {
        assertNull(PassAdvisor.advise(emptyList(), FareProfile.REGULAR, october))
    }

    @Test fun `fewer than 10 trips in the month, no advice`() {
        assertNull(PassAdvisor.advise(commute(4) + trip("2026-09-10T12:00", 800), FareProfile.REGULAR, october))
        assertEquals(10, PassAdvisor.advise(commute(5), FareProfile.REGULAR, october)!!.trips)
    }

    @Test fun `a heavy month says the pass would have saved`() {
        // 22 days x 2 x ₪8 = ₪352 against the ₪315 pass.
        val a = PassAdvisor.advise(commute(22), FareProfile.REGULAR, october)!!
        assertEquals(september, a.month)
        assertEquals(35200, a.singleAgorot)
        assertEquals(31500, a.passAgorot)
        assertEquals(3700, a.passSavesAgorot)
        assertFalse(a.withTrain)
    }

    @Test fun `a light month says paying per ride was cheaper`() {
        val a = PassAdvisor.advise(commute(6), FareProfile.REGULAR, october)!!
        assertEquals(9600 - 31500, a.passSavesAgorot)
    }

    @Test fun `the daily cap applies per day`() {
        // Four green rides on one day: 4 x ₪14.5 = ₪58, capped at ₪29.
        val day = (8..11).map { trip("2026-09-01T%02d:00".format(it), 1450, band = 1) }
        val a = PassAdvisor.forMonth(day + commute(5).drop(2), FareProfile.REGULAR, september)!!
        assertEquals(2900 + 8 * 800, a.singleAgorot)
    }

    @Test fun `any train picks the with-train pass of the longest band`() {
        val r = commute(5) + trip("2026-09-20T08:00", 2700, band = 2, train = true)
        val a = PassAdvisor.forMonth(r, FareProfile.REGULAR, september)!!
        assertTrue(a.withTrain)
        assertEquals(46400, a.passAgorot)
    }

    @Test fun `a trip from 23_50 to 00_30 stays on its day, and before 04_00 is the day before`() {
        assertEquals(java.time.LocalDate.of(2026, 9, 30), PassAdvisor.serviceDay(trip("2026-09-30T23:50", 800).startedAt))
        // 1 October 00:30 is still 30 September's service day, so it counts in September.
        val late = trip("2026-10-01T00:30", 800)
        assertEquals(java.time.LocalDate.of(2026, 9, 30), PassAdvisor.serviceDay(late.startedAt))
        assertEquals(11, PassAdvisor.forMonth(commute(5) + late, FareProfile.REGULAR, september)!!.trips)
    }

    @Test fun `only a finished month is advised`() {
        val octoberTrips = (1..5).flatMap { d -> listOf(trip("2026-10-%02dT07:30".format(d), 800), trip("2026-10-%02dT17:30".format(d), 800)) }
        assertNull(PassAdvisor.advise(octoberTrips, FareProfile.REGULAR, october))
        // Early on 1 November (before 04:00) October is not over yet.
        assertNull(PassAdvisor.advise(octoberTrips, FareProfile.REGULAR, Instant.parse("2026-11-01T00:30:00Z")))
        assertEquals(YearMonth.of(2026, 10), PassAdvisor.advise(octoberTrips, FareProfile.REGULAR, Instant.parse("2026-11-01T03:00:00Z"))!!.month)
    }

    @Test fun `the profile discounts fares and pass alike, FREE gives no advice`() {
        val half = PassAdvisor.advise(commute(22), FareProfile.HALF, october)!!
        assertEquals(17600, half.singleAgorot)
        assertEquals(15750, half.passAgorot)
        assertNull(PassAdvisor.advise(commute(22), FareProfile.FREE, october))
    }

    @Test fun `old records without a fare are left out`() {
        val old = TripRecord(ZonedDateTime.of(LocalDateTime.parse("2026-09-15T08:00"), ISRAEL).toEpochSecond(), "", "x", "TRIP", 10, 5, 0)
        assertNull(PassAdvisor.advise(commute(4) + List(5) { old }, FareProfile.REGULAR, october))
    }
}
