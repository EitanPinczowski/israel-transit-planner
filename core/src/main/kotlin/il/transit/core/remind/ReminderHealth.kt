package il.transit.core.remind

import java.time.Duration
import java.time.Instant

/**
 * "Will my reminder ring?" (Phase 11 E1, 0 requests). The Android side reads the phone's state
 * into a [PhoneState] of plain values; [ReminderHealth.check] says what could stop a reminder,
 * worst first, each with the one settings page that fixes it. No problem = "All set".
 */
data class PhoneState(
    /** `Build.VERSION.SDK_INT`. */
    val sdk: Int,
    /** `NotificationManagerCompat.areNotificationsEnabled()`: the app's own switch (and, on 33+, the permission). */
    val notificationsEnabled: Boolean,
    /** `POST_NOTIFICATIONS` granted; always true below 33, where it does not exist. */
    val postNotificationsGranted: Boolean = true,
    /** Importance of each of our channels by id (`NotificationManager.IMPORTANCE_*`); a missing id is not created yet. */
    val channels: Map<String, Int> = emptyMap(),
    /** `AlarmManager.canScheduleExactAlarms()`; always true below 31. */
    val exactAlarms: Boolean = true,
    /** `PowerManager.isIgnoringBatteryOptimizations`. */
    val ignoringBatteryOptimizations: Boolean = false,
    /** `ActivityManager.isBackgroundRestricted` (28+; false below). */
    val backgroundRestricted: Boolean = false,
    /** `UsageStatsManager.appStandbyBucket` (28+; null below). */
    val standbyBucket: Int? = null,
    /** Fine or coarse location, for the "get off at the next stop" alert. */
    val locationGranted: Boolean = true,
    /** `Build.MANUFACTURER`. */
    val manufacturer: String = "",
    /** A Rav-Kav pass date is set, so its channel matters. */
    val passSet: Boolean = false,
    /** "Last trip home" is on, so its channel matters. */
    val lastTripOn: Boolean = false,
)

/** How bad a problem is, worst first. */
enum class Severity {
    /** Reminders will not show at all. */
    WONT_RING,

    /** Reminders may come late, or without sound. */
    MAY_FAIL,

    /** Another alert (pass, last trip, get-off) will not show. */
    OTHER_ALERT,

    /** Worth a look; stock Android rings anyway. */
    TIP,
}

/** The one page each problem's button opens. */
enum class FixPage {
    /** `Settings.ACTION_APP_NOTIFICATION_SETTINGS`. */
    APP_NOTIFICATIONS,

    /** `Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS` for [ReminderProblem.channel]. */
    CHANNEL,

    /** `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` (31–32, where the app may ask). */
    EXACT_ALARMS,

    /** `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`: the list, so no new permission. */
    BATTERY_LIST,

    /** The app's details page ("Battery → Unrestricted", permissions). */
    APP_DETAILS,
}

/** The text is the kind's own string, `check_<kind in lower case>`, in both languages. */
enum class ProblemKind {
    NOTIFICATIONS_PERMISSION,
    NOTIFICATIONS_OFF,
    REMINDER_CHANNEL_OFF,
    BACKGROUND_RESTRICTED,
    REMINDER_CHANNEL_QUIET,
    EXACT_ALARMS_OFF,
    STANDBY_RESTRICTED,
    BATTERY_OPTIMIZED,
    PASS_CHANNEL_OFF,
    LAST_TRIP_CHANNEL_OFF,
    LOCATION_OFF,
}

data class ReminderProblem(
    val kind: ProblemKind,
    val severity: Severity,
    val fix: FixPage,
    /** For [FixPage.CHANNEL]: which channel. */
    val channel: String? = null,
)

