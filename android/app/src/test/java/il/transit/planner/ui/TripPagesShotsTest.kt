package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import il.transit.core.api.Itinerary
import il.transit.core.api.MotisJson
import il.transit.core.api.PlanResponse
import il.transit.core.api.StopTimesResponse
import il.transit.core.geo.LatLon
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPages
import il.transit.core.plan.TripResult
import il.transit.core.present.departureRow
import il.transit.core.present.stopPlatform
import il.transit.core.present.tripDetails
import il.transit.planner.ui.screens.ResultsPanel
import il.transit.planner.ui.screens.StopPanel
import il.transit.planner.ui.screens.TripDetailsSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant

/**
 * Earlier / Later, "if I miss this" and platforms (Phase 9 C1), in English and Hebrew, light
 * and dark. Two real pages of one search (Tue 13 Oct 2026, Be'er Sheva → Tel Aviv, 08:00).
 */
@RunWith(Parameterized::class)
class TripPagesShotsTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    /** Two pages merged; the selected bus shows its platform and "if I miss this: next at…". */
    @Test fun tripPages() = results("trip_pages", tripState(selected = 1))

    /** The last option picked (nothing later loaded: "show later"), and a Later tap that failed offline. */
    @Test fun tripPagesFailed() = results(
        "trip_pages_failed",
        tripState(selected = 2).copy(results = short, pages = TripPagesUi(failed = true)),
    )

    /** A Later page loading. */
    @Test fun tripPagesLoading() = results("trip_pages_loading", tripState(selected = 0).copy(results = short, pages = TripPagesUi(loading = TripPages.Direction.LATER)))

    /** The trip sheet of bus 370: board at platform 2, stop 13907. */
    @Test fun tripDetailsPlatform() = paparazzi.snapshot("trip_details_platform") {
        Frame {
            val leg = bus370.firstTransitLeg!!
            val sheet = TripSheet(leg, tripDetails(leg, null, leg.start.minusSeconds(600)), legOnly = true)
            TripDetailsSheet(sheet, onClose = {}, modifier = Modifier, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
        }
    }

    /** The stop sheet's header: "Platform 2 · stop 13907". */
    @Test fun stopPlatform() = paparazzi.snapshot("stop_platform") {
        Frame {
            val sheet = StopSheet(
                board.place!!.stopId!!, board.place!!.name, false, board.stopTimes.map(::departureRow).take(4), false,
                platform = stopPlatform(board.place!!),
            )
            StopPanel(sheet, emptyList(), NoActions, false, {}, Modifier, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
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

    private fun tripState(selected: Int) = UiState(
        from = PlaceRef.Point("ת. מרכזית באר שבע", LatLon(31.2437, 34.7976)),
        to = PlaceRef.Point("ת. מרכזית תל אביב", LatLon(32.0560, 34.7794)),
        timeMode = TimeMode.DEPART_AT,
        time = search,
        results = merged,
        resultsAt = search,
        selected = selected,
    )

    companion object {
        private fun fixture(name: String) = TripPagesShotsTest::class.java.getResource("/fixtures/$name.json")!!.readText()
        private fun plan(name: String) = MotisJson.decodeFromString(PlanResponse.serializer(), fixture(name))

        private val search = Instant.parse("2026-10-13T05:00:00Z")
        private val first = plan("plan_pages_first")
        private val merged: TripResult = TripPages.merge(
            TripResult(first.itineraries.sortedBy { it.end }, null, first.previousPageCursor, first.nextPageCursor, search.epochSecond),
            plan("plan_pages_later"), TripPages.Direction.LATER, arriveBy = false,
        )
        /** The first three options only, so the buttons under them are in the picture. */
        private val short = TripResult(
            first.itineraries.sortedBy { it.end }.take(3), null, first.previousPageCursor, first.nextPageCursor, search.epochSecond,
        )
        private val bus370: Itinerary = first.itineraries[1]
        private val board = MotisJson.decodeFromString(StopTimesResponse.serializer(), fixture("stoptimes_weekday_beersheva_central"))

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
