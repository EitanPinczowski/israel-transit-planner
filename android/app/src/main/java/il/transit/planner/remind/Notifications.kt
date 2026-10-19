package il.transit.planner.remind

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import il.transit.core.plan.LastTripAlert
import il.transit.core.plan.LastTripHome
import il.transit.core.plan.MissedIt
import il.transit.core.remind.Countdown
import il.transit.core.remind.LiveProgress
import il.transit.core.remind.PassExpiry
import il.transit.core.remind.ReminderHealth
import il.transit.core.remind.Reminder
import il.transit.planner.MainActivity
import il.transit.planner.R
import il.transit.planner.tile.NextTripTile
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale

object Notifications {
    private const val CHANNEL = ReminderHealth.CHANNEL_REMINDERS
    private const val ID_LEAVE = 1001
    private const val ID_CHANGED = 1002

    /** "Last trip home" has its own channel, so it can be silenced without silencing reminders. */
    private const val CHANNEL_LAST_TRIP = ReminderHealth.CHANNEL_LAST_TRIP
    private const val ID_LAST_TRIP = 1003

    /** Rav-Kav pass expiry (Phase 11 E1): its own channel, default importance. */
    private const val CHANNEL_PASS = ReminderHealth.CHANNEL_PASS
    private const val ID_PASS = 1004
    private const val ID_TEST = 1005

    /** A tap on the pass notice opens Settings (E4 points it at the payment app). */
    const val EXTRA_OPEN_SETTINGS = "open_settings"

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
        if (nm.getNotificationChannel(CHANNEL_PASS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PASS, context.getString(R.string.channel_pass), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    /**
     * "Test: your reminders work", on the reminders' own channel (Phase 11 E1). True when it was
     * handed to the system: notifications on and the channel not switched off.
     */
    fun test(context: Context): Boolean {
        ensureChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        return post(context, ID_TEST, context.getString(R.string.check_test_title), context.getString(R.string.check_test_text))
    }

    /** "Your Rav-Kav pass ends on Sun 1 Nov" (3 days before) / "… ends today, Sun 1 Nov". Nothing about prices. */
    fun pass(context: Context, n: PassExpiry.Notice) {
        val date = PassExpiry.dateLabel(n.endsOn, context.resources.configuration.locales[0])
        val text = when (n.kind) {
            PassExpiry.Kind.SOON -> context.getString(R.string.pass_ends_on, date)
            PassExpiry.Kind.LAST_DAY -> context.getString(R.string.pass_ends_today, date)
        }
        val open = PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java)
                .setAction(EXTRA_OPEN_SETTINGS)
                .putExtra(EXTRA_OPEN_SETTINGS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(context, ID_PASS, context.getString(R.string.pass_title), text, CHANNEL_PASS) { b ->
            b.setContentIntent(open).setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
        // Android 16+: a Live Update (the trip's legs as a bar, the chip counting down); below, a plain bar.
        val live = r.itinerary?.let { LiveProgress.countdown(it, c.boardAt, now) }
        post(context, ID_LEAVE, context.getString(R.string.notify_leave_title), text, live = live) { b ->
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
            b.addAction(MissedItAction.action(context, MissedIt.Target(r.toName, r.to, r.arriveBy))) // Phase 11 E2
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
        post(context, ID_LAST_TRIP, context.getString(R.string.last_trip_title), head + next + LastTripReceiver.eveNote(context, a), CHANNEL_LAST_TRIP) { b ->
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
    ) { b -> b.addAction(MissedItAction.action(context, MissedIt.Target(r.toName, r.to, r.arriveBy))) } // Phase 11 E2

    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers POST_NOTIFICATIONS
    private fun post(
        context: Context,
        id: Int,
        title: String,
        text: String,
        channel: String = CHANNEL,
        live: LiveProgress? = null,
        extra: (NotificationCompat.Builder) -> Unit = {},
    ): Boolean {
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false
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
        nm.notify(id, build(context, b, live, text))
        return true
    }

    /**
     * Phase 10 D10. With [live] on Android 16+: a Live Update, `ProgressStyle` with one segment
     * per leg in its [il.transit.core.present.LegPalette] colour, a point per boarding, promoted
     * to the status bar with [LiveProgress.chip] as its text. Below 16: the same notification
     * with a plain progress bar. Either way one notification, posted on the caller's id.
     * The caller makes it ongoing with a title, which promotion needs. [text] is the caller's
     * content text: ProgressStyle replaces the BigTextStyle that showed it whole, so on 16+ its
     * last " · " piece ("walk 6 min", "arrive 08:55") moves to the header's subtext.
     */
    fun build(context: Context, b: NotificationCompat.Builder, live: LiveProgress?, text: String? = null): Notification {
        if (live == null) return b.build()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            return b.setProgress(live.max, live.progress, false).build()
        }
        return LiveUpdate.build(context, b, live, text, shortText(context, live.chip))
    }

    /** The Android 16 calls, apart so the rest of [Notifications] never refers to them below 36. */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private object LiveUpdate {
        fun build(context: Context, b: NotificationCompat.Builder, live: LiveProgress, text: String?, chip: String?): Notification {
            val cut = text?.lastIndexOf(Countdown.SEP) ?: -1
            if (text != null && cut > 0) {
                b.setContentText(text.substring(0, cut)).setSubText(text.substring(cut + Countdown.SEP.length))
            }
            val style = NotificationCompat.ProgressStyle()
                .setProgressSegments(live.segments.map { NotificationCompat.ProgressStyle.Segment(it.length).setColor(Color.parseColor(it.color)) })
                .setProgressPoints(live.points.map { NotificationCompat.ProgressStyle.Point(it) })
                .setProgress(live.progress)
            val n = b.setStyle(style).setRequestPromotedOngoing(true).build()
            if (chip == null) return n
            return Notification.Builder.recoverBuilder(context, n).setShortCriticalText(chip).build()
        }
    }

    /** The status-bar chip: "3 stops", "8 min" (short: the chip shows ~7 characters). */
    fun shortText(context: Context, s: LiveProgress.Chip?): String? = when (s) {
        is LiveProgress.Chip.Stops -> context.resources.getQuantityString(R.plurals.live_stops, s.n, s.n)
        is LiveProgress.Chip.Minutes -> context.resources.getQuantityString(R.plurals.live_min, s.n, s.n)
        null -> null
    }
}
