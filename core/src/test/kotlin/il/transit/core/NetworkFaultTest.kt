package il.transit.core

import il.transit.core.api.BudgetExceededException
import il.transit.core.api.BudgetedTransitApi
import il.transit.core.api.Endpoint
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.MotisClient
import il.transit.core.api.PlanRequest
import il.transit.core.api.TransitApi
import il.transit.core.api.TransitHttpException
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import il.transit.core.search.GuardedGeocoder
import il.transit.core.search.PhotonClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.SocketTimeoutException
import java.time.Duration

/**
 * Phase 10 D4: what the guards do when the network misbehaves. Faults are injected at the
 * [TransitApi] / `BackupGeocoder` level ([FaultyTransitApi], [FaultyGeocoder]) and, for parsing, in
 * an OkHttp interceptor ([ScriptedHttp]); no socket is opened.
 */
class NetworkFaultTest {
    private suspend fun <T> refused(block: suspend () -> T): TransitHttpException = try {
        block()
        fail("expected a refusal")
        error("unreachable")
    } catch (e: TransitHttpException) {
        e
    }

    private fun TestScope.guarded(inner: TransitApi, maxConcurrent: Int = 2) =
        GuardedTransitApi(inner, maxConcurrent = maxConcurrent, backoff = Duration.ofSeconds(2))

    private val ok = """[{"type":"STOP","name":"רגר","id":"x","lat":31.26,"lon":34.8,"tokens":[],"areas":[],"score":1.0}]"""
    private val from = Endpoint.Coord(LatLon(31.26, 34.8))
    private val planReq = PlanRequest(from, Endpoint.Coord(LatLon(32.08, 34.8)), NOON)

    // ---- GuardedTransitApi + BudgetedTransitApi ------------------------------------------------

    @Test fun `429 then 200 - one retry, one answer, counted once against the budget`() = runTest {
        val inner = FaultyTransitApi(Fault.Http(429))
        val api = BudgetedTransitApi(guarded(inner), budget = 5)
        assertEquals("x", api.geocode("x").single().name)
        assertEquals(2, inner.calls) // the refusal + its one retry
        assertEquals(1, api.used)
        assertEquals(2_000, currentTime) // the default back-off, in virtual time
        api.geocode("x") // cached by the guard: no third call. It is still asked for, so it counts.
        assertEquals(2, inner.calls)
        assertEquals(2, api.used)
    }

    @Test fun `Retry-After is waited out, not the default back-off`() = runTest {
        val inner = FaultyTransitApi(Fault.Http(429, retryAfterSec = 5))
        guarded(inner).plan(planReq)
        assertEquals(5_000, currentTime)
        assertEquals(2, inner.calls)
    }

    @Test fun `503 twice - fails after one retry, never a third call, nothing cached`() = runTest {
        val inner = FaultyTransitApi(Fault.Http(503), Fault.Http(503))
        val api = BudgetedTransitApi(guarded(inner), budget = 5)
        assertEquals(503, refused { api.plan(planReq) }.code)
        assertEquals(2, inner.calls)
        assertEquals(1, api.used) // the retry is the guard's, not a second ask
        // The failure was not cached: the same request goes out again, and now works.
        api.plan(planReq)
        assertEquals(3, inner.calls)
    }

    @Test fun `a refusal that asks for a long wait is not waited out`() = runTest {
        val inner = FaultyTransitApi(Fault.Http(429, retryAfterSec = 3_600))
        val e = refused { guarded(inner).plan(planReq) }
        assertEquals(429, e.code)
        assertEquals(1, inner.calls) // asked to stay away for an hour: we do, and the search says so now
        assertEquals(0, currentTime) // …instead of keeping a slot asleep for an hour
    }

    @Test fun `other errors are not retried`() = runTest {
        for (code in listOf(400, 404, 500, 502)) {
            val inner = FaultyTransitApi(Fault.Http(code))
            assertEquals(code, refused { guarded(inner).plan(planReq) }.code)
            assertEquals("HTTP $code", 1, inner.calls)
        }
    }

