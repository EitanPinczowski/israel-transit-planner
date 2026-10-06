package il.transit.planner.remind

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import il.transit.core.plan.LastTripAlert
import il.transit.core.plan.LastTripHome
import il.transit.core.remind.Countdown
import il.transit.core.remind.Reminder
import il.transit.planner.MainActivity
import il.transit.planner.R
import il.transit.planner.tile.NextTripTile
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale

object Notifications {
    private const val CHANNEL = "trip_reminders"
    private const val ID_LEAVE = 1001
    private const val ID_CHANGED = 1002

    /** "Last trip home" has its own channel, so it can be silenced without silencing reminders. */
    private const val CHANNEL_LAST_TRIP = "last_trip_home"
    private const val ID_LAST_TRIP = 1003

    /** "Start trip" on the countdown: [MainActivity] starts the reminder's ride, as the in-app button does. */
    const val EXTRA_START_RIDE = "start_reminder_ride"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH),
            )
        }
        if (nm.getNotificationChannel(CHANNEL_LAST_TRIP) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_LAST_TRIP, context.getString(R.string.channel_last_trip), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    /**
     * The leave alarm's notification, ongoing (Phase 9 C5): it counts down to boarding and
     * says "Line 5 · board at Rager/Oren · walk 6 min". Same id as before, so there is never
     * a second one; posting again (a re-check moved the bus) updates it without a new sound.
     * It removes itself [Countdown.LINGER] after boarding. False when that time has passed.
     */
    fun countdown(context: Context, r: Reminder, now: Instant = Instant.now()): Boolean {
        val c = Countdown.of(r, now) ?: return false
        val line = when {
            c.line == null -> context.getString(R.string.kind_other)
            c.rail -> context.getString(R.string.countdown_train, c.line)
            else -> context.getString(R.string.countdown_line, c.line)
        }
        val walk = c.walkMin?.let { context.resources.getQuantityString(R.plurals.countdown_walk, it, it) }
        val text = Countdown.text(line, context.getString(R.string.countdown_board, c.boardStop), walk)
        post(context, ID_LEAVE, context.getString(R.string.notify_leave_title), text) { b ->
            b.setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(c.boardAt.toEpochMilli())
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setTimeoutAfter(c.timeoutMs(now))
            if (r.itinerary != null) {
                val start = PendingIntent.getActivity(
                    context,
                    1,
                    Intent(context, MainActivity::class.java)
                        .setAction(EXTRA_START_RIDE)
                        .putExtra(EXTRA_START_RIDE, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                b.addAction(0, context.getString(R.string.countdown_start), start)
            }
            b.addAction(0, context.getString(R.string.countdown_dismiss), ReminderScheduler.dismissIntent(context))
        }
        return true
    }

    /** Removes the countdown: the ride started, the reminder was cancelled or dismissed. */
    fun cancelCountdown(context: Context) = NotificationManagerCompat.from(context).cancel(ID_LEAVE)

    /**
     * "Last trip home 23:10 from Be'er Sheva Central: leave in 30 min" (+ "(next: Saturday
     * 19:30)" before a long gap). A tap opens the trip home (C4's intent).
     */
    fun lastTrip(context: Context, a: LastTripAlert, m: LastTripHome.Message) {
        val head = when (m) {
            is LastTripHome.Message.LeaveIn ->
                context.resources.getQuantityString(R.plurals.last_trip_leave_in, m.minutes, a.boardTime, a.boardStop, m.minutes)
            LastTripHome.Message.LeaveNow -> context.getString(R.string.last_trip_leave_now, a.boardTime, a.boardStop)
        }
        val next = if (a.nextDay != null && a.nextTime != null) {
            val day = DayOfWeek.of(a.nextDay!!).getDisplayName(TextStyle.FULL, Locale.getDefault())
            " " + context.getString(R.string.last_trip_next, day, a.nextTime)
        } else {
            ""
        }
        val open = PendingIntent.getActivity(context, 2, NextTripTile.tripHomeIntent(context), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        post(context, ID_LAST_TRIP, context.getString(R.string.last_trip_title), head + next, CHANNEL_LAST_TRIP) { b ->
            b.setContentIntent(open).setPriority(NotificationCompat.PRIORITY_DEFAULT)
        }
    }

    /** "Bus 3 is 4 min late — leave at 08:16" / "comes 3 min early — leave at 08:09". */
    fun timeChanged(context: Context, r: Reminder, movedMin: Long) {
        val line = r.line ?: context.getString(R.string.kind_other)
        val leave = il.transit.core.present.hhmm(r.leaveAt)
        val text = if (movedMin > 0) {
            context.getString(R.string.notify_late_text, line, movedMin.toInt(), leave)
        } else {
            context.getString(R.string.notify_early_text, line, (-movedMin).toInt(), leave)
        }
        post(context, ID_CHANGED, context.getString(R.string.notify_time_changed_title), text)
    }

    fun changed(context: Context, r: Reminder) = post(
        context,
        ID_CHANGED,
        context.getString(R.string.notify_changed_title),
        context.getString(R.string.notify_changed_text, r.line ?: "", r.boardStop),
    )

    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers POST_NOTIFICATIONS
    private fun post(
        context: Context,
        id: Int,
        title: String,
        text: String,
        channel: String = CHANNEL,
        extra: (NotificationCompat.Builder) -> Unit = {},
    ) {
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
        extra(b)
        nm.notify(id, b.build())
    }
}
