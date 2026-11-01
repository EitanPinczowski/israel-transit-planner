package il.transit.planner.remind

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import il.transit.core.geo.LatLon
import il.transit.core.plan.MissedIt
import il.transit.core.present.SharedPlace
import il.transit.core.search.PlaceSearch
import il.transit.planner.MainActivity
import il.transit.planner.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * "Missed it? Plan from here" on the ride, leave-countdown and "trip changed" notifications
 * (Phase 11 E2). The destination travels in the intent, so a tap works after the app was killed;
 * [MainActivity] hands it to `MainViewModel.missedIt`, which sends at most 1 `plan`.
 */
object MissedItAction {
    const val EXTRA_MISSED_IT = "missed_it"
    private const val EXTRA_LAT = "missed_it_lat"
    private const val EXTRA_LON = "missed_it_lon"
    private const val EXTRA_NAME = "missed_it_name"
    private const val EXTRA_ARRIVE_BY = "missed_it_arrive_by"

    /** Its own request code: the other PendingIntents to [MainActivity] use 0–2. */
    private const val REQUEST = 3

    /** A place name, not a page: a longer one from another app is cut. */
    private const val MAX_NAME = 120

    fun intent(context: Context, t: MissedIt.Target): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(EXTRA_MISSED_IT)
            .putExtra(EXTRA_MISSED_IT, true)
            .putExtra(EXTRA_LAT, t.at.lat)
            .putExtra(EXTRA_LON, t.at.lon)
            .putExtra(EXTRA_NAME, t.name)
            .putExtra(EXTRA_ARRIVE_BY, t.arriveBy)
            // Extras don't tell PendingIntents apart: the data does, so each destination keeps its own.
            .setData(Uri.parse("missedit:${t.at.lat},${t.at.lon}?arriveBy=${t.arriveBy}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun action(context: Context, t: MissedIt.Target): NotificationCompat.Action {
        val pi = PendingIntent.getActivity(context, REQUEST, intent(context, t), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Action(0, context.getString(R.string.missed_it_action), pi)
    }

    /** The destination in a tapped action; null for any other intent or a broken one. */
    fun target(intent: Intent?): MissedIt.Target? {
        if (intent?.getBooleanExtra(EXTRA_MISSED_IT, false) != true) return null
        val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        val lon = intent.getDoubleExtra(EXTRA_LON, Double.NaN)
        // MainActivity is exported: a destination outside Israel (or NaN, 999) is refused before any request (T6).
        if (!PlaceSearch.inIsrael(LatLon(lat, lon))) return null
        val name = intent.getStringExtra(EXTRA_NAME)?.take(MAX_NAME)?.let(SharedPlace::stripBidi)
        return MissedIt.Target(name, LatLon(lat, lon), intent.getBooleanExtra(EXTRA_ARRIVE_BY, false))
    }

    /** The phone's location for one tap: [MissedIt.Fixes] on the platform `LocationManager`, read only when asked. */
    fun fixes(context: Context, mapFix: () -> Location?): MissedIt.Fixes = object : MissedIt.Fixes {
        override fun last() = lastFix(context, mapFix())
        override suspend fun fresh() = freshFix(context)
    }

    private fun allowed(context: Context) =
        listOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun fix(l: Location) = MissedIt.Fix(
        LatLon(l.latitude, l.longitude),
        (SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos) / 1_000_000_000L,
        l.accuracy.takeIf { l.hasAccuracy() },
    )

    /**
     * The phone's freshest fix, read on the tap only: the map's [mapFix] or the platform's last
     * known GPS / network / fused fix (a cold start from a notification has no map fix yet), with
     * its age and accuracy ([MissedIt.pick]). Null without location permission.
     */
    @SuppressLint("MissingPermission") // checked first
    fun lastFix(context: Context, mapFix: Location?): MissedIt.Fix? {
        if (!allowed(context)) return null
        val lm = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused")
        val platform = providers.mapNotNull { p -> runCatching { lm?.getLastKnownLocation(p) }.getOrNull() }
        return MissedIt.pick((platform + listOfNotNull(mapFix)).map(::fix))
    }

    /**
     * One new fix (GPS when it is on, else network), asked only on the tap and only with permission:
     * `getCurrentLocation` on API 30+, `requestSingleUpdate` below. The tap cancels it after
     * [MissedIt.WAIT_MS], which stops the request. Null when there is none to ask.
     */
    @SuppressLint("MissingPermission") // checked first
    suspend fun freshFix(context: Context): MissedIt.Fix? {
        if (!allowed(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        return suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= 30) {
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                lm.getCurrentLocation(provider, cancel, context.mainExecutor) { l -> if (cont.isActive) cont.resume(l?.let(::fix)) }
            } else {
                // An explicit object, never a lambda: below API 29 the other methods are abstract.
                val listener = object : LocationListener {
                    override fun onLocationChanged(l: Location) {
                        if (cont.isActive) cont.resume(fix(l))
                    }

                    @Deprecated("Deprecated in API 29")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }
    }
}
