package il.transit.core

import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.TransitHttpException
import il.transit.core.api.UsageDay
import il.transit.core.api.UsageLog
import il.transit.core.api.UsageOutcome
import il.transit.core.api.UsageService
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import il.transit.core.search.GuardedGeocoder
import il.transit.core.weather.GuardedWeather
import il.transit.core.weather.RainForecast
import il.transit.core.weather.WeatherSource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** Phase 11 E4: the data card's counts. Only counts and times, per Israel day, 7 days kept. */
class UsageLogTest {
    /** A clock the test moves. */
    private class Moving(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }

    // Tue 27 Oct 2026, 10:00 Israel (UTC+2 after the clocks went back on the 25th).
    private val morning = Instant.parse("2026-10-27T08:00:00Z")

    @Test fun `a guard counts the network, the cache and a retry for Transitous`() = runTest {
        val clock = Moving(morning)
        val log = UsageLog(clock)
        val inner = FaultyTransitApi(Fault.Http(429))
        val api = GuardedTransitApi(inner, clock, backoff = Duration.ofMillis(1), usage = log)
        api.stops(BBox(LatLon(31.0, 34.0), LatLon(31.1, 34.1)), null, "he") // 429, then answered
        api.stops(BBox(LatLon(31.0, 34.0), LatLon(31.1, 34.1)), null, "he") // cache
        assertEquals(2, inner.calls)
        val day = log.days().single()
        assertEquals(mapOf("transitous.stops.net" to 1, "transitous.stops.retry" to 1, "transitous.stops.cache" to 1), day.counts)
        val s = log.summary()
        assertEquals(3, s.transitous) // what the card calls "requests": every one asked
        assertEquals(1, s.transitousCache)
        assertEquals(1, s.transitousRetries)
        assertEquals("2 left the phone: the first try and the retry", 2, s.transitous - s.transitousCache)
    }

    @Test fun `a refusal twice is still two requests sent, nothing cached`() = runTest {
        val log = UsageLog(Moving(morning))
        val inner = FaultyTransitApi(Fault.Http(503), Fault.Http(503))
        val api = GuardedTransitApi(inner, backoff = Duration.ofMillis(1), usage = log)
        runCatching { api.geocode("x", "he", null, 5) }.also { assertTrue(it.exceptionOrNull() is TransitHttpException) }
        assertEquals(mapOf("transitous.geo.net" to 1, "transitous.geo.retry" to 1), log.days().single().counts)
    }

    @Test fun `the backup geocoder and the weather count as their own services`() = runTest {
        val clock = Moving(morning)
        val log = UsageLog(clock)
        val geo = GuardedGeocoder(FaultyGeocoder(), clock, usage = log)
        geo.search("טבנקין", "he", null)
        geo.search("טבנקין", "he", null) // cached a day
        val weather = GuardedWeather(WeatherSource { RainForecast(emptyList()) }, clock, usage = log)
        weather.forTrip(morning, LatLon(32.08, 34.78))
        weather.forTrip(morning, LatLon(32.08, 34.78)) // same cell, same hour
        val s = log.summary()
        assertEquals(1, s.address)
        assertEquals(1, s.weather)
        assertEquals(0, s.transitous)
        val counts = log.days().single().counts
        assertEquals(1, counts["address.search.cache"])
        assertEquals(1, counts["weather.forecast.cache"])
    }

    @Test fun `counting never changes what is sent`() = runTest {
        val plain = FaultyTransitApi(Fault.Http(429))
        val counted = FaultyTransitApi(Fault.Http(429))
        val box = BBox(LatLon(31.0, 34.0), LatLon(31.1, 34.1))
        GuardedTransitApi(plain, backoff = Duration.ofMillis(1)).let { it.stops(box, null, "he"); it.stops(box, null, "he") }
        GuardedTransitApi(counted, backoff = Duration.ofMillis(1), usage = UsageLog()).let { it.stops(box, null, "he"); it.stops(box, null, "he") }
        assertEquals(plain.calls, counted.calls)
    }

    @Test fun `the day ends at midnight Israel time, not UTC`() {
        val clock = Moving(Instant.parse("2026-10-27T21:59:00Z")) // 23:59 Israel, Tue
        val log = UsageLog(clock)
        log.count(UsageService.TRANSITOUS, "plan", UsageOutcome.NETWORK)
        clock.now = Instant.parse("2026-10-27T22:01:00Z") // 00:01 Israel, Wed; still Tue in UTC
        assertEquals("a new day: nothing yet", 0, log.summary().transitous)
        log.count(UsageService.TRANSITOUS, "plan", UsageOutcome.NETWORK)
        log.count(UsageService.TRANSITOUS, "plan", UsageOutcome.NETWORK)
        assertEquals(2, log.summary().transitous)
        assertEquals(listOf("2026-10-27", "2026-10-28"), log.days().map { UsageLog.date(it.day).toString() })
    }

