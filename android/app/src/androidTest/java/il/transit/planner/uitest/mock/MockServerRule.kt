package il.transit.planner.uitest.mock

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import il.transit.planner.MockNetwork
import il.transit.planner.UiTestApp
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.rules.ExternalResource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** How the server misbehaves. [OFFLINE] is not an answer: the server is shut down and airplane mode is on. */
enum class Fault(val retried: Boolean) {
    /** 429 twice: the guard's one retry is refused too. */
    TWICE_429(true),
    TWICE_503(true),

    /** The server takes the request and never answers. */
    TIMEOUT(false),

    /** 200 with an HTML error page. */
    GARBAGE(false),

    /** 200 with no body at all. */
    EMPTY(false),

    /** 200 with `{}`. */
    EMPTY_OBJECT(false),

    /** 200, and the connection drops halfway through the body. */
    CUT(false),
    OFFLINE(false),

    /** 429 then the normal answer: the guard's retry must get it. */
    RETRY_THEN_OK(true),
}

/**
 * A `MockWebServer` on the emulator's loopback, inside the test process (which is the app's), for
 * the network-fault matrix (Phase 11 T4). Nothing reaches the internet.
 *
 * Every request is counted per endpoint ([count]): Transitous's path after `/api/<version>/`
 * (`plan`, `one-to-many`, `map/stops`, `geocode`, `reverse-geocode`, `stoptimes`, `trip`, `map/trips`),
 * `photon` and `meteo`. [script] sets the fault of some endpoints; the others answer normally:
 * Transitous with the replay's answer to the same call ([MockNetwork.answer]), Photon and Open-Meteo
 * with their recorded fixtures. [bodies] overrides a normal answer (Photon after a Transitous miss).
 */
class MockServerRule : ExternalResource() {
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    @Volatile private var faults: Map<String, Fault> = emptyMap()

    /** Normal answers replaced, per endpoint (e.g. `geocode` → `[]`, a miss). */
    @Volatile var bodies: Map<String, String> = emptyMap()

    /** Requests (endpoint + query) already refused once by [Fault.RETRY_THEN_OK]. */
    private val refusedOnce = ConcurrentHashMap.newKeySet<String>()

    private var server: MockWebServer? = null
    private var airplane = false

    val app: UiTestApp get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as UiTestApp

    /** The server's root, for [UiTestApp.useMockServer]. */
    val url: String get() = requireNotNull(server).url("/").toString()

    override fun before() {
        start()
    }

    override fun after() {
        runCatching { server?.shutdown() }
        server = null
        if (airplane) airplane(false)
    }

    private fun start() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = respond(request)
            }
            start(java.net.InetAddress.getByName("127.0.0.1"), 0)
        }
    }

    fun count(endpoint: String): Int = counts[endpoint]?.get() ?: 0

    fun count(endpoints: Collection<String>): Int = endpoints.sumOf(::count)

    fun counts(): Map<String, Int> = counts.mapValues { it.value.get() }.toSortedMap()

    /** [fault] on [endpoints] (null: they answer normally); every other endpoint answers normally. */
    fun script(endpoints: Collection<String>, fault: Fault?) {
        faults = if (fault == null) emptyMap() else endpoints.associateWith { fault }
        refusedOnce.clear()
    }

    /** "Offline": the server shut down (connections refused) and airplane mode on. */
    fun goOffline() {
        runCatching { server?.shutdown() }
        airplane(true)
    }

    /** Airplane mode off and a new server (a new port): the app is pointed at it. */
    fun goOnline(network: MockNetwork) {
        airplane(false)
        start()
        network.base = url
        network.clearCaches()
    }

    private fun airplane(on: Boolean) {
        shell("cmd connectivity airplane-mode ${if (on) "enable" else "disable"}")
        airplane = on
        SystemClock.sleep(1_500)
    }

    private fun shell(cmd: String) {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() } // runs as it is drained
    }

    private fun respond(r: RecordedRequest): MockResponse {
        val path = r.requestUrl?.encodedPath.orEmpty()
        val endpoint = when {
            path.startsWith("/photon") -> "photon"
            path.startsWith("/meteo") -> "meteo"
            else -> path.substringAfter("/api/").substringAfter('/')
        }
        counts.getOrPut(endpoint) { AtomicInteger() }.incrementAndGet()
        val fault = faults[endpoint]
        Log.i(TAG, "$endpoint ${fault ?: "ok"}")
        return when (fault) {
            null, Fault.OFFLINE -> normal(endpoint, path)
            Fault.TWICE_429 -> refuse(429)
            Fault.TWICE_503 -> refuse(503)
            Fault.TIMEOUT -> MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
            Fault.GARBAGE -> MockResponse().setHeader("Content-Type", "text/html").setBody("<html><body><h1>502 Bad Gateway</h1></body></html>")
            Fault.EMPTY -> MockResponse().setBody("")
            Fault.EMPTY_OBJECT -> MockResponse().setHeader("Content-Type", "application/json").setBody("{}")
            Fault.CUT -> normal(endpoint, path).apply {
                // Long enough to be cut in the middle, whatever the normal answer was.
                val body = getBody()?.readUtf8().orEmpty().ifEmpty { "[" + "{\"name\":\"x\"},".repeat(200) + "{}]" }
                setBody(body).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
            }
            Fault.RETRY_THEN_OK -> {
                // The query identifies the request; the id in the path changes on the guard's retry.
                val key = endpoint + "?" + r.requestUrl?.encodedQuery
                if (refusedOnce.add(key)) refuse(429) else normal(endpoint, path)
            }
        }
    }

    private fun refuse(code: Int) = MockResponse().setResponseCode(code).setHeader("Retry-After", "1").setBody("{\"error\":\"busy\"}")

    private fun normal(endpoint: String, path: String): MockResponse {
        val body = bodies[endpoint] ?: when (endpoint) {
            "photon" -> fixture("photon_rager")
            "meteo" -> fixture("openmeteo_rain_telaviv")
            else -> path.substringAfter("/motis/").substringBefore('/').toLongOrNull()?.let { app.network?.answer(it) }
        }
        return if (body == null) {
            MockResponse().setResponseCode(500).setBody("no answer for $path")
        } else {
            MockResponse().setHeader("Content-Type", "application/json").setBody(body)
        }
    }

    private fun fixture(name: String): String = app.assets.open("fixtures/$name.json").bufferedReader().use { it.readText() }

    companion object {
        const val TAG = "FaultMatrix"
    }
}
