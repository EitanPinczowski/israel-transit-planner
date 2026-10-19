package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.LayoutDirection
import com.android.resources.NightMode
import il.transit.core.features.ISRAEL
import il.transit.core.plan.TimeMode
import il.transit.planner.R
import il.transit.planner.ui.screens.ClockStepContent
import il.transit.planner.ui.screens.DateStepContent
import il.transit.planner.ui.screens.DayStepContent
import il.transit.planner.ui.screens.LocalDayClock
import il.transit.planner.ui.screens.TimeRow
import il.transit.planner.ui.screens.dateDialogMargin
import il.transit.planner.ui.screens.pastAskText
import il.transit.planner.ui.screens.rememberDayPickerState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Pick a day (Phase 11 E7): the day step, the date picker, the clock, the "Tomorrow at …?"
 * question and the time chips with a day, in English and Hebrew, light and dark, and on the
 * small phone (360 × 640 dp) at font ×1.3 with the longest Hebrew labels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(Parameterized::class)
class DayPickShotsTest(private val v: Variant) {
    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    /** Tue 22 Sep 2026, 10:00: Erev Sukkot on Friday, Sukkot on Shabbat, Chol HaMoed on Sunday. Tomorrow picked. */
    @Test fun dayStep() = paparazzi.snapshot("day_step") {
        Card {
            Column {
                Title(stringResource(R.string.day_step_title))
                DayStepContent(sukkotWeek, LocalDate.of(2026, 9, 23), {}, {})
                Buttons(stringResource(R.string.cancel))
            }
        }
    }

    /** 00:30 on Tue 20 Oct: still Monday night, so Today and Tomorrow name their dates; a date from Other date… picked. */
    @Test fun dayStepNight() = paparazzi.snapshot("day_step_night") {
        Card {
            Column {
                Title(stringResource(R.string.day_step_title))
                DayStepContent(il("2026-10-20T00:30"), LocalDate.of(2026, 11, 5), {}, {})
                Buttons(stringResource(R.string.cancel))
            }
        }
    }

    /**
     * Other date…: Material's picker. January 2026, so the picture never meets the real "today"
     * the picker marks by itself; days before the 10th are greyed out.
     */
    @Test fun datePicker() = paparazzi.snapshot("day_date_picker") {
        Frame {
            // As DateStepDialog draws it: its side margin for this screen, the picker, then Cancel and OK.
            val margin = dateDialogMargin(LocalConfiguration.current.screenWidthDp)
            Box(Modifier.fillMaxSize().background(scrim()).padding(horizontal = margin), contentAlignment = Alignment.Center) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    DateStepContent(rememberDayPickerState(il("2026-01-10T09:00"), LocalDate.of(2026, 1, 14)), {}, {})
                }
            }
        }
    }

    /** The clock for a day named by its date. */
    @Test fun clockStep() = paparazzi.snapshot("day_clock") {
        Frame {
            Box(Modifier.fillMaxSize().background(scrim()).padding(16.dp), contentAlignment = Alignment.Center) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    ClockStepContent(LocalDate.of(2026, 10, 23), monday, rememberTimePickerState(17, 30, is24Hour = true), false, {}, {}, {})
                }
            }
        }
    }

    /** The clock with the fields to type in, for tomorrow. */
    @Test fun clockTyping() = paparazzi.snapshot("day_clock_typing") {
        Frame {
            Box(Modifier.fillMaxSize().background(scrim()).padding(16.dp), contentAlignment = Alignment.Center) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    ClockStepContent(LocalDate.of(2026, 10, 20), monday, rememberTimePickerState(8, 0, is24Hour = true), true, {}, {}, {})
                }
            }
        }
    }

    /** 06:10 picked at 10:00: asked, never moved. */
    @Test fun pastAsk() = pastAskShot("day_past_ask", il("2026-10-19T06:10"), monday)

    /** 00:10 picked at 00:30 (still Monday night): the question names the date, as the chip will. */
    @Test fun pastAskNight() = pastAskShot("day_past_ask_night", il("2026-10-20T00:10"), il("2026-10-20T00:30"))

    private fun pastAskShot(name: String, at: Instant, now: Instant) = paparazzi.snapshot(name) {
        Card {
            Column {
                Title(stringResource(R.string.past_title))
                Text(pastAskText(at, now), style = MaterialTheme.typography.bodyMedium)
                Buttons(stringResource(R.string.past_change), stringResource(R.string.past_yes))
            }
        }
    }

    /** The chips with a day: tomorrow, a date, an arrive-by date; and today, no day. */
    @Test fun timeChips() = paparazzi.snapshot("day_time_chips") {
        Frame {
            CompositionLocalProvider(LocalDayClock provides Clock.fixed(monday, ISRAEL)) {
                Box(Modifier.fillMaxSize().background(scrim()), contentAlignment = Alignment.TopCenter) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TimeRow(UiState(timeMode = TimeMode.DEPART_AT, time = il("2026-10-20T08:15")), NoActions)
                            TimeRow(UiState(timeMode = TimeMode.DEPART_AT, time = il("2026-10-23T17:30")), NoActions)
                            TimeRow(UiState(timeMode = TimeMode.ARRIVE_BY, time = il("2026-10-23T17:30")), NoActions)
                            TimeRow(UiState(timeMode = TimeMode.ARRIVE_BY, time = il("2026-10-19T17:30")), NoActions)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))

    /** AlertDialog's buttons: at the end, the main one last. */
    @Composable
    private fun Buttons(vararg labels: String) = Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
        labels.forEach { TextButton(onClick = {}) { Text(it) } }
    }

    /** Roughly what AlertDialog draws around its content. */
    @Composable
    private fun Card(content: @Composable () -> Unit) = Frame {
        Box(Modifier.fillMaxSize().background(scrim()).padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(Modifier.padding(24.dp)) { content() }
            }
        }
    }

    private fun scrim() = if (v.dark) Color(0xFF2B2E33) else Color(0xFFEDEAE4)

    /** Theme + direction; Paparazzi does not flip the layout for Hebrew by itself. */
    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val direction = if (v.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) { AppTheme(dark = v.dark, content = content) }
    }

    companion object {
        private fun il(s: String): Instant = LocalDateTime.parse(s).atZone(ISRAEL).toInstant()
        private val sukkotWeek = il("2026-09-22T10:00")
        private val monday = il("2026-10-19T10:00")

        private val phone = DeviceConfig.PIXEL_5
        private val hebrew = phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL)
        private val small = DeviceConfig.NEXUS_5 // 360 x 640 dp

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants() = listOf(
            Variant("en_light", phone, dark = false, rtl = false),
            Variant("en_dark", phone.copy(nightMode = NightMode.NIGHT), dark = true, rtl = false),
            Variant("he_light", hebrew, dark = false, rtl = true),
            Variant("he_dark", hebrew.copy(nightMode = NightMode.NIGHT), dark = true, rtl = true),
            // The small phone at font ×1.3: the longest labels ("חול המועד סוכות", the arrive-by date) must still fit.
            Variant("he_small_large", small.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, fontScale = 1.3f), dark = false, rtl = true),
            Variant("en_small_large", small.copy(fontScale = 1.3f), dark = false, rtl = false),
        )
    }
}
