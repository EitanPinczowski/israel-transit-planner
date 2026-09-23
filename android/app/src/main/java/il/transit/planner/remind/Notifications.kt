package il.transit.planner.remind

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import il.transit.core.remind.Reminder
import il.transit.planner.MainActivity
import il.transit.planner.R

object Notifications {
    private const val CHANNEL = "trip_reminders"
    private const val ID_LEAVE = 1001
    private const val ID_CHANGED = 1002

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    fun leaveNow(context: Context, r: Reminder) {
        val line = r.line ?: context.getString(R.string.kind_other)
        val delay = r.boardingDelayMin?.takeIf { it > 0 }?.let { " " + context.getString(R.string.late, it) }.orEmpty()
        post(
            context,
            ID_LEAVE,
            context.getString(R.string.notify_leave_title),
            context.getString(R.string.board_line, line, r.boardingTime, r.boardStop) + delay,
        )
    }

    fun changed(context: Context, r: Reminder) = post(
        context,
        ID_CHANGED,
        context.getString(R.string.notify_changed_title),
        context.getString(R.string.notify_changed_text, r.line ?: "", r.boardStop),
    )

    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers POST_NOTIFICATIONS
    private fun post(context: Context, id: Int, title: String, text: String) {
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify(id, n)
    }
}
