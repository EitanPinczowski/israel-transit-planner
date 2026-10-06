package il.transit.planner.remind

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import il.transit.core.remind.Reminder
import il.transit.core.remind.ReminderLogic
import java.time.Instant

/**
 * Two exact alarms per reminder: the next real-time re-check (leave − [Reminder.RECHECKS]),
 * re-armed after each one, and the "leave now" notification. The app is sideloaded, so USE_EXACT_ALARM is granted on
 * Android 13+; if exact alarms are ever refused, an inexact alarm is still better than none.
 * Once the leave alarm has fired, a third one ([ACTION_END]) lets go of the reminder when
 * its countdown ends (2 min after boarding).
 */
object ReminderScheduler {
    const val ACTION_RECHECK = "il.transit.planner.action.RECHECK"
    const val ACTION_LEAVE = "il.transit.planner.action.LEAVE"
    const val ACTION_END = "il.transit.planner.action.COUNTDOWN_END"
    const val ACTION_DISMISS = "il.transit.planner.action.COUNTDOWN_DISMISS"

    fun schedule(context: Context, r: Reminder, now: Instant = Instant.now()) {
        ReminderLogic.nextRecheck(r, now)?.let { set(context, it, ACTION_RECHECK) }
        if (!r.counting) scheduleLeave(context, r, now)
    }

    fun scheduleLeave(context: Context, r: Reminder, now: Instant = Instant.now()) =
        set(context, maxOf(r.leaveAt, now.plusSeconds(5)), ACTION_LEAVE)

    /** When the countdown is over, the reminder is dropped. */
    fun scheduleEnd(context: Context, at: Instant) = set(context, at, ACTION_END)

    /** Cancelling a reminder also takes its countdown down. */
    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pending(context, ACTION_RECHECK))
        am.cancel(pending(context, ACTION_LEAVE))
        am.cancel(pending(context, ACTION_END))
        Notifications.cancelCountdown(context)
    }

    /** "Dismiss" on the countdown. */
    fun dismissIntent(context: Context): PendingIntent = pending(context, ACTION_DISMISS)

    private fun set(context: Context, at: Instant, action: String) = setAlarm(context, at, pending(context, action), exact = true)

    /**
     * [exact]: exact while allowed, else inexact rather than nothing. Inexact
     * (`setAndAllowWhileIdle`) is for checks that may run a few minutes late.
     */
    @SuppressLint("MissingPermission") // exactness is checked; inexact needs no permission
    internal fun setAlarm(context: Context, at: Instant, pi: PendingIntent, exact: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java)
        val exactAllowed = exact && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms())
        if (exactAllowed) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
        }
    }

    private fun pending(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
