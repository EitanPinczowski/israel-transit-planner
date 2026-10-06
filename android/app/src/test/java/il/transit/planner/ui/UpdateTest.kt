package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import il.transit.core.update.LatestRelease
import il.transit.core.update.UpdateFailure
import il.transit.core.update.UpdateState
import il.transit.planner.ui.screens.UpdateBanner
import il.transit.planner.ui.screens.UpdateSettingsRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The in-app update: the banner's states and the Settings row, Hebrew and English × light and dark. */
@RunWith(Parameterized::class)
class UpdateTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    private val release = LatestRelease("0.8.0", "https://github.com/x/y/releases/tag/v0.8.0", "https://github.com/x/y/a.apk", "ab".repeat(32))

    /** Banner: available, downloading 40 %, needs permission, a failure, the signature clash. */
    @Test fun banner() = shot("update_banner", Color(0xFFEDEAE4), Color(0xFF2B2E33)) {
        UpdateBanner(release, UpdateState.Available(release), NoActions)
        UpdateBanner(release, UpdateState.Downloading(release, 40), NoActions)
        UpdateBanner(release, UpdateState.NeedsPermission(release), NoActions)
        UpdateBanner(release, UpdateState.Failed(release, UpdateFailure.NETWORK), NoActions)
        UpdateBanner(release, UpdateState.Failed(release, UpdateFailure.DIFFERENT_BUILD), NoActions)
    }

    /** Settings → Check for updates: before, latest, available, couldn't check. */
    @Test fun settingsRow() = shot("update_settings", null, null) {
        UpdateSettingsRow(UpdateState.Idle, NoActions, version = "0.7.0")
        UpdateSettingsRow(UpdateState.UpToDate("0.7.0"), NoActions, version = "0.7.0")
        UpdateSettingsRow(UpdateState.Available(release), NoActions, version = "0.7.0")
        UpdateSettingsRow(UpdateState.CheckFailed, NoActions, version = "0.7.0")
    }

    /** Over a flat stand-in for the map (banner), or on the dialog's surface (Settings). */
    private fun shot(name: String, light: Color?, dark: Color?, content: @Composable () -> Unit) = paparazzi.snapshot(name) {
        val direction = if (v.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            AppTheme(dark = v.dark) {
                val bg = (if (v.dark) dark else light) ?: MaterialTheme.colorScheme.surfaceContainerHigh
                Surface(color = bg, modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.background(bg).padding(16.dp), contentAlignment = Alignment.TopCenter) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
                    }
                }
            }
        }
    }

    companion object {
        private val phone = DeviceConfig.PIXEL_5

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants() = listOf(
            Variant("en_light", phone, dark = false, rtl = false),
            Variant("en_dark", phone.copy(nightMode = NightMode.NIGHT), dark = true, rtl = false),
            Variant("he_light", phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL), dark = false, rtl = true),
            Variant("he_dark", phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, nightMode = NightMode.NIGHT), dark = true, rtl = true),
        )
    }
}
