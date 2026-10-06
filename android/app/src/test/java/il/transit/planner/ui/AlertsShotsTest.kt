package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.LayoutDirection
import com.android.resources.NightMode
import il.transit.core.user.SavedPlace
import il.transit.core.user.UserSettings
import il.transit.planner.ui.screens.LastTripSection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * "Last trip home alert" in Settings (Phase 9 C5), in both languages and both themes plus
 * Hebrew large text: off (the default), refused notifications, and on with no Home set.
 * The countdown and the alert themselves are system notifications, which Paparazzi cannot draw.
 */
@RunWith(Parameterized::class)
class AlertsShotsTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    @Test fun lastTripSetting() = paparazzi.snapshot("last_trip_setting") {
        Frame {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(24.dp), contentAlignment = Alignment.Center) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    val bayit = SavedPlace("בית", 31.279, 34.82)
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LastTripSection(UserSettings(homePlace = "בית"), listOf(bayit), denied = false) {}
                        HorizontalDivider()
                        LastTripSection(UserSettings(homePlace = "בית"), listOf(bayit), denied = true) {}
                        HorizontalDivider()
                        LastTripSection(UserSettings(lastTripAlert = true), listOf(bayit), denied = false) {}
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
