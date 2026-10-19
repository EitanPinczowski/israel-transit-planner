package il.transit.planner.remind

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import il.transit.core.geo.LatLon
import il.transit.core.plan.MissedIt
import il.transit.planner.MainActivity
import il.transit.planner.R

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

    /** Its own request code: the other PendingIntents to [MainActivity] use 0–2. */
    private const val REQUEST = 3

    fun intent(context: Context, t: MissedIt.Target): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(EXTRA_MISSED_IT)
            .putExtra(EXTRA_MISSED_IT, true)
            .putExtra(EXTRA_LAT, t.at.lat)
            .putExtra(EXTRA_LON, t.at.lon)
            .putExtra(EXTRA_NAME, t.name)
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
        if (lat.isNaN() || lon.isNaN()) return null
        return MissedIt.Target(intent.getStringExtra(EXTRA_NAME), LatLon(lat, lon))
    }

    /**
     * The phone's freshest fix, read on the tap only: the map's [mapFix] or the platform's last
     * known GPS / network / fused fix (a cold start from a notification has no map fix yet), with
     * its age and accuracy for [MissedIt.usable]. Null without location permission.
     */
    @SuppressLint("MissingPermission") // checked first
    fun lastFix(context: Context, mapFix: Location?): MissedIt.Fix? {
        val allowed = listOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!allowed) return null
        val lm = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused")
        val platform = providers.mapNotNull { p -> runCatching { lm?.getLastKnownLocation(p) }.getOrNull() }
        val fixes = (platform + listOfNotNull(mapFix))
            .map { l ->
                val ageSec = (SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos) / 1_000_000_000L
                MissedIt.Fix(LatLon(l.latitude, l.longitude), ageSec, l.accuracy.takeIf { l.hasAccuracy() })
            }.sortedBy { it.ageSec }
        // The newest precise one; a fresher rough network fix must not hide a GPS fix of a minute ago.
        return fixes.firstOrNull(MissedIt::usable) ?: fixes.firstOrNull()
    }
}
