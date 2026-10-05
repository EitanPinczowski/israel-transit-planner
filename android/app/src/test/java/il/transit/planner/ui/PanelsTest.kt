package il.transit.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import il.transit.core.geo.BBox
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.Itinerary
import il.transit.core.api.MotisJson
import il.transit.core.api.Place
import il.transit.core.api.PlanRequest
import il.transit.core.api.PlanResponse
import il.transit.core.api.StopTimesResponse
import il.transit.core.api.TransitApi
import il.transit.core.api.TripSegment
import il.transit.core.features.BetterStartPlanner
import il.transit.core.features.BetterStartQuery
import il.transit.core.features.BetterStartResult
import il.transit.core.features.DropOffKind
import il.transit.core.features.DropOffOption
import il.transit.core.features.DropOffResult
import il.transit.core.features.Option
import il.transit.core.features.PickUpPlanner
import il.transit.core.features.PickUpQuery
import il.transit.core.features.PickUpResult
import il.transit.core.features.ParkRidePlanner
import il.transit.core.features.ParkRideQuery
import il.transit.core.features.ParkRideResult
import il.transit.core.api.Endpoint
import il.transit.core.plan.CalendarDestination
import il.transit.core.plan.CalendarEvent
import il.transit.planner.ui.screens.CalendarBufferSetting
import il.transit.planner.ui.screens.CalendarConfirmBody
import il.transit.planner.ui.screens.CalendarEventList
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import il.transit.core.geo.LatLon
import il.transit.core.plan.CarTime
import il.transit.core.plan.ChainResult
import il.transit.core.plan.ChainStop
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripResult
import il.transit.core.present.departureRow
import il.transit.core.remind.Reminder
import il.transit.core.ride.RideProgress
import il.transit.core.user.FavoriteLine
import il.transit.core.user.PlaceRoutine
import il.transit.core.user.SavedPlace
import il.transit.core.user.UserSettings
import il.transit.planner.ui.screens.HomeSuggestion
import il.transit.planner.ui.screens.NightRefreshSection
import il.transit.planner.ui.screens.SavedPlaceRow
import il.transit.planner.ui.screens.CrashLogSection
import il.transit.planner.ui.screens.CrashLogUi
import il.transit.planner.ui.screens.FavoritesContent
import il.transit.planner.ui.screens.HistoryContent
import il.transit.planner.ui.screens.ResultsPanel
import il.transit.planner.ui.screens.StopPanel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant

/**
 * One panel per shot, in English (light) and Hebrew (RTL, dark): the states [ScreensTest]'s
 * whole-screen pictures don't reach — empty answers, loading, failures, errands, a reminder and
 * a ride in progress, My lines, the crash log. The car features run their real planners over
 * answers recorded from Transitous (`core/src/test/resources/fixtures/`), so the cards show
 * real stop names and times. No network: [FixtureApi] only reads files.
 */
@RunWith(Parameterized::class)
class PanelsTest(private val v: Variant) {

    class Variant(val name: String, val device: DeviceConfig, val dark: Boolean, val rtl: Boolean) {
        override fun toString() = name
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = v.device, maxPercentDifference = 0.1)

    // --- Better start (BetterStartPanel.kt) ---

    @Test fun betterStartMeitar() = panel("betterstart_meitar", carState(AppMode.BETTER_START).copy(betterStart = betterStart))

    @Test fun betterStartNone() = panel(
        "betterstart_none",
        carState(AppMode.BETTER_START).copy(betterStart = BetterStartResult(bgu.first(), emptyList(), "CAR")),
    )

    // --- Let me off on the way (DropOffPanel.kt) ---

    @Test fun dropOffChoices() {
        fun onTheWay(itin: Itinerary, detour: Int, ride: Int): Option<DropOffOption> {
            val board = itin.firstTransitLeg!!.from
            return Option(DropOffOption(DropOffKind.STOP_ON_THE_WAY, board, detour, ride, itin), detour, itin.end, itin.transfers)
        }
        val fromStart = bgu.last()
        val options = listOf(
            onTheWay(bgu[0], 240, 600),
            onTheWay(bgu[1], 420, 900),
            Option(DropOffOption(DropOffKind.TRANSIT_FROM_START, null, 0, 0, fromStart), 0, fromStart.end, fromStart.transfers),
        )
        panel("dropoff_choices", carState(AppMode.DROP_OFF).copy(dropOff = DropOffResult(4383, options, candidatesConsidered = 5)))
    }

    @Test fun dropOffNone() = panel(
        "dropoff_none",
        carState(AppMode.DROP_OFF).copy(dropOff = DropOffResult(4383, emptyList(), candidatesConsidered = 5)),
    )

