package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.LayoutDirection
import com.android.resources.NightMode
import il.transit.core.remind.PassExpiry
import il.transit.core.remind.PhoneState
import il.transit.core.remind.ReminderHealth
import il.transit.core.remind.TestResult
import il.transit.planner.ui.screens.PassExpirySection
import il.transit.planner.ui.screens.ReminderCheckContent
import il.transit.planner.ui.screens.ReminderCheckRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant
import java.time.LocalDate

/**
 * Phase 11 E1: "Will my reminder ring?" with 0, 1 and 3 problems (and the test's waiting, rang and
 * missed lines), the Settings row, and "Rav-Kav pass ends on" (Off, a date, a refused date), in
 * both languages, both themes and large text. The test and pass notifications are system ones,
 * which Paparazzi cannot draw (journey j15 checks the test is posted).
 */
@RunWith(Parameterized::class)
class ReminderCheckShotsTest(private val v: Variant) {
    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    private val pixel = PhoneState(
        sdk = 35,
        notificationsEnabled = true,
        channels = mapOf(ReminderHealth.CHANNEL_REMINDERS to 4),
        ignoringBatteryOptimizations = true,
        manufacturer = "Google",
    )
    private val due = Instant.parse("2026-10-14T07:42:00Z") // 10:42 Israel

    private fun check(name: String, phone: PhoneState, test: TestResult) = paparazzi.snapshot(name) {
        Card { ReminderCheckContent(ReminderHealth.check(phone), ReminderHealth.maker(phone), test, onFix = {}, onTest = {}) }
    }

    /** Nothing wrong: "All set", the last test rang. */
    @Test fun checkAllSet() = check("check_all_set", pixel, TestResult.Rang(due.plusSeconds(3)))

    /** One problem on a Samsung: battery optimisation, with the maker line; the test is waiting. */
    @Test fun checkOne() = check("check_one", pixel.copy(ignoringBatteryOptimizations = false, manufacturer = "samsung"), TestResult.Waiting(due))

    /** Three: notifications refused (the test can't ring), exact alarms off, location refused; the last test missed. */
    @Test fun checkThree() = check(
        "check_three",
        pixel.copy(sdk = 33, notificationsEnabled = false, postNotificationsGranted = false, exactAlarms = false, locationGranted = false),
        TestResult.Missed(due),
    )

    @Test fun checkRow() = paparazzi.snapshot("check_row") { Card { ReminderCheckRow {} } }

    @Test fun passOff() = paparazzi.snapshot("pass_off") { Card { PassExpirySection(null, null, {}, {}) } }

    @Test fun passSet() = paparazzi.snapshot("pass_set") { Card { PassExpirySection(LocalDate.parse("2026-11-01"), null, {}, {}) } }

    @Test fun passRefused() = paparazzi.snapshot("pass_refused") { Card { PassExpirySection(null, PassExpiry.Validity.TOO_FAR, {}, {}) } }

    /** One state in a settings-like card, scrolling so large text is never cut. */
    @Composable
    private fun Card(content: @Composable () -> Unit) = Frame {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
            }
        }
    }

    /** Theme + direction; Paparazzi does not flip the layout for Hebrew by itself. */
    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val direction = if (v.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) { AppTheme(dark = v.dark, content = content) }
    }

    companion object {
        private val phone = DeviceConfig.PIXEL_5
        private val hebrew = phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants() = listOf(
            Variant("en_light", phone, dark = false, rtl = false),
            Variant("en_dark", phone.copy(nightMode = NightMode.NIGHT), dark = true, rtl = false),
            Variant("en_bigtext", phone.copy(fontScale = 1.5f), dark = false, rtl = false),
            Variant("he_light", hebrew, dark = false, rtl = true),
            Variant("he_dark", hebrew.copy(nightMode = NightMode.NIGHT), dark = true, rtl = true),
            Variant("he_bigtext", hebrew.copy(fontScale = 1.5f), dark = false, rtl = true),
        )
    }
}
