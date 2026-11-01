package il.transit.planner.uitest.mock

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.rules.ExternalResource
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Collections
import javax.net.SocketFactory
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * GitHub Releases, faked on the emulator's loopback inside the test process (Phase 11 T6). Nothing
 * reaches the internet: not even a DNS lookup.
 *
 * The app's own clients are used ([client] derives from `ApkInstaller.client()` / `UpdateChecker.client()`,
 * so redirects stay unfollowed and timeouts stay real) with three changes that only move *where* a
 * connection lands, never *whether* the app makes it:
 *  - [Dns]: every host name answers 127.0.0.1 and is recorded ([lookups]);
 *  - the socket factory: every connection goes to this server's port, whatever port the URL had, and
 *    is recorded with the host the app asked for ([connects]);
 *  - TLS: the server presents a self-signed test certificate for the GitHub hosts
 *    (`androidTest/assets/fake_release_server.p12`, test only), and the client trusts only that one.
 * So the app's real URL checks (https, the host allowlist, port 443) decide, and the records prove
 * that a refused host was never looked up or connected to.
 *
 * Paths: `releases/latest` answers [latestJson]; [APK_PATH] answers [apkMode] (the APK, cut halfway,
 * or a redirect to [redirectTo]); [ASSET_PATH] is the asset host's copy of the APK.
 */
class FakeReleaseServer : ExternalResource() {
    enum class ApkMode { SERVE, CUT, REDIRECT }

    /** The APK served; a few KB of bytes is enough for the download checks, the end-to-end test serves a real one. */
    @Volatile var apk: ByteArray = ByteArray(300_000) { (it * 31 % 251).toByte() }

    @Volatile var apkMode: ApkMode = ApkMode.SERVE

    /** Where [ApkMode.REDIRECT] sends the download (GitHub sends its asset host). */
    @Volatile var redirectTo: String = "https://$ASSET_HOST$ASSET_PATH"

    /** The `releases/latest` answer; [release] builds a GitHub-shaped one. */
    @Volatile var latestJson: String = ""

    /** Host names the app looked up, in order. */
    val lookups: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** "host:port" of every connection the app opened, in order (the port it asked for, before the move). */
    val connects: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** "Host header + path" of every request that reached the server. */
    val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private lateinit var server: MockWebServer
    private lateinit var tls: SSLContext
    private lateinit var trust: X509TrustManager

    override fun before() {
        val ks = KeyStore.getInstance("PKCS12")
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        assets.open("fake_release_server.p12").use { ks.load(it, PASSWORD) }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, PASSWORD) }
        tls = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
        val anchors = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("fake-github", ks.getCertificate("server"))
        }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(anchors)
        trust = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
        server = MockWebServer()
        // HTTP/1.1, as GitHub answers a plain client too: the Host header names the host the app asked for (h2 has none).
        server.protocols = listOf(Protocol.HTTP_1_1)
        server.useHttps(tls.socketFactory, false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requests += "${request.getHeader("Host")}$path"
                return when {
                    path.endsWith("/releases/latest") -> MockResponse()
                        .setBody(latestJson)
                        .setHeader("Content-Type", "application/json")
                    path == APK_PATH -> when (apkMode) {
                        ApkMode.SERVE -> apkResponse()
                        ApkMode.REDIRECT -> MockResponse()
                            .setResponseCode(302)
                            .setHeader("Location", redirectTo)
                        // The full length announced, half the bytes sent, then the connection drops.
                        ApkMode.CUT -> apkResponse().setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                    }
                    path == ASSET_PATH -> apkResponse()
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start(InetAddress.getByName("127.0.0.1"), 0)
    }

    override fun after() {
        runCatching { server.shutdown() }
    }

    private fun apkResponse() = MockResponse()
        .setHeader("Content-Type", "application/vnd.android.package-archive")
        .setBody(Buffer().write(apk))

    /** [base] with its DNS, sockets and trust moved to this server; everything else (redirects, timeouts) kept. */
    fun client(base: OkHttpClient): OkHttpClient {
        val port = server.port
        val sockets = object : SocketFactory() {
            private fun moved() = object : Socket() {
                override fun connect(endpoint: SocketAddress, timeout: Int) {
                    val asked = endpoint as InetSocketAddress
                    connects += "${asked.hostString}:${asked.port}"
                    super.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), timeout)
                }
            }
            override fun createSocket(): Socket = moved()
            override fun createSocket(host: String?, p: Int): Socket = unsupported()
            override fun createSocket(host: String?, p: Int, local: InetAddress?, localPort: Int): Socket = unsupported()
            override fun createSocket(host: InetAddress?, p: Int): Socket = unsupported()
            override fun createSocket(address: InetAddress?, p: Int, local: InetAddress?, localPort: Int): Socket = unsupported()
            private fun unsupported(): Socket = throw UnsupportedOperationException("OkHttp connects unconnected sockets")
        }
        val clientTls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        val dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                lookups += hostname
                return listOf(InetAddress.getByAddress(hostname, byteArrayOf(127, 0, 0, 1)))
            }
        }
        return base
            .newBuilder()
            .proxy(Proxy.NO_PROXY)
            .dns(dns)
            .socketFactory(sockets)
            .sslSocketFactory(clientTls.socketFactory, trust)
            .build()
    }

    /** Lookups, connections and requests: none of them should name [host]. */
    fun touched(host: String): Boolean =
        lookups.any { it.equals(host, true) } || connects.any { it.startsWith("$host:", true) } || requests.any { it.startsWith(host, true) }

    fun clear() {
        lookups.clear()
        connects.clear()
        requests.clear()
        apkMode = ApkMode.SERVE
        redirectTo = "https://$ASSET_HOST$ASSET_PATH"
    }

    companion object {
        private val PASSWORD = "t6-test-only".toCharArray()
        const val ASSET_HOST = "release-assets.githubusercontent.com"
        const val APK_PATH = "/EitanPinczowski/israel-transit-planner/releases/download/v0.0.2/israel-transit-planner.apk"
        const val ASSET_PATH = "/assets/israel-transit-planner.apk"
        const val APK_URL = "https://github.com$APK_PATH"

        fun sha256(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }

        /** A GitHub `releases/latest` answer: [tag], one APK asset at [apkUrl] with [digest] ("sha256:<hex>", or null). */
        fun release(tag: String, apkUrl: String = APK_URL, digest: String?): String {
            val d = digest?.let { "\"digest\": \"$it\"," } ?: ""
            val page = "https://github.com/EitanPinczowski/israel-transit-planner/releases/tag/$tag"
            val asset = "{\"name\": \"israel-transit-planner.apk\", $d \"browser_download_url\": \"$apkUrl\"}"
            return "{\"tag_name\": \"$tag\", \"html_url\": \"$page\", \"draft\": false, \"prerelease\": false, \"assets\": [$asset]}"
        }
    }
}
