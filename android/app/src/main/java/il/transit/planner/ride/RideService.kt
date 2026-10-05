package il.transit.planner.ride

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import il.transit.core.api.Itinerary
import il.transit.core.api.MotisJson
import il.transit.core.geo.LatLon
import il.transit.core.ride.RideEvent
import il.transit.core.ride.RideTracker
import il.transit.planner.MainActivity
import il.transit.planner.TransitApp
import il.transit.planner.R
import kotlinx.coroutines.flow.MutableStateFlow
import il.transit.core.ride.RideProgress
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * "Get off at the next stop": a foreground location service that runs ONLY while a trip is
 * being ridden, feeds GPS fixes to core's RideTracker, vibrates once per leg before the stop,
 * and stops itself when the trip is over. Uses the platform LocationManager (no Play services).
 */
class RideService : Service() {
    private var tracker: RideTracker? = null
    private var finalStop: String = ""

    /** Speaks the get-off alert when the user asked for it; null otherwise or until ready. */
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var locationManager: LocationManager? = null
    // An explicit object, not a lambda: before API 29 the other three methods are abstract
    // too, and a SAM lambda would crash with AbstractMethodError when a provider toggles.
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)

        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val itinerary = intent?.getStringExtra(EXTRA_ITINERARY)
            ?.let { runCatching { MotisJson.decodeFromString(Itinerary.serializer(), it) }.getOrNull() }
        finalStop = itinerary?.legs?.lastOrNull { it.isTransit }?.to?.name.orEmpty()
        // startForegroundService() must be answered by startForeground() even when we are about
        // to stop, or Android kills the app ("did not then call Service.startForeground()").
        ServiceCompat.startForeground(
            this,
            ID_ONGOING,
            ongoing(finalStop),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        if (itinerary == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        tracker = RideTracker(itinerary)
        if (intent?.getBooleanExtra(EXTRA_SPEAK, false) == true && tts == null) startSpeech()
        startLocation()
        _active.value = true
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // the app only offers "Start trip" with location granted
    private fun startLocation() {
        val lm = getSystemService(LocationManager::class.java)
        locationManager = lm
        try {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(provider, UPDATE_MS, UPDATE_M, listener, Looper.getMainLooper())
                }
            }
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers POST_NOTIFICATIONS
    private fun onLocation(loc: Location) {
        val pos = LatLon(loc.latitude, loc.longitude)
        val now = (application as TransitApp).clock.instant()
        when (val e = tracker?.update(pos, now)) {
            is RideEvent.Approaching -> getOffNext(e)
            RideEvent.Finished -> { stopSelf(); return }
            null -> Unit
        }
        val p = tracker?.progress(pos, now)
        _progress.value = p
        // The ongoing notification follows the ride: "Line 5 · 3 stops left · arrive 08:47".
        val nm = NotificationManagerCompat.from(this)
        if (p != null && nm.areNotificationsEnabled()) nm.notify(ID_ONGOING, ongoing(finalStop, p))
    }

    /** One TextToSpeech for the ride, in the phone's language, on the navigation audio stream. */
    private fun startSpeech() {
        tts = TextToSpeech(this) { status ->
            // No voice for the phone's language (often Hebrew): stay silent rather than read
            // Hebrew with an English voice. The notification and vibration still come.
            val lang = if (status == TextToSpeech.SUCCESS) tts?.setLanguage(java.util.Locale.getDefault()) else null
            ttsReady = lang != null && lang >= TextToSpeech.LANG_AVAILABLE
            if (ttsReady) {
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
            }
        }
    }

    /** Duck the music while speaking ("Next stop: X. Get off."), then give it back. */
    private fun speak(text: String) {
        val t = tts?.takeIf { ttsReady } ?: return
        val am = getSystemService(AudioManager::class.java)
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).build(),
            )
            .build()
        am.requestAudioFocus(focus)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { am.abandonAudioFocusRequest(focus) }
            @Deprecated("Deprecated in API 21")
            override fun onError(utteranceId: String?) { am.abandonAudioFocusRequest(focus) }
        })
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "get-off")
    }

    override fun onDestroy() {
        tts?.shutdown()
        tts = null
        locationManager?.removeUpdates(listener)
        _active.value = false
        _progress.value = null
        super.onDestroy()
    }

    private fun ongoing(finalStop: String, p: RideProgress? = null): Notification {
        ensureChannels(this)
        val stop = PendingIntent.getService(
            this, 0, Intent(this, RideService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.brand))
            .setContentTitle(p?.let { rideProgressTitle(this, it) } ?: getString(R.string.ride_ongoing_title))
            .setContentText(p?.let { rideProgressText(this, it) } ?: getString(R.string.ride_ongoing_text, finalStop))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openApp())
            .addAction(0, getString(R.string.ride_stop), stop)
            .build()
    }

    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers POST_NOTIFICATIONS
    private fun getOffNext(e: RideEvent.Approaching) {
        speak(getString(if (e.isLastLeg) R.string.ride_speak_get_off else R.string.ride_speak_change, e.stopName))
        val nm = NotificationManagerCompat.from(this)
        if (!nm.areNotificationsEnabled()) return
        val title = getString(if (e.isLastLeg) R.string.ride_get_off_title else R.string.ride_change_title)
        val n = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.brand))
            .setContentTitle(title)
            .setContentText(getString(R.string.ride_get_off_text, e.stopName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(VIBRATION)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        nm.notify(ID_ALERT_BASE + e.legIndex, n)
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val ACTION_STOP = "il.transit.planner.action.STOP_RIDE"
        private const val EXTRA_ITINERARY = "itinerary"
        private const val EXTRA_SPEAK = "speak"
        private const val CHANNEL_ONGOING = "ride_ongoing"
        private const val CHANNEL_ALERTS = "ride_alerts"
        private const val ID_ONGOING = 2001
        private const val ID_ALERT_BASE = 2100
        private const val UPDATE_MS = 5_000L
        private const val UPDATE_M = 15f
        private val VIBRATION = longArrayOf(0, 600, 250, 600, 250, 600)

        private val _active = MutableStateFlow(false)
        private val _progress = MutableStateFlow<RideProgress?>(null)

        /** The latest progress of the ride being tracked; null when none. */
        val progress: StateFlow<RideProgress?> = _progress.asStateFlow()

        /** "Line 5 · 3 stops left" */
        fun rideProgressTitle(context: Context, p: RideProgress): String {
            val stops = context.resources.getQuantityString(R.plurals.stops_left, p.stopsLeft, p.stopsLeft)
            return if (p.line.isNullOrBlank()) stops else context.getString(R.string.ride_progress_title, p.line, stops)
        }

        /** "Get off at X ~08:47 (+2) · arrive 08:55" */
        fun rideProgressText(context: Context, p: RideProgress): String {
            val late = if (p.delayMin >= 2) " " + context.getString(R.string.late_paren, p.delayMin) else ""
            return context.getString(R.string.ride_progress_text, p.alightStop, il.transit.core.present.hhmm(p.arriveAt)) + late +
                " · " + context.getString(R.string.ride_progress_arrive, il.transit.core.present.hhmm(p.tripArriveAt))
        }

        /** True while a ride is being tracked; the service clears it when it stops itself. */
        val active: StateFlow<Boolean> = _active.asStateFlow()

        fun start(context: Context, itinerary: Itinerary, speak: Boolean) {
            val intent = Intent(context, RideService::class.java)
                .putExtra(EXTRA_ITINERARY, MotisJson.encodeToString(Itinerary.serializer(), itinerary))
                .putExtra(EXTRA_SPEAK, speak)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RideService::class.java).setAction(ACTION_STOP))
        }

        private fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ONGOING) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.channel_ride_ongoing), NotificationManager.IMPORTANCE_LOW),
                )
            }
            if (nm.getNotificationChannel(CHANNEL_ALERTS) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_ride_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                        enableVibration(true)
                        vibrationPattern = VIBRATION
                    },
                )
            }
        }
    }
}
