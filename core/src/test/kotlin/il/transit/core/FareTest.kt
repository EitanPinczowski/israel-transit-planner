package il.transit.core

import il.transit.core.api.Itinerary
import il.transit.core.api.Leg
import il.transit.core.fare.FareEstimate
import il.transit.core.fare.FareEstimator
import il.transit.core.fare.FareProfile
import il.transit.core.fare.FareTable
import il.transit.core.geo.LatLon
import il.transit.core.present.fareLabel
import il.transit.core.present.summarize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FareTest {
    private val t0 = Instant.parse("2026-09-29T07:00:00Z")
    private val origin = LatLon(31.2525, 34.7915)

    /** A point [km] due north of [origin] (1° of latitude ≈ 111.195 km). */
    private fun north(km: Double) = LatLon(origin.lat + km / 111.195, origin.lon)

    /** A ride of [km] starting [atMin] minutes after t0, lasting 20 minutes. */
    private fun ride(mode: String, km: Double, atMin: Long, fromKm: Double = 0.0): Leg =
        leg(mode, place("a", north(fromKm)), place("b", north(fromKm + km)), t0.plusSeconds(atMin * 60), t0.plusSeconds((atMin + 20) * 60))

    private fun fare(vararg legs: Leg, profile: FareProfile = FareProfile.REGULAR): FareEstimate? =
        FareEstimator.estimate(itinerary(*legs), profile)

    @Test fun `table matches the published list`() {
        assertEquals(listOf(800, 1450, 1900, 1900, 3050, 8732), FareTable.BUS)
        assertEquals(listOf(1150, 2100, 2700, 3050, 5250), FareTable.TRAIN)
        assertEquals(listOf(1750, 2900, 3750, 3750, 6050, 7950), FareTable.DAILY_CAP_BUS)
        assertEquals(listOf(2300, 3250, 4200, 4700, 8050), FareTable.DAILY_CAP_WITH_TRAIN)
        assertEquals(listOf(31500, 31500, 31500, 31500, 31500, 68400), FareTable.MONTHLY_BUS)
        assertEquals(listOf(32300, 32300, 46400, 68400, 68400), FareTable.MONTHLY_WITH_TRAIN)
        assertEquals(68400, FareTable.monthlyPass(5, withTrain = true))
        assertEquals("2026-10-06", FareTable.CHECKED)
        assertEquals(0, FareTable.band(15.0)) // limits are inclusive
        assertEquals(1, FareTable.band(15.1))
        assertEquals(5, FareTable.band(300.0))
    }

    @Test fun `one short bus is the yellow fare`() {
        assertEquals(800, fare(ride("BUS", 5.0, 0))!!.agorot)
    }

    @Test fun `yellow transfers are free inside 90 minutes, not after`() {
        assertEquals(800, fare(ride("BUS", 5.0, 0), ride("BUS", 4.0, 40, fromKm = 5.0))!!.agorot)
        assertEquals(1600, fare(ride("BUS", 5.0, 0), ride("BUS", 4.0, 100, fromKm = 5.0))!!.agorot)
    }

    @Test fun `a ride over 15 km pays the band and gets no free transfer`() {
        assertEquals(1450, fare(ride("BUS", 30.0, 0))!!.agorot)
        assertEquals(1450 + 800, fare(ride("BUS", 30.0, 0), ride("BUS", 3.0, 30, fromKm = 30.0))!!.agorot)
    }

    @Test fun `a train uses its own column and never joins the window`() {
        val f = fare(ride("BUS", 3.0, 0), ride("REGIONAL_RAIL", 50.0, 25, fromKm = 3.0))!!
        assertEquals(800 + 2700, f.agorot)
        assertTrue(f.hasTrain)
        assertFalse(f.capped)
    }

    @Test fun `the daily cap of the longest band limits the total`() {
        val f = fare(ride("BUS", 20.0, 0), ride("BUS", 20.0, 30, 20.0), ride("BUS", 20.0, 60, 40.0))!!
        assertEquals(2900, f.agorot)
        assertTrue(f.capped)
    }

    @Test fun `light rail costs like a bus`() {
        assertEquals(800, fare(ride("TRAM", 10.0, 0))!!.agorot)
        assertEquals(800, fare(ride("BUS", 4.0, 0), ride("TRAM", 6.0, 30, 4.0))!!.agorot)
    }

    @Test fun `legs of one vehicle trip are one ride`() {
        val a = ride("BUS", 10.0, 0).copy(tripId = "t1")
        val b = ride("BUS", 10.0, 20, 10.0).copy(tripId = "t1")
        assertEquals(1450, fare(a, b)!!.agorot) // 20 km as one ride, not two free yellow ones
    }

    @Test fun `discounts apply to the total`() {
        assertEquals(400, fare(ride("BUS", 5.0, 0), profile = FareProfile.HALF)!!.agorot)
        assertEquals(725, fare(ride("BUS", 30.0, 0), profile = FareProfile.HALF)!!.agorot)
        assertEquals(0, fare(ride("BUS", 5.0, 0), profile = FareProfile.FREE)!!.agorot)
    }

    @Test fun `walking all the way has no fare`() {
        val walk = leg("WALK", place("a", origin), place("b", north(1.0)), t0, t0.plusSeconds(900))
        assertNull(fare(walk))
    }

    @Test fun `label trims the agorot and summaries carry the fare`() {
        assertEquals("≈ ₪8", fareLabel(FareEstimate(800, false, false)))
        assertEquals("≈ ₪14.5", fareLabel(FareEstimate(1450, false, false)))
        assertEquals("≈ ₪87.32", fareLabel(FareEstimate(8732, false, false)))
        assertEquals("≈ ₪0", fareLabel(FareEstimate(0, false, false)))
        val it: Itinerary = itinerary(ride("BUS", 5.0, 0))
        assertEquals(800, summarize(it).fare!!.agorot)
        assertEquals(400, summarize(it, FareProfile.HALF).fare!!.agorot)
    }
}
