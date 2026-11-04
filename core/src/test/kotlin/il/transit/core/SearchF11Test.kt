package il.transit.core

import il.transit.core.api.GeocodeMatch
import il.transit.core.api.TransitHttpException
import il.transit.core.geo.LatLon
import il.transit.core.search.BackupGeocoder
import il.transit.core.search.PlaceSearch
import il.transit.core.search.SearchTrace
import il.transit.core.search.TypingSearch
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset

/**
 * Phase 11 F11 (owner's bug bash A4): "TLV", "Rehovot railway station" and "Tel Aviv Central Bus Station" found nothing
 * on the Pixel with location off. A Transitous failure threw the bundled train station away with it; and the next bug
 * bash keeps one line of what each stage answered ([SearchTrace]).
 */
class SearchF11Test {
    private fun down() = FakeTransitApi().apply { onGeocode = { throw IOException("unreachable") } }

    @Test fun `a Transitous failure keeps the bundled station`() = runTest {
        val answer = PlaceSearch(down(), null).search("Rehovot railway station", "en", null)
        assertEquals(1, answer.stations.size)
        assertTrue(answer.exact.isNotEmpty())
        assertFalse(answer.notFound)
        // The list still says "No connection", with Retry.
        assertTrue(answer.transitFailed)
    }

    @Test fun `a trace line stays one line`() {
        val t = SearchTrace("Herzl\n12\t Be'er  Sheva", "en", hasFix = true)
        val line = t.line(Instant.parse("2026-11-03T19:05:00Z"), ZoneOffset.UTC)
        assertFalse(line, line.contains('\n') || line.contains('\t'))
        assertTrue(line, "\"Herzl 12 Be'er Sheva\"" in line)
    }

    @Test fun `a Transitous failure with no station is still the search's failure`() = runTest {
        try {
            PlaceSearch(down(), null).search("TLV", "en", null)
            fail("expected the Transitous error")
        } catch (e: IOException) {
            assertEquals("unreachable", e.message)
        }
    }

    @Test fun `the trace says what each stage answered`() = runTest {
        val api = FakeTransitApi().apply { onGeocode = { listOf(GeocodeMatch("PLACE", "Somewhere", "x", 40.0, 30.0)) } } // abroad
        val slow = object : BackupGeocoder {
            override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
                delay(PlaceSearch.BACKUP_DEADLINE_MS + 1_000)
                return emptyList()
            }
        }
        val trace = SearchTrace("Herzl 12 Somewhere", "en", hasFix = false)
        val answer = PlaceSearch(api, slow).search("Herzl 12 Somewhere", "en", null, trace = trace)
        assertTrue(answer.notFound)
        assertEquals(1, trace.transitRaw)
        assertEquals(0, trace.transitKept)
        assertEquals("timeout", trace.backup)
        val line = trace.line(Instant.parse("2026-11-03T19:05:00Z"), ZoneOffset.ofHours(2))
        assertTrue(line, line.startsWith("2026-11-03 21:05 · \"Herzl 12 Somewhere\" · en · fix no · Transitous 1 (0 kept)"))
        assertTrue(line, "backup timeout (0 kept)" in line)
        // No coordinates: neither the answer's nor any bias.
        assertFalse(line, Regex("\\d+\\.\\d+").containsMatchIn(line))
    }

    @Test fun `a failed typing search leaves its trace with the error`() = runTest {
        var failed: Boolean? = null
        val typing = TypingSearch(this, PlaceSearch(FakeTransitApi().apply { onGeocode = { throw TransitHttpException(503, "busy") } }, null)) { _, _, f -> failed = f }
        typing.onText("TLV", "en") { null }
        advanceUntilIdle()
        assertEquals(true, failed)
        val t = checkNotNull(typing.lastTrace)
        assertEquals("failed", t.outcome)
        assertEquals("TransitHttpException", t.transitError)
        assertFalse(t.hasFix)
    }
}
