package il.transit.core

import il.transit.core.api.GeocodeMatch
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.MotisClient
import il.transit.core.api.TransitApi
import il.transit.core.geo.LatLon
import il.transit.core.search.PlaceSearch
import il.transit.core.search.SearchAnswer
import il.transit.core.search.TypingSearch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Phase 10 D2: stale searches are cancelled end to end, and an old answer never replaces a newer query's. */
class TypingSearchTest {
    private fun match(name: String) = GeocodeMatch("PLACE", name, name, 32.0, 34.8)

    @Test fun `five keystrokes in 300 ms send one geocode`() = runTest {
        val api = FakeTransitApi().apply { onGeocode = { listOf(match(it)) } }
        val answers = mutableListOf<String>()
        val typing = TypingSearch(this, PlaceSearch(api, null)) { q, _, _ -> answers += q }
        for (t in listOf("רמ", "רמת", "רמת ", "רמת א", "רמת אב")) {
            typing.onText(t, "he") { null }
            advanceTimeBy(60)
        }
        advanceUntilIdle()
        assertEquals(listOf("geocode"), api.calls)
        assertEquals(listOf("רמת אב"), answers)
    }

    /**
     * A geocoder that ignores cancellation (like the phone's blocking Geocoder): its answer
     * comes back after the user typed on. The sequence number drops it.
     */
    private class BlockingApi : TransitApi by FakeTransitApi() {
        val waiting = mutableMapOf<String, Continuation<List<GeocodeMatch>>>()
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> =
            if (text.startsWith("slow")) suspendCoroutine { waiting[text] = it } else listOf(GeocodeMatch("PLACE", text, text, 32.0, 34.8))
    }

    @Test fun `a slow first answer arriving after the second is dropped`() = runTest {
        val api = BlockingApi()
        val shown = mutableListOf<Pair<String, SearchAnswer>>()
        val typing = TypingSearch(this, PlaceSearch(api, null)) { q, a, _ -> shown += q to a }
        typing.onText("slow one", "he") { null }
        advanceTimeBy(TypingSearch.DEBOUNCE_MS + 1)
        runCurrent()
        assertTrue("slow one" in api.waiting)
        typing.onText("fast two", "he") { null }
        advanceUntilIdle()
        assertEquals(listOf("fast two"), shown.map { it.first })
        // The old query's answer finally arrives: never shown.
        api.waiting.getValue("slow one").resume(listOf(match("old")))
        advanceUntilIdle()
        assertEquals(listOf("fast two"), shown.map { it.first })
        // Closing the search drops an answer still on its way, too.
        typing.onText("slow three", "he") { null }
        advanceTimeBy(TypingSearch.DEBOUNCE_MS + 1)
        runCurrent()
        typing.cancel()
        api.waiting.getValue("slow three").resume(listOf(match("late")))
        advanceUntilIdle()
        assertEquals(listOf("fast two"), shown.map { it.first })
    }

    /** Never answers; counts calls. */
    private class HangingApi : TransitApi by FakeTransitApi() {
        var calls = 0
        val started = CompletableDeferred<Unit>()
        var hang = true
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
            calls++
            started.complete(Unit)
            if (hang) awaitCancellation()
            return listOf(GeocodeMatch("PLACE", text, text, 32.0, 34.8))
        }
    }

    @Test fun `a cancelled call frees its slot and leaves nothing in the cache`() = runBlocking {
        val inner = HangingApi()
        val guard = GuardedTransitApi(inner, maxConcurrent = 1)
        val first = launch(Dispatchers.Default) { guard.geocode("רמת אביב", "he", null, 8) }
        inner.started.await()
        first.cancelAndJoin()
        inner.hang = false
        // The only slot is free again, and the same query is asked again (nothing cached).
        val again = withTimeout(2_000) { guard.geocode("רמת אביב", "he", null, 8) }
        assertEquals("רמת אביב", again.single().name)
        assertEquals(2, inner.calls)
    }

    @Test fun `cancelling the coroutine cancels the HTTP call`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.start()
        try {
            val client = MotisClient(server.url("/").toString())
            val call = async(Dispatchers.Default) { client.geocode("רמת אביב", "he", null, 8) }
            server.takeRequest(5, TimeUnit.SECONDS)!!
            val t0 = System.nanoTime()
            call.cancelAndJoin()
            // A blocking execute() would hold on until the read timeout (20 s).
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 2_000)
            assertTrue(call.isCancelled)
        } finally {
            server.shutdown()
        }
    }
}
