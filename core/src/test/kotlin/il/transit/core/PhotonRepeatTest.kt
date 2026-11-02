package il.transit.core

import il.transit.core.search.GuardedGeocoder
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/**
 * Phase 11 F6, T4's finding 2: "after 429 then 200, the same text typed again sends 0 to Transitous but 1 to
 * Photon". Traced: the fault matrix reads the typing search as answered from Transitous's interim answer, then
 * closes the search (`cancelEditing`) while Photon's guard is still waiting out `Retry-After`. A cancelled search
 * caches nothing (a stale answer must never land), so the same text later asks Photon once more.
 *
 * That is inside the Photon rules (CLAUDE.md): each typing pause sends at most 1 request plus the guard's one
 * retry, and the new request comes after the `Retry-After` the server asked for. Pinned here so a change that
 * sends more (a second retry, a retry outside the slot) or caches a cancelled answer fails.
 */
class PhotonRepeatTest {
    @Test fun `a search closed during the Retry-After wait sends 1, caches nothing, and the same text later sends exactly 1 more`() = runTest {
        val inner = FaultyGeocoder(Fault.Http(429, retryAfterSec = 1))
        val guard = GuardedGeocoder(inner, backoff = Duration.ofSeconds(2))

        val first = launch { guard.search("רגר א", "he", null) }
        runCurrent()
        assertEquals(1, inner.calls) // the refused request; the guard now waits 1 s for its one retry
        advanceTimeBy(500)
        first.cancel() // the user closed the search (the matrix's reset)
        runCurrent()
        assertEquals(1, inner.calls) // the retry is never sent once its search is gone
        assertEquals(0, inner.inFlight)

        advanceTimeBy(1_500) // past Retry-After
        assertEquals("רגר א", guard.search("רגר א", "he", null).single().name)
        assertEquals(2, inner.calls) // the repeat: one request, nothing was cached

        guard.search("רגר א", "he", null)
        assertEquals(2, inner.calls) // now the day cache answers
    }

    @Test fun `a search that waits out Retry-After to the end sends 2 and caches the answer`() = runTest {
        val inner = FaultyGeocoder(Fault.Http(429, retryAfterSec = 1))
        val guard = GuardedGeocoder(inner, backoff = Duration.ofSeconds(2))
        guard.search("רגר ב", "he", null)
        assertEquals(2, inner.calls)
        guard.search("רגר ב", "he", null)
        assertEquals(2, inner.calls) // the matrix's repeat sends 0 when the search was not closed early
    }
}
