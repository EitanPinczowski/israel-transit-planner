package il.transit.core

import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.features.ISRAEL
import il.transit.core.geo.LatLon
import il.transit.core.plan.CacheLookup
import il.transit.core.plan.PlanCache
import il.transit.core.plan.TripCacheJson
import il.transit.core.plan.TripPages
import il.transit.core.plan.TripResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

/** The Quick Settings tile's cache read (Phase 9 C4): 0 requests, Home by distance from the key. */
class CacheLookupTest {
    private fun il(local: String): Instant = LocalDateTime.parse(local).atZone(ISRAEL).toInstant()

    /** Recorded Saturday night 2026-10-03, Be'er Sheva → Tel Aviv; the first bus leaves 23:39. */
    private val saturdayNight: TripResult = run {
        val resp = MotisJson.decodeFromString(PlanResponse.serializer(), javaClass.getResource("/fixtures/plan_now_bs_hahagana.json")!!.readText())
        TripResult(TripPages.order(TripPages.sane(resp.itineraries), false), null)
    }
    private val bs = LatLon(31.262, 34.801)
    private val home = LatLon(32.053978, 34.78484) // the recorded destination
    private val homeKey = PlanCache.key("TRIP-NOW", bs, home)
    private val savedAt = il("2026-10-03T23:20")

    private fun entry(key: String, at: Instant = savedAt, value: TripResult = saturdayNight) = PlanCache.Entry(key, at, value)

    @Test fun `ends read both places from a Trip key and nothing else`() {
        val e = CacheLookup.ends(homeKey)!!
        assertEquals(31.262, e.from.lat, 1e-9)
        assertEquals(34.785, e.to.lon, 1e-9)
        assertNull(CacheLookup.ends(PlanCache.key("LAST-2026-10-03", bs, home)))
        assertNull(CacheLookup.ends(PlanCache.key("TRIP-NOW", bs, null)))
        assertNull(CacheLookup.ends("TRIP-NOW:garbage"))
    }

    @Test fun `a destination about 100 m from Home counts, 300 m does not`() {
        val near = LatLon(home.lat + 0.0008, home.lon) // ~90 m north
        val far = LatLon(home.lat + 0.0027, home.lon) // ~300 m north
        val now = il("2026-10-03T23:30")
        assertNotNull(CacheLookup.tripHome(listOf(entry(PlanCache.key("TRIP-NOW", bs, home))), near, now))
        assertNull(CacheLookup.tripHome(listOf(entry(homeKey)), far, now))
    }

    @Test fun `the first option still ahead, after a decode round trip`() {
        val cached = TripCacheJson.decode(TripCacheJson.encode(listOf(entry(homeKey))))
        val hit = CacheLookup.tripHome(cached, home, il("2026-10-03T23:30"))!!
        assertEquals(bs.lat, hit.from.lat, 1e-9)
        assertEquals(saturdayNight.itineraries.first(), hit.next)
    }

    @Test fun `the newest entry home wins, and one with nothing left gives way to an older one`() {
        val now = il("2026-10-03T23:30")
        val otherOrigin = LatLon(31.25, 34.79)
        val older = entry(homeKey, at = il("2026-10-03T22:00"))
        val newer = entry(PlanCache.key("TRIP-DEPART_AT", otherOrigin, home), at = il("2026-10-03T23:25"))
        assertEquals(31.25, CacheLookup.tripHome(listOf(older, newer), home, now)!!.from.lat, 1e-9)
        val emptyNewer = newer.copy(value = TripResult(emptyList(), null))
        assertEquals(31.262, CacheLookup.tripHome(listOf(emptyNewer, older), home, now)!!.from.lat, 1e-9)
    }

    @Test fun `every option gone - the entry still gives its origin, with no next option`() {
        val hit = CacheLookup.tripHome(listOf(entry(homeKey)), home, il("2026-10-04T15:00"))!!
        assertNull(hit.next)
        assertEquals(31.262, hit.from.lat, 1e-9)
    }

    @Test fun `entries going elsewhere and last-ride entries are ignored`() {
        val elsewhere = entry(PlanCache.key("TRIP-NOW", home, bs))
        val lastRide = entry(PlanCache.key("LAST-2026-10-03", bs, home))
        assertNull(CacheLookup.tripHome(listOf(elsewhere, lastRide), home, il("2026-10-03T23:30")))
    }

    @Test fun `where a tap plans from - fix, cached origin, nothing, no Home`() {
        val now = il("2026-10-03T23:30")
        val hit = CacheLookup.tripHome(listOf(entry(homeKey)), home, now)
        val fix = LatLon(31.25, 34.79)
        assertEquals(CacheLookup.Start.NoHome, CacheLookup.start(false, fix, hit)) // tile added before Home: Settings
        assertEquals(CacheLookup.Start.Here, CacheLookup.start(true, fix, hit)) // a fix wins over the cache
        assertEquals(CacheLookup.Start.FromCache(bs), CacheLookup.start(true, null, hit)) // permission denied
        assertEquals(CacheLookup.Start.Here, CacheLookup.start(true, null, null)) // nothing: the "no location" error
        // Every option gone still knows where the trip home starts.
        val gone = CacheLookup.tripHome(listOf(entry(homeKey)), home, il("2026-10-04T15:00"))
        assertEquals(CacheLookup.Start.FromCache(bs), CacheLookup.start(true, null, gone))
    }
}