/** Phone makers known to stop background apps harder than stock Android (owner decision 3: generic text plus the name). */
enum class Maker(val label: String, private val names: List<String>) {
    SAMSUNG("Samsung", listOf("samsung")),
    XIAOMI("Xiaomi", listOf("xiaomi", "redmi", "poco")),
    HUAWEI("Huawei", listOf("huawei")),
    HONOR("Honor", listOf("honor")),
    ONEPLUS("OnePlus", listOf("oneplus")),
    OPPO("Oppo", listOf("oppo")),
    REALME("Realme", listOf("realme")),
    ;

    companion object {
        fun of(manufacturer: String): Maker? {
            val m = manufacturer.trim().lowercase()
            return entries.firstOrNull { e -> e.names.any { m == it || m.startsWith("$it ") } }
        }
    }
}

/**
 * What a reminder alarm does, and how many Transitous requests it may send. [action] is the
 * broadcast's action string (`ReminderScheduler` sets it, `ReminderReceiver` dispatches on [of]).
 * Only kinds that [readsTrip] look at the stored reminder; the others are handled before it is read.
 */
enum class AlarmKind(val action: String, val requests: Int, val readsTrip: Boolean) {
    /** A real-time re-check of the reminder's trip: one `plan`. */
    RECHECK("il.transit.planner.action.RECHECK", 1, true),
    LEAVE("il.transit.planner.action.LEAVE", 0, true),
    END("il.transit.planner.action.COUNTDOWN_END", 0, true),
    DISMISS("il.transit.planner.action.COUNTDOWN_DISMISS", 0, true),

    /** "Test reminder in 1 minute": posts and stores the time; never looks at a trip. */
    TEST("il.transit.planner.action.TEST_REMINDER", 0, false),

    /** Rav-Kav pass expiry. */
    PASS("il.transit.planner.action.PASS_EXPIRY", 0, false),
    ;

    companion object {
        fun of(action: String?): AlarmKind? = entries.firstOrNull { it.action == action }
    }
}

/** Where the test reminder stands on the check screen. */
sealed interface TestResult {
    data object None : TestResult
    data class Waiting(val dueAt: Instant) : TestResult
    data class Rang(val at: Instant) : TestResult

    /** [ReminderHealth.MISSED_AFTER] past the due time with no delivery stored. */
    data class Missed(val dueAt: Instant) : TestResult
}

object ReminderHealth {
    const val CHANNEL_REMINDERS = "trip_reminders"
    const val CHANNEL_PASS = "rav_kav_pass"
    const val CHANNEL_LAST_TRIP = "last_trip_home"

    // NotificationManager.IMPORTANCE_*: plain numbers so core needs no Android.
    const val IMPORTANCE_NONE = 0
    const val IMPORTANCE_LOW = 2
    const val IMPORTANCE_DEFAULT = 3

    /** UsageStatsManager.STANDBY_BUCKET_RARE (40) and RESTRICTED (45): alarms are rationed. */
    const val BUCKET_RARE = 40

    /** The test alarm is due this long after the tap. */
    val TEST_DELAY: Duration = Duration.ofMinutes(1)

    /** No delivery this long after the due time: "The test did not ring". */
    val MISSED_AFTER: Duration = Duration.ofMinutes(3)

