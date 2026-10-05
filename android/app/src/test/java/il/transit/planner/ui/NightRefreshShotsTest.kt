package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import il.transit.core.user.PlaceRoutine
import il.transit.core.user.SavedPlace
import il.transit.core.user.UserSettings
import il.transit.planner.ui.screens.HomeSuggestion
import il.transit.planner.ui.screens.NightRefreshSection
import il.transit.planner.ui.screens.ResultsPanel
import il.transit.planner.ui.screens.SavedPlaceRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Night refresh and Home (Phase 9 C2), in both languages and both themes plus Hebrew large
 * text: the long Hebrew help text and the offline banner are the layout risks.
 */
@RunWith(Parameterized::class)
class NightRefreshShotsTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    /** The Trip tab offline the next morning, from a copy the night refresh planned. */
    @Test fun tripOfflineNight() = paparazzi.snapshot("trip_offline_night") {
        Frame {
            Box(Modifier.fillMaxSize().background(if (v.dark) Color(0xFF2B2E33) else Color(0xFFEDEAE4)), contentAlignment = Alignment.BottomCenter) {
                val state = UiState(
                    from = PlaceRef.Point("בית", LatLon(31.262, 34.801)),
                    to = PlaceRef.Point("ת. רכבת תל אביב - סבידור", LatLon(32.08, 34.79)),
                    timeMode = TimeMode.NOW,
                    // The day labels compare against this; left null they read the wall
                    // clock, and every option gets a weekday once CI's date moves past the
                    // fixture's.
                    time = planned,
                    results = TripResult(bgu, null),
                    resultsAt = planned,
                    offlineSince = planned,
                    offlineNight = true,
                )
                val actions = ScreenActions(OfflineState(), {}, {}, {}, {})
                ResultsPanel(state, NoActions, actions, false, {}, {}, Modifier, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            }
        }
    }

    /** The switch with no Home yet, the one-time "בית as Home?" offer, then Home set and a place to make Home. */
    @Test fun nightRefreshAndHome() = paparazzi.snapshot("night_refresh_home") {
        Frame {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(24.dp), contentAlignment = Alignment.Center) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    val bayit = SavedPlace("בית", 31.279, 34.82)
                    val uni = SavedPlace("אוניברסיטת בן גוריון", 31.262, 34.801, PlaceRoutine(listOf(7, 1, 2, 3, 4), 7 * 60, 10 * 60))
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        NightRefreshSection(UserSettings(), listOf(bayit, uni)) {}
                        HomeSuggestion(UserSettings(), listOf(bayit, uni)) {}
                        SavedPlaceRow(bayit, onRoutine = {}, isHome = true, onSetHome = {}) {}
                        SavedPlaceRow(uni, onRoutine = {}, isHome = false, onSetHome = {}) {}
                    }
                }
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
        private val bgu = MotisJson.decodeFromString(
            PlanResponse.serializer(),
            NightRefreshShotsTest::class.java.getResource("/fixtures/plan_bgu_telaviv.json")!!.readText(),
        ).itineraries

        /** 02:14 the night before the first option. */
        private val planned = bgu.first().start.minusSeconds(5 * 3600 + 46 * 60)

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