    @Test fun `a slow answer past the deadline is an error, nothing cached, the slot freed`() = runTest {
        val inner = FaultyTransitApi(Fault.Slow(60_000))
        val api = guarded(inner, maxConcurrent = 1)
        try {
            withTimeout(5_000) { api.plan(planReq) }
            fail("expected the deadline")
        } catch (e: TimeoutCancellationException) {
            // the caller's deadline, as the search screens use
        }
        assertEquals(0, inner.inFlight)
        // Only slot is free again, and the same request is asked again (nothing cached).
        withTimeout(5_000) { api.plan(planReq) }
        assertEquals(2, inner.calls)
    }

    @Test fun `the network's own read timeout is an error - not retried, not cached, slot freed`() = runTest {
        val inner = FaultyTransitApi(Fault.Timeout)
        val api = guarded(inner, maxConcurrent = 1)
        try {
            api.plan(planReq)
            fail("expected a timeout")
        } catch (e: SocketTimeoutException) {
            // propagates as it is: the screens show their "no connection" state
        }
        assertEquals(1, inner.calls)
        api.plan(planReq)
        assertEquals(2, inner.calls)
    }

    @Test fun `cancelled mid-call - nothing cached, the slot freed, the next call works (every endpoint)`() = runTest {
        val box = BBox(LatLon(31.0, 34.0), LatLon(32.0, 35.0))
        val calls: List<Pair<String, suspend (TransitApi) -> Any>> = listOf(
            "plan" to { a -> a.plan(planReq) },
            "oneToMany" to { a -> a.oneToMany(from.at, listOf(LatLon(31.3, 34.8)), "CAR", 600, false) },
            "stops" to { a -> a.stops(box) },
            "geocode" to { a -> a.geocode("x") },
            "reverseGeocode" to { a -> a.reverseGeocode(from.at) },
            "stopTimes" to { a -> a.stopTimes("s1") },
            "trip" to { a -> a.trip("t1") },
            "mapTrips" to { a -> a.mapTrips(box, NOON, NOON.plusSeconds(60), 14.0) },
        )
        for ((name, call) in calls) {
            val inner = FaultyTransitApi(Fault.Hang)
            val api = guarded(inner, maxConcurrent = 1)
            val first = launch { call(api) }
            runCurrent()
            assertEquals(name, 1, inner.inFlight)
            first.cancel()
            runCurrent()
            assertTrue(name, first.isCancelled)
            assertEquals(name, 0, inner.inFlight)
            call(api) // the only slot is free, and the cancelled call left no answer behind
            assertEquals(name, 2, inner.calls)
        }
    }

    @Test fun `cancelled while waiting out a 429 - the slot is freed and the retry never goes out`() = runTest {
        val inner = FaultyTransitApi(Fault.Http(429, retryAfterSec = 5))
        val api = guarded(inner, maxConcurrent = 1)
        val first = launch { api.plan(planReq) }
        advanceTimeBy(1_000)
        first.cancel()
        runCurrent()
        assertEquals(1, inner.calls)
        api.plan(planReq)
        assertEquals(2, inner.calls)
    }

    @Test fun `3 calls at once with a limit of 2 - the third waits`() = runTest {
        val inner = FaultyTransitApi(Fault.Slow(100), Fault.Slow(100), Fault.Slow(100))
        val api = guarded(inner, maxConcurrent = 2)
        val done = (1..3).map { i -> async { api.geocode("q$i"); currentTime } }
        runCurrent()
        assertEquals(2, inner.calls)
        assertEquals(2, inner.inFlight)
        advanceUntilIdle()
        assertEquals(3, inner.calls)
        assertEquals(2, inner.maxInFlight)
        assertEquals(listOf(100L, 100L, 200L), done.awaitAll().sorted())
    }

    @Test fun `a retry happens inside its slot - the limit holds while it waits`() = runTest {
        val inner = FaultyTransitApi(Fault.Http(429), Fault.Slow(10), Fault.Slow(10))
        val api = guarded(inner, maxConcurrent = 1)
        val a = async { api.geocode("a") }
        val b = async { api.geocode("b") }
        advanceUntilIdle()
        a.await(); b.await()
        assertEquals(1, inner.maxInFlight)
    }

