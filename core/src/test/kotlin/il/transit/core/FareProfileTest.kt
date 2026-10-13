package il.transit.core

import il.transit.core.api.Leg
import il.transit.core.fare.FareEstimator
import il.transit.core.fare.FareProfile
import il.transit.core.fare.FareTable
import il.transit.core.fare.PassAdvisor
import il.transit.core.features.ISRAEL
import il.transit.core.geo.LatLon
import il.transit.core.history.TripRecord
import il.transit.core.user.UserJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZonedDateTime

/** Phase 10 D8: each fare profile against the Rav-Pass profiles page, and old settings. */
class FareProfileTest {
    private val t0 = Instant.parse("2026-09-29T07:00:00Z")
    private val origin = LatLon(31.2525, 34.7915)
    private fun north(km: Double) = LatLon(origin.lat + km / 111.195, origin.lon)
    private fun ride(km: Double, atMin: Long, fromKm: Double = 0.0, mode: String = "BUS"): Leg =
        leg(mode, place("a", north(fromKm)), place("b", north(fromKm + km)), t0.plusSeconds(atMin * 60), t0.plusSeconds((atMin + 20) * 60))
    private fun fare(profile: FareProfile, vararg legs: Leg) = FareEstimator.estimate(itinerary(*legs), profile)!!

    @Test fun `every number is the profiles page's, checked on its date`() {
        assertEquals("https://s3-eu-west-1.amazonaws.com/static.hopon.co.il/mot/profiles.html", FareProfile.PROFILES_URL)
        assertEquals("2026-10-13", FareProfile.CHECKED)
        fun shares(p: FareProfile) = Triple(p.singlePaid, p.dailyPaid, p.monthlyPaid)
        assertEquals(Triple(100, 100, 100), shares(FareProfile.REGULAR))
        assertEquals(Triple(0, 0, 0), shares(FareProfile.SENIOR)) // 67+: 100% off everything
        assertEquals(Triple(67, 67, 100), shares(FareProfile.STUDENT)) // 33% off single, no monthly discount
        assertEquals(Triple(50, 50, 50), shares(FareProfile.YOUTH)) // 50% single and monthly
        assertEquals(Triple(0, 0, 0), shares(FareProfile.SOLDIER)) // mandatory service: free (owner confirms)
        assertEquals(Triple(50, 50, 50), shares(FareProfile.HALF))
        assertEquals(Triple(0, 0, 0), shares(FareProfile.FREE))
        assertEquals(
            listOf("REGULAR", "SENIOR", "STUDENT", "YOUTH", "SOLDIER", "HALF", "FREE"),
            FareProfile.entries.map { it.name },
        )
    }

    @Test fun `single fares per profile`() {
        val yellow = ride(5.0, 0)
        assertEquals(800, fare(FareProfile.REGULAR, yellow).agorot)
        assertEquals(0, fare(FareProfile.SENIOR, yellow).agorot)
        assertEquals(536, fare(FareProfile.STUDENT, yellow).agorot)
        assertEquals(400, fare(FareProfile.YOUTH, yellow).agorot) // the price list's own example: ₪4 instead of ₪8
        assertEquals(0, fare(FareProfile.SOLDIER, yellow).agorot)
        assertEquals(972, fare(FareProfile.STUDENT, ride(30.0, 0)).agorot) // 14.50 × 0.67 = 9.715
    }

    @Test fun `the daily cap takes the profile's daily share`() {
        val three = arrayOf(ride(20.0, 0), ride(20.0, 30, 20.0), ride(20.0, 60, 40.0)) // 3 × 14.50 > 29 cap
        assertEquals(2900, fare(FareProfile.REGULAR, *three).agorot)
        assertEquals(1450, fare(FareProfile.YOUTH, *three).agorot)
        val student = fare(FareProfile.STUDENT, *three)
        assertEquals(1943, student.agorot) // 29 × 0.67
        assertTrue(student.capped)
    }

    @Test fun `settings stored before D8 decode to the same prices`() {
        val yellow = ride(5.0, 0)
        for ((stored, agorot) in listOf("REGULAR" to 800, "HALF" to 400, "FREE" to 0)) {
            val s = UserJson.decodeSettings("""{"maxWalkMin":15,"fareProfile":"$stored","peakFactor":1.3}""")
            assertEquals(stored, s.fareProfile.name)
            assertEquals(agorot, fare(s.fareProfile, yellow).agorot)
        }
        assertEquals(FareProfile.REGULAR, UserJson.decodeSettings("""{"maxWalkMin":15}""").fareProfile)
        val student = UserJson.encodeSettings(UserJson.decodeSettings(null).copy(fareProfile = FareProfile.STUDENT))
        assertEquals(FareProfile.STUDENT, UserJson.decodeSettings(student).fareProfile)
    }

    private fun trip(local: String, agorot: Int) = TripRecord(
        startedAtEpoch = ZonedDateTime.of(LocalDateTime.parse(local), ISRAEL).toEpochSecond(),
        from = "", to = "Work", mode = "TRIP", transitMin = 30, walkMin = 5, transfers = 0,
        totalMin = 40, fareAgorot = agorot, fareBand = FareTable.YELLOW, withTrain = false,
    )

    /** Two yellow rides a day on 22 days of September: ₪352 in single fares. */
    private val month = (1..22).flatMap { d ->
        listOf(trip("2026-09-%02dT07:30".format(d), 800), trip("2026-09-%02dT17:30".format(d), 800))
    }
    private val september = YearMonth.of(2026, 9)

    @Test fun `pass advice uses the profile's single and monthly shares`() {
        val regular = PassAdvisor.forMonth(month, FareProfile.REGULAR, september)!!
        assertEquals(35200, regular.singleAgorot)
        assertEquals(31500, regular.passAgorot)

        val youth = PassAdvisor.forMonth(month, FareProfile.YOUTH, september)!!
        assertEquals(17600, youth.singleAgorot)
        assertEquals(15750, youth.passAgorot) // the price list: ₪157.5 instead of ₪315

        // A student pays 67% per ride but the full pass, so per ride wins here.
        val student = PassAdvisor.forMonth(month, FareProfile.STUDENT, september)!!
        assertEquals(23584, student.singleAgorot)
        assertEquals(31500, student.passAgorot)
        assertTrue(student.passSavesAgorot < 0)
    }

    @Test fun `free profiles get no pass advice`() {
        val october = Instant.parse("2026-10-06T09:00:00Z")
        for (p in FareProfile.entries.filter { it.free }) {
            assertNull(p.name, PassAdvisor.advise(month, p, october))
        }
        assertEquals(setOf(FareProfile.SENIOR, FareProfile.SOLDIER, FareProfile.FREE), FareProfile.entries.filter { it.free }.toSet())
        assertFalse(FareProfile.STUDENT.free)
    }
}
