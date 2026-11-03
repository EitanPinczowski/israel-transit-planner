package il.transit.planner.uitest

import android.location.Geocoder
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.MotisClient
import il.transit.core.geo.LatLon
import il.transit.core.search.FallbackGeocoder
import il.transit.core.search.GuardedGeocoder
import il.transit.core.search.PhotonClient
import il.transit.core.search.PlaceSearch
import il.transit.planner.data.AndroidGeocoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A few searches through the app's own chain (Transitous, the phone's Geocoder, Photon: as `TransitApp` builds them)
 * against the LIVE services, on a google_apis emulator (Phase 11 F10, owner's bug bash A4: with location off, "TLV",
 * "Rehovot railway station" and "Tel Aviv Central Bus Station" showed nothing on the Pixel). It logs what each answers,
 * or the error, and asserts nothing: a probe, run on demand only (`address-corpus.yml`, branch `corpus-run/probe-*`),
 * one query a second.
 */
@RunWith(AndroidJUnit4::class)
class SearchProbeTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun probe() = runBlocking {
        val device = if (Geocoder.isPresent()) GuardedGeocoder(AndroidGeocoder(target)) else null
        val search = PlaceSearch(GuardedTransitApi(MotisClient()), FallbackGeocoder(device, GuardedGeocoder(PhotonClient())))
        val beerSheva = LatLon(31.25, 34.79)
        val queries = listOf(
            Triple("TLV", "en", null),
            Triple("Rehovot railway station", "en", null),
            Triple("Tel Aviv Central Bus Station", "en", null),
            Triple("תחנה מרכזית", "en", beerSheva),
            Triple("central station", "en", beerSheva),
            Triple("תחנה מרכזית", "he", null),
        )
        val out = StringBuilder("| query | lang | near | ms | exact | closest | stations | missed | error |\n|---|---|---|---|---|---|---|---|---|\n")
        for ((q, lang, near) in queries) {
            val t0 = System.nanoTime()
            val line = try {
                val a = search.search(q, lang, near)
                val ms = (System.nanoTime() - t0) / 1_000_000
                "| $q | $lang | ${near?.comma() ?: "—"} | $ms | ${a.exact.take(4).joinToString(" / ") { "${it.name} (%.2f,%.2f)".format(it.lat, it.lon) }} | " +
                    "${a.closest.take(3).joinToString(" / ") { it.name }} | ${a.stations.size} | ${a.missed} | — |"
            } catch (e: Exception) {
                "| $q | $lang | ${near?.comma() ?: "—"} | ${(System.nanoTime() - t0) / 1_000_000} | — | — | — | — | ${e.javaClass.name}: ${e.message} |"
            }
            out.append(line).append('\n')
            Log.i(TAG, line)
            delay(1_000)
        }
        File(target.getExternalFilesDir(null), "address_corpus.md").writeText(out.toString())
    }

    companion object {
        const val TAG = "SearchProbe"
    }
}
