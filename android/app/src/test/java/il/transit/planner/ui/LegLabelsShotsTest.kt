package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import il.transit.core.api.Leg
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.fare.FareProfile
import il.transit.core.present.fewerStops
import il.transit.core.present.tripDetails
import il.transit.planner.ui.screens.ItineraryCard
import il.transit.planner.ui.screens.TripDetailsSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Leg labels (Phase 11 E3), English and Hebrew, light and dark, and the smallest phone in Hebrew
 * at a large font. Real answers: BGU → Tel Aviv (train 406 makes 4 fewer stops than 28, 14 min
 * later) and Rahat → Be'er Sheva (bus 455 NOT_ACCESSIBLE, then bus 8 ACCESSIBLE).
 */
@RunWith(Parameterized::class)
class LegLabelsShotsTest(private val v: Variant) {
    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    /** The selected card: "Train 406" chip, "Fewer stops", the ride line "Train 406 to Tel Aviv…"; under it the 28. */
    @Test fun trainCards() = paparazzi.snapshot("leg_labels_train_cards") {
        Frame {
            val fewer = fewerStops(bgu.itineraries).map { it.option }.toSet()
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3).forEach { i ->
                    val itin = bgu.itineraries[i]
                    ItineraryCard(itin, selected = i == 1, fareProfile = FareProfile.REGULAR, searchedAt = itin.start, fewerStops = i in fewer, onLeg = {}) {}
                }
            }
        }
    }

    /** The trip sheet of train 406: "Train 406 to Tel Aviv · 4 stops on the way". */
    @Test fun trainSheet() = sheet("leg_labels_train_sheet", bgu.itineraries[1].legs[1], accessible = true)

    /** Accessible routes on: bus 8 says "Accessible bus" with the wheelchair badge. */
    @Test fun busAccessible() = sheet("leg_labels_bus_accessible", rahat.itineraries[1].legs[3], accessible = true)

    /** Accessible routes on: bus 455, the amber "may not be wheelchair accessible" line. */
    @Test fun busNotAccessible() = sheet("leg_labels_bus_not_accessible", rahat.itineraries[1].legs[1], accessible = true)

    private fun sheet(name: String, leg: Leg, accessible: Boolean) = paparazzi.snapshot(name) {
        Frame {
            val sheet = TripSheet(leg, tripDetails(leg, null, leg.start.minusSeconds(600)), legOnly = true)
            TripDetailsSheet(sheet, onClose = {}, modifier = Modifier, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), accessibleRoutes = accessible)
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

    companion object {
        private fun plan(name: String) =
            MotisJson.decodeFromString(PlanResponse.serializer(), LegLabelsShotsTest::class.java.getResource("/fixtures/$name.json")!!.readText())

        private val bgu = plan("plan_bgu_telaviv")
        private val rahat = plan("plan_access_rahat")

        private val phone = DeviceConfig.PIXEL_5
        private val small = DeviceConfig.NEXUS_5 // 360 x 640 dp

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants() = listOf(
            Variant("en_light", phone, dark = false, rtl = false),
            Variant("en_dark", phone.copy(nightMode = NightMode.NIGHT), dark = true, rtl = false),
            Variant("he_light", phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL), dark = false, rtl = true),
            Variant("he_dark", phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, nightMode = NightMode.NIGHT), dark = true, rtl = true),
            Variant("he_large", small.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, fontScale = 1.5f), dark = false, rtl = true),
        )
    }
}