    // --- Best pick-up point (PickUpPanel.kt) ---

    @Test fun pickUpLehavim() = panel("pickup_lehavim", carState(AppMode.PICK_UP).copy(pickUp = pickUp))

    @Test fun pickUpNone() = panel("pickup_none", carState(AppMode.PICK_UP).copy(pickUp = PickUpResult(bgu.first(), emptyList())))

    // --- Park & Ride (ParkRidePanel.kt, #23) ---

    @Test fun parkRideMeitar() = panel(
        "parkride_meitar",
        carState(AppMode.PARK_RIDE).copy(time = sunday0730, parkRide = ParkRideUi(result = parkRide, origin = meitar)),
    )

    @Test fun parkRideNone() = panel(
        "parkride_none",
        carState(AppMode.PARK_RIDE).copy(parkRide = ParkRideUi(result = ParkRideResult(bgu.first(), emptyList(), 0), origin = meitar)),
    )

    /** "Way back to my car": the Trip tab to the parked station, then the drive home estimate. */
    @Test fun parkRideWayBack() {
        val parked = parkRide.options.first().payload
        val state = tripState().copy(
            to = PlaceRef.Point(parked.station.name, parked.stationAt),
            // One option, so the drive-home line under it is on screen.
            results = TripResult(listOf(bgu.first()), null),
            parkRide = ParkRideUi(parked = parked),
        )
        panel("parkride_wayback", state)
    }

    // --- From my calendar (CalendarPicker.kt, #25) ---

    @Test fun calendarEvents() {
        val events = listOf(
            CalendarEvent("רופא שיניים", "הרצל 12, באר שבע", start.plusSeconds(5400), start.plusSeconds(7200)),
            CalendarEvent("Team meeting", "Azrieli Center, Tel Aviv", start.plusSeconds(10_800), start.plusSeconds(14_400)),
            CalendarEvent("", "אוניברסיטת בן גוריון", start.plusSeconds(86_400), start.plusSeconds(90_000)),
        )
        dialog("calendar_events") { CalendarEventList(events, now = start) {} }
    }

    @Test fun calendarEmpty() = dialog("calendar_empty") { CalendarEventList(emptyList(), now = start) {} }

