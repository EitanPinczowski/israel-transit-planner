package il.transit.planner.uitest

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.MotisClient
import il.transit.core.geo.LatLon
import il.transit.core.search.BackupGeocoder
import il.transit.core.search.FallbackGeocoder
import il.transit.core.search.GuardedGeocoder
import il.transit.core.search.PlaceSearch
import il.transit.planner.data.AndroidGeocoder
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** F9 scratch probe 2 (never merged): the app's own chain (async Geocoder, deadlines), Photon stubbed. 6 Transitous requests. */
@RunWith(AndroidJUnit4::class)
class GeocoderProbeTest {
    private class NoPhoton : BackupGeocoder {
        var calls = 0
        override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> { calls++; return emptyList() }
    }

    @Test fun probe() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val queries = listOf("Herzl 12 Be'er Sheva", "Jaffa Road 97 Jerusalem", "Allenby 40 Tel Aviv")
        for (lang in listOf("en", "he")) {
            for (q in queries) {
                // A fresh chain each time, like a first search after the app starts.
                val photon = NoPhoton()
                val search = PlaceSearch(GuardedTransitApi(MotisClient()), FallbackGeocoder(GuardedGeocoder(AndroidGeocoder(ctx)), photon))
                val t0 = System.nanoTime()
                val raw = try { AndroidGeocoder(ctx).search(q, lang, null, 5).map { it.name } } catch (e: Throwable) { listOf("ERR $e") }
                val t1 = System.nanoTime()
                val a = search.search(q, lang, null)
                val t2 = System.nanoTime()
                Log.i(TAG, "Q[$q|$lang] async-phone ${(t1 - t0) / 1_000_000} ms: $raw")
                Log.i(TAG, "  search ${(t2 - t1) / 1_000_000} ms missed=${a.missed} photonCalls=${photon.calls} notFound=${a.notFound}")
                Log.i(TAG, "  primary=${a.primary.map { it.name + "/" + it.type }} backup=${a.backup.map { it.name }}")
                Log.i(TAG, "  all=${a.all.map { it.name + "=" + a.level(it) }}")
                Thread.sleep(1500)
            }
        }
    }

    companion object { const val TAG = "GeoProbe" }
}
