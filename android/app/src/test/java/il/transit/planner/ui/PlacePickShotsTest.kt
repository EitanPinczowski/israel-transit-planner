package il.transit.planner.ui

import androidx.compose.foundation.background
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
import il.transit.core.geo.LatLon
import il.transit.planner.ui.screens.MapPinPanel
import il.transit.planner.ui.screens.SaveHereRow
import il.transit.planner.ui.screens.SavePlaceContent
import il.transit.planner.ui.screens.SuggestionList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Search fix (S1, S2, S3, pick 20), in both languages and both themes: the long-press sheet
 * named and offline, the save dialog's body, "Save my current location", and suggestions with
 * Home and a recent pick first and Photon's answer (and its credit) last.
 */
@RunWith(Parameterized::class)
class PlacePickShotsTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    private val tabenkin = LatLon(32.1801, 34.8712)

    /** The address reverse geocoding found. */
    @Test fun pinNamed() = paparazzi.snapshot("pin_named") { Bottom { MapPinPanel(MapPin(tabenkin, "טבנקין 15", naming = false), NoActions, Modifier, it) } }

    /** Offline, or nothing near: the coordinates. */
    @Test fun pinOffline() = paparazzi.snapshot("pin_offline") { Bottom { MapPinPanel(MapPin(tabenkin, null, naming = false), NoActions, Modifier, it) } }

    @Test fun saveDialog() = paparazzi.snapshot("save_place_dialog") {
        Card { SavePlaceContent(PlaceDraft(tabenkin, "טבנקין 15"), "טבנקין 15", {}, true, {}) }
    }

    @Test fun saveNoFix() = paparazzi.snapshot("save_place_no_fix") { Card { SavePlaceContent(PlaceDraft(null), "", {}, false, {}) } }

    @Test fun saveHereRow() = paparazzi.snapshot("save_here_row") { Card { Column { SaveHereRow(NoActions) } } }

    @Test fun suggestionsLocalFirst() = paparazzi.snapshot("suggestions_local_first") {
        val state = UiState(
            editing = Field.TO,
            query = "טבנקין",
            suggestions = listOf(
                Suggestion("בית", null, tabenkin, saved = true, isStop = false, home = true),
                Suggestion("טבנקין פינת אחוזה", null, LatLon(32.19, 34.87), saved = false, isStop = false, recent = true),
                Suggestion("סירקין", "רעננה", LatLon(32.18, 34.87), saved = false, isStop = false),
                Suggestion("טבנקין 15", "רעננה", tabenkin, saved = false, isStop = false, backup = true),
            ),
            places = PlacesUi(backupShown = true),
        )
        Frame {
            Box(Modifier.fillMaxSize().background(mapStandIn()).padding(12.dp)) { SuggestionList(state, NoActions, Modifier) }
        }
    }

    /** No geocoder knows the text: said plainly, with the way out (the map). */
    @Test fun suggestionsNotFound() = paparazzi.snapshot("suggestions_not_found") {
        val state = UiState(
            editing = Field.TO,
            query = "שדרות הנשיא 100 חיפה",
            suggestions = listOf(Suggestion("שדרות המגינים 100", "חיפה", LatLon(32.81, 34.99), saved = false, isStop = false, closest = true)),
            places = PlacesUi(notFound = true),
        )
        Frame {
            Box(Modifier.fillMaxSize().background(mapStandIn()).padding(12.dp)) { SuggestionList(state, NoActions, Modifier) }
        }
    }

    /** As the bottom sheet of a phone, over a flat stand-in for the map. */
    @Composable
    private fun Bottom(content: @Composable (androidx.compose.ui.graphics.Shape) -> Unit) = Frame {
        Box(Modifier.fillMaxSize().background(mapStandIn()), contentAlignment = Alignment.BottomCenter) {
            content(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        }
    }

    /** Roughly what AlertDialog draws around its text. */
    @Composable
    private fun Card(content: @Composable () -> Unit) = Frame {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(Modifier.padding(24.dp)) { content() }
            }
        }
    }

    private fun mapStandIn() = if (v.dark) Color(0xFF2B2E33) else Color(0xFFEDEAE4)

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
        )
    }
}
