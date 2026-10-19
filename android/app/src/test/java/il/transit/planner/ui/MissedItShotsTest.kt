package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.geo.LatLon
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripResult
import il.transit.planner.ui.screens.MissedItUi
import il.transit.planner.ui.screens.ResultsPanel
import il.transit.planner.ui.screens.SearchCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant

/**
 * "Missed it? Plan from here" (Phase 11 E2): the banner above the options ("From your location at
 * 07:46", and the arrive-by note), and "You're already there", in English and Hebrew, light and dark,
 * Hebrew on the smallest phone at 1.5× text. The options are a real Be'er Sheva → Tel Aviv answer.
 */
@RunWith(Parameterized::class)
class MissedItShotsTest(private val v: Variant) {
    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    @Test fun planned() = results("missed_it_planned", base.copy(results = short, missedIt = MissedItUi(MissedItUi.Kind.PLANNED, to, at)))

    @Test fun arriveBy() = results("missed_it_arrive_by", base.copy(results = short, missedIt = MissedItUi(MissedItUi.Kind.PLANNED, to, at, wasArriveBy = true)))

    @Test fun alreadyThere() = results("missed_it_there", base.copy(missedIt = MissedItUi(MissedItUi.Kind.ALREADY_THERE, to)))

    /** No fix: From is focused and empty under "Where are you?" (the results panel is hidden while typing). */
    @Test fun needStart() = paparazzi.snapshot("missed_it_where") {
        Frame {
            Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.TopCenter) {
                SearchCard(base.copy(editing = Field.FROM, missedIt = MissedItUi(MissedItUi.Kind.NEED_START, to)), NoActions) {}
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

    companion object {
        private fun fixture(name: String) = MissedItShotsTest::class.java.getResource("/fixtures/$name.json")!!.readText()
        private val first = MotisJson.decodeFromString(PlanResponse.serializer(), fixture("plan_pages_first"))
        private val short = TripResult(first.itineraries.sortedBy { it.end }.take(3), null)

        private val to = LatLon(32.0560, 34.7794)
        private val at: Instant = first.itineraries.minOf { it.start }.minusSeconds(300)
        private val base = UiState(
            from = PlaceRef.MyLocation,
            to = PlaceRef.Point("ת. מרכזית תל אביב", to),
            timeMode = TimeMode.NOW,
            resultsAt = at,
        )

        private val phone = DeviceConfig.PIXEL_5
        private val small = DeviceConfig.NEXUS_5 // 360 x 640 dp

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants() = listOf(
            Variant("en_light", phone, dark = false, rtl = false),
            Variant("en_dark", phone.copy(nightMode = NightMode.NIGHT), dark = true, rtl = false),
            Variant("he_light", phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL), dark = false, rtl = true),
            Variant("he_dark", phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, nightMode = NightMode.NIGHT), dark = true, rtl = true),
            Variant("he_large", small.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, fontScale = 1.5f), dark = false, rtl = true),
        )
    }
}
