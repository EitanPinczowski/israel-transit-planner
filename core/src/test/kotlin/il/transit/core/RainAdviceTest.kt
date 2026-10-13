package il.transit.core

import il.transit.core.features.ISRAEL
import il.transit.core.geo.LatLon
import il.transit.core.weather.GuardedWeather
import il.transit.core.weather.RainAdvice
import il.transit.core.weather.RainForecast
import il.transit.core.weather.RainHour
import il.transit.core.weather.WeatherSource
import il.transit.core.weather.parseOpenMeteo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** Rain on the walking parts (Phase 10 D9, owner decision 7: ≥ 60% or ≥ 0.5 mm, ≥ 5 min of walking). */
class RainAdviceTest {
    private val a = place("A", LatLon(32.08, 34.78))
    private val s1 = place("Stop 1", LatLon(32.07, 34.79), stopId = "s1")
    private val s2 = place("Stop 2", LatLon(32.06, 34.80), stopId = "s2")
    private val b = place("B", LatLon(32.05, 34.81))

    /** 2026-10-16 09:00 in Israel. */
    private val nine = Instant.parse("2026-10-16T06:00:00Z")
    private fun at(min: Long, base: Instant = nine) = base.plusSeconds(min * 60)

    /** Walk [walk1] min from [start], bus 20 min, walk [walk2] min. */
    private fun option(start: Long, walk1: Long, walk2: Long, base: Instant = nine) = itinerary(
        leg("WALK", a, s1, at(start, base), at(start + walk1, base)),
        leg("BUS", s1, s2, at(start + walk1, base), at(start + walk1 + 20, base)),
        leg("WALK", s2, b, at(start + walk1 + 20, base), at(start + walk1 + 20 + walk2, base)),
    )

    private fun hour(start: Instant, probability: Int?, mm: Double?) = RainForecast(listOf(RainHour(start, probability, mm)))

    @Test fun `threshold edges`() {
        fun rainy(p: Int?, mm: Double?) = RainAdvice.rainy(RainHour(nine, p, mm))
        assertFalse(rainy(59, 0.49))
        assertTrue(rainy(60, 0.0))
        assertTrue(rainy(0, 0.5))
        assertFalse(rainy(null, null))
        assertTrue(rainy(null, 3.0))
    }

    @Test fun `5 min of walking in a rainy hour counts, 4 min 59 s does not`() {
        val wet = hour(nine, 70, 0.2)
        // The first walk ends exactly at 09:05 / 09:04:59 inside the hour.
        assertNotNull(RainAdvice.advise(listOf(option(0, 5, 0)), wet))
        val short = itinerary(
            leg("WALK", a, s1, nine, nine.plusSeconds(299)),
            leg("BUS", s1, b, nine.plusSeconds(299), at(30)),
        )
        assertNull(RainAdvice.advise(listOf(short), wet))
    }

    @Test fun `walking is counted per hour, both walks of an option add up`() {
        // 3 min before 09:00 and 3 min after: no hour has 5 min.
        val both = RainForecast(listOf(RainHour(nine.minusSeconds(3600), 70, null), RainHour(nine, 70, null)))
        assertNull(RainAdvice.advise(listOf(option(-3, 6, 0)), both))
        // A 3-min walk to the bus and a 2-min walk off it, both 09:xx.
        val note = RainAdvice.advise(listOf(option(10, 3, 2)), hour(nine, 0, 1.2))
        assertNotNull(note)
        assertEquals(at(10), note!!.at)
        assertFalse(note.byProbability) // the amount made it rainy
    }

    @Test fun `no walking, no note`() {
        val ride = itinerary(leg("BUS", s1, s2, at(10), at(40)))
        assertNull(RainAdvice.advise(listOf(ride), hour(nine, 100, 9.0)))
        assertNull(RainAdvice.advise(emptyList(), hour(nine, 100, 9.0)))
        assertNull(RainAdvice.advise(listOf(option(10, 8, 8)), null))
    }

