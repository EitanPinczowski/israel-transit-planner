package il.transit.core.prop

import il.transit.core.api.Itinerary
import il.transit.core.remind.LiveProgress
import il.transit.core.ride.RideProgress
import io.kotest.property.Arb
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

class LiveProgressPropertyTest {
    private val t0 = Instant.parse("2026-10-05T05:00:00Z")

    /** Alternating walk / ride legs, each of the given minutes, with the given waits between them. */
    private data class Piece(val min: Int, val waitMin: Int, val ride: Boolean)

    private val pieces = Arb.list(Arb.bind(Arb.int(1..40), Arb.int(0..15), Arb.int(0..1)) { m, w, r -> Piece(m, w, r == 1) }, 1..6)

    private fun build(ps: List<Piece>): Itinerary {
        var at = 0
        val legs = ps.mapIndexed { i, p ->
            at += p.waitMin
            val s = t0.plusSeconds(at * 60L)
            at += p.min
            val e = t0.plusSeconds(at * 60L)
            leg(if (p.ride) "BUS" else "WALK", stop("s$i", 31.0 + i * 0.01, 34.8), stop("s${i + 1}", 31.0 + (i + 1) * 0.01, 34.8), s, e)
        }
        return itinerary(legs)
    }

    private fun total(it: Itinerary) = (it.legs.last().end.epochSecond - it.legs.first().start.epochSecond).toInt()

    @Test fun `segments add up to the trip and progress stays inside the bar, never going back as time moves on`() = runBlocking<Unit> {
        checkAll(cfg(1000), pieces, Arb.int(-3600..7200), Arb.int(0..1800)) { ps, nowOff, step ->
            val trip = build(ps)
            val start = LiveProgress.start(trip) ?: return@checkAll
            assertEquals("segments vs trip", total(trip), start.max)
            assertTrue(start.segments.all { it.length > 0 })
            assertTrue(start.points.all { it in 0..start.max })

            val boardAt = trip.firstTransitLeg?.start ?: t0
            val now1 = t0.plusSeconds(nowOff.toLong())
            val now2 = now1.plusSeconds(step.toLong())

            val c1 = LiveProgress.countdown(trip, boardAt, now1)!!
            val c2 = LiveProgress.countdown(trip, boardAt, now2)!!
            for (c in listOf(c1, c2)) assertTrue("countdown ${c.progress}/${c.max}", c.progress in 0..c.max)
            assertTrue("countdown went back", c2.progress >= c1.progress)

            for (leg in 0..2) {
                fun at(now: Instant) = LiveProgress.ride(trip, null, leg, now)!!
                val a = at(now1)
                val b = at(now2)
                assertTrue("ride progress ${a.progress}/${a.max}", a.progress in 0..a.max)
                assertTrue("ride went back", b.progress >= a.progress)
            }
        }
    }

    @Test fun `a ride with a tracker fix keeps progress in range and never goes back when the clock moves on`() = runBlocking<Unit> {
        checkAll(cfg(1000), pieces, Arb.int(-600..7200), Arb.int(0..1800), Arb.int(-5..30), Arb.int(0..12)) { ps, nowOff, step, delay, stops ->
            val trip = build(ps)
            val ride = trip.legs.count { it.isTransit }
            if (ride == 0) return@checkAll
            fun progress(now: Instant) = RideProgress("1", "x", stops, now.plusSeconds(600), delay, now.plusSeconds(1200))
            val now1 = t0.plusSeconds(nowOff.toLong())
            val now2 = now1.plusSeconds(step.toLong())
            for (idx in 0 until ride) {
                val a = LiveProgress.ride(trip, progress(now1), idx, now1) ?: continue
                val b = LiveProgress.ride(trip, progress(now2), idx, now2) ?: continue
                assertTrue("progress ${a.progress}/${a.max}", a.progress in 0..a.max && b.progress in 0..b.max)
                assertTrue("progress went back: ${a.progress} -> ${b.progress}", b.progress >= a.progress)
                assertEquals(total(trip), a.max)
            }
        }
    }
}
