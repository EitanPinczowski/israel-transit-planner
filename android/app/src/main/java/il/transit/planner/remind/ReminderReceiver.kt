package il.transit.planner.remind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import il.transit.core.api.Endpoint
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripQuery
import il.transit.core.remind.ReminderLogic
import il.transit.core.remind.ReminderUpdate
import il.transit.planner.TransitApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Handles both reminder alarms. The re-check asks for a fresh plan around the leave time
 * and moves the "leave now" alarm if real-time data shifted the bus. No network, or any
 * failure, keeps the original time: a reminder must never disappear because of a bad signal.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TransitApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(20_000) { handle(app, intent.action) }
            } catch (e: Exception) {
                // Keep whatever is scheduled.
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(app: TransitApp, action: String?) {
        val r = app.store.reminder.first() ?: return
        when (action) {
            ReminderScheduler.ACTION_RECHECK -> {
                val fresh = runCatching {
                    TripPlanner(app.api).plan(
                        TripQuery(
                            from = Endpoint.Coord(r.from),
                            to = Endpoint.Coord(r.to),
                            timeMode = TimeMode.DEPART_AT,
                            time = r.leaveAt.minusSeconds(600),
                            settings = r.settings,
                            language = app.language,
                        ),
                    )
                }.getOrNull() ?: return
                when (val u = ReminderLogic.update(r, fresh.itineraries)) {
                    is ReminderUpdate.Updated -> {
                        app.store.setReminder(u.reminder)
                        ReminderScheduler.scheduleLeave(app, u.reminder)
                    }
                    ReminderUpdate.Gone -> {
                        Notifications.changed(app, r)
                        ReminderScheduler.cancel(app)
                        app.store.setReminder(null)
                    }
                }
            }
            ReminderScheduler.ACTION_LEAVE -> {
                Notifications.leaveNow(app, r)
                app.store.setReminder(null)
            }
        }
    }
}

/** Alarms do not survive a reboot; re-arm the active reminder, or drop it if it has passed. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as TransitApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val r = app.store.reminder.first() ?: return@launch
                if (r.leaveAt.isAfter(java.time.Instant.now())) ReminderScheduler.schedule(app, r) else app.store.setReminder(null)
            } catch (e: Exception) {
                // Nothing to re-arm.
            } finally {
                pending.finish()
            }
        }
    }
}
