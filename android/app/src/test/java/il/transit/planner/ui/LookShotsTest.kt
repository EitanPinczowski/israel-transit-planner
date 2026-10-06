package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
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
import il.transit.core.fare.FareProfile
import il.transit.core.user.Appearance
import il.transit.core.user.UserSettings
import il.transit.planner.ui.screens.AppearanceSection
import il.transit.planner.ui.screens.ItineraryCard
import il.transit.planner.ui.screens.TOUR_PAGES
import il.transit.planner.ui.screens.WelcomeTourContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Phase 9 C6, in both languages and both themes plus large text: Settings → Appearance, every
 * page of the welcome tour, and recorded trips whose buses share one operator colour, now
 * each in its own colour (`LegPalette`). The map line and stop dots use the same colours
 * (core `LegPaletteTest`); Paparazzi draws no MapLibre map.
 */
@RunWith(Parameterized::class)
class LookShotsTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    @Test fun appearanceSystem() = paparazzi.snapshot("appearance_system") { Card { AppearanceSection(UserSettings(), {}) {} } }

    @Test fun appearancePicked() = paparazzi.snapshot("appearance_picked") {
        Card { AppearanceSection(UserSettings(language = "he", theme = Appearance.DARK), {}) {} }
    }

    @Test fun tourPages() {
        TOUR_PAGES.indices.forEach { i ->
            paparazzi.snapshot("tour_page_${i + 1}") { Frame { WelcomeTourContent(rememberPagerState(initialPage = i) { TOUR_PAGES.size }) {} } }
        }
    }

    /** Two buses (Be'er Sheva → Tel Aviv), and a train then three buses. */
    @Test fun legColours() = paparazzi.snapshot("leg_colours") {
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ItineraryCard(twoBuses, selected = true, fareProfile = FareProfile.REGULAR, searchedAt = twoBuses.start, onLeg = {}) {}
                ItineraryCard(trainThreeBuses, selected = false, fareProfile = FareProfile.REGULAR, searchedAt = trainThreeBuses.start, onLeg = {}) {}
            }
        }
    }

    /** One state in a settings-like card, so large text has the whole screen. */
    @Composable
    private fun Card(content: @Composable () -> Unit) = Frame {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(24.dp)) { content() }
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
        private fun plan(name: String) = MotisJson.decodeFromString(
            PlanResponse.serializer(),
            LookShotsTest::class.java.getResource("/fixtures/$name.json")!!.readText(),
        )

        private val twoBuses = plan("plan_bgu_telaviv").itineraries[2]
        private val trainThreeBuses = plan("plan_cardropoff_post_probe").itineraries[1]

        private val phone = DeviceConfig.PIXEL_5
        private val hebrew = phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants() = listOf(
            Variant("en_light", phone, dark = false, rtl = false),
            Variant("en_dark", phone.copy(nightMode = NightMode.NIGHT), dark = true, rtl = false),
            Variant("en_bigtext", phone.copy(fontScale = 1.5f), dark = false, rtl = false),
            Variant("he_light", hebrew, dark = false, rtl = true),
            Variant("he_dark", hebrew.copy(nightMode = NightMode.NIGHT), dark = true, rtl = true),
            Variant("he_bigtext", hebrew.copy(fontScale = 1.5f), dark = false, rtl = true),
        )
    }
}
