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
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.fare.FareProfile
import il.transit.core.features.ISRAEL
import il.transit.core.history.TripRecord
import il.transit.core.user.PlaceRoutine
import il.transit.core.user.SavedPlace
import il.transit.planner.ui.screens.BestLeaveLine
import il.transit.planner.ui.screens.HistoryContent
import il.transit.planner.ui.screens.ItineraryCard
import il.transit.planner.ui.screens.UsualLine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime

/**
 * History insights (Phase 9 C3) in both languages and both themes plus Hebrew large text:
 * the "+8" on the selected option, the best leave time and the monthly pass advice ("₪37" in RTL).
 */
@RunWith(Parameterized::class)
class InsightsShotsTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    /** The selected option with "Usually … · this one +N" under its summary. */
    @Test fun usualTrip() = paparazzi.snapshot("insights_usual") {
        Frame {
            Box(Modifier.fillMaxSize().background(if (v.dark) Color(0xFF2B2E33) else Color(0xFFEDEAE4)).padding(16.dp), contentAlignment = Alignment.TopCenter) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                    val itin = bgu.first()
                    // Relative to the wall clock: the line only reads the last 90 days, and shows no dates.
                    val now = Instant.now()
                    val fromCell = TripRecord.cellOf(itin.legs.first().from.latLon)
                    val toCell = TripRecord.cellOf(itin.legs.last().to.latLon)
                    val usual = (itin.duration / 60) - 8
                    val records = (1..4).map { d ->
                        TripRecord(now.minusSeconds(d * 86_400L).epochSecond, "", "", "TRIP", usual - 10, 10, 0, totalMin = usual + d % 2, fromCell = fromCell, toCell = toCell)
                    }
                    ItineraryCard(itin, selected = true, fareProfile = FareProfile.REGULAR, searchedAt = itin.start, extra = { UsualLine(UiState(history = records), itin, now) }) {}
                }
            }
        }
    }

    /** The routine's best leave time over the history section with the monthly pass advice. */
    @Test fun historyInsights() = paparazzi.snapshot("insights_history") {
        Frame {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(24.dp), contentAlignment = Alignment.Center) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BestLeaveLine(september, uni, uni.routine!!.days)
                        HistoryContent(UiState(history = september), NoActions, now = october)
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
        private val bgu = MotisJson.decodeFromString(
            PlanResponse.serializer(),
            InsightsShotsTest::class.java.getResource("/fixtures/plan_bgu_telaviv.json")!!.readText(),
        ).itineraries

        private val october = Instant.parse("2026-10-06T09:00:00Z")
        private val uni = SavedPlace("אוניברסיטת בן גוריון", 31.262, 34.801, PlaceRoutine(listOf(7, 1, 2, 3, 4), 7 * 60, 10 * 60))

        /** Sunday to Thursday in September 2026: to BGU around 07:20, home around 17:30, ₪8 each. */
        private val september = (1..30).map { java.time.LocalDate.of(2026, 9, it) }
            .filter { it.dayOfWeek.value in uni.routine!!.days }
            .flatMap { d ->
                fun at(h: Int, m: Int) = ZonedDateTime.of(LocalDateTime.of(d, java.time.LocalTime.of(h, m)), ISRAEL).toEpochSecond()
                listOf(
                    TripRecord(at(17, 30), "אוניברסיטת בן גוריון", "בית", "TRIP", 18, 6, 0, totalMin = 26, fareAgorot = 800, fareBand = 0, withTrain = false),
                    TripRecord(at(7, 15 + d.dayOfMonth % 3 * 5), "בית", "אוניברסיטת בן גוריון", "TRIP", 17, 6, 0, totalMin = 24 + d.dayOfMonth % 4, fareAgorot = 800, fareBand = 0, withTrain = false, toCell = TripRecord.cellOf(uni.latLon)),
                )
            }
            .sortedByDescending { it.startedAtEpoch }

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