    @Test fun `summer time too - midnight is 21_00 UTC`() {
        val clock = Moving(Instant.parse("2026-07-01T20:59:00Z")) // 23:59 IDT
        val log = UsageLog(clock)
        log.count(UsageService.WEATHER, "forecast", UsageOutcome.NETWORK)
        clock.now = Instant.parse("2026-07-01T21:00:00Z") // 00:00 IDT, 2 July
        assertEquals(0, log.summary().weather)
        assertEquals(1, log.days().size)
    }

    @Test fun `seven days are kept, the eighth goes`() {
        val clock = Moving(morning)
        val log = UsageLog(clock)
        repeat(9) { i ->
            clock.now = morning.plus(Duration.ofDays(i.toLong()))
            log.count(UsageService.TRANSITOUS, "plan", UsageOutcome.NETWORK)
        }
        val days = log.days()
        assertEquals(UsageLog.KEEP_DAYS, days.size)
        assertEquals("2026-10-29", UsageLog.date(days.first().day).toString())
        assertEquals("2026-11-04", UsageLog.date(days.last().day).toString())
        // A restored file is trimmed too.
        clock.now = morning.plus(Duration.ofDays(30))
        assertTrue(UsageLog(clock, days).days().isEmpty())
    }

    @Test fun `the night refresh and ride GPS - the last night within a day, GPS in whole minutes`() {
        val clock = Moving(Instant.parse("2026-10-27T00:14:00Z")) // 02:14 Israel
        val log = UsageLog(clock)
        log.nightRefresh(clock.now, 4)
        clock.now = morning
        log.rideGps(Duration.ofMinutes(11).plusSeconds(20))
        log.rideGps(Duration.ZERO)
        log.rideGps(Duration.ofSeconds(-5))
        val s = log.summary()
        assertEquals(Instant.parse("2026-10-27T00:14:00Z"), s.nightAt)
        assertEquals(4, s.nightRequests)
        assertEquals(12, s.rideGpsMin)
        assertFalse(s.empty)
        // A night run at 23:30 is yesterday's day, but still last night's run the next morning.
        clock.now = Instant.parse("2026-10-28T08:00:00Z")
        log.nightRefresh(Instant.parse("2026-10-27T21:30:00Z"), 6)
        assertEquals(6, log.summary().nightRequests)
        assertEquals(0, log.summary().rideGpsMin)
        // Two days later: no recent run.
        clock.now = Instant.parse("2026-10-29T22:00:00Z")
        assertNull(log.summary().nightAt)
        assertTrue(log.summary().empty)
    }

    @Test fun `the JSON holds no text but keys and numbers, and round-trips`() {
        val log = UsageLog(Moving(morning))
        log.count(UsageService.TRANSITOUS, "plan", UsageOutcome.NETWORK)
        log.count(UsageService.ADDRESS, "search", UsageOutcome.CACHE)
        log.count(UsageService.TRANSITOUS, "דיזנגוף 50 תל אביב", UsageOutcome.NETWORK) // never a query: folded
        log.count(UsageService.TRANSITOUS, "32.08,34.78", UsageOutcome.NETWORK)
        log.count(UsageService.TRANSITOUS, "dizengoff", UsageOutcome.NETWORK) // a Latin query is not a kind either
        log.count(UsageService.ADDRESS, "ben_gurion_airport", UsageOutcome.NETWORK)
        log.nightRefresh(morning, 3)
        log.rideGps(Duration.ofMinutes(5))
        val text = UsageLog.encode(log.days())
        val kinds = (UsageLog.KINDS + "other").joinToString("|")
        val allowedKey = Regex("^(transitous|weather|address)\\.($kinds)\\.(net|cache|retry)$")
        fun check(e: JsonElement, inCounts: Boolean = false) {
            when (e) {
                is JsonObject -> e.forEach { (k, v) ->
                    if (inCounts) assertTrue("key $k", allowedKey.matches(k)) else assertTrue(k in setOf("day", "counts", "nightAt", "nightRequests", "rideGpsSec"))
                    check(v, k == "counts")
                }
                is JsonArray -> e.forEach { check(it) }
                is JsonPrimitive -> assertFalse("a string value: $e", e.isString)
            }
        }
        Json.parseToJsonElement(text).jsonArray.forEach { check(it) }
        assertFalse(text.contains("דיזנגוף"))
        assertFalse(text.contains("dizengoff"))
        assertFalse(text.contains("gurion"))
        assertEquals(3, log.days().single().counts["transitous.other.net"])
        assertEquals(1, log.days().single().counts["address.other.net"])
        assertEquals(log.days(), UsageLog.decode(text))
        assertEquals(emptyList<UsageDay>(), UsageLog.decode("not json"))
        assertEquals(emptyList<UsageDay>(), UsageLog.decode(null))
    }

