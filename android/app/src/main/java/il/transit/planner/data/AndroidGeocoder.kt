package il.transit.planner.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import il.transit.core.api.GeocodeArea
import il.transit.core.api.GeocodeMatch
import il.transit.core.geo.LatLon
import il.transit.core.search.BackupGeocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The phone's own address search (`android.location.Geocoder`): part of the OS, keyless, no
 * account. On phones with Google Play services Google's address data answers it. This is NOT
 * the Google Maps SDK or Places (no key, no billing; see `dead-ends`). It knows the owner's
 * home ("טבנקין 15, רעננה" at 32.1881, 34.8605), which Transitous and OSM don't
 * (`transitous-api`, 2026-10-07).
 *
 * Asked only after a Transitous street miss, and to name a long-pressed pin or the location
 * fix. Only the typed text (or the point) leaves the phone. Every call gives up after
 * [timeoutMs] (a cold first call took 2.5 s on the emulator), so Photon can take over.
 * Use only when [Geocoder.isPresent].
 */
class AndroidGeocoder(private val context: Context, private val timeoutMs: Long = TIMEOUT_MS) : BackupGeocoder {

    override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> =
        withTimeout(timeoutMs) { byName(Geocoder(context, localeOf(language)), text, max) }.mapNotNull { toMatch(it) }

    override suspend fun reverseName(at: LatLon, language: String): String? =
        withTimeout(timeoutMs) { byPoint(Geocoder(context, localeOf(language)), at) }.firstOrNull()?.let { nameOf(it) }

    @Suppress("DEPRECATION")
    private suspend fun byName(g: Geocoder, text: String, max: Int): List<Address> =
        if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { c ->
                g.getFromLocationName(text, max, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { if (c.isActive) c.resume(addresses) }
                    override fun onError(errorMessage: String?) { if (c.isActive) c.resumeWithException(IllegalStateException(errorMessage)) }
                })
            }
        } else {
            // Blocking below 33: off the main thread, and interrupted when the timeout hits.
            runInterruptible(Dispatchers.IO) { g.getFromLocationName(text, max).orEmpty() }
        }

    @Suppress("DEPRECATION")
    private suspend fun byPoint(g: Geocoder, at: LatLon): List<Address> =
        if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { c ->
                g.getFromLocation(at.lat, at.lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { if (c.isActive) c.resume(addresses) }
                    override fun onError(errorMessage: String?) { if (c.isActive) c.resumeWithException(IllegalStateException(errorMessage)) }
                })
            }
        } else {
            runInterruptible(Dispatchers.IO) { g.getFromLocation(at.lat, at.lon, 1).orEmpty() }
        }

    companion object {
        const val TIMEOUT_MS = 3_000L

        /** "iw" is the Hebrew every Android version resolves (both "he" and "iw" answered alike). */
        fun localeOf(language: String): Locale = if (language == "en") Locale.ENGLISH else Locale("iw")

        /** "טבנקין 15", the street and number, else the place's own name. */
        private fun streetName(a: Address): String? =
            a.thoroughfare?.let { listOfNotNull(it, a.subThoroughfare).joinToString(" ") }
                ?: a.featureName?.takeUnless { it == a.subThoroughfare }

        /** For a pin: "טבנקין 15, רעננה". */
        fun nameOf(a: Address): String? =
            listOfNotNull(streetName(a), a.locality).distinct().joinToString(", ").ifEmpty { a.getAddressLine(0) }

        fun toMatch(a: Address): GeocodeMatch? {
            if (!a.hasLatitude() || !a.hasLongitude()) return null
            val name = streetName(a) ?: a.locality ?: return null
            val areas = buildList {
                a.countryName?.let { add(GeocodeArea(it, 2.0)) }
                a.adminArea?.let { add(GeocodeArea(it, 4.0)) }
                a.locality?.let { add(GeocodeArea(it, 8.0, default = true)) }
                a.subLocality?.let { add(GeocodeArea(it, 10.0)) }
            }
            return GeocodeMatch(
                type = if (a.thoroughfare != null) "ADDRESS" else "PLACE",
                name = name,
                id = "android:${a.latitude},${a.longitude}",
                lat = a.latitude,
                lon = a.longitude,
                street = a.thoroughfare,
                houseNumber = a.subThoroughfare,
                areas = areas,
            )
        }
    }
}
