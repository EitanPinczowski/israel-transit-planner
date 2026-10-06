package il.transit.planner.remind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import il.transit.core.geo.LatLon
import il.transit.core.plan.LastTripAlert
import il.transit.core.plan.LastTripChecker
import il.transit.core.plan.LastTripHome
import il.transit.core.user.Home
import il.transit.planner.TransitApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/**
 * "Last trip home" (Phase 9 C5; decisions in core `LastTripHome`). Two alarms:
 *  - [ACTION_CHECK], inexact, once per evening (19:00 Sun–Thu, 12:00 Fri, 20:00 Sat): where
 *    am I, and when away from Home `LastRideFinder` under a budget of 3, once per service day;
 *  - [ACTION_NOTIFY], exact (inexact if refused), 30 min before leaving: posts the alert,
 *    at most once per service day.
 * Worst case: 3 `plan` requests a day, and only while the setting is on.
 */
class LastTripReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TransitApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(30_000) {
                    when (intent.action) {
                        ACTION_CHECK -> check(app)
                        ACTION_NOTIFY -> notifyPending(app)
                    }
                }
            } catch (e: CancellationException) {
                // Timed out: the day is already marked as checked, tomorrow tries again.
            } catch (e: Exception) {
                // No signal or a bad answer: no alert tonight.
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun check(app: TransitApp) {
        val now = Instant.now()
        val store = LastTripStore(app)
        // Whatever happens below, the next evening's check is armed.
        set(app, LastTripHome.following(now), ACTION_CHECK, exact = false)
        val settings = app.store.settings.first()
        if (!settings.lastTripAlert) return
        val home = Home.of(settings, app.store.places.first()) ?: return
        app.history.load()
        val origin = LastTripHome.origin(app.history.records.value, store.seen(), home.latLon, now)
        val out = LastTripChecker(app.api).check(store.checkedDay(), origin, home.latLon, now, settings, app.language) {
            store.setCheckedDay(it)
        } ?: return
        val a = out.alert ?: return
        if (store.notifiedDay() == a.day) return
        if (a.notifyAt.isAfter(now)) {
            store.setPending(a)
            set(app, a.notifyAt, ACTION_NOTIFY, exact = true)
        } else {
            post(app, store, a, now) // "leaves at 23:10, leave now"
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

    private fun post(app: TransitApp, store: LastTripStore, a: LastTripAlert, now: Instant) {
        store.setPending(null)
        val m = LastTripHome.message(a, now) ?: return // gone: nothing
        Notifications.lastTrip(app, a, m)
        store.setNotifiedDay(a.day)
    }

    companion object {
        const val ACTION_CHECK = "il.transit.planner.action.LAST_TRIP_CHECK"
        const val ACTION_NOTIFY = "il.transit.planner.action.LAST_TRIP_NOTIFY"

        /**
         * App start, boot, and every change of the setting: arm the evening check (at once
         * when today's was missed) and re-arm a pending alert; or cancel both when off.
         */
        fun sync(context: Context, enabled: Boolean) {
            val store = LastTripStore(context)
            if (!enabled) {
                val am = context.getSystemService(AlarmManager::class.java)
                am.cancel(pending(context, ACTION_CHECK))
                am.cancel(pending(context, ACTION_NOTIFY))
                store.setPending(null)
                return
            }
            val now = Instant.now()
            set(context, LastTripHome.nextCheck(now, store.checkedDay()).coerceAtLeast(now.plusSeconds(5)), ACTION_CHECK, exact = false)
            store.pending()?.let { a -> set(context, a.notifyAt.coerceAtLeast(now.plusSeconds(5)), ACTION_NOTIFY, exact = true) }
        }

        /** The app was in front here: one of the two "where am I" signals. Never asked in the background. */
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

/** The day checked, the day notified, the alert waiting for its time, and the last foreground fix. */
internal class LastTripStore(context: Context) {
    private val prefs = context.getSharedPreferences("last_trip_home", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun checkedDay(): LocalDate? = day(CHECKED)
    fun setCheckedDay(d: LocalDate) = prefs.edit().putString(CHECKED, d.toString()).apply()
    fun notifiedDay(): LocalDate? = day(NOTIFIED)
    fun setNotifiedDay(d: LocalDate) = prefs.edit().putString(NOTIFIED, d.toString()).apply()

    fun pending(): LastTripAlert? =
        prefs.getString(PENDING, null)?.let { runCatching { json.decodeFromString(LastTripAlert.serializer(), it) }.getOrNull() }

    fun setPending(a: LastTripAlert?) = prefs.edit().apply {
        if (a == null) remove(PENDING) else putString(PENDING, json.encodeToString(LastTripAlert.serializer(), a))
    }.apply()

    fun seen(): LastTripHome.Seen? {
        if (!prefs.contains(SEEN_AT)) return null
        val lat = prefs.getFloat(SEEN_LAT, 0f).toDouble()
        val lon = prefs.getFloat(SEEN_LON, 0f).toDouble()
        return LastTripHome.Seen(LatLon(lat, lon), Instant.ofEpochSecond(prefs.getLong(SEEN_AT, 0)))
    }

    fun setSeen(s: LastTripHome.Seen) = prefs.edit()
        .putFloat(SEEN_LAT, s.at.lat.toFloat())
        .putFloat(SEEN_LON, s.at.lon.toFloat())
        .putLong(SEEN_AT, s.time.epochSecond)
        .apply()

    private fun day(key: String) = prefs.getString(key, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private companion object {
        const val CHECKED = "checked_day"
        const val NOTIFIED = "notified_day"
        const val PENDING = "pending"
        const val SEEN_LAT = "seen_lat"
        const val SEEN_LON = "seen_lon"
        const val SEEN_AT = "seen_at"
    }
}
