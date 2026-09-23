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
 * Two exact alarms per reminder: a real-time re-check [Reminder.RECHECK_BEFORE] ahead, and
 * the "leave now" notification. The app is sideloaded, so USE_EXACT_ALARM is granted on
 * Android 13+; if exact alarms are ever refused, an inexact alarm is still better than none.
 */
object ReminderScheduler {
    const val ACTION_RECHECK = "il.transit.planner.action.RECHECK"
    const val ACTION_LEAVE = "il.transit.planner.action.LEAVE"

    fun schedule(context: Context, r: Reminder, now: Instant = Instant.now()) {
        val recheck = ReminderLogic.recheckTime(r, now)
        if (recheck.isBefore(r.leaveAt)) set(context, recheck, ACTION_RECHECK)
        scheduleLeave(context, r, now)
    }

    fun scheduleLeave(context: Context, r: Reminder, now: Instant = Instant.now()) =
        set(context, maxOf(r.leaveAt, now.plusSeconds(5)), ACTION_LEAVE)

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pending(context, ACTION_RECHECK))
        am.cancel(pending(context, ACTION_LEAVE))
    }

    @SuppressLint("MissingPermission") // exactness is checked; inexact needs no permission
    private fun set(context: Context, at: Instant, action: String) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, action)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
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
