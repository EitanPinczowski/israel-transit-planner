package il.transit.core.prop

import il.transit.core.api.Itinerary
import il.transit.core.fare.FareEstimator
import il.transit.core.fare.FareKind
import il.transit.core.fare.FareProfile
import il.transit.core.fare.FareTable
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FarePropertyTest {
    private val t0 = Instant.parse("2026-10-05T05:00:00Z")
    private val modes = listOf("BUS", "RAIL", "TRAM")

    /** One ride of [km] kilometres due north of the equator-ish origin, starting [startMin] after t0. */
    private fun ride(mode: String, km: Double, startMin: Int, tripId: String?) = leg(
        mode,
        stop("a", 31.0, 34.8),
        stop("b", 31.0 + km / 111.2, 34.8),
        t0.plusSeconds(startMin * 60L),
        t0.plusSeconds((startMin + 20) * 60L),
        tripId,
    )

    private fun single(mode: String, km: Double): Itinerary = itinerary(listOf(ride(mode, km, 0, "t")))

    private data class Ride(val mode: String, val km: Double, val gapMin: Int)

    private val rides = Arb.bind(Arb.element(modes), Arb.double(0.2, 300.0), Arb.int(0..120)) { m, k, g -> Ride(m, k, g) }
    private val trips: Arb<Itinerary> = Arb.list(rides, 1..4).map { rs ->
        var at = 0
        itinerary(rs.mapIndexed { i, r -> ride(r.mode, r.km, at.also { at += 20 + r.gapMin }, "trip$i") })
    }

    @Test fun `a fare is never negative, a profile never costs more than regular, a free profile costs 0`() = runBlocking<Unit> {
        checkAll(cfg(1000), trips) { trip ->
            val regular = FareEstimator.estimate(trip)!!
            assertTrue("regular < 0: ${regular.agorot}", regular.agorot >= 0)
            for (p in FareProfile.values()) {
                val f = FareEstimator.estimate(trip, p)!!
                assertTrue("$p negative", f.agorot >= 0)
                assertTrue("$p ${f.agorot} > regular ${regular.agorot}", f.agorot <= regular.agorot)
                if (p.free) assertEquals("$p", 0, f.agorot)
                assertTrue("$p over the daily cap", f.agorot <= (FareTable.dailyCap(f.maxBand, f.hasTrain) * p.dailyPaid + 50) / 100 + 1)
            }
        }
    }

    @Test fun `a longer ride never costs less, whatever the vehicle`() = runBlocking<Unit> {
        checkAll(cfg(1000), Arb.element(modes), Arb.double(0.1, 300.0), Arb.double(0.0, 100.0)) { mode, km, extra ->
            for (p in FareProfile.values()) {
                val short = FareEstimator.estimate(single(mode, km), p)!!.agorot
                val long = FareEstimator.estimate(single(mode, km + extra), p)!!.agorot
                assertTrue("$mode $p: ${km}km=$short but ${km + extra}km=$long", long >= short)
            }
        }
    }

    @Test fun `the price tables never fall with the band`() {
        for (kind in FareKind.values()) {
            val prices = (0..FareTable.BAND_KM.size).map { FareTable.price(kind, it) }
            assertEquals("$kind", prices.sorted(), prices)
        }
        for (train in listOf(false, true)) {
            val caps = (0..FareTable.BAND_KM.size).map { FareTable.dailyCap(it, train) }
            assertEquals("cap train=$train", caps.sorted(), caps)
        }
        // Bands are consistent with the distances: strictly increasing, and band() is monotonic.
        assertEquals(FareTable.BAND_KM.sorted(), FareTable.BAND_KM)
    }

    @Test fun `band is monotonic in the distance`() = runBlocking<Unit> {
        checkAll(cfg(500), Arb.double(0.0, 1000.0), Arb.double(0.0, 100.0)) { km, extra ->
            assertTrue(FareTable.band(km + extra) >= FareTable.band(km))
        }
    }
}