    // ---- BudgetedTransitApi --------------------------------------------------------------------

    @Test fun `failed requests count against the budget, and the cap holds`() = runTest {
        val inner = FaultyTransitApi(Fault.Http(500), Fault.Http(500))
        val api = BudgetedTransitApi(guarded(inner), budget = 2)
        refused { api.plan(planReq) }
        refused { api.plan(planReq) }
        try {
            api.plan(planReq)
            fail("expected the budget to stop it")
        } catch (e: BudgetExceededException) {
            assertEquals(2, e.budget)
        }
        assertEquals(2, inner.calls) // the third never left the app
    }

    @Test fun `20 callers on real threads - exactly the budget gets through`() = runBlocking {
        val inner = FaultyTransitApi()
        val api = BudgetedTransitApi(inner, budget = 3)
        val results = (1..20).map { i ->
            async(Dispatchers.Default) { runCatching { api.geocode("q$i") }.exceptionOrNull() }
        }.awaitAll()
        assertEquals(3, results.count { it == null })
        assertEquals(17, results.count { it is BudgetExceededException })
        assertEquals(3, inner.calls)
        assertEquals(3, api.used)
    }

    // ---- MotisClient: what comes back off the wire ---------------------------------------------

    private fun client(http: ScriptedHttp) = MotisClient("https://motis.test", http.client)

    @Test fun `malformed or empty bodies are a clear error, never a crash, never cached`() = runBlocking {
        val bodies = listOf("", "  ", "{", """[{"type":""", "<html><h1>502 Bad Gateway</h1></html>", "null", "{}", "42")
        for (body in bodies) {
            val http = ScriptedHttp(Reply(200, body))
            val api = GuardedTransitApi(client(http), backoff = Duration.ZERO)
            repeat(2) {
                try {
                    api.geocode("x")
                    fail("body '$body' should not parse as an answer")
                } catch (e: IllegalArgumentException) {
                    // kotlinx's SerializationException: names what it expected and the position
                    assertNotNull(e.message)
                }
            }
            assertEquals("'$body' was cached", 2, http.count)
        }
    }

    @Test fun `a plan answer that is not JSON of the right shape is an error too`() = runBlocking {
        for (body in listOf("", "[]", "{", "not json", """{"itineraries": 5}""")) {
            val http = ScriptedHttp(Reply(200, body))
            val api = GuardedTransitApi(client(http), backoff = Duration.ZERO)
            try {
                api.plan(planReq)
                fail("body '$body' should not parse as a plan")
            } catch (e: IllegalArgumentException) {
                assertNotNull(e.message)
            }
            try { api.plan(planReq) } catch (_: IllegalArgumentException) {}
            assertEquals("'$body' was cached", 2, http.count)
        }
    }

