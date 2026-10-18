package il.transit.planner.remind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import il.transit.core.api.Endpoint
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripQuery
import il.transit.core.remind.AlarmKind
import il.transit.core.remind.Countdown
import il.transit.core.remind.PassExpiry
import il.transit.core.remind.Rearm
import il.transit.core.remind.Reminder
import il.transit.core.remind.ReminderLogic
import il.transit.core.remind.ReminderUpdate
import il.transit.planner.TransitApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Handles the reminder alarms. The re-check asks for a fresh plan around the leave time
 * and moves the "leave now" alarm if real-time data shifted the bus. No network, or any
 * failure, keeps the original time: a reminder must never disappear because of a bad signal.
 * The leave alarm posts the ongoing countdown (Phase 9 C5, 0 requests) and keeps the
 * reminder until the countdown ends, so cancelling it in the app still takes it down.
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
        // Phase 11 E1: neither looks at the reminder's trip, so neither can send a request.
        val kind = AlarmKind.of(action) ?: return
        if (!kind.readsTrip) {
            when (kind) {
                AlarmKind.TEST -> if (Notifications.test(app)) ReminderCheck.rang(app, java.time.Instant.now())
                AlarmKind.PASS -> passAlarm(app)
                else -> Unit
            }
            return
        }
        val r = app.store.reminder.first()
        if (r == null) {
            if (action == ReminderScheduler.ACTION_DISMISS) Notifications.cancelCountdown(app)
            return
        }
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
                }.getOrNull()
                // The user may have cancelled or replaced it while the plan was loading.
                if (app.store.reminder.first() != r) return
                if (fresh == null) {
                    // No signal: keep the reminder as it is and try at the next re-check.
                    ReminderScheduler.schedule(app, r)
                    return
                }
                when (val u = ReminderLogic.update(r, fresh.itineraries)) {
                    is ReminderUpdate.Updated -> {
                        var next = u.reminder
                        ReminderLogic.alertMinutes(next)?.let { moved ->
                            Notifications.timeChanged(app, next, moved)
                            next = next.copy(alertedLeaveAtEpoch = next.leaveAtEpoch)
                        }
                        app.store.setReminder(next)
                        ReminderScheduler.schedule(app, next) // the leave alarm and the next re-check
                        if (next.counting) {
                            // Only a late re-check lands here (they run before leaving): new boarding time, new end.
                            Notifications.countdown(app, next)
                            Countdown.of(next, java.time.Instant.now())?.let { ReminderScheduler.scheduleEnd(app, it.endAt) }
                        }
                    }
                    ReminderUpdate.Gone -> {
                        Notifications.changed(app, r)
                        ReminderScheduler.cancel(app)
                        app.store.setReminder(null)
                    }
                }
            }
            ReminderScheduler.ACTION_LEAVE -> startCountdown(app, r)
            ReminderScheduler.ACTION_END -> {
                // Only the reminder whose countdown is over: a newer one set since stays.
                if (r.counting && Countdown.of(r, java.time.Instant.now()) == null) {
                    Notifications.cancelCountdown(app)
                    app.store.setReminder(null)
                }
            }
            ReminderScheduler.ACTION_DISMISS -> {
                ReminderScheduler.cancel(app)
                app.store.setReminder(null)
            }
        }
    }

    companion object {
        /**
         * Posts the pass notice that is due (if [post]), arms the next, and clears the date once it
         * has passed or its last notice has gone ([PassExpiry.step]).
         */
        internal suspend fun passAlarm(app: TransitApp, post: Boolean = true) {
            val s = app.store.settings.first()
            val step = PassExpiry.step(s.passEndsOn, java.time.Instant.now(), post)
            step.post?.let { Notifications.pass(app, it) }
            ReminderScheduler.armPass(app, step.armAt)
            if (step.clear) app.store.setSettings(s.copy(passEndsOn = null))
        }

        /** Posts the countdown and keeps the reminder until it ends; past its end, drops it. */
        internal suspend fun startCountdown(app: TransitApp, r: Reminder) {
            val end = Countdown.of(r, java.time.Instant.now())?.endAt
            if (end == null) {
                app.store.setReminder(null)
                return
            }
            Notifications.countdown(app, r)
            val counting = r.copy(counting = true)
            app.store.setReminder(counting)
            ReminderScheduler.scheduleEnd(app, end)
        }
    }
}

/**
 * Alarms do not survive a reboot; re-arm the active reminder, bring back its countdown while
 * boarding is still ahead, or drop it if it has passed. The "last trip home" check is re-armed
 * too (and runs at once when the phone was off at check time). An app update clears alarms too
 * and sends no boot broadcast, so it re-arms the same. Phase 11 E1: the Rav-Kav pass notice
 * (posted if the phone slept through it that day) and a pending test reminder come back too; a
 * time-zone change re-arms those two. What each cause re-arms is core's [Rearm].
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cause = Rearm.of(intent.action) ?: return
        val app = context.applicationContext as TransitApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (cause.trip) {
                    val settings = app.store.settings.first()
                    LastTripReceiver.sync(app, settings.lastTripAlert)
                    val r = app.store.reminder.first()
                    if (r != null) {
                        if (!r.counting && r.leaveAt.isAfter(java.time.Instant.now())) ReminderScheduler.schedule(app, r) else ReminderReceiver.startCountdown(app, r)
                    }
                }
            } catch (e: Exception) {
                // Nothing to re-arm.
            }
            // Phase 11 E1, after the trip reminder and on their own, so a failure here cannot cost it:
            // a test still ahead, and the pass notice (09:00 Israel time, whatever the phone's zone).
            runCatching { ReminderCheck.rearmTest(app) }
            runCatching { ReminderReceiver.passAlarm(app, post = cause.postMissedPass) }
            pending.finish()
        }
    }
}
