package il.transit.planner.remind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import il.transit.core.geo.LatLon
import il.transit.core.plan.LastTripAlert
import il.transit.core.plan.LastTripChecker
import il.transit.core.plan.LastTripDay
import il.transit.core.plan.LastTripHome
import il.transit.core.user.Home
import il.transit.planner.TransitApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * "Last trip home" (Phase 9 C5; decisions in core `LastTripHome`). Two alarms:
 *  - [ACTION_CHECK], inexact, once per evening (19:00 Sun–Thu, 12:00 Fri, 20:00 Sat): hands
 *    the check to [LastTripWorker] (a broadcast must finish within seconds; three `plan` calls
 *    on a slow network may not), which waits for a network and retries a check that had no
 *    signal 15 min later while the day's budget lasts;
 *  - [ACTION_NOTIFY], exact (inexact if refused), 30 min before leaving: posts the alert,
 *    at most once per service day.
 * Worst case: 3 `plan` requests a day, and only while the setting is on.
 */
class LastTripReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TransitApp
        when (intent.action) {
            ACTION_CHECK -> {
                // The next evening's check is armed whatever happens to this one.
                set(app, LastTripHome.following(Instant.now()), ACTION_CHECK, exact = false)
                LastTripWorker.enqueue(app)
            }
            ACTION_NOTIFY -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        withTimeout(5_000) { notifyPending(app) } // no network: reads the store and posts
                    } catch (e: Exception) {
                        // Nothing to post.
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private suspend fun notifyPending(app: TransitApp) {
        val store = LastTripStore(app)
        val a = store.pending() ?: return
        if (!app.store.settings.first().lastTripAlert || store.notifiedDay() == a.day) {
            store.setPending(null)
            return
        }
        post(app, store, a, Instant.now())
    }

    companion object {
        const val ACTION_CHECK = "il.transit.planner.action.LAST_TRIP_CHECK"
        const val ACTION_NOTIFY = "il.transit.planner.action.LAST_TRIP_NOTIFY"

        /** Runs one check (from [LastTripWorker]); true when it had no signal and may be retried. */
        internal suspend fun check(app: TransitApp): Boolean {
            val now = Instant.now()
            val store = LastTripStore(app)
            val settings = app.store.settings.first()
            if (!settings.lastTripAlert) return false
            val home = Home.of(settings, app.store.places.first()) ?: return false
            app.history.load()
            val origin = LastTripHome.origin(app.history.records.value, store.seen(), home.latLon, now)
            val out = LastTripChecker(app.api).check(store.day(), origin, home.latLon, now, settings, app.language) {
                store.setDay(it)
            } ?: return false
            val a = out.alert ?: return out.retry
            if (store.notifiedDay() == a.day) return false
            if (a.notifyAt.isAfter(now)) {
                store.setPending(a)
                set(app, a.notifyAt, ACTION_NOTIFY, exact = true)
            } else {
                post(app, store, a, now) // "leaves at 23:10, leave now"
            }
            return false
        }

        private fun post(app: TransitApp, store: LastTripStore, a: LastTripAlert, now: Instant) {
            store.setPending(null)
            val m = LastTripHome.message(a, now) ?: return // gone: nothing
            Notifications.lastTrip(app, a, m)
            store.setNotifiedDay(a.day)
        }

        /**
         * App start, boot, and every change of the setting: arm the evening check (at once
         * when today's was missed) and re-arm a pending alert; or cancel everything when off.
         */
        fun sync(context: Context, enabled: Boolean) {
            val store = LastTripStore(context)
            if (!enabled) {
                val am = context.getSystemService(AlarmManager::class.java)
                am.cancel(pending(context, ACTION_CHECK))
                am.cancel(pending(context, ACTION_NOTIFY))
                LastTripWorker.cancel(context)
                store.setPending(null)
                store.setSeen(null) // kept only while the alert is on
                return
            }
            val now = Instant.now()
            set(context, LastTripHome.nextCheck(now, store.day()).coerceAtLeast(now.plusSeconds(5)), ACTION_CHECK, exact = false)
            store.pending()?.let { a -> set(context, a.notifyAt.coerceAtLeast(now.plusSeconds(5)), ACTION_NOTIFY, exact = true) }
        }

        /**
         * The app was in front here: one of the two "where am I" signals. Never asked in the
         * background; the caller saves it only while the alert is on.
         */
        fun saveSeen(context: Context, at: LatLon?) {
            if (at != null) LastTripStore(context).setSeen(LastTripHome.Seen(at, Instant.now()))
        }

        private fun set(context: Context, at: Instant, action: String, exact: Boolean) =
            ReminderScheduler.setAlarm(context, at, pending(context, action), exact)

        private fun pending(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                Intent(context, LastTripReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

/**
 * The evening check itself, off the broadcast: one-time work that waits for a network.
 * [LastTripReceiver.check] decides; a check with no signal returns [Result.retry] (linear
 * backoff, [LastTripHome.RETRY]), and the stored day record caps all tries at 3 requests.
 */
class LastTripWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (LastTripReceiver.check(applicationContext as TransitApp)) Result.retry() else Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.success() // a bad answer: tomorrow evening tries again
    }

    companion object {
        private const val NAME = "last-trip-check"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<LastTripWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, LastTripHome.RETRY.toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}

/** The day's checks so far, the day notified, the alert waiting for its time, and the last foreground fix. */
internal class LastTripStore(context: Context) {
    private val prefs = context.getSharedPreferences("last_trip_home", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun day(): LastTripDay? =
        prefs.getString(DAY, null)?.let { runCatching { json.decodeFromString(LastTripDay.serializer(), it) }.getOrNull() }

    /** Written synchronously: it guards the budget, and the process may die right after. */
    fun setDay(d: LastTripDay) {
        prefs.edit().putString(DAY, json.encodeToString(LastTripDay.serializer(), d)).commit()
    }

    fun notifiedDay(): LocalDate? = prefs.getString(NOTIFIED, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun setNotifiedDay(d: LocalDate) = prefs.edit().putString(NOTIFIED, d.toString()).apply()

    fun pending(): LastTripAlert? =
        prefs.getString(PENDING, null)?.let { runCatching { json.decodeFromString(LastTripAlert.serializer(), it) }.getOrNull() }

    fun setPending(a: LastTripAlert?) {
        val e = prefs.edit()
        if (a == null) e.remove(PENDING) else e.putString(PENDING, json.encodeToString(LastTripAlert.serializer(), a))
        e.apply()
    }

    fun seen(): LastTripHome.Seen? {
        if (!prefs.contains(SEEN_AT)) return null
        val lat = prefs.getFloat(SEEN_LAT, 0f).toDouble()
        val lon = prefs.getFloat(SEEN_LON, 0f).toDouble()
        return LastTripHome.Seen(LatLon(lat, lon), Instant.ofEpochSecond(prefs.getLong(SEEN_AT, 0)))
    }

    fun setSeen(s: LastTripHome.Seen?) {
        val e = prefs.edit()
        if (s == null) {
            e.remove(SEEN_LAT).remove(SEEN_LON).remove(SEEN_AT)
        } else {
            e.putFloat(SEEN_LAT, s.at.lat.toFloat()).putFloat(SEEN_LON, s.at.lon.toFloat()).putLong(SEEN_AT, s.time.epochSecond)
        }
        e.apply()
    }

    private companion object {
        const val DAY = "day"
        const val NOTIFIED = "notified_day"
        const val PENDING = "pending"
        const val SEEN_LAT = "seen_lat"
        const val SEEN_LON = "seen_lon"
        const val SEEN_AT = "seen_at"
    }
}
