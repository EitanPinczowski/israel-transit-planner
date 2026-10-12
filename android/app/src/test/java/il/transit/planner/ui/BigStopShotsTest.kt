package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
import il.transit.core.api.Itinerary
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.geo.LatLon
import il.transit.core.plan.BigStopChange
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripResult
import il.transit.core.present.transfers
import il.transit.planner.ui.screens.ResultsPanel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Big-stop changes (Phase 10 D5): the "Safer change" row on the selected card — the offer
 * (stay on, get off earlier) and, after "Use this change", the way back. English and Hebrew,
 * light and dark, Hebrew on the smallest phone and both at 1.5× text there. The option is a
 * real Be'er Sheva → Tel Aviv answer; the numbers in the row are set here.
 */
@RunWith(Parameterized::class)
class BigStopShotsTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    /** "Stay on line 370 one more stop to …, take line 4 there. Same arrival; 9 min to change instead of 2." */
    @Test fun stayOn() = results("big_stop_stay", state(change(extraStops = 1), applied = false))

    /** "Get off line 370 2 stops earlier, at …". */
    @Test fun earlier() = results("big_stop_earlier", state(change(extraStops = -2), applied = false))

    /** After "Use this change": "Changing at …: 9 min instead of 2." + "Back to the original change". */
    @Test fun applied() = results("big_stop_applied", state(change(extraStops = 1), applied = true))

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

    private fun change(extraStops: Int): BigStopChange {
        val t = transfers(option).first()
        return BigStopChange(
            original = option, changed = option.copy(), transfer = t,
            stop = "ת.מרכזית תל אביב קומה 6/הורדה", fromLeg = t.fromLeg, toLeg = t.toLeg,
            extraStops = extraStops, slackMin = 9, originalSlackMin = 2,
        )
    }

    private fun state(c: BigStopChange, applied: Boolean) = UiState(
        from = PlaceRef.Point("ת. מרכזית באר שבע", LatLon(31.2437, 34.7976)),
        to = PlaceRef.Point("ת. רכבת ההגנה", LatLon(32.0540, 34.7848)),
        timeMode = TimeMode.DEPART_AT,
        time = option.start,
        resultsAt = option.start,
        results = TripResult(listOf(if (applied) c.changed else c.original), null),
        bigStop = BigStopUi(c),
    )

    companion object {
        private fun fixture(name: String) = BigStopShotsTest::class.java.getResource("/fixtures/$name.json")!!.readText()

        /** 370 to Tel Aviv, then a city bus: one change. */
        private val option: Itinerary = MotisJson.decodeFromString(PlanResponse.serializer(), fixture("plan_pages_first"))
            .itineraries.first { transfers(it).size == 1 }

        private val phone = DeviceConfig.PIXEL_5
        private val small = DeviceConfig.NEXUS_5 // 360 x 640 dp

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants() = listOf(
            Variant("en_light", phone, dark = false, rtl = false),
            Variant("en_dark", phone.copy(nightMode = NightMode.NIGHT), dark = true, rtl = false),
            Variant("he_dark", phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, nightMode = NightMode.NIGHT), dark = true, rtl = true),
            Variant("he_small", small.copy(locale = "iw", layoutDirection = LayoutDirection.RTL), dark = false, rtl = true),
            Variant("he_large", small.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, fontScale = 1.5f), dark = false, rtl = true),
            Variant("en_large", small.copy(fontScale = 1.5f), dark = false, rtl = false),
        )
    }
}
