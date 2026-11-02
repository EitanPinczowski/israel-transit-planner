package il.transit.planner.labs

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.LayoutDirection
import com.android.resources.NightMode
import il.transit.core.card.CardFile
import il.transit.core.card.CardReport
import il.transit.core.card.FileRead
import il.transit.core.card.StatusWord
import il.transit.planner.ui.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The Rav-Kav lab (Phase 11 E6, debug builds only): waiting for a card, a read, and "Can't read
 * this card", in English (light) and Hebrew (dark). The bytes are made up, not from a real card.
 */
@RunWith(Parameterized::class)
class RavKavLabShotsTest(private val v: Variant) {
    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    @Test fun waiting() = shot("labs_ravkav_waiting", LabUi.Waiting)

    @Test fun answered() = shot("labs_ravkav_answered", LabUi.Done(answered))

    @Test fun cantRead() = shot("labs_ravkav_cant_read", LabUi.Done(CardReport.CantRead))

    private fun shot(name: String, ui: LabUi) = paparazzi.snapshot(name) { Frame { RavKavLabContent(ui, onClose = {}, onRetry = {}) } }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val direction = if (v.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) { AppTheme(dark = v.dark) { Surface { content() } } }
    }

    companion object {
        private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
        private val record = bytes(0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF, 0x10, 0x20, 0x30, 0x40, 0x50)
        private val answered = CardReport.Answered(
            selectStatus = StatusWord(0x9000),
            cla = 0x00,
            files = listOf(
                FileRead(CardFile.CONTRACT_LIST, StatusWord(0x6A83), listOf(record)),
                FileRead(CardFile.CONTRACTS, StatusWord(0x6A83), listOf(record, record)),
                FileRead(CardFile.COUNTERS, StatusWord(0x6982), emptyList()),
                FileRead(CardFile.EVENT_LOG, StatusWord(0x6A83), emptyList()),
                FileRead(CardFile.SPECIAL_EVENTS, StatusWord(0x6A82), emptyList()),
            ),
            millis = 412,
        )

        private val phone = DeviceConfig.PIXEL_5

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants() = listOf(
            Variant("en_light", phone, dark = false, rtl = false),
            Variant("he_dark", phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, nightMode = NightMode.NIGHT), dark = true, rtl = true),
        )
    }
}
