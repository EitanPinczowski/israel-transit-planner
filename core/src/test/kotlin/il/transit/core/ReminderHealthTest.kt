package il.transit.core

import il.transit.core.remind.AlarmKind
import il.transit.core.remind.FixPage
import il.transit.core.remind.Maker
import il.transit.core.remind.PhoneState
import il.transit.core.remind.ProblemKind
import il.transit.core.remind.ReminderHealth
import il.transit.core.remind.ReminderHealth.CHANNEL_LAST_TRIP
import il.transit.core.remind.ReminderHealth.CHANNEL_PASS
import il.transit.core.remind.ReminderHealth.CHANNEL_REMINDERS
import il.transit.core.remind.Severity
import il.transit.core.remind.TestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Phase 11 E1: "Will my reminder ring?" from the phone's state as plain values. */
class ReminderHealthTest {
    /** A Pixel on Android 15 with everything allowed. */
    private val fine = PhoneState(
        sdk = 35,
        notificationsEnabled = true,
        channels = mapOf(CHANNEL_REMINDERS to 4, CHANNEL_PASS to 3, CHANNEL_LAST_TRIP to 3),
        ignoringBatteryOptimizations = true,
        standbyBucket = 10,
        manufacturer = "Google",
    )

    private fun kinds(s: PhoneState) = ReminderHealth.check(s).map { it.kind }
    private fun only(s: PhoneState) = ReminderHealth.check(s).single()

    @Test fun `all fine on every level we support`() {
        for (sdk in listOf(26, 31, 33, 34, 35)) {
            val p = ReminderHealth.check(fine.copy(sdk = sdk))
            assertEquals("API $sdk", emptyList<Any>(), p)
            assertTrue(ReminderHealth.allSet(p))
            assertTrue(ReminderHealth.canTest(p))
        }
        assertNull(ReminderHealth.maker(fine))
    }

    @Test fun `notifications permission refused on 33+ - the test says it can't ring`() {
        val p = only(fine.copy(sdk = 33, notificationsEnabled = false, postNotificationsGranted = false))
        assertEquals(ProblemKind.NOTIFICATIONS_PERMISSION, p.kind)
        assertEquals(Severity.WONT_RING, p.severity)
        assertEquals(FixPage.APP_NOTIFICATIONS, p.fix)
        assertFalse(ReminderHealth.canTest(listOf(p)))
        assertFalse(ReminderHealth.allSet(listOf(p)))
    }

    @Test fun `notifications off on API 26 (no permission there) - the app's notification page`() {
        val p = only(fine.copy(sdk = 26, notificationsEnabled = false))
        assertEquals(ProblemKind.NOTIFICATIONS_OFF, p.kind)
        assertEquals(FixPage.APP_NOTIFICATIONS, p.fix)
        assertFalse(ReminderHealth.canTest(listOf(p)))
    }

    @Test fun `channels only matter while notifications are on`() {
        val off = fine.copy(notificationsEnabled = false, channels = mapOf(CHANNEL_REMINDERS to 0))
        assertEquals(listOf(ProblemKind.NOTIFICATIONS_OFF), kinds(off))
    }

    @Test fun `the reminders channel switched off, or made quiet`() {
        val off = only(fine.copy(channels = mapOf(CHANNEL_REMINDERS to 0)))
        assertEquals(ProblemKind.REMINDER_CHANNEL_OFF, off.kind)
        assertEquals(Severity.WONT_RING, off.severity)
        assertEquals(FixPage.CHANNEL, off.fix)
        assertEquals(CHANNEL_REMINDERS, off.channel)
        val quiet = only(fine.copy(channels = mapOf(CHANNEL_REMINDERS to 2)))
        assertEquals(ProblemKind.REMINDER_CHANNEL_QUIET, quiet.kind)
        assertEquals(Severity.MAY_FAIL, quiet.severity)
        assertTrue(ReminderHealth.canTest(listOf(quiet)))
        // Not created yet (first run): it will be created at full importance.
        assertEquals(emptyList<Any>(), kinds(fine.copy(channels = emptyMap())))
    }

    @Test fun `the pass and last-trip channels count only when those alerts are on`() {
        val silenced = fine.copy(channels = mapOf(CHANNEL_REMINDERS to 4, CHANNEL_PASS to 0, CHANNEL_LAST_TRIP to 0))
        assertEquals(emptyList<Any>(), kinds(silenced))
        val pass = only(silenced.copy(passSet = true))
        assertEquals(ProblemKind.PASS_CHANNEL_OFF, pass.kind)
        assertEquals(Severity.OTHER_ALERT, pass.severity)
        assertEquals(CHANNEL_PASS, pass.channel)
        val last = only(silenced.copy(lastTripOn = true))
        assertEquals(ProblemKind.LAST_TRIP_CHANNEL_OFF, last.kind)
        assertEquals(CHANNEL_LAST_TRIP, last.channel)
        assertTrue(ReminderHealth.canTest(listOf(pass, last)))
    }

    @Test fun `exact alarms refused - the switch on 31 and 32, the app's page after`() {
        assertEquals(FixPage.EXACT_ALARMS, only(fine.copy(sdk = 31, exactAlarms = false)).fix)
        assertEquals(FixPage.EXACT_ALARMS, only(fine.copy(sdk = 32, exactAlarms = false)).fix)
        val p = only(fine.copy(sdk = 34, exactAlarms = false))
        assertEquals(ProblemKind.EXACT_ALARMS_OFF, p.kind)
        assertEquals(Severity.MAY_FAIL, p.severity)
        assertEquals(FixPage.APP_DETAILS, p.fix)
        assertEquals(emptyList<Any>(), kinds(fine.copy(sdk = 26, exactAlarms = false))) // no such switch before 31
    }

