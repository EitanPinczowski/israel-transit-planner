package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import il.transit.core.fare.FareProfile
import il.transit.core.features.ISRAEL
import il.transit.core.history.Report
import il.transit.core.history.TripRecord
import il.transit.core.user.UserSettings
import il.transit.planner.ui.screens.FareSection
import il.transit.planner.ui.screens.ReportMonthList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.Locale

/** Phase 10 D8: Settings → Fare with a profile's note, and the monthly report's month list. */
@RunWith(Parameterized::class)
class FareReportShotsTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    /** All seven chips wrapping, Student picked: "33% off each ride; the monthly pass is full price." */
    @Test fun fareSettings() = paparazzi.snapshot("fare_settings") {
        Dialog { FareSection(UserSettings(fareProfile = FareProfile.STUDENT)) {} }
    }

    /** History → Monthly report: the finished months, newest first, with their trip counts. */
    @Test fun reportMonths() = paparazzi.snapshot("report_months") {
        Frame {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(24.dp), contentAlignment = Alignment.Center) {
                Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 3.dp, modifier = Modifier.width(240.dp)) {
                    val locale = if (v.rtl) Locale.forLanguageTag("he") else Locale.ENGLISH
                    ReportMonthList(history, Report.finishedMonths(history, now), locale) { _, _ -> }
                }
            }
        }
    }

    @Composable
    private fun Dialog(content: @Composable () -> Unit) = Frame {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
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
        private val now = Instant.parse("2026-10-06T09:00:00Z")

        /** 1 trip in July, 2 in August, 23 in September, and October's (unfinished) left out. */
        private val history = listOf(
            "2026-07-14T08:00" to 1, "2026-08-03T08:00" to 2, "2026-09-01T08:00" to 23, "2026-10-01T08:00" to 4,
        ).flatMap { (start, n) ->
            val t = ZonedDateTime.of(LocalDateTime.parse(start), ISRAEL)
            (0 until n).map { d -> TripRecord(t.plusDays(d.toLong()).toEpochSecond(), "בית", "עבודה", "TRIP", 25, 5, 0, totalMin = 30, fareAgorot = 800) }
        }

        private val phone = DeviceConfig.PIXEL_5
        private val hebrew = phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants() = listOf(
            Variant("en_light", phone, dark = false, rtl = false),
            Variant("en_dark", phone.copy(nightMode = NightMode.NIGHT), dark = true, rtl = false),
            Variant("he_light", hebrew, dark = false, rtl = true),
            Variant("he_dark", hebrew.copy(nightMode = NightMode.NIGHT), dark = true, rtl = true),
            Variant("he_bigtext", hebrew.copy(fontScale = 1.5f), dark = false, rtl = true),
        )
    }
}
