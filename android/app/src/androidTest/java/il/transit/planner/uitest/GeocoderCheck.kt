package il.transit.planner.uitest

import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * THROWAWAY (scratch branch claude/geocoder-check): logs, never asserts, what Android's built-in
 * Geocoder answers for the owner's home, to decide whether it can be the backup geocoder.
 * Read with `adb logcat -d -s GeoCheck`.
 */
@RunWith(AndroidJUnit4::class)
class GeocoderCheck {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun log(msg: String) {
        Log.i(TAG, msg)
        println("$TAG $msg")
    }

    @Test fun logWhatGeocoderAnswers() {
        log("API ${Build.VERSION.SDK_INT} ${Build.PRODUCT} isPresent=${Geocoder.isPresent()}")
        val queries = listOf("טבנקין 15 רעננה", "טבנקין 15, רעננה", "Tabenkin 15, Ra'anana")
        for (locale in listOf(Locale.forLanguageTag("he"), Locale("iw"), Locale.ENGLISH)) {
            val g = Geocoder(context, locale)
            for (q in queries) {
                val (list, ms, err) = timed { byName(g, q) }
                log("FWD locale=$locale q=\"$q\" ms=$ms err=$err n=${list?.size}")
                list.orEmpty().forEachIndexed { i, a -> log("  [$i] ${describe(a)}") }
                list?.firstOrNull()?.let { a ->
                    val (rev, rms, rerr) = timed { byLocation(g, a.latitude, a.longitude) }
                    log("REV locale=$locale at=${a.latitude},${a.longitude} ms=$rms err=$rerr n=${rev?.size}")
                    rev.orEmpty().forEachIndexed { i, r -> log("  [$i] ${describe(r)}") }
                }
            }
        }
        // The owner's street, roughly (Ra'anana centre), reverse only.
        val (rev, rms, rerr) = timed { byLocation(Geocoder(context, Locale.forLanguageTag("he")), 32.1801, 34.8712) }
        log("REV ra'anana-centre ms=$rms err=$rerr n=${rev?.size}")
        rev.orEmpty().forEachIndexed { i, r -> log("  [$i] ${describe(r)}") }
    }

    private fun describe(a: Address) =
        "line0=\"${a.getAddressLine(0)}\" locality=${a.locality} thoroughfare=${a.thoroughfare} " +
            "subThoroughfare=${a.subThoroughfare} feature=${a.featureName} lat=${a.latitude} lon=${a.longitude}"

    private fun <T> timed(call: () -> T?): Triple<T?, Long, String?> {
        val t0 = System.nanoTime()
        return try {
            val r = call()
            Triple(r, (System.nanoTime() - t0) / 1_000_000, null)
        } catch (e: Throwable) {
            Triple(null, (System.nanoTime() - t0) / 1_000_000, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun byName(g: Geocoder, q: String): List<Address>? {
        if (Build.VERSION.SDK_INT < 33) return g.getFromLocationName(q, 5)
        val latch = CountDownLatch(1)
        var out: List<Address>? = null
        var error: String? = null
        g.getFromLocationName(q, 5, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) { out = addresses; latch.countDown() }
            override fun onError(errorMessage: String?) { error = errorMessage; latch.countDown() }
        })
        if (!latch.await(30, TimeUnit.SECONDS)) error("timeout after 30 s")
        error?.let { error("onError: $it") }
        return out
    }

    @Suppress("DEPRECATION")
    private fun byLocation(g: Geocoder, lat: Double, lon: Double): List<Address>? {
        if (Build.VERSION.SDK_INT < 33) return g.getFromLocation(lat, lon, 3)
        val latch = CountDownLatch(1)
        var out: List<Address>? = null
        var error: String? = null
        g.getFromLocation(lat, lon, 3, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) { out = addresses; latch.countDown() }
            override fun onError(errorMessage: String?) { error = errorMessage; latch.countDown() }
        })
        if (!latch.await(30, TimeUnit.SECONDS)) error("timeout after 30 s")
        error?.let { error("onError: $it") }
        return out
    }

    private companion object {
        const val TAG = "GeoCheck"
    }
}