    /** The match was only rough (a city, not the street): the warning shows in red. */
    @Test fun calendarConfirm() = dialog("calendar_confirm") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CalendarConfirmBody(CalendarConfirm(CalendarDestination("הרצל 12, באר שבע", LatLon(31.24, 34.79), "הרצל 12 באר שבע"), start))
            CalendarConfirmBody(CalendarConfirm(CalendarDestination("באר שבע", LatLon(31.25, 34.79), "Dr. Cohen, Beer Sheva", approximate = true), start))
            CalendarBufferSetting(current = 10) {}
        }
    }

    // --- Trip panel states (TripPanel.kt) ---

    @Test fun tripLoading() = panel("trip_loading", tripState().copy(results = null, loading = true))

    @Test fun tripNoResults() = panel("trip_noresults", tripState().copy(results = null, error = UiError.NO_RESULTS))

    /** A reminder set and a ride being tracked, plus "by car" answered. */
    @Test fun tripTracking() {
        val itin = bgu.first()
        val leg = itin.firstTransitLeg!!
        val reminder = Reminder(
            fromLat = 31.262, fromLon = 34.801, toLat = 32.08, toLon = 34.79, settings = UserSettings(),
            tripId = leg.tripId, line = leg.routeShortName, boardStop = leg.from.name,
            scheduledBoardingEpoch = leg.start.epochSecond, leaveAtEpoch = leg.start.epochSecond - 600,
        )
        val progress = RideProgress(leg.routeShortName, leg.to.name, stopsLeft = 3, arriveAt = leg.end.plusSeconds(120), delayMin = 2, tripArriveAt = itin.end.plusSeconds(120))
        panel(
            "trip_tracking",
            tripState().copy(reminder = reminder, riding = true, rideProgress = progress, carTime = CarTime(95, 1.3, 113.0)),
        )
    }

    /** Errands: home → post office (30 min) → Tel Aviv, two legs. */
    @Test fun tripErrands() = panel(
        "trip_errands",
        tripState().copy(
            chainStops = listOf(ChainStop(LatLon(31.252, 34.791), "סניף הדואר", stayMin = 30)),
            chain = ChainResult(bgu.take(2), failedAt = null),
        ),
    )

    // --- Stop sheet (StopSheet.kt) ---

    @Test fun stopDepartures() {
        val pinned = FavoriteLine("s1", stopName, departures[1].line, departures[1].headsign)
        panel("stop_departures", UiState(stopSheet = StopSheet("s1", stopName, false, departures, false), favorites = listOf(pinned)))
    }

    @Test fun stopFailed() = panel("stop_failed", UiState(stopSheet = StopSheet("s1", stopName, false, emptyList(), true)))

    @Test fun stopEmpty() = panel("stop_empty", UiState(stopSheet = StopSheet("s1", stopName, false, emptyList(), false)))

    /** My lines: one with departures, one still loading, one failed, under the offline note. */
    @Test fun favorites() {
        val a = FavoriteLine("s1", stopName, departures[0].line, departures[0].headsign)
        val b = FavoriteLine("s1", stopName, departures[1].line, departures[1].headsign)
        val c = FavoriteLine("s2", "מרכז רפואי סורוקה", "5", "")
        val state = UiState(
            favorites = listOf(a, b, c),
            favoriteBoards = mapOf(a to departures.filter { it.line == a.line }.take(3), c to null),
            favoritesOfflineSince = start,
        )
        dialog("favorites") { FavoritesContent(state, NoActions) }
    }

    @Test fun favoritesEmpty() = dialog("favorites_empty") { FavoritesContent(UiState(), NoActions) }

    // --- Settings and history dialogs (SettingsDialog.kt, CrashLogSection.kt) ---

    @Test fun crashLog() = dialog("crashlog") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CrashLogSection(CrashLogUi(count = 3))
            CrashLogSection(CrashLogUi(count = 1))
            CrashLogSection(CrashLogUi(count = 0))
        }
    }

    @Test fun historyEmpty() = dialog("history_empty") { HistoryContent(UiState(), NoActions) }

    // --- Night refresh and Home (NightRefreshSettings.kt, SettingsDialog.kt) ---

    /** The Trip tab offline the next morning, from a copy the night refresh planned. */
    @Test fun tripOfflineNight() = panel("trip_offline_night", tripState().copy(offlineSince = start.minusSeconds(5 * 3600), offlineNight = true))

    /** The toggle with no Home yet, the one-time "בית as Home?" offer, then a Home set (🏠) and a place to make Home. */
    @Test fun nightRefreshAndHome() = dialog("night_refresh_home") {
        val bayit = SavedPlace("בית", 31.279, 34.82)
        val uni = SavedPlace("אוניברסיטת בן גוריון", 31.262, 34.801, PlaceRoutine(listOf(7, 1, 2, 3, 4), 7 * 60, 10 * 60))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NightRefreshSection(UserSettings(), listOf(bayit, uni)) {}
            HomeSuggestion(UserSettings(), listOf(bayit, uni)) {}
            SavedPlaceRow(bayit, onRoutine = {}, isHome = true, onSetHome = {}) {}
            SavedPlaceRow(uni, onRoutine = {}, isHome = false, onSetHome = {}) {}
        }
    }

    // --- Frames ---

    /** The panel as the bottom sheet of a phone: rounded top, over a flat stand-in for the map. */
    private fun panel(name: String, state: UiState) = paparazzi.snapshot(name) {
        Frame {
            Box(Modifier.fillMaxSize().background(if (v.dark) Color(0xFF2B2E33) else Color(0xFFEDEAE4)), contentAlignment = Alignment.BottomCenter) {
                val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                val actions = ScreenActions(OfflineState(), {}, {}, {}, {})
                val sheet = state.stopSheet
                if (sheet != null) {
                    StopPanel(sheet, state.favorites, NoActions, false, {}, Modifier, shape)
                } else {
                    ResultsPanel(state, NoActions, actions, false, {}, {}, Modifier, shape)
                }
            }
        }
    }

    /** Roughly what AlertDialog draws around its text: a scrim, a rounded surface, 24dp in. */
    private fun dialog(name: String, content: @Composable () -> Unit) = paparazzi.snapshot(name) {
        Frame {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(24.dp), contentAlignment = Alignment.Center) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(Modifier.padding(24.dp)) { content() }
                }
            }
        }
    }

    /** Theme + direction; Paparazzi does not flip the layout for Hebrew by itself (see ScreensTest). */
    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val direction = if (v.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) { AppTheme(dark = v.dark, content = content) }
    }

    private fun tripState() = UiState(
        from = PlaceRef.Point("אוניברסיטת בן גוריון", LatLon(31.262, 34.801)),
        to = PlaceRef.Point("ת. רכבת תל אביב - סבידור", LatLon(32.08, 34.79)),
        timeMode = TimeMode.DEPART_AT,
        time = start,
        results = TripResult(bgu, null),
        resultsAt = start,
    )

    private fun carState(mode: AppMode) = UiState(
        mode = mode,
        from = PlaceRef.Point("מיתר", meitar),
        to = PlaceRef.Point("תל אביב מרכז", telAviv),
        driverTo = if (mode == AppMode.DROP_OFF) PlaceRef.Point("רמת גן", LatLon(32.08, 34.81)) else null,
        timeMode = TimeMode.DEPART_AT,
        time = monday8,
        maxDriveMin = 20,
        maxPickUpDriveMin = 20,
    )

    /** Answers every plan from a recording; anything else is a bug in the test. */
    private class FixtureApi(
        private val drives: List<Int?>? = null,
        private val onPlan: (PlanRequest) -> PlanResponse,
    ) : TransitApi {
        override suspend fun plan(req: PlanRequest) = onPlan(req)
        override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean): List<Int?> =
            drives ?: error("not recorded")
        override suspend fun stops(box: BBox, modes: Set<String>?, language: String): List<Place> = error("not recorded")
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> = error("not recorded")
        override suspend fun reverseGeocode(at: LatLon, language: String, max: Int): List<GeocodeMatch> = error("not recorded")
        override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String): StopTimesResponse = error("not recorded")
        override suspend fun trip(tripId: String, language: String): Itinerary = error("not recorded")
        override suspend fun mapTrips(box: BBox, start: Instant, end: Instant, zoom: Double, language: String): List<TripSegment> = error("not recorded")
    }

    companion object {
        private fun fixture(name: String) = PanelsTest::class.java.getResource("/fixtures/$name.json")!!.readText()
        private fun plan(name: String) = MotisJson.decodeFromString(PlanResponse.serializer(), fixture(name))

        private val meitar = LatLon(31.3236, 34.9381)
        private val telAviv = LatLon(32.0839, 34.7983)

        /** Monday 2026-10-05 08:00 and 17:00 in Israel, when the car fixtures were recorded. */
        private val monday8 = Instant.parse("2026-10-05T05:00:00Z")
        private val monday17 = Instant.parse("2026-10-05T14:00:00Z")

        private val bgu: List<Itinerary> = plan("plan_bgu_telaviv").itineraries
        private val start = bgu.first().start

        /** Meitar → Tel Aviv, driven to Be'er Sheva North (the real planner, as RecordedTripsTest). */
        private val betterStart: BetterStartResult = runBlocking {
            val api = FixtureApi { req -> if (req.maxPreTransitSec == null) PlanResponse() else plan("plan_car_pre_meitar") }
            BetterStartPlanner(api).plan(BetterStartQuery(meitar, telAviv, monday8, maxDriveMin = 20))
        }

        /** Tel Aviv → Meitar, collected at Lehavim-Rahat. */
        private val pickUp: PickUpResult = runBlocking {
            val api = FixtureApi { req -> if (req.maxPostTransitSec == null) PlanResponse() else plan("plan_car_post_pickup") }
            PickUpPlanner(api).plan(PickUpQuery(telAviv, meitar, monday17, maxDriveMin = 20))
        }

        /** Sunday 2026-10-11 07:30 in Israel, when the park & ride fixtures were recorded. */
        private val sunday0730 = Instant.parse("2026-10-11T04:30:00Z")

        /** Meitar → Tel Aviv: park at Be'er Sheva North, direct train (as ParkRideTest). */
        private val parkRide: ParkRideResult = runBlocking {
            val drives = MotisJson.decodeFromString(ListSerializer(JsonObject.serializer()), fixture("one_to_many_park_ride_meitar"))
                .map { it["duration"]?.jsonPrimitive?.content?.toDouble()?.toInt() }
            val api = FixtureApi(drives) { req ->
                when (val from = req.from) {
                    is Endpoint.Stop -> if (from.stopId == "il-Israel-MOT_37314") plan("plan_park_ride_bs_north") else plan("plan_park_ride_bs_center")
                    else -> plan("plan_park_ride_baseline_meitar")
                }
            }
            ParkRidePlanner(api).plan(ParkRideQuery(meitar, telAviv, sunday0730, maxDriveMin = 20))
        }

        private val board = MotisJson.decodeFromString(StopTimesResponse.serializer(), fixture("stoptimes_weekday_beersheva_central"))
        private val departures = board.stopTimes.map(::departureRow)
        private val stopName = board.place?.name ?: "באר שבע מרכז"

        private val phone = DeviceConfig.PIXEL_5

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variants() = listOf(
            Variant("en_light", phone, dark = false, rtl = false),
            Variant("he_dark", phone.copy(locale = "iw", layoutDirection = LayoutDirection.RTL, nightMode = NightMode.NIGHT), dark = true, rtl = true),
        )
    }
}
