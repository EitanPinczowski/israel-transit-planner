package il.transit.core.prop

import il.transit.core.api.Itinerary
import il.transit.core.geo.LatLon
import il.transit.core.ride.RideEvent
import il.transit.core.ride.RideTracker
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** R2: whatever the fixes, a leg's alert never fires before boarding, nor more than 20 min before its end. */
class RideTrackerPropertyTest {
    private val t0 = Instant.parse("2026-10-05T05:00:00Z")

    /** Walk / ride legs, each of the given minutes after the given wait, stops ~1.1 km apart. */
    private data class Piece(val min: Int, val waitMin: Int, val ride: Boolean)

    private val pieces = Arb.list(Arb.bind(Arb.int(1..90), Arb.int(0..15), Arb.int(0..1)) { m, w, r -> Piece(m, w, r == 1) }, 1..5)

    private fun build(ps: List<Piece>): Itinerary {
        var at = 0
        val legs = ps.mapIndexed { i, p ->
            at += p.waitMin
            val s = t0.plusSeconds(at * 60L)
            at += p.min
            leg(if (p.ride) "BUS" else "WALK", stop("s$i", 31.0 + i * 0.01, 34.8), stop("s${i + 1}", 31.0 + (i + 1) * 0.01, 34.8), s, t0.plusSeconds(at * 60L))
        }
        return itinerary(legs)
    }

    /** A fix every 30 s at a random point along the stops (so often near one), with a random lead. */
    private val wander = Arb.list(Arb.double(0.0, 5.0), 50..400)

    @Test fun `an alert fires only between boarding and 20 min before the vehicle is due`() = runBlocking<Unit> {
        checkAll(cfg(300), pieces, wander, Arb.int(-30..10)) { ps, path, leadMin ->
            val trip = build(ps)
            val transit = trip.legs.filter { it.isTransit }
            val tracker = RideTracker(trip)
            path.forEachIndexed { k, x ->
                val now = t0.plusSeconds(leadMin * 60L + k * 30L)
                val e = tracker.update(LatLon(31.0 + x * 0.01, 34.8), now) as? RideEvent.Approaching ?: return@forEachIndexed
                val leg = transit[e.legIndex]
                assertTrue("alert at $now before boarding at ${leg.start}", !now.isBefore(leg.start))
                assertTrue("alert at $now, ${Duration.between(now, leg.end).toMinutes()} min before ${leg.end}", Duration.between(now, leg.end) <= Duration.ofMinutes(20))
            }
        }
    }
}
