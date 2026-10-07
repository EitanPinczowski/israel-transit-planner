package il.transit.core

import il.transit.core.api.GeocodeMatch
import il.transit.core.api.MotisClient
import il.transit.core.api.MotisJson
import il.transit.core.api.TransitHttpException
import il.transit.core.geo.LatLon
import il.transit.core.present.geocodeTown
import il.transit.core.search.BackupGeocoder
import il.transit.core.search.GuardedGeocoder
import il.transit.core.search.PhotonClient
import il.transit.core.search.PlaceSearch
import il.transit.core.search.SearchAnswer
import il.transit.core.search.TypingSearch
import il.transit.core.search.parsePhoton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** S3: Transitous first, Photon only after a miss, one request at most, merged below. */
class PlaceSearchTest {
    private fun text(name: String) = javaClass.getResource("/fixtures/$name.json")!!.readText()
    private fun transitous(name: String): List<GeocodeMatch> =
        MotisJson.decodeFromString(ListSerializer(GeocodeMatch.serializer()), text(name))

    /** Photon fixtures are hand-written from Photon's documented GeoJSON: this environment can't reach photon.komoot.io. */
    private fun photon(name: String) = parsePhoton(text(name))

    private class FakeBackup(var answer: (String) -> List<GeocodeMatch> = { emptyList() }) : BackupGeocoder {
        val calls: MutableList<String> = Collections.synchronizedList(ArrayList())
        override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
            calls += text
            return answer(text)
        }
    }

    private val raanana = LatLon(32.1801, 34.8712)

    // --- when the backup is asked -------------------------------------------------------

    @Test fun `the owner's home is a miss - Transitous answers only other places in Ra'anana`() {
        // Recorded 2026-10-07: "טבנקין 15 רעננה" near Ra'anana, placeBias 10.
        val found = transitous("geocode_tabenkin_raanana")
        assertTrue(found.isNotEmpty())
        assertTrue(found.none { it.name.contains("טבנקין") })
        assertTrue(PlaceSearch.needsBackup("טבנקין 15 רעננה", found))
        assertTrue(PlaceSearch.needsBackup("רחוב טבנקין רעננה", found))
    }

    @Test fun `a street Transitous knows is a hit, the typed town and the number don't count`() {
        // Recorded 2026-10-05: "הרצל חיפה".
        assertFalse(PlaceSearch.needsBackup("הרצל חיפה", transitous("geocode_herzl_haifa")))
        // Number + town words + "street" are not what is looked for.
        assertFalse(PlaceSearch.needsBackup("רחוב הרצל 12 חיפה", transitous("geocode_herzl_haifa")))
    }

    @Test fun `a short loose match is a miss, too short is never sent`() {
        // Recorded 2026-10-03: "רגר" → Hagar, Rigba… Rager Blvd is not among them.
        assertTrue(PlaceSearch.needsBackup("רגר", transitous("geocode_rager")))
        assertFalse(PlaceSearch.needsBackup("רג", emptyList()))
        assertTrue(PlaceSearch.needsBackup("טבנקין", emptyList()))
    }

    @Test fun `only a town typed is a hit when Transitous has it`() {
        val found = transitous("geocode_tabenkin_raanana")
        assertFalse(PlaceSearch.needsBackup("רעננה", found))
    }

    // --- the search ---------------------------------------------------------------------

    @Test fun `a miss asks the backup once and puts its answers below Transitous's`() = runTest {
        val api = FakeTransitApi().apply { onGeocode = { transitous("geocode_tabenkin_raanana") } }
        val backup = FakeBackup { photon("photon_handwritten_tabenkin_raanana") }
        val answer = PlaceSearch(api, backup).search("טבנקין 15 רעננה", "he", raanana)

        assertEquals(listOf("geocode"), api.calls)
        assertEquals(PlaceSearch.BACKUP_PER_SEARCH, backup.calls.size)
        assertEquals(transitous("geocode_tabenkin_raanana").size, answer.primary.size)
        assertEquals(answer.primary + answer.backup, answer.all)
        val home = answer.backup.first()
        assertEquals("טבנקין 15", home.name)
        assertEquals("ADDRESS", home.type)
        assertEquals("רעננה", geocodeTown(home))
    }

    @Test fun `a hit never asks the backup`() = runTest {
        val api = FakeTransitApi().apply { onGeocode = { transitous("geocode_herzl_haifa") } }
        val backup = FakeBackup { fail("asked the backup after a hit"); emptyList() }
        val answer = PlaceSearch(api, backup).search("הרצל חיפה", "he", raanana)
        assertTrue(answer.backup.isEmpty())
        assertTrue(backup.calls.isEmpty())
    }

    @Test fun `a failing backup leaves Transitous's answers`() = runTest {
        val api = FakeTransitApi().apply { onGeocode = { transitous("geocode_tabenkin_raanana") } }
        val backup = FakeBackup { throw TransitHttpException(503, "busy") }
        val answer = PlaceSearch(api, backup).search("טבנקין 15 רעננה", "he", raanana)
        assertEquals(transitous("geocode_tabenkin_raanana").size, answer.primary.size)
        assertTrue(answer.backup.isEmpty())
        assertEquals(1, backup.calls.size)
    }

    @Test fun `a failing Transitous fails the search and does not reach the backup`() = runTest {
        val api = FakeTransitApi().apply { onGeocode = { throw TransitHttpException(500, "down") } }
        val backup = FakeBackup()
        try {
            PlaceSearch(api, backup).search("טבנקין 15 רעננה", "he", raanana)
            fail("expected the Transitous error")
        } catch (e: TransitHttpException) {
            assertEquals(500, e.code)
        }
        assertTrue(backup.calls.isEmpty())
    }

    @Test fun `backup answers Transitous already gave are dropped`() = runTest {
        val same = GeocodeMatch("PLACE", "בית עיריית רעננה", "x", 32.1840, 34.8710)
        val api = FakeTransitApi().apply { onGeocode = { listOf(same) } }
        val near = same.copy(id = "photon:N1", lat = 32.1845) // ~55 m away, same name
        val other = GeocodeMatch("ADDRESS", "טבנקין 15", "photon:N2", 32.1893, 34.8668)
        val answer = PlaceSearch(api, FakeBackup { listOf(near, other) }).search("טבנקין עירייה", "he", raanana)
        assertEquals(listOf(other), answer.backup)
    }

    @Test fun `no backup configured is Transitous alone`() = runTest {
        val api = FakeTransitApi().apply { onGeocode = { transitous("geocode_tabenkin_raanana") } }
        assertTrue(PlaceSearch(api, null).search("טבנקין 15 רעננה", "he", raanana).backup.isEmpty())
    }

    @Test fun `the bias sent to the backup is rounded so the day cache hits`() {
        assertEquals(LatLon(32.2, 34.9), PlaceSearch.roundBias(LatLon(32.1801, 34.8712)))
    }

    // --- debounce: one pause, at most one request of each ------------------------------

    @Test fun `typing the whole address costs one Transitous and one Photon request`() = runTest {
        val api = FakeTransitApi().apply { onGeocode = { transitous("geocode_tabenkin_raanana") } }
        val backup = FakeBackup { photon("photon_handwritten_tabenkin_raanana") }
        val answers = mutableListOf<Pair<String, SearchAnswer>>()
        val typing = TypingSearch(this, PlaceSearch(api, backup)) { q, a, _ -> answers += q to a }
        val full = "טבנקין 15 רעננה"
        for (i in 1..full.length) {
            typing.onText(full.take(i), "he") { raanana }
            advanceTimeBy(120) // a fast typist: never a pause
        }
        advanceUntilIdle()
        assertEquals(listOf("geocode"), api.calls)
        assertEquals(listOf(full), backup.calls)
        assertEquals(1, answers.size)
        assertEquals("טבנקין 15", answers.single().second.backup.first().name)
    }

    @Test fun `each pause is one search, and a cleared box cancels the waiting one`() = runTest {
        val api = FakeTransitApi().apply { onGeocode = { emptyList() } }
        val backup = FakeBackup()
        val typing = TypingSearch(this, PlaceSearch(api, backup)) { _, _, _ -> }
        typing.onText("טבנ", "he") { null }
        advanceTimeBy(TypingSearch.DEBOUNCE_MS + 1)
        typing.onText("טבנקין", "he") { null }
        advanceTimeBy(TypingSearch.DEBOUNCE_MS + 1)
        typing.onText("טבנקין ר", "he") { null }
        typing.onText("", "he") { null } // cleared before the pause ended
        advanceUntilIdle()
        assertEquals(2, api.calls.size)
        assertEquals(listOf("טבנ", "טבנקין"), backup.calls)
    }

    @Test fun `a failed search answers empty and says so`() = runTest {
        val api = FakeTransitApi().apply { onGeocode = { throw TransitHttpException(500, "down") } }
        var failed: Boolean? = null
        TypingSearch(this, PlaceSearch(api, FakeBackup())) { _, a, f -> failed = f; assertTrue(a.all.isEmpty()) }
            .onText("טבנקין", "he") { null }
        advanceUntilIdle()
        assertEquals(true, failed)
    }

    // --- the guard ----------------------------------------------------------------------

    private val clock = Clock.fixed(Instant.parse("2026-10-07T09:00:00Z"), ZoneOffset.UTC)

    @Test fun `the guard caches a day and retries once on 429`() = runTest {
        var n = 0
        val inner = object : BackupGeocoder {
            override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
                n++
                if (n == 1) throw TransitHttpException(429, "slow down", retryAfterSec = 1)
                return photon("photon_handwritten_tabenkin_raanana")
            }
        }
        val guard = GuardedGeocoder(inner, clock)
        guard.search("טבנקין 15 רעננה", "he", null)
        guard.search("טבנקין 15 רעננה", "he", null)
        assertEquals(2, n) // the refused one + its single retry; the second search is cached
    }

    @Test fun `a second refusal propagates`() = runTest {
        var n = 0
        val inner = object : BackupGeocoder {
            override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
                n++
                throw TransitHttpException(503, "busy")
            }
        }
        try {
            GuardedGeocoder(inner, clock, backoff = Duration.ofMillis(1)).search("x y z", "he", null)
            fail("expected the refusal")
        } catch (e: TransitHttpException) {
            assertEquals(503, e.code)
        }
        assertEquals(2, n)
    }

    @Test fun `at most two requests at once`() = runBlocking {
        val running = AtomicInteger()
        var peak = 0
        val inner = object : BackupGeocoder {
            override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
                val now = running.incrementAndGet()
                synchronized(this) { peak = maxOf(peak, now) }
                delay(30)
                running.decrementAndGet()
                return emptyList()
            }
        }
        val guard = GuardedGeocoder(inner, clock)
        (1..6).map { i -> async { guard.search("q$i", "he", null) } }.awaitAll()
        assertEquals(2, peak)
    }

    // --- the client ---------------------------------------------------------------------

    // runBlocking, not runTest: the client hops to Dispatchers.IO for real socket I/O.
    @Test fun `photon is asked with our User-Agent, Israel's box and the app's language`() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setBody(text("photon_handwritten_tabenkin_raanana")))
            server.enqueue(MockResponse().setBody(text("photon_handwritten_rager_en")))
            server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
            val client = PhotonClient(server.url("/").toString())

            val he = client.search("טבנקין 15 רעננה", "he", raanana, 5)
            val r1 = server.takeRequest()
            assertEquals(MotisClient.USER_AGENT, r1.getHeader("User-Agent"))
            val u1 = r1.requestUrl!!
            assertEquals("/api", u1.encodedPath)
            assertEquals("טבנקין 15 רעננה", u1.queryParameter("q"))
            assertEquals("default", u1.queryParameter("lang")) // OSM's own name: Hebrew in Israel
            assertEquals(PhotonClient.ISRAEL_BBOX, u1.queryParameter("bbox"))
            assertEquals("32.1801", u1.queryParameter("lat"))
            assertEquals("34.8712", u1.queryParameter("lon"))
            assertEquals("5", u1.queryParameter("limit"))
            assertEquals(listOf("טבנקין 15", "טבנקין", "רעננה"), he.map { it.name })
            assertEquals(listOf("ADDRESS", "ADDRESS", "PLACE"), he.map { it.type })
            assertEquals(32.1893, he.first().lat, 1e-9)
            assertEquals(34.8668, he.first().lon, 1e-9)

            val en = client.search("Rager", "en", null, 5)
            val u2 = server.takeRequest().requestUrl!!
            assertEquals("en", u2.queryParameter("lang"))
            assertEquals(PhotonClient.ISRAEL_CENTRE.lat.toString(), u2.queryParameter("lat"))
            // A named place at a house number is a place, not an address.
            assertEquals(listOf("ADDRESS" to "Rager Boulevard", "PLACE" to "Soroka Medical Center"), en.map { it.type to it.name })
            assertEquals("hospital", en[1].category)

            try {
                client.search("x", "he", null, 5)
                fail("expected an HTTP error")
            } catch (e: TransitHttpException) {
                assertEquals(500, e.code)
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun `an empty answer is no answer`() {
        assertTrue(photon("photon_handwritten_empty").isEmpty())
    }
}
