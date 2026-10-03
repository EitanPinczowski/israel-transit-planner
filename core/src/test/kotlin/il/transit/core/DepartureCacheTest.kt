package il.transit.core

import il.transit.core.api.MotisJson
import il.transit.core.api.StopTimesResponse
import il.transit.core.present.DepartureCache
import il.transit.core.present.DepartureCacheJson
import il.transit.core.present.departureRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DepartureCacheTest {
    // Be'er Sheva North, recorded: ten trains from 08:10 to 09:10 Israel time (05:10Z–06:10Z).
    private val rows = MotisJson.decodeFromString(
        StopTimesResponse.serializer(),
        javaClass.getResource("/fixtures/stoptimes_beersheva_north.json")!!.readText(),
    ).stopTimes.map(::departureRow)
    private val stop = "il-Israel-MOT_37314"
    private val saved = Instant.parse("2026-10-05T05:00:00Z")

    @Test fun `offline, only the departures still ahead are shown, without live data`() {
        val c = DepartureCache().apply { put(stop, rows, saved) }
        val (at, all) = c.upcoming(stop, saved)!!
        assertEquals(saved, at)
        assertEquals(10, all.size)
        val (_, later) = c.upcoming(stop, Instant.parse("2026-10-05T05:50:00Z"))!! // 08:50
        assertEquals(listOf("08:57", "09:04", "09:10"), later.map { it.time })
        assertTrue(later.all { it.delayMin == null && !it.cancelled })
        assertNull(c.upcoming(stop, Instant.parse("2026-10-05T07:00:00Z"))) // all gone
        assertNull(c.upcoming("other", saved))
    }

    @Test fun `round-trips, keeps the newest stops, and garbage restores as empty`() {
        val c = DepartureCache(maxStops = 2)
        c.put("a", rows, saved); c.put("b", rows, saved); c.put(stop, rows, saved)
        assertEquals(listOf("b", stop), c.all.map { it.stopId })
        val back = DepartureCache().apply { restore(DepartureCacheJson.decode(DepartureCacheJson.encode(c.all))) }
        assertEquals(rows.map { it.headsign }, back.upcoming(stop, saved)!!.second.map { it.headsign })
        assertTrue(DepartureCacheJson.decode("[{").isEmpty())
    }
}
