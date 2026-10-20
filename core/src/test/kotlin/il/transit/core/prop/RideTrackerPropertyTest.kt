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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * R2: safety, whatever the fixes on legs that list no stops (armed by time only), an alert never fires before
 * boarding nor more than 20 min before the leg's end; liveness, a rider on an earlier run of the whole trip than
 * planned gets exactly one alert per ride, in order, while on it.
 */
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

    /** A ride or walk of [min] minutes after [waitMin], with [stops] listed stops (rides only). */
    private data class Hop(val min: Int, val waitMin: Int, val ride: Boolean, val stops: Int)

    private val hops = Arb.list(Arb.bind(Arb.int(1..40), Arb.int(0..10), Arb.int(0..1), Arb.int(0..5)) { m, w, r, k -> Hop(m, w, r == 1, k) }, 1..5)

    /**
     * Legs due north, 1.1 km each, listed stops evenly spaced. A ride listing fewer than 3 stops is armed by
     * time only, so it lasts at least [early] + 2 min: ridden that much early, it still reaches its stop after
     * its planned start.
     */
    private fun build(hs: List<Hop>, early: Int): Itinerary {
        var at = 0
        val legs = hs.mapIndexed { i, h ->
            at += h.waitMin
            val s = t0.plusSeconds(at * 60L)
            val byTime = h.ride && h.stops < 3
            at += if (byTime) maxOf(h.min, early + 2) else h.min
            val lat = 31.0 + i * 0.01
            val l = leg(if (h.ride) "BUS" else "WALK", stop("s$i", lat, 34.8), stop("s${i + 1}", lat + 0.01, 34.8), s, t0.plusSeconds(at * 60L))
            if (!h.ride) l else l.copy(intermediateStops = (1..h.stops).map { j -> stop("s$i.$j", lat + 0.01 * j / (h.stops + 1), 34.8) })
        }
        return itinerary(legs)
    }

    /** Where the rider is at [now], following [trip] [early] minutes ahead of plan, at an even pace on each leg. */
    private fun where(trip: Itinerary, early: Int, now: Instant): LatLon {
        val t = now.plusSeconds(early * 60L)
        val leg = trip.legs.lastOrNull { !t.isBefore(it.start) } ?: trip.legs.first()
        val f = if (!t.isBefore(leg.end)) 1.0 else Duration.between(leg.start, t).toMillis().toDouble() / Duration.between(leg.start, leg.end).toMillis()
        return LatLon(leg.from.lat + (leg.to.lat - leg.from.lat) * f, 34.8)
    }

    @Test fun `a rider on an earlier run than planned gets one alert per ride, in order, while on it`() = runBlocking<Unit> {
        checkAll(cfg(300), hops, Arb.int(0..20)) { hs, early ->
            val trip = build(hs, early)
            val transit = trip.legs.filter { it.isTransit }
            val tracker = RideTracker(trip)
            val lead = Duration.ofMinutes(early.toLong())
            val first = trip.start.minus(lead)
            val last = trip.end.minus(lead).plusSeconds(60)
            val alerts = mutableListOf<Pair<Instant, RideEvent.Approaching>>()
            var now = first
            while (!now.isAfter(last)) {
                (tracker.update(where(trip, early, now), now) as? RideEvent.Approaching)?.let { alerts += now to it }
                now = now.plusSeconds(15)
            }
            val ctx = "early $early min, legs ${trip.legs.map { "${it.mode} ${it.start}-${it.end} ${it.intermediateStops.size} stops" }}, alerts $alerts"
            assertEquals(ctx, transit.indices.toList(), alerts.map { it.second.legIndex })
            alerts.forEach { (at, a) ->
                val leg = transit[a.legIndex]
                assertTrue("before boarding: $ctx", !at.isBefore(leg.start.minusSeconds(early * 60L)))
                assertTrue("after arriving: $ctx", !at.isAfter(leg.end.minusSeconds(early * 60L).plusSeconds(15)))
            }
        }
    }
}