    @Test fun `background restriction blocks, from API 28`() {
        val p = only(fine.copy(sdk = 31, backgroundRestricted = true))
        assertEquals(ProblemKind.BACKGROUND_RESTRICTED, p.kind)
        assertEquals(Severity.WONT_RING, p.severity)
        assertEquals(FixPage.APP_DETAILS, p.fix)
        assertEquals(emptyList<Any>(), kinds(fine.copy(sdk = 26, backgroundRestricted = true)))
    }

    @Test fun `a rare or restricted standby bucket may delay it`() {
        assertEquals(ProblemKind.STANDBY_RESTRICTED, only(fine.copy(standbyBucket = 40)).kind)
        assertEquals(ProblemKind.STANDBY_RESTRICTED, only(fine.copy(standbyBucket = 45)).kind)
        assertEquals(emptyList<Any>(), kinds(fine.copy(standbyBucket = 30)))
        assertEquals(emptyList<Any>(), kinds(fine.copy(standbyBucket = null)))
        assertEquals(emptyList<Any>(), kinds(fine.copy(sdk = 26, standbyBucket = 45)))
    }

    @Test fun `battery optimisation is a tip on stock Android and a risk on a known maker`() {
        val stock = ReminderHealth.check(fine.copy(ignoringBatteryOptimizations = false))
        assertEquals(Severity.TIP, stock.single().severity)
        assertEquals(FixPage.BATTERY_LIST, stock.single().fix)
        assertTrue(ReminderHealth.allSet(stock)) // "All set", with the tip under it
        val samsung = only(fine.copy(ignoringBatteryOptimizations = false, manufacturer = "samsung"))
        assertEquals(Severity.MAY_FAIL, samsung.severity)
    }

    @Test fun `location refused only affects the get-off alert`() {
        val p = only(fine.copy(locationGranted = false))
        assertEquals(ProblemKind.LOCATION_OFF, p.kind)
        assertEquals(Severity.OTHER_ALERT, p.severity)
        assertEquals(FixPage.APP_DETAILS, p.fix)
        assertTrue(ReminderHealth.canTest(listOf(p)))
    }

    @Test fun `worst first`() {
        val bad = fine.copy(
            sdk = 33,
            notificationsEnabled = false,
            postNotificationsGranted = false,
            locationGranted = false,
            ignoringBatteryOptimizations = false,
            exactAlarms = false,
            backgroundRestricted = true,
            manufacturer = "Xiaomi",
        )
        assertEquals(
            listOf(
                ProblemKind.NOTIFICATIONS_PERMISSION,
                ProblemKind.BACKGROUND_RESTRICTED,
                ProblemKind.EXACT_ALARMS_OFF,
                ProblemKind.BATTERY_OPTIMIZED,
                ProblemKind.LOCATION_OFF,
            ),
            kinds(bad),
        )
    }

    @Test fun `each maker line`() {
        val cases = mapOf(
            "samsung" to Maker.SAMSUNG,
            "Xiaomi" to Maker.XIAOMI,
            "Redmi" to Maker.XIAOMI,
            "POCO" to Maker.XIAOMI,
            "HUAWEI" to Maker.HUAWEI,
            "HONOR" to Maker.HONOR,
            "OnePlus" to Maker.ONEPLUS,
            "OPPO" to Maker.OPPO,
            "realme" to Maker.REALME,
            " Samsung Electronics" to Maker.SAMSUNG,
        )
        for ((m, want) in cases) assertEquals(m, want, ReminderHealth.maker(fine.copy(manufacturer = m)))
        for (m in listOf("Google", "motorola", "Nothing", "", "samsungish")) assertNull(m, Maker.of(m))
        assertEquals(listOf("Samsung", "Xiaomi", "Huawei", "Honor", "OnePlus", "Oppo", "Realme"), Maker.entries.map { it.label })
    }

    @Test fun `the test alarm sends 0 requests, only the re-check plans`() {
        assertEquals(0, AlarmKind.TEST.requests)
        assertEquals(0, AlarmKind.PASS.requests)
        assertEquals(listOf(AlarmKind.RECHECK), AlarmKind.entries.filter { it.requests > 0 })
        assertEquals(1, AlarmKind.RECHECK.requests)
    }

    @Test fun `the test result - waiting, rang, missed 3 min after the due time`() {
        val due = Instant.parse("2026-10-14T07:42:00Z")
        assertEquals(TestResult.None, ReminderHealth.testResult(null, null, due))
        assertEquals(TestResult.Waiting(due), ReminderHealth.testResult(due, null, due.minusSeconds(30)))
        assertEquals(TestResult.Waiting(due), ReminderHealth.testResult(due, null, due.plusSeconds(180)))
        assertEquals(TestResult.Missed(due), ReminderHealth.testResult(due, null, due.plusSeconds(181)))
        val rang = due.plusSeconds(4)
        assertEquals(TestResult.Rang(rang), ReminderHealth.testResult(due, rang, due.plusSeconds(600)))
        assertEquals(TestResult.Rang(rang), ReminderHealth.testResult(null, rang, due))
        assertEquals(60L, ReminderHealth.TEST_DELAY.seconds)
    }
}
