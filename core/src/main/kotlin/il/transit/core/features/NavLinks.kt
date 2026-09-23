package il.transit.core.features

import il.transit.core.geo.LatLon
import java.util.Locale

/**
 * Links that open turn-by-turn navigation to a point, for sending a drop-off or pick-up
 * spot to the driver. Plain URLs: no API, no key, and they open in the app if installed
 * or in the browser if not.
 */
object NavLinks {
    fun waze(at: LatLon): String = "https://waze.com/ul?ll=${coord(at)}&navigate=yes"

    fun googleMaps(at: LatLon): String =
        "https://www.google.com/maps/dir/?api=1&destination=${coord(at)}&travelmode=driving"

    // Fixed decimals in Locale.US: never "3.1E-5", never a decimal comma.
    private fun coord(at: LatLon) = String.format(Locale.US, "%.6f,%.6f", at.lat, at.lon)
}
