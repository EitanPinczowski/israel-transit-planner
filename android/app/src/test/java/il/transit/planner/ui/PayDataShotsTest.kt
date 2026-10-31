package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import il.transit.core.api.UsageSummary
import il.transit.core.present.tripDetails
import il.transit.core.user.UserSettings
import il.transit.planner.ui.screens.DataCardContent
import il.transit.planner.ui.screens.NO_USAGE
import il.transit.planner.ui.screens.PayChooserContent
import il.transit.planner.ui.screens.PaySettingsSection
import il.transit.planner.ui.screens.TripDetailsSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant
import androidx.compose.ui.unit.LayoutDirection as ComposeDirection

/**
 * Pay with… and the data and battery card (Phase 11 E4): English and Hebrew, light and dark, and the
 * smallest phone in Hebrew at font 1.3. The trip sheet is a real Rahat → Be'er Sheva bus.
 */
@RunWith(Parameterized::class)
class PayDataShotsTest(private val v: Variant) {
    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    /** The first day: nothing sent yet, no night refresh, no map cache file. */
    @Test fun cardEmpty() = dialog("data_card_empty") { DataCardContent(NO_USAGE, null, lowData = false) {} }

    /** A busy day: "14 requests to Transitous (6 answered from the cache) · weather 1 · … · ride GPS 12 min", low data on. */
    @Test fun cardBusy() = dialog("data_card_busy") { DataCardContent(busy, 38.4, lowData = true) {} }

    /** Settings → Paying for rides, Moovit chosen. */
    @Test fun paySettings() = dialog("pay_settings") { PaySettingsSection(UserSettings(paymentApp = "com.tranzmate")) {} }

    /** The chooser with none of the apps installed: each says "Get it on Google Play". */
    @Test fun chooserNone() = dialog("pay_chooser_none") { PayChooserContent(emptySet()) {} }

    /** One installed (Moovit): it comes first. */
    @Test fun chooserOne() = dialog("pay_chooser_one") { PayChooserContent(setOf("com.tranzmate")) {} }

    /** All installed: the owner's order. */
    @Test fun chooserAll() = dialog("pay_chooser_all") { PayChooserContent(setOf("com.pcentra.ravkavonlinemobile", "co.hopon.client", "com.tranzmate")) {} }

    /** The trip sheet's button with no app chosen ("Pay with…") and the line under it. */
    @Test fun sheetButton() = sheet("pay_sheet_button", null)

    /** … and with Moovit chosen ("Pay with Moovit"). */
    @Test fun sheetButtonChosen() = sheet("pay_sheet_button_moovit", "com.tranzmate")

    private fun sheet(name: String, app: String?) = paparazzi.snapshot(name) {
        Frame(Alignment.BottomCenter) {
            val leg = rahat.itineraries[1].legs[3]
            val sheet = TripSheet(leg, tripDetails(leg, null, leg.start.minusSeconds(600)), legOnly = true)
            TripDetailsSheet(sheet, onClose = {}, modifier = Modifier, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), paymentApp = app)
        }
    }

    private fun dialog(name: String, content: @Composable () -> Unit) = paparazzi.snapshot(name) {
        Frame(Alignment.Center) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(24.dp), contentAlignment = Alignment.Center) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
                }
            }
        }
    }

    @Composable
    private fun Frame(align: Alignment, content: @Composable () -> Unit) {
        val direction = if (v.rtl) ComposeDirection.Rtl else ComposeDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            AppTheme(dark = v.dark) {
                Box(Modifier.fillMaxSize().background(if (v.dark) Color(0xFF2B2E33) else Color(0xFFEDEAE4)), contentAlignment = align) {
                    content()
                }
            }
        }
    }

    companion object {
        private val rahat =
            MotisJson.decodeFromString(PlanResponse.serializer(), PayDataShotsTest::class.java.getResource("/fixtures/plan_access_rahat.json")!!.readText())

        /** 02:14 Israel time on 27 Oct 2026 (UTC+2). */
        private val busy = UsageSummary(
            transitous = 14,
            transitousCache = 6,
            transitousRetries = 1,
            weather = 1,
            address = 0,
            nightAt = Instant.parse("2026-10-27T00:14:00Z"),
            nightRequests = 4,
            rideGpsMin = 12,
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
            Variant("he_small_13", small.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, fontScale = 1.3f), dark = false, rtl = true),
        )
    }
}
