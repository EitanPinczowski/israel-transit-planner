package il.transit.planner.remind

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import il.transit.core.remind.FixPage
import il.transit.core.remind.PhoneState
import il.transit.core.remind.ReminderHealth
import il.transit.core.remind.ReminderProblem
import il.transit.core.remind.TestResult
import il.transit.core.user.UserSettings
import java.time.Instant

/**
 * "Will my reminder ring?" (Phase 11 E1): reads the phone's state for core's [ReminderHealth],
 * opens the one settings page that fixes a problem, and keeps the test reminder's due and
 * delivery times (system clock, like the alarm itself). 0 requests.
 */
object ReminderCheck {
    private const val PREFS = "reminder_check"
    private const val DUE = "test_due"
    private const val RANG = "test_rang"

    fun state(context: Context, s: UserSettings): PhoneState {
        Notifications.ensureChannel(context) // a channel not created yet would read as "fine" anyway; this makes it real
        val nm = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(ReminderHealth.CHANNEL_REMINDERS, ReminderHealth.CHANNEL_PASS, ReminderHealth.CHANNEL_LAST_TRIP)
            .mapNotNull { id -> nm.getNotificationChannel(id)?.let { id to it.importance } }
            .toMap()
        val sdk = Build.VERSION.SDK_INT
        val am = context.getSystemService(AlarmManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        return PhoneState(
            sdk = sdk,
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            postNotificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(context, Manifest.permission.POST_NOTIFICATIONS),
            channels = channels,
            exactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms(),
            ignoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.packageName),
            backgroundRestricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && context.getSystemService(ActivityManager::class.java).isBackgroundRestricted,
            standbyBucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) context.getSystemService(UsageStatsManager::class.java).appStandbyBucket else null,
            locationGranted = granted(context, Manifest.permission.ACCESS_FINE_LOCATION) || granted(context, Manifest.permission.ACCESS_COARSE_LOCATION),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            passSet = s.passEndsOn != null,
            lastTripOn = s.lastTripAlert,
        )
    }

    private fun granted(context: Context, p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    /** Exactly the page that fixes [p]; the app's details page if the phone has no such screen. */
    fun open(context: Context, p: ReminderProblem) {
        val pkg = context.packageName
        val intent = when (p.fix) {
            FixPage.APP_NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            FixPage.CHANNEL -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                .putExtra(Settings.EXTRA_CHANNEL_ID, p.channel)
            FixPage.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg"))
            } else {
                details(pkg)
            }
            FixPage.BATTERY_LIST -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            FixPage.APP_DETAILS -> details(pkg)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Some makers remove a settings screen: the app's page is always there.
            runCatching { context.startActivity(details(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private fun details(pkg: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))

    /** Arms the test reminder [ReminderHealth.TEST_DELAY] from now; forgets the last delivery. */
    fun startTest(context: Context, now: Instant = Instant.now()): Instant {
        val due = now.plus(ReminderHealth.TEST_DELAY)
        prefs(context)
            .edit()
            .putLong(DUE, due.toEpochMilli())
            .remove(RANG)
            .apply()
        ReminderScheduler.scheduleTest(context, due)
        return due
    }

    /** The receiver posted the test notification. */
    fun rang(context: Context, at: Instant) {
        prefs(context).edit().putLong(RANG, at.toEpochMilli()).apply()
    }

    fun result(context: Context, now: Instant = Instant.now()): TestResult {
        val p = prefs(context)
        val due = p.getLong(DUE, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        val rang = p.getLong(RANG, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        return ReminderHealth.testResult(due, rang, now)
    }

    /** After a reboot: a test still ahead (or just missed while the phone was off) is armed again. */
    fun rearmTest(context: Context, now: Instant = Instant.now()) {
        val r = result(context, now)
        if (r is TestResult.Waiting) ReminderScheduler.scheduleTest(context, maxOf(r.dueAt, now.plusSeconds(5)))
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
