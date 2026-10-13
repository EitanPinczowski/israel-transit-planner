package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
import il.transit.planner.ui.screens.ResultsPanel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Phase 10 D11: the selected card highlighted further down the list (as after a tap on its route),
 * with the bottom action row: Remind me, then Start trip (filled), each ≥ 48 dp. English and
 * Hebrew (the row mirrors), light and dark, and the narrowest phone at a large font.
 */
@RunWith(Parameterized::class)
class PolishShotsTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    /** The third option chosen: its card tinted, the others plain; the action row under the list. */
    @Test fun selectedWithActions() = results("polish_selected_actions", tripState().copy(selected = 2))

    /** A ride is being tracked: Start trip is gone, Remind me takes the whole row. */
    @Test fun ridingOneAction() = results("polish_riding_one_action", tripState().copy(selected = 0, riding = true))

    private fun results(name: String, state: UiState) = paparazzi.snapshot(name) {
        val direction = if (v.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            AppTheme(dark = v.dark) {
                Box(Modifier.fillMaxSize().background(if (v.dark) Color(0xFF2B2E33) else Color(0xFFEDEAE4)), contentAlignment = Alignment.BottomCenter) {
                    val actions = ScreenActions(OfflineState(), {}, {}, {}, {})
                    ResultsPanel(state, NoActions, actions, false, {}, {}, Modifier, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                }
            }
        }
    }

    private fun tripState() = UiState(
        from = PlaceRef.Point("אוניברסיטת בן גוריון", LatLon(31.262, 34.801)),
        to = PlaceRef.Point("ת. רכבת תל אביב - סבידור", LatLon(32.08, 34.79)),
        timeMode = TimeMode.DEPART_AT,
        time = bgu.itineraries.first().start,
        results = TripResult(bgu.itineraries, null),
        resultsAt = bgu.itineraries.first().start,
    )

    companion object {
        private val bgu = MotisJson.decodeFromString(
            PlanResponse.serializer(),
            PolishShotsTest::class.java.getResource("/fixtures/plan_bgu_telaviv.json")!!.readText(),
        )

        private val phone = DeviceConfig.PIXEL_5

        /** 320 dp wide (P3, "Display size: Large"), the narrowest profile the layout audit runs. */
        private val narrow = DeviceConfig.NEXUS_5.copy(screenWidth = 960, locale = "iw", layoutDirection = LayoutDirection.RTL, fontScale = 1.3f)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants() = listOf(
            Variant("en_light", phone, dark = false, rtl = false),
            Variant("en_dark", phone.copy(nightMode = NightMode.NIGHT), dark = true, rtl = false),
            Variant("he_light", phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL), dark = false, rtl = true),
            Variant("he_dark", phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, nightMode = NightMode.NIGHT), dark = true, rtl = true),
            Variant("narrow_he_bigtext", narrow, dark = false, rtl = true),
        )
    }
}
