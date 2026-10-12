package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.LayoutDirection
import com.android.resources.NightMode
import il.transit.core.geo.LatLon
import il.transit.planner.ui.screens.PlaceEditor
import il.transit.planner.ui.screens.SuggestionList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Phase 10 D6: the search field with the voice mic (at the end: the start side in Hebrew), a typed
 * stop number the phone holds (its stop as the answer), and one it doesn't ("not in this area yet").
 */
@RunWith(Parameterized::class)
class WaysInShotsTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    @Test fun voiceMic() = paparazzi.snapshot("search_voice_mic") {
        Shot(UiState(editing = Field.TO, query = if (v.rtl) "קניון עזריאלי" else "Azrieli Mall"))
    }

    @Test fun stopCodeHit() = paparazzi.snapshot("search_stop_code_hit") {
        Shot(
            UiState(
                editing = Field.TO,
                query = "14381",
                suggestions = listOf(
                    Suggestion("שדרות דוד בן גוריון/דרך השלום", null, LatLon(31.258158, 34.811462), saved = false, isStop = true, stopId = "il-Israel-MOT_26873"),
                ),
            ),
        )
    }

    @Test fun stopCodeMissing() = paparazzi.snapshot("search_stop_code_missing") {
        Shot(UiState(editing = Field.TO, query = "תחנה 47899", places = PlacesUi(notFound = true)))
    }

    @Composable
    private fun Shot(state: UiState) = Frame {
        Column(Modifier.fillMaxSize().background(if (v.dark) Color(0xFF2B2E33) else Color(0xFFEDEAE4)).padding(12.dp)) {
            PlaceEditor(state, NoActions, Modifier.fillMaxWidth())
            SuggestionList(state, NoActions, Modifier.padding(top = 8.dp))
        }
    }

    /** Theme + direction; Paparazzi does not flip the layout for Hebrew by itself. */
    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val direction = if (v.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction, LocalVoiceAvailable provides true) { AppTheme(dark = v.dark, content = content) }
    }

    companion object {
        private val phone = DeviceConfig.PIXEL_5
        private val hebrew = phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants() = listOf(
            Variant("en_light", phone, dark = false, rtl = false),
            Variant("en_dark", phone.copy(nightMode = NightMode.NIGHT), dark = true, rtl = false),
            Variant("he_light", hebrew, dark = false, rtl = true),
            Variant("he_dark", hebrew.copy(nightMode = NightMode.NIGHT), dark = true, rtl = true),
            Variant("small_he_bigtext", DeviceConfig.NEXUS_5.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, fontScale = 1.5f), dark = false, rtl = true),
        )
    }
}
