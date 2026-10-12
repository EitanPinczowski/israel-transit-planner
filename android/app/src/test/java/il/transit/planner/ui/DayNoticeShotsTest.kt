package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.geo.LatLon
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripResult
import il.transit.planner.ui.screens.HolidayTableOutOfDate
import il.transit.planner.ui.screens.ResultsPanel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant

/**
 * Holidays + dates (Phase 10 D3): the banner above the options and the 0-options notices, in
 * English and Hebrew, light and dark, Hebrew on the smallest phone (360 × 640 dp) and both at
 * 1.5× text there. The
 * options are a real Be'er Sheva → Tel Aviv answer; only the searched day changes.
 */
@RunWith(Parameterized::class)
class DayNoticeShotsTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    /** A weekday holiday eve with options: Wed 21 Apr 2027 (Erev Pesach), 14:00. */
    @Test fun holidayEve() = results("day_eve", withOptions(t("2027-04-21T11:00:00Z")))

    /** Erev Yom Kippur afternoon (Sun 20 Sep 2026, 13:00) with options. */
    @Test fun yomKippurEve() = results("day_yom_kippur_eve", withOptions(t("2026-09-20T10:00:00Z")))

    /** Chol HaMoed: reduced service, check the times. */
    @Test fun reduced() = results("day_reduced", withOptions(t("2026-09-29T06:00:00Z")))

    /** Pesach, 0 options: the banner, "No service that day" and "Plan for Thu 20:00". */
    @Test fun holidayNone() = results("day_holiday_none", none(t("2027-04-22T07:00:00Z")))

    /** Friday evening, 0 options: "Shabbat: no service that evening", "Plan for Sat 20:00". */
    @Test fun shabbatNone() = results("day_shabbat_none", none(t("2026-10-16T15:00:00Z")))

    /** A month ahead, 0 options: "The timetable may not reach Thu 12 Nov yet", "Plan for Thu 15 Oct 08:00". */
    @Test fun beyondHorizon() = results("day_beyond", none(t("2026-11-12T06:00:00Z"), now = t("2026-10-12T07:00:00Z")))

    /** Settings, from 2031: the holiday table needs an app update. */
    @Test fun tableOutOfDate() = paparazzi.snapshot("day_table_old") {
        Frame {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.padding(24.dp)) {
                Box(Modifier.padding(24.dp)) { HolidayTableOutOfDate() }
            }
        }
    }

    private fun results(name: String, state: UiState) = paparazzi.snapshot(name) {
        Frame {
            val actions = ScreenActions(OfflineState(), {}, {}, {}, {})
            ResultsPanel(state, NoActions, actions, false, {}, {}, Modifier, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        }
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val direction = if (v.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            AppTheme(dark = v.dark) {
                Box(Modifier.fillMaxSize().background(if (v.dark) Color(0xFF2B2E33) else Color(0xFFEDEAE4)), contentAlignment = Alignment.BottomCenter) {
                    content()
                }
            }
        }
    }

    private fun base(at: Instant, now: Instant) = UiState(
        from = PlaceRef.Point("ת. מרכזית באר שבע", LatLon(31.2437, 34.7976)),
        to = PlaceRef.Point("ת. מרכזית תל אביב", LatLon(32.0560, 34.7794)),
        timeMode = TimeMode.DEPART_AT,
        time = at,
        resultsAt = now,
    )

    private fun withOptions(at: Instant) = base(at, at).copy(results = short)

    private fun none(at: Instant, now: Instant = at.minusSeconds(86_400)) = base(at, now).copy(error = UiError.NO_RESULTS)

    companion object {
        private fun t(iso: String) = Instant.parse(iso)
        private fun fixture(name: String) = DayNoticeShotsTest::class.java.getResource("/fixtures/$name.json")!!.readText()
        private val first = MotisJson.decodeFromString(PlanResponse.serializer(), fixture("plan_pages_first"))

        /** Three options, so the banner and the cards under it are both in the picture. */
        private val short = TripResult(first.itineraries.sortedBy { it.end }.take(3), null)

        private val phone = DeviceConfig.PIXEL_5
        private val small = DeviceConfig.NEXUS_5 // 360 x 640 dp

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants() = listOf(
            Variant("en_light", phone, dark = false, rtl = false),
            Variant("en_dark", phone.copy(nightMode = NightMode.NIGHT), dark = true, rtl = false),
            Variant("he_dark", phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, nightMode = NightMode.NIGHT), dark = true, rtl = true),
            Variant("he_small", small.copy(locale = "iw", layoutDirection = LayoutDirection.RTL), dark = false, rtl = true),
            // Large text on the small phone: the longest lines and the button must still fit.
            Variant("he_large", small.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, fontScale = 1.5f), dark = false, rtl = true),
            Variant("en_large", small.copy(fontScale = 1.5f), dark = false, rtl = false),
        )
    }
}