    @Test fun `the earliest rainy walk is named, and the option that walks least`() {
        val forecast = RainForecast(listOf(RainHour(nine, 40, 0.1), RainHour(at(60), 80, 0.6)))
        val walky = option(62, 12, 10) // walks 10:02–10:14 and 10:34–10:44
        val early = option(50, 6, 0) // walks 09:50–09:56 (the dry hour), then rides
        val lessWalk = option(65, 4, 3) // walks 10:05–10:09 and 10:29–10:32: 7 min in the wet hour
        val note = RainAdvice.advise(listOf(walky, early, lessWalk), forecast)!!
        assertEquals(at(62), note.at) // 10:02, the first walk in the rain of any option
        assertEquals(80, note.probability)
        assertTrue(note.byProbability)
        // early walks 6 min, lessWalk 7: early is the least-walking option.
        assertSame(early, note.leastWalking)
        assertEquals(6, note.leastWalkMin)
    }

    @Test fun `ties go to the earlier option, and one option offers no other`() {
        val wet = hour(nine, 90, 2.0)
        val x = option(20, 5, 0)
        val y = option(10, 5, 0)
        assertSame(y, RainAdvice.advise(listOf(x, y), wet)!!.leastWalking)
        val one = RainAdvice.advise(listOf(x), wet)!!
        assertNull(one.leastWalking)
        assertEquals(5, one.leastWalkMin)
    }

    @Test fun `Asia Jerusalem hours across the clock change`() {
        // Sun 25 Oct 2026, 02:00 summer time becomes 01:00: 22:00 and 23:00 UTC are both "01:00" in Israel.
        val first = Instant.parse("2026-10-24T22:00:00Z")
        val second = Instant.parse("2026-10-24T23:00:00Z")
        assertEquals(1, first.atZone(ISRAEL).hour)
        assertEquals(1, second.atZone(ISRAEL).hour)
        val forecast = RainForecast(listOf(RainHour(first, 10, 0.0), RainHour(second, 75, 1.0)))
        // Walking 01:10–01:20 summer time: the dry hour.
        assertNull(RainAdvice.advise(listOf(option(10, 10, 0, first)), forecast))
        // Walking 01:10–01:20 winter time: the wet one, named 01:10 local.
        val note = RainAdvice.advise(listOf(option(10, 10, 0, second)), forecast)!!
        assertEquals(Instant.parse("2026-10-24T23:10:00Z"), note.at)
        assertEquals("01:10", note.at.atZone(ISRAEL).toLocalTime().toString())
        // The real recording's stamps are UTC seconds: one hour apart, whatever the zone does.
        val f = parseOpenMeteo(javaClass.getResource("/fixtures/openmeteo_rain_telaviv.json")!!.readText())
        assertTrue(f.hours.zipWithNext().all { (p, q) -> Duration.between(p.start, q.start) == Duration.ofHours(1) })
    }

    @Test fun `the recorded rainy morning, a 09_10 walk gets the note, by the amount`() {
        val f = parseOpenMeteo(javaClass.getResource("/fixtures/openmeteo_rain_telaviv.json")!!.readText())
        val note = RainAdvice.advise(listOf(option(10, 8, 4), option(25, 2, 2)), f)!!
        assertEquals(at(10), note.at) // 09:10 Israel, the 0.5 mm hour
        assertEquals(0.5, note.mm!!, 1e-9)
        assertEquals(4, note.leastWalkMin)
        // An 08:xx walk is dry (0.1–0.4 mm, ≤ 16%).
        assertNull(RainAdvice.advise(listOf(option(-50, 8, 0)), f))
    }

    @Test fun `budget, a second search in the same hour and cell sends 0`() = runTest {
        var calls = 0
        val source = WeatherSource { calls++; RainForecast(emptyList()) }
        val clock = Clock.fixed(nine, ZoneOffset.UTC)
        val w = GuardedWeather(source, clock)
        w.forTrip(at(30), LatLon(32.0853, 34.7818))
        w.forTrip(at(90), LatLon(32.0700, 34.7900)) // another search, same 0.1° cell
        assertEquals(1, calls)
    }
}
