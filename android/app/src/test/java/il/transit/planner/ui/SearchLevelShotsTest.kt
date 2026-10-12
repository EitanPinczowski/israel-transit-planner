package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import il.transit.core.search.MatchLevel
import il.transit.core.search.MatchNote
import il.transit.planner.ui.screens.SuggestionList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Phase 10 D2: a neighbourhood in two towns lists both towns; "Closest matches" rows say what they are missing ("Street only: no number 12",
 * "Town only"), in both languages and themes, and Hebrew large text on a small phone. Built from
 * the corpus: "הרצל 12, באר שבע" finds הרצל 126 and the town, not the house.
 */
@RunWith(Parameterized::class)
class SearchLevelShotsTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    @Test fun streetAndTownOnly() = paparazzi.snapshot("suggestions_match_levels") {
        val state = UiState(
            editing = Field.TO,
            query = "הרצל 12, באר שבע",
            suggestions = listOf(
                Suggestion("הרצל 126", "באר שבע", LatLon(31.2405, 34.7895), saved = false, isStop = false, closest = true, match = MatchNote(MatchLevel.STREET, "12")),
                Suggestion("הרצל", "באר שבע", LatLon(31.2418, 34.7893), saved = false, isStop = false, closest = true, match = MatchNote(MatchLevel.STREET, "12")),
                Suggestion("באר שבע", null, LatLon(31.2457, 34.7925), saved = false, isStop = false, closest = true, match = MatchNote(MatchLevel.TOWN)),
                Suggestion("הרצל/העצמאות", "באר שבע", LatLon(31.2401, 34.7889), saved = false, isStop = true, closest = true, match = MatchNote(MatchLevel.NONE)),
            ),
            places = PlacesUi(notFound = true),
        )
        Frame {
            Box(Modifier.fillMaxSize().background(if (v.dark) Color(0xFF2B2E33) else Color(0xFFEDEAE4)).padding(12.dp)) { SuggestionList(state, NoActions, Modifier) }
        }
    }

    /** Owner, 2026-10-12: a neighbourhood in two towns asks which first, one row per town, nearest first. */
    @Test fun townChoices() = paparazzi.snapshot("suggestions_town_choices") {
        val state = UiState(
            editing = Field.TO,
            query = "רמות",
            suggestions = listOf(
                Suggestion("רמות", if (v.rtl) "באר שבע" else "Beersheba", LatLon(31.2457, 34.7925), saved = false, isStop = false, refine = "רמות, באר שבע"),
                Suggestion("רמות", if (v.rtl) "ירושלים" else "Jerusalem", LatLon(31.7885, 35.2188), saved = false, isStop = false, refine = "רמות, ירושלים"),
            ),
        )
        Frame {
            Box(Modifier.fillMaxSize().background(if (v.dark) Color(0xFF2B2E33) else Color(0xFFEDEAE4)).padding(12.dp)) { SuggestionList(state, NoActions, Modifier) }
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
            Variant("he_light", hebrew, dark = false, rtl = true),
            Variant("he_dark", hebrew.copy(nightMode = NightMode.NIGHT), dark = true, rtl = true),
            Variant("small_he_bigtext", DeviceConfig.NEXUS_5.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, fontScale = 1.5f), dark = false, rtl = true),
        )
    }
}
