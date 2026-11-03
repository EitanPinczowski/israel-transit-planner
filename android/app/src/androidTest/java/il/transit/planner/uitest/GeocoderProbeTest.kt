package il.transit.planner.uitest

import android.location.Geocoder
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import il.transit.core.search.PlaceSearch
import il.transit.core.search.Towns
import il.transit.planner.data.AndroidGeocoder
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** F9 scratch probe (never merged): what the phone's Geocoder answers for the owner's English addresses. Phone Geocoder only. */
@RunWith(AndroidJUnit4::class)
class GeocoderProbeTest {
    @Suppress("DEPRECATION")
    @Test fun probe() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        Log.i(TAG, "present=${Geocoder.isPresent()}")
        val queries = listOf(
            "Herzl 12 Be'er Sheva" to "en", "Jaffa Road 97 Jerusalem" to "en", "Allenby 40 Tel Aviv" to "en",
            "הרצל 12 באר שבע" to "he", "Herzl 12 Beersheba" to "en", "Herzl 12 Be'er Sheva" to "he",
        )
        for ((q, lang) in queries) {
            for (bounded in listOf(true, false)) {
                val g = Geocoder(ctx, AndroidGeocoder.localeOf(lang))
                val res = try {
                    if (bounded) g.getFromLocationName(q, 5, 29.45, 34.2, 33.35, 35.95) else g.getFromLocationName(q, 5)
                } catch (e: Exception) {
                    Log.i(TAG, "Q[$q|$lang|bounded=$bounded] ERROR $e"); null
                }
                Log.i(TAG, "Q[$q|$lang|bounded=$bounded] n=${res?.size}")
                res.orEmpty().forEachIndexed { i, a ->
                    Log.i(TAG, "  #$i feature=${a.featureName} thoroughfare=${a.thoroughfare} sub=${a.subThoroughfare} locality=${a.locality} subLocality=${a.subLocality} admin=${a.adminArea} subAdmin=${a.subAdminArea} country=${a.countryName} line0=${a.getAddressLine(0)} lat=${a.latitude} lon=${a.longitude} premises=${a.premises}")
                    val m = AndroidGeocoder.toMatch(a)
                    val town = Towns.typedIn(q).firstOrNull()
                    Log.i(TAG, "     match=$m level=${m?.let { PlaceSearch.level(q, it, town) }} town=${town?.name} holds=${m?.let { mm -> town?.let { Towns.holds(it, mm) } }} inIsrael=${m?.let { PlaceSearch.inIsrael(il.transit.core.geo.LatLon(it.lat, it.lon)) }}")
                }
                Thread.sleep(1000)
            }
        }
    }

    companion object { const val TAG = "GeoProbe" }
}
