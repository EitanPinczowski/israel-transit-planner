package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.LayoutDirection
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import il.transit.core.api.MotisJson
import il.transit.planner.ui.screens.HistoryContent
import il.transit.planner.ui.screens.LocalWalkDirectionsOpen
import il.transit.planner.ui.screens.SettingsContent
import il.transit.core.api.PlanResponse
import il.transit.core.features.BetterStartOption
import il.transit.core.features.BetterStartResult
import il.transit.core.features.DropOffKind
import il.transit.core.features.PickUpOption
import il.transit.core.features.PickUpResult
import il.transit.core.history.TripRecord
import il.transit.core.plan.LastRide
import il.transit.core.update.LatestRelease
import il.transit.core.features.DropOffOption
import il.transit.core.features.DropOffResult
import il.transit.core.features.Option
import il.transit.core.geo.LatLon
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripResult
import il.transit.core.present.DepartureRow
import il.transit.core.present.LegKind
import il.transit.core.user.SavedPlace
import il.transit.core.user.SavedTrip
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Screenshots of the main screen in every shape that has bitten us: a small phone, a big
 * one, landscape (the side-column layout), light and dark, Hebrew and English, large text.
 * `./gradlew -p android :app:recordPaparazziDebug` writes them to `src/test/snapshots/`;
 * CI uploads them as the `screenshots` artifact. The map is a flat placeholder (no MapView
 * outside a device) and there are no system bars, so insets are checked on a phone.
 */