    @Test fun `onChange fires after each count, so the app can save`() = runBlocking {
        val log = UsageLog(Moving(morning))
        var n = 0
        log.onChange = { n++ }
        log.count(UsageService.TRANSITOUS, "plan", UsageOutcome.CACHE)
        log.rideGps(Duration.ofMinutes(1))
        log.nightRefresh(morning, 1)
        assertEquals(3, n)
    }

    @Test fun `a retry of the backup geocoder and of the weather is counted as sent`() = runTest {
        val clock = Moving(morning)
        val log = UsageLog(clock)
        val geo = GuardedGeocoder(FaultyGeocoder(Fault.Http(429)), clock, backoff = Duration.ofMillis(1), usage = log)
        geo.search("טבנקין", "he", null)
        var calls = 0
        val source = WeatherSource {
            if (calls++ == 0) throw TransitHttpException(503, "busy", null)
            RainForecast(emptyList())
        }
        GuardedWeather(source, clock, backoff = Duration.ofMillis(1), usage = log).forTrip(morning, LatLon(32.08, 34.78))
        val counts = log.days().single().counts
        assertEquals(1, counts["address.search.net"])
        assertEquals(1, counts["address.search.retry"])
        assertEquals(1, counts["weather.forecast.net"])
        assertEquals(1, counts["weather.forecast.retry"])
        val s = log.summary()
        assertEquals("both tries left the phone", 2, s.address)
        assertEquals(2, s.weather)
    }

    @Test fun `a timeout is counted as sent, and not as a retry`() = runTest {
        val log = UsageLog(Moving(morning))
        val api = GuardedTransitApi(FaultyTransitApi(Fault.Timeout), usage = log)
        runCatching { api.geocode("x", "he", null, 5) }
        assertEquals(mapOf("transitous.geo.net" to 1), log.days().single().counts)
    }

    /** 25 Oct 2026, the night the clocks go back: a 25-hour Israel day in which 01:00–02:00 comes twice. */
    @Test fun `the day the clocks go back is one day of 25 hours`() {
        fun day(utc: String) = UsageLog.date(UsageLog.dayOf(Instant.parse(utc))).toString()
        assertEquals("2026-10-24", day("2026-10-24T20:59:00Z")) // 23:59 IDT
        assertEquals("2026-10-25", day("2026-10-24T21:00:00Z")) // 00:00 IDT
        assertEquals("2026-10-25", day("2026-10-24T22:30:00Z")) // 01:30 IDT
        assertEquals("2026-10-25", day("2026-10-24T23:30:00Z")) // 01:30 IST, the second time
        assertEquals("2026-10-25", day("2026-10-25T21:30:00Z")) // 23:30 IST
        assertEquals("2026-10-25", day("2026-10-25T21:59:00Z")) // 23:59 IST
        assertEquals("2026-10-26", day("2026-10-25T22:00:00Z")) // 00:00 IST

        val clock = Moving(Instant.parse("2026-10-24T20:59:00Z"))
        val log = UsageLog(clock)
        for (t in listOf("2026-10-24T20:59:00Z", "2026-10-24T21:00:00Z", "2026-10-24T22:30:00Z", "2026-10-24T23:30:00Z", "2026-10-25T21:59:00Z", "2026-10-25T22:00:00Z")) {
            clock.now = Instant.parse(t)
            log.count(UsageService.TRANSITOUS, "plan", UsageOutcome.NETWORK)
        }
        assertEquals(listOf(1, 4, 1), log.days().map { it.counts.getValue("transitous.plan.net") })

        // The night refresh at the repeated 01:14 (IST) is still last night's run the next morning.
        log.nightRefresh(Instant.parse("2026-10-24T23:14:00Z"), 5)
        clock.now = Instant.parse("2026-10-25T06:00:00Z") // 08:00 IST
        assertEquals(Instant.parse("2026-10-24T23:14:00Z"), log.summary().nightAt)
        assertEquals(5, log.summary().nightRequests)
    }
}