    @Test fun `one-to-many with the wrong number of answers is an error, not a shifted list`() = runBlocking {
        val http = ScriptedHttp(Reply(200, """[{"duration":60.0}]"""))
        val many = listOf(LatLon(31.3, 34.8), LatLon(31.4, 34.8))
        try {
            client(http).oneToMany(from.at, many, "CAR", 600, false)
            fail("expected a mismatch error")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("1 for 2"))
        }
        // …and a path-less entry is null, not a crash.
        val partial = ScriptedHttp(Reply(200, """[{"duration":60.0},{}]"""))
        assertEquals(listOf(60, null), client(partial).oneToMany(from.at, many, "CAR", 600, false))
    }

    @Test fun `refusals carry the code and Retry-After; an HTTP-date Retry-After is ignored`() = runBlocking {
        val http = ScriptedHttp(
            Reply(429, "slow down", mapOf("Retry-After" to "7")),
            Reply(503, "x".repeat(1_000), mapOf("Retry-After" to "Wed, 21 Oct 2026 07:28:00 GMT")),
            Reply(404, ""),
        )
        val api = client(http)
        refused { api.geocode("x") }.let { assertEquals(429, it.code); assertEquals(7, it.retryAfterSec) }
        refused { api.geocode("x") }.let {
            assertEquals(503, it.code)
            assertNull(it.retryAfterSec)
            assertTrue(it.message!!.length < 400) // the body is cut, so a log line stays a line
        }
        assertEquals(404, refused { api.geocode("x") }.code)
    }

    @Test fun `a network failure from OkHttp reaches the caller as an IOException`() = runBlocking {
        val http = ScriptedHttp(Reply(fail = SocketTimeoutException("timeout")), Reply(200, ok))
        val api = GuardedTransitApi(client(http), backoff = Duration.ZERO)
        try {
            api.geocode("x")
            fail("expected a timeout")
        } catch (e: java.io.IOException) {
            assertEquals("timeout", e.message)
        }
        assertEquals("רגר", api.geocode("x").single().name) // not cached: asked again
        assertEquals(2, http.count)
    }

    @Test fun `the whole stack - 429 then 200 is two requests on the wire, one against the budget, both with our User-Agent`() = runBlocking {
        val http = ScriptedHttp(Reply(429, "", mapOf("Retry-After" to "0")), Reply(200, ok))
        val api = BudgetedTransitApi(GuardedTransitApi(client(http)), budget = 4)
        assertEquals("רגר", api.geocode("רגר").single().name)
        assertEquals(2, http.count)
        assertEquals(1, api.used)
        assertTrue(http.requests.all { it.header("User-Agent") == MotisClient.USER_AGENT })
    }

    // ---- GuardedGeocoder (+ Photon's parsing) --------------------------------------------------

    private fun TestScope.geoGuard(inner: FaultyGeocoder, maxConcurrent: Int = 2) =
        GuardedGeocoder(inner, maxConcurrent = maxConcurrent, backoff = Duration.ofSeconds(2))

    @Test fun `geocoder - 429 then 200 is one retry and one answer, then cached`() = runTest {
        val inner = FaultyGeocoder(Fault.Http(429))
        val guard = geoGuard(inner)
        assertEquals("x", guard.search("x", "he", null).single().name)
        assertEquals(2, inner.calls)
        assertEquals(2_000, currentTime)
        guard.search("x", "he", null)
        assertEquals(2, inner.calls)
    }

    @Test fun `geocoder - 503 twice fails after one retry, never a third call, nothing cached`() = runTest {
        val inner = FaultyGeocoder(Fault.Http(503), Fault.Http(503))
        val guard = geoGuard(inner)
        assertEquals(503, refused { guard.search("x", "he", null) }.code)
        assertEquals(2, inner.calls)
        guard.search("x", "he", null)
        assertEquals(3, inner.calls)
    }

    @Test fun `geocoder - a long Retry-After is not waited out, other errors are not retried`() = runTest {
        val long = FaultyGeocoder(Fault.Http(429, retryAfterSec = 3_600))
        assertEquals(429, refused { geoGuard(long).search("x", "he", null) }.code)
        assertEquals(1, long.calls)
        assertEquals(0, currentTime)
        val bad = FaultyGeocoder(Fault.Http(400))
        assertEquals(400, refused { geoGuard(bad).search("x", "he", null) }.code)
        assertEquals(1, bad.calls)
    }

    @Test fun `geocoder - a slow answer past the deadline frees the slot and caches nothing`() = runTest {
        val inner = FaultyGeocoder(Fault.Slow(60_000))
        val guard = geoGuard(inner, maxConcurrent = 1)
        try {
            withTimeout(4_000) { guard.search("x", "he", null) }
            fail("expected the deadline")
        } catch (e: TimeoutCancellationException) {
        }
        assertEquals(0, inner.inFlight)
        withTimeout(4_000) { guard.search("x", "he", null) }
        assertEquals(2, inner.calls)
    }

    @Test fun `geocoder - cancelled mid-call frees the slot, caches nothing; reverse names too`() = runTest {
        val inner = FaultyGeocoder(Fault.Hang, Fault.Hang)
        val guard = geoGuard(inner, maxConcurrent = 1)
        val search = launch { guard.search("x", "he", null) }
        runCurrent()
        search.cancel()
        runCurrent()
        assertEquals(0, inner.inFlight)
        val reverse = launch { guard.reverseName(LatLon(32.0, 34.8), "he") }
        runCurrent()
        assertEquals(1, inner.inFlight)
        reverse.cancel()
        runCurrent()
        assertEquals(0, inner.inFlight)
        assertEquals("x", guard.search("x", "he", null).single().name)
        assertEquals("here", guard.reverseName(LatLon(32.0, 34.8), "he"))
        assertEquals(4, inner.calls)
    }

    @Test fun `geocoder - a failing reverse name frees the slot`() = runTest {
        val inner = FaultyGeocoder(Fault.Timeout)
        val guard = geoGuard(inner, maxConcurrent = 1)
        try {
            guard.reverseName(LatLon(32.0, 34.8), "he")
            fail("expected a timeout")
        } catch (e: SocketTimeoutException) {
        }
        assertEquals("here", guard.reverseName(LatLon(32.0, 34.8), "he"))
    }

    @Test fun `geocoder - 3 at once with a limit of 2, the third waits`() = runTest {
        val inner = FaultyGeocoder(Fault.Slow(100), Fault.Slow(100), Fault.Slow(100))
        val guard = geoGuard(inner, maxConcurrent = 2)
        val done = (1..3).map { i -> async { guard.search("q$i", "he", null); currentTime } }
        runCurrent()
        assertEquals(2, inner.inFlight)
        advanceUntilIdle()
        assertEquals(2, inner.maxInFlight)
        assertEquals(listOf(100L, 100L, 200L), done.awaitAll().sorted())
    }

    @Test fun `Photon - malformed or empty bodies are a clear error and are not cached`() = runBlocking {
        for (body in listOf("", "{", "<html>Service Unavailable</html>", "[]", """{"features": 3}""")) {
            val http = ScriptedHttp(Reply(200, body))
            val guard = GuardedGeocoder(PhotonClient("https://photon.test", http.client), backoff = Duration.ZERO)
            repeat(2) {
                try {
                    guard.search("x", "he", null)
                    fail("body '$body' should not parse")
                } catch (e: IllegalArgumentException) {
                    assertNotNull(e.message)
                }
            }
            assertEquals("'$body' was cached", 2, http.count)
        }
    }

    @Test fun `Photon - features it cannot place are dropped, an empty answer is just empty`() = runBlocking {
        val body = """{"features":[
            {"geometry":{"coordinates":[34.8]},"properties":{"name":"no latitude"}},
            {"geometry":{"coordinates":[34.8,32.0]},"properties":{}},
            {"geometry":{"coordinates":[34.8,32.0]},"properties":{"name":"ok"}}]}"""
        val got = PhotonClient("https://photon.test", ScriptedHttp(Reply(200, body)).client).search("x", "he", null)
        assertEquals(listOf("ok"), got.map { it.name })
        val none = PhotonClient("https://photon.test", ScriptedHttp(Reply(200, """{"features":[]}""")).client)
        assertTrue(none.search("x", "he", null).isEmpty())
    }

    @Test fun `Photon - 429 through the guard is one retry, our User-Agent on both`() = runBlocking {
        val http = ScriptedHttp(Reply(429, "", mapOf("Retry-After" to "0")), Reply(200, """{"features":[{"geometry":{"coordinates":[34.8,32.0]},"properties":{"name":"ok"}}]}"""))
        val guard = GuardedGeocoder(PhotonClient("https://photon.test", http.client), backoff = Duration.ZERO)
        assertEquals("ok", guard.search("x", "he", null).single().name)
        assertEquals(2, http.count)
        assertTrue(http.requests.all { it.header("User-Agent") == MotisClient.USER_AGENT })
        assertFalse(http.requests.any { it.header("Retry-After") != null })
    }

    @Test fun `a cancellation is never swallowed as an error`() = runTest {
        val inner = FaultyTransitApi(Fault.Hang)
        val job = async { runCatching { guarded(inner).plan(planReq) } }
        runCurrent()
        job.cancel()
        try {
            job.await()
            fail("expected cancellation")
        } catch (e: CancellationException) {
        }
    }
}
