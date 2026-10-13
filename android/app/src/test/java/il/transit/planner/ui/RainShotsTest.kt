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
import il.transit.core.api.Itinerary
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.geo.LatLon
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripResult
import il.transit.core.plan.TripSort
import il.transit.core.user.UserSettings
import il.transit.core.weather.RainForecast
import il.transit.core.weather.RainHour
import il.transit.planner.ui.screens.RainSetting
import il.transit.planner.ui.screens.ResultsPanel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.temporal.ChronoUnit

/**
 * Rain on the walks (Phase 10 D9): the note above the Trip options, by chance (with the
 * "Least walking first" button) and by amount (already sorted by least walking: no button),
 * and the Settings switch. English and Hebrew, light and dark, the smallest phone and 1.5×
 * text. The options are a real Be'er Sheva → Tel Aviv answer; the forecast is set here.
 */
@RunWith(Parameterized::class)
class RainShotsTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    /** "Rain likely around 08:19 (70%). Least walking: 13 min (the 08:27 option)" + the button. */
    @Test fun byChance() = results("rain_chance", state(probability = 70, mm = 0.2, TripSort.FASTEST))

    /** "Rain likely around 08:19 (1.2 mm)", the list already sorted by least walking. */
    @Test fun byAmountSorted() = results("rain_amount_sorted", state(probability = 30, mm = 1.2, TripSort.LEAST_WALKING))

    /** Settings → "Rain on the walks": the switch and what it sends. */
    @Test fun setting() = paparazzi.snapshot("rain_setting") {
        Frame {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(24.dp), contentAlignment = Alignment.Center) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(Modifier.padding(24.dp)) { RainSetting(UserSettings()) {} }
                }
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

    /** The whole morning wet: every hour from the first option's on. */
    private fun state(probability: Int, mm: Double, sort: TripSort): UiState {
        val first = options.minOf { it.start }.truncatedTo(ChronoUnit.HOURS)
        val forecast = RainForecast((0L until 4L).map { RainHour(first.plusSeconds(it * 3600), probability, mm) })
        val at = options.first().start
        return UiState(
            from = PlaceRef.Point("ת. מרכזית באר שבע", LatLon(31.2437, 34.7976)),
            to = PlaceRef.Point("ת. רכבת ההגנה", LatLon(32.0540, 34.7848)),
            timeMode = TimeMode.DEPART_AT,
            time = at,
            resultsAt = at,
            results = TripResult(options, null),
            settings = UserSettings(tripSort = sort),
            rain = RainUi(at, forecast),
        )
    }

    companion object {
        private fun fixture(name: String) = RainShotsTest::class.java.getResource("/fixtures/$name.json")!!.readText()

        private val options: List<Itinerary> = MotisJson.decodeFromString(PlanResponse.serializer(), fixture("plan_pages_first")).itineraries

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