    /** Everything that could stop a reminder, worst first (ties in [ProblemKind] order). */
    fun check(s: PhoneState): List<ReminderProblem> {
        val out = ArrayList<ReminderProblem>()
        val maker = Maker.of(s.manufacturer)
        // The permission only exists from 33; below, a false value means nothing.
        val notifications = s.notificationsEnabled && (s.sdk < 33 || s.postNotificationsGranted)
        when {
            s.sdk >= 33 && !s.postNotificationsGranted ->
                out += ReminderProblem(ProblemKind.NOTIFICATIONS_PERMISSION, Severity.WONT_RING, FixPage.APP_NOTIFICATIONS)
            !s.notificationsEnabled ->
                out += ReminderProblem(ProblemKind.NOTIFICATIONS_OFF, Severity.WONT_RING, FixPage.APP_NOTIFICATIONS)
        }
        // A channel's own switch only matters while the app's notifications are on.
        if (notifications) {
            val reminders = s.channels[CHANNEL_REMINDERS]
            when {
                reminders == IMPORTANCE_NONE ->
                    out += ReminderProblem(ProblemKind.REMINDER_CHANNEL_OFF, Severity.WONT_RING, FixPage.CHANNEL, CHANNEL_REMINDERS)
                reminders != null && reminders < IMPORTANCE_DEFAULT ->
                    out += ReminderProblem(ProblemKind.REMINDER_CHANNEL_QUIET, Severity.MAY_FAIL, FixPage.CHANNEL, CHANNEL_REMINDERS)
            }
            if (s.passSet && s.channels[CHANNEL_PASS] == IMPORTANCE_NONE) {
                out += ReminderProblem(ProblemKind.PASS_CHANNEL_OFF, Severity.OTHER_ALERT, FixPage.CHANNEL, CHANNEL_PASS)
            }
            if (s.lastTripOn && s.channels[CHANNEL_LAST_TRIP] == IMPORTANCE_NONE) {
                out += ReminderProblem(ProblemKind.LAST_TRIP_CHANNEL_OFF, Severity.OTHER_ALERT, FixPage.CHANNEL, CHANNEL_LAST_TRIP)
            }
        }
        if (s.sdk >= 28 && s.backgroundRestricted) {
            out += ReminderProblem(ProblemKind.BACKGROUND_RESTRICTED, Severity.WONT_RING, FixPage.APP_DETAILS)
        }
        if (s.sdk >= 31 && !s.exactAlarms) {
            // Only 31–32 let the app send the user to the switch; on 33+ USE_EXACT_ALARM has none, so the app's page.
            out += ReminderProblem(ProblemKind.EXACT_ALARMS_OFF, Severity.MAY_FAIL, if (s.sdk <= 32) FixPage.EXACT_ALARMS else FixPage.APP_DETAILS)
        }
        if (s.sdk >= 28 && (s.standbyBucket ?: 0) >= BUCKET_RARE) {
            out += ReminderProblem(ProblemKind.STANDBY_RESTRICTED, Severity.MAY_FAIL, FixPage.APP_DETAILS)
        }
        if (!s.ignoringBatteryOptimizations) {
            // Stock Android still rings exact alarms for an optimised app; some makers' savers do not.
            out += ReminderProblem(ProblemKind.BATTERY_OPTIMIZED, if (maker != null) Severity.MAY_FAIL else Severity.TIP, FixPage.BATTERY_LIST)
        }
        if (!s.locationGranted) {
            out += ReminderProblem(ProblemKind.LOCATION_OFF, Severity.OTHER_ALERT, FixPage.APP_DETAILS)
        }
        return out.sortedWith(compareBy({ it.severity }, { it.kind }))
    }

    /** The maker line under the list, or null on phones without a known battery saver. */
    fun maker(s: PhoneState): Maker? = Maker.of(s.manufacturer)

    /** A test would not show anything: the button says so instead of pretending. */
    fun canTest(problems: List<ReminderProblem>): Boolean = problems.none { it.severity == Severity.WONT_RING }

    /** Only [Severity.TIP]s (or nothing): "All set: reminders should ring on this phone". */
    fun allSet(problems: List<ReminderProblem>): Boolean = problems.all { it.severity == Severity.TIP }

    /**
     * [dueAt]: when the last test was armed for (null: never); [rangAt]: when its notification
     * was posted (cleared when a new test is armed).
     */
    fun testResult(dueAt: Instant?, rangAt: Instant?, now: Instant): TestResult = when {
        rangAt != null -> TestResult.Rang(rangAt)
        dueAt == null -> TestResult.None
        now.isAfter(dueAt.plus(MISSED_AFTER)) -> TestResult.Missed(dueAt)
        else -> TestResult.Waiting(dueAt)
    }
}