@RunWith(Parameterized::class)
class ScreensTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean = false) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    @Test fun empty() = shot("empty", UiState(savedPlaces = listOf(SavedPlace("Home", 31.25, 34.79), SavedPlace("BGU", 31.262, 34.801))))

    @Test fun search() = shot(
        "search",
        UiState(
            editing = Field.TO,
            query = "תל",
            suggestions = listOf(
                Suggestion("תל אביב - סבידור מרכז", "Tel Aviv", LatLon(32.08, 34.79), saved = false, isStop = true),
                Suggestion("Home", null, LatLon(31.25, 34.79), saved = true, isStop = false),
                Suggestion("תל שבע", "Negev", LatLon(31.24, 34.86), saved = false, isStop = false),
            ),
        ),
    )

    @Test fun results() = shot("results", tripState())

    @Test fun offline() = shot("offline", tripState().copy(offlineSince = start))

    @Test fun error() = shot("error", tripState().copy(results = null, error = UiError.NETWORK))

    @Test fun dropOff() {
        val itin = trip.itineraries.first()
        val leg = itin.legs.first { it.isTransit }
        val option = DropOffOption(DropOffKind.STOP_ON_THE_WAY, leg.from, detourSec = 240, rideSec = 600, transit = itin)
        val state = UiState(
            mode = AppMode.DROP_OFF,
            to = PlaceRef.Point("ת. רכבת תל אביב - סבידור", LatLon(32.08, 34.79)),
            driverTo = PlaceRef.Point("Kiryat Gat", LatLon(31.61, 34.76)),
            timeMode = TimeMode.DEPART_AT,
            time = start,
            dropOff = DropOffResult(1500, listOf(Option(option, 240, itin.end, itin.transfers)), candidatesConsidered = 6),
        )
        shot("dropoff", state)
    }

    @Test fun stopSheet() {
        val rows = listOf(
            DepartureRow("5", "סורוקה", LegKind.BUS, "12:04", delayMin = 0, cancelled = false, instant = null),
            DepartureRow("R1", "Bat Yam", LegKind.LIGHT_RAIL, "12:06", delayMin = 3, cancelled = false, instant = null, color = "#FFD600"),
            DepartureRow("370", "Tel Aviv", LegKind.BUS, "12:15", delayMin = null, cancelled = false, instant = null),
            DepartureRow("", "Haifa", LegKind.TRAIN, "12:20", delayMin = null, cancelled = true, instant = null),
        )
        shot("stop", UiState(stopSheet = StopSheet("s1", "מרכז רפואי סורוקה/אוניברסיטת בן גוריון", false, rows, false)))
    }

    @Test fun betterStart() {
        val itin = trip.itineraries.first()
        val o = BetterStartOption(itin, driveSec = 540, dropOffStopName = "מרכז רפואי סורוקה", dropOffAt = LatLon(31.258, 34.80), leaveAt = start)
        val state = tripState().copy(
            mode = AppMode.BETTER_START,
            results = null,
            betterStart = BetterStartResult(trip.itineraries[1], listOf(Option(o, 540, itin.end, itin.transfers)), "CAR"),
        )
        shot("betterstart", state)
    }

    @Test fun pickUp() {
        val itin = trip.itineraries.first()
        val o = PickUpOption(itin, "מחלף לה גווארדייה", LatLon(32.05, 34.79), itin.end, driveSec = 900, driverLeavesAt = itin.end.minusSeconds(900))
        val state = tripState().copy(
            mode = AppMode.PICK_UP,
            results = null,
            pickUp = PickUpResult(trip.itineraries[1], listOf(Option(o, 1800, itin.end.plusSeconds(900), itin.transfers))),
        )
        shot("pickup", state)
    }

    /** The selected trip unfolded: walking directions (arrows never mirror) and a last-trip warning. */
    @Test fun walking() = shot(
        "walking",
        tripState().copy(lastRide = LastRide(last = trip.itineraries.last(), next = null)),
        walkOpen = true,
    )

    @Test fun banners() = shot(
        "banners",
        UiState(
            update = LatestRelease("0.3.0", "https://github.com", null),
            savedTrips = listOf(SavedTrip("עבודה", null, home)),
            savedPlaces = listOf(home, SavedPlace("BGU", 31.262, 34.801)),
        ),
    )

    @Test fun settings() {
        val state = UiState(savedTrips = listOf(SavedTrip("עבודה", null, home)), savedPlaces = listOf(home))
        val actions = ScreenActions(OfflineState(OfflineState.Status.READY, 100, 42.5), {}, {}, {}, {})
        paparazzi.snapshot("settings") { Frame { DialogBody { SettingsContent(state, NoActions, actions) } } }
    }

    @Test fun history() {
        val t0 = start.epochSecond
        val records = listOf(
            TripRecord(t0, "המיקום שלי", "ת. רכבת תל אביב - סבידור", "TRIP", 82, 18, 2),
            TripRecord(t0 - 86_400, "Home", "BGU", "BETTER_START", 21, 4, 0, savedMin = 12),
            TripRecord(t0 - 3 * 86_400, "", "Soroka", "PICK_UP", 35, 9, 1, savedMin = 25),
        )
        paparazzi.snapshot("history") { Frame { DialogBody { HistoryContent(UiState(history = records), NoActions) } } }
    }

    private fun shot(name: String, state: UiState, walkOpen: Boolean = false) = paparazzi.snapshot(name) {
        Frame {
            CompositionLocalProvider(LocalWalkDirectionsOpen provides walkOpen) {
                val actions = ScreenActions(OfflineState(), {}, {}, {}, {})
                MainScreen(state, NoActions, actions) {
                    Box(Modifier.fillMaxSize().background(if (v.dark) Color(0xFF2B2E33) else Color(0xFFEDEAE4)))
                }
            }
        }
    }

    /** Theme + direction. Paparazzi takes Hebrew strings from the locale but does not flip the
     *  layout (a phone does both), so RTL is set here or the "Hebrew" shots are mirror-wrong. */
    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val direction = if (v.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) { AppTheme(dark = v.dark, content = content) }
    }

    /** Roughly what AlertDialog draws around its text: a scrim, a rounded surface, 24dp in. */
    @Composable
    private fun DialogBody(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(Modifier.padding(24.dp)) { content() }
            }
        }
    }

    private fun tripState() = UiState(
        to = PlaceRef.Point("ת. רכבת תל אביב - סבידור", LatLon(32.08, 34.79)),
        timeMode = TimeMode.DEPART_AT,
        time = start,
        results = TripResult(trip.itineraries, null),
        resultsAt = start,
    )

    companion object {
        private val plan: PlanResponse = MotisJson.decodeFromString(
            PlanResponse.serializer(),
            ScreensTest::class.java.getResource("/fixtures/plan_bgu_telaviv.json")!!.readText(),
        )

        /** The recorded trip, with one line repainted yellow to show text contrast on light colours. */
        private val trip = TripResult(
            plan.itineraries.mapIndexed { i, it ->
                if (i != 1) it else it.copy(legs = it.legs.map { l -> if (l.isTransit) l.copy(routeColor = "FFD600") else l })
            },
            null,
        )
        private val start = trip.itineraries.first().start
        private val home = SavedPlace("Home", 31.25, 34.79)

        private val small = DeviceConfig.NEXUS_5 // 360 x 640 dp
        private val big = DeviceConfig.PIXEL_6_PRO
        private val landscape = DeviceConfig.PIXEL_5.copy(
            screenWidth = DeviceConfig.PIXEL_5.screenHeight,
            screenHeight = DeviceConfig.PIXEL_5.screenWidth,
            orientation = ScreenOrientation.LANDSCAPE,
        )

        private fun DeviceConfig.hebrew() = copy(locale = "iw", layoutDirection = LayoutDirection.RTL)
        private fun DeviceConfig.night() = copy(nightMode = NightMode.NIGHT)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants() = listOf(
            Variant("small_en_light", small, dark = false),
            Variant("small_he_dark", small.hebrew().night(), dark = true, rtl = true),
            Variant("small_he_bigtext", small.hebrew().copy(fontScale = 1.5f), dark = false, rtl = true),
            Variant("big_he_light", big.hebrew(), dark = false, rtl = true),
            Variant("big_en_dark", big.night(), dark = true),
            Variant("landscape_he_dark", landscape.hebrew().night(), dark = true, rtl = true),
            Variant("landscape_en_light", landscape, dark = false),
        )
    }
}
