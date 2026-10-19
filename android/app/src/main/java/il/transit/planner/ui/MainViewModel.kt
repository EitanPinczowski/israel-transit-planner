package il.transit.planner.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import il.transit.core.api.BudgetedTransitApi
import il.transit.core.api.Endpoint
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.Itinerary
import il.transit.core.api.TransitApi
import il.transit.core.features.BetterStartPlanner
import il.transit.core.features.BetterStartQuery
import il.transit.core.features.BetterStartResult
import il.transit.core.features.DropOffPlanner
import il.transit.core.features.DropOffQuery
import il.transit.core.features.DropOffResult
import il.transit.core.features.PickUpPlanner
import il.transit.core.features.PickUpQuery
import il.transit.core.features.PickUpResult
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import il.transit.core.geo.MapData
import il.transit.core.geo.StopsViewport
import il.transit.core.plan.CarCompare
import il.transit.core.plan.CarTime
import il.transit.core.plan.ChainPlanner
import il.transit.core.plan.ChainResult
import il.transit.core.plan.ChainStop
import il.transit.core.plan.LastRide
import il.transit.core.plan.LastRideFinder
import il.transit.core.plan.PlanCache
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPlanner
import il.transit.core.user.FavoriteLine
import il.transit.core.user.PlaceRoutine
import il.transit.core.ride.RideProgress
import il.transit.core.user.Routines
import il.transit.core.plan.TripSort
import il.transit.core.plan.sortOptions
import il.transit.core.plan.reselect
import il.transit.core.plan.TripQuery
import il.transit.core.plan.TripResult
import il.transit.core.plan.TripPages
import il.transit.core.present.SharedPlace
import il.transit.core.present.StopPlatform
import il.transit.core.present.stopPlatform
import il.transit.core.present.needsFullNameHint
import il.transit.core.present.geocodeDetail
import il.transit.core.search.StopCode
import il.transit.core.search.BackupGeocoder
import il.transit.core.search.LocalFirst
import il.transit.core.search.LocalHit
import il.transit.core.search.PlaceSearch
import il.transit.core.search.SearchAnswer
import il.transit.core.search.TypingSearch
import il.transit.core.present.DepartureRow
import il.transit.core.present.departureRow
import il.transit.core.remind.Reminder
import il.transit.core.user.SavedPlace
import il.transit.core.user.SavedTrip
import il.transit.core.user.UserSettings
import il.transit.core.history.History
import il.transit.core.history.HistoryStats
import il.transit.core.history.TripRecord
import il.transit.planner.Reminders
import il.transit.planner.Rides
import il.transit.core.update.LatestRelease
import il.transit.planner.data.HistoryStore
import il.transit.planner.data.UpdateChecker
import il.transit.planner.TransitApp
import il.transit.planner.data.PlanCacheStore
import il.transit.planner.data.StopsStore
import il.transit.planner.data.UserStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/** Where a trip starts or ends. */
sealed interface PlaceRef {
    data object MyLocation : PlaceRef

    /** [name] is null until reverse geocoding names a dropped pin. */
    data class Point(val name: String?, val at: LatLon) : PlaceRef
}

/** DRIVER_TO is the drop-off tab's third field: where the car is going (B). */
enum class Field { FROM, TO, DRIVER_TO, STOP }

/** The tabs on top of the search card. */
enum class AppMode { TRIP, BETTER_START, DROP_OFF, PICK_UP, PARK_RIDE }

enum class UiError { NO_LOCATION, NETWORK, NO_RESULTS }

/** A row under the search box. [home]/[recent]: from the phone (no request); [backup]: from Photon. */
data class Suggestion(
    val name: String,
    val detail: String?,
    val at: LatLon,
    val saved: Boolean,
    val isStop: Boolean,
    val home: Boolean = false,
    val recent: Boolean = false,
    val backup: Boolean = false,
    /** Not what was typed, only near it: listed under "Closest matches", never as the answer. */
    val closest: Boolean = false,
    /** A closest match's level: the row says "Street only: no number 15" / "Town only" (Phase 10 D2). */
    val match: il.transit.core.search.MatchNote? = null,
    /** A neighbourhood in several towns: tapping searches this text, which names the town (owner, 2026-10-12). */
    val refine: String? = null,
    /** A typed stop number's stop (Phase 10 D6): tapping opens its stop sheet. */
    val stopId: String? = null,
)

data class StopSheet(
    val stopId: String,
    val name: String,
    val loading: Boolean,
    val rows: List<DepartureRow>,
    val failed: Boolean,
    /** "Platform 12 · stop 47899" for the header, once the departures answer names the stop. */
    val platform: StopPlatform = StopPlatform(),
    /** Phase 10 D7: no signal, the board the night refresh saved for this stop; null online. */
    val saved: SavedDepartures? = null,
    /** Phase 10 D7: the day timetable of one line, shown in place of the departures. */
    val timetable: TimetableUi? = null,
)

data class UiState(
    val mode: AppMode = AppMode.TRIP,
    /** Better start: how far the driver is willing to go. */
    val maxDriveMin: Int = 10,
    val betterStart: BetterStartResult? = null,
    /** Drop-off: where the driver is going (B). The passenger's own destination (C) is [to]. */
    val driverTo: PlaceRef? = null,
    val maxDetourMin: Int = 10,
    val dropOff: DropOffResult? = null,
    /** Pick-up: the driver starts and ends at [to] (usually home); this is their one-way limit. */
    val maxPickUpDriveMin: Int = 15,
    val pickUp: PickUpResult? = null,
    /** Park & ride: drive limit, results, and the station the car was left at. */
    val parkRide: ParkRideUi = ParkRideUi(),
    val from: PlaceRef = PlaceRef.MyLocation,
    val to: PlaceRef? = null,
    val editing: Field? = null,
    val query: String = "",
    val suggestions: List<Suggestion> = emptyList(),
    /** The search was short and nothing found contains it: suggest typing the full name. */
    val searchHint: Boolean = false,
    val timeMode: TimeMode = TimeMode.NOW,
    val time: Instant? = null,
    val loading: Boolean = false,
    val error: UiError? = null,
    val results: TripResult? = null,
    val selected: Int = 0,
    val stopSheet: StopSheet? = null,
    val showSettings: Boolean = false,
    /** Phase 11 E1: "Will my reminder ring?" open, offered as a hint, or closed. */
    val reminderCheck: il.transit.planner.ui.screens.ReminderCheckView = il.transit.planner.ui.screens.ReminderCheckView.NONE,
    val settings: UserSettings = UserSettings(),
    /** [settings] come from the store, not the defaults: the first-run tour waits for this. */
    val settingsLoaded: Boolean = false,
    val savedPlaces: List<SavedPlace> = emptyList(),
    val savedTrips: List<SavedTrip> = emptyList(),
    /** When the shown results were fetched (Trip tab), for "Updated 12:07". */
    val resultsAt: Instant? = null,
    /** Set when the results are a saved copy shown because the network failed. */
    val offlineSince: Instant? = null,
    /** The saved copy was planned by the night refresh: "planned last night at 02:14". */
    val offlineNight: Boolean = false,
    val reminder: Reminder? = null,
    /** A "get off at the next stop" ride is being tracked. */
    val riding: Boolean = false,
    val history: List<TripRecord> = emptyList(),
    val showHistory: Boolean = false,
    /** A newer release to offer, until dismissed. */
    val update: LatestRelease? = null,
    /** Where the in-app update stands (banner and Settings → "Check for updates"). */
    val updateState: il.transit.core.update.UpdateState = il.transit.core.update.UpdateState.Idle,
    /** Trip tab: the evening's last trip for the shown route (looked up on evenings, Fridays
     *  and Saturdays, or when asked), and for the way back when asked. */
    val lastRide: LastRide? = null,
    val lastRideAsked: Boolean = false,
    val lastRideBack: LastRide? = null,
    val lastRideLoading: Boolean = false,
    /** The destination was filled in by this saved place's routine, not typed by the user. */
    val routinePlace: String? = null,
    /** Live progress while a ride is tracked ("3 stops left · arrive 08:47"). */
    val rideProgress: RideProgress? = null,
    val favorites: List<FavoriteLine> = emptyList(),
    val showFavorites: Boolean = false,
    /** Next departures per pinned line; a missing key = still loading, null value = failed. */
    val favoriteBoards: Map<FavoriteLine, List<DepartureRow>?> = emptyMap(),
    /** Set when some of My lines show a saved board because the network failed. */
    val favoritesOfflineSince: Instant? = null,
    /** Trip tab errands: stops on the way, each with a stay (A → stops → B). */
    val chainStops: List<ChainStop> = emptyList(),
    val chain: ChainResult? = null,
    /** "🚗 By car?" for the shown trip. */
    val carTime: CarTime? = null,
    val carLoading: Boolean = false,
    /** Earlier / Later under the Trip options. */
    val pages: TripPagesUi = TripPagesUi(),
    /** Map pin sheet, the save-place dialog, recent picks (search fix). */
    val places: PlacesUi = PlacesUi(),
    /** "Safer change" for the opened option (Phase 10 D5). */
    val bigStop: BigStopUi = BigStopUi(),
    /** Rain on the walking parts of the Trip options (Phase 10 D9). */
    val rain: RainUi = RainUi(),
    /** Bumped when a route is tapped on the map (D11): the results list scrolls to its card. */
    val mapPick: Int = 0,
) {
    val historyStats: HistoryStats get() = History.stats(history, Instant.now())

    /** The routine's place, while the destination is still the one the routine filled in. */
    val activeRoutine: String? get() = routinePlace?.takeIf { (to as? PlaceRef.Point)?.name == it }

    /** The itineraries the results panel lists, for whichever tab is showing. The car + transit
     *  results are stored already sorted ([arranged]), so this list, the cards, [selectedCarPath]
     *  and "send to driver" all index the same order. */
    val options: List<Itinerary>
        get() = when (mode) {
            AppMode.TRIP -> chain?.legs ?: results?.let { sortOptions(it, settings.tripSort, timeMode == TimeMode.ARRIVE_BY) }.orEmpty()
            AppMode.BETTER_START -> betterStart?.options?.map { it.payload.itinerary }.orEmpty()
            AppMode.DROP_OFF -> dropOff?.options?.map { it.payload.transit }.orEmpty()
            AppMode.PICK_UP -> pickUp?.options?.map { it.payload.itinerary }.orEmpty()
            AppMode.PARK_RIDE -> parkRide.result?.options?.map { it.payload.itinerary }.orEmpty()
        }

    val hasResults: Boolean get() = results != null || chain != null || betterStart != null || dropOff != null || pickUp != null || parkRide.result != null

    /** Everything the current tab needs before it can search. */
    val readyToPlan: Boolean get() = to != null && (mode != AppMode.DROP_OFF || driverTo != null)

    val selectedItinerary: Itinerary? get() = options.getOrNull(selected)

    /** The car part to draw before [selectedItinerary] (drop-off tab only). */
    val selectedCarPath: List<LatLon>
        get() = when (mode) {
            AppMode.DROP_OFF -> dropOff?.options?.getOrNull(selected)?.payload?.carPath.orEmpty()
            AppMode.PARK_RIDE -> parkRide.carPath(selected)
            else -> emptyList()
        }
}

class MainViewModel(
    private val api: TransitApi,
    private val store: UserStore,
    /** Read per request: the app language can change while this ViewModel lives (C6). */
    private val languageOf: () -> String,
    private val planCache: PlanCacheStore? = null,
    private val reminders: Reminders? = null,
    private val rides: Rides? = null,
    private val historyStore: HistoryStore? = null,
    private val updates: UpdateChecker? = null,
    private val stopsCache: StopsStore? = null,
    private val departureCache: il.transit.planner.data.DepartureCacheStore? = null,
    private val clock: Clock = Clock.systemUTC(),
    /** Asked only after a Transitous miss ([PlaceSearch]); null = Transitous alone. */
    private val backup: BackupGeocoder? = null,
    /** Boards the night refresh saved for My lines' stops (Phase 10 D7). */
    private val timetables: il.transit.planner.data.TimetableStore? = null,
    /** Rain on the walks (Phase 10 D9): Open-Meteo, guarded; null = no note. */
    private val weather: il.transit.core.weather.GuardedWeather? = null,
    /** The search and the chosen option across process death (D11, [SavedUi]); null = not kept. */
    private val saved: SavedStateHandle? = null,
) : ViewModel() {
    private val language: String get() = languageOf()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _stops = MutableStateFlow(MapData.EMPTY)

    /** GeoJSON for the stops layer; empty below [StopsViewport.MIN_ZOOM]. */
    val stops: StateFlow<String> = _stops.asStateFlow()

    /** Set by the Activity once the map's location component is live. */
    var locationProvider: () -> LatLon? = { null }

    private val planner = TripPlanner(api)
    private val typing = TypingSearch(viewModelScope, PlaceSearch(api, backup, stopsCache?.let { c -> c::known })) { q, answer, failed -> showAnswer(q, answer, failed) }
    private val picker = PlacePicker(api, backup, store, { language }, viewModelScope, { f -> _state.update(f) }, { _state.value })
    private var planJob: Job? = null
    private var stopsJob: Job? = null
    private var loadedStops: BBox? = null

    private val updater = updates?.let { UpdateController(it, viewModelScope, clock, { f -> _state.update(f) }, { _state.value }) }

    /** A routine the user dismissed today ("name|day"), so it does not come straight back. */
    private var dismissedRoutine: String? = null

    init {
        viewModelScope.launch {
            combine(store.settings, store.places, store.trips) { s, p, t -> Triple(s, p, t) }.collect { (s, p, t) ->
                val first = _state.value.savedPlaces.isEmpty() && p.isNotEmpty()
                _state.update { it.copy(settings = s, settingsLoaded = true, savedPlaces = p, savedTrips = t) }
                if (first) applyRoutine()
            }
        }
        viewModelScope.launch { store.reminder.collect { r -> _state.update { it.copy(reminder = r) } } }
        viewModelScope.launch { store.recents.collect { r -> _state.update { it.copy(places = it.places.copy(recents = r)) } } }
        rides?.let { r -> viewModelScope.launch { r.active.collect { a -> _state.update { it.copy(riding = a) } } } }
        rides?.let { r -> viewModelScope.launch { r.progress.collect { p -> _state.update { it.copy(rideProgress = p) } } } }
        viewModelScope.launch { store.favorites.collect { f -> _state.update { it.copy(favorites = f) } } }
        updater?.daily()
        historyStore?.let { h ->
            viewModelScope.launch {
                h.load()
                h.records.collect { list -> _state.update { it.copy(history = list) } }
            }
        }
    }

    // --- live refresh ------------------------------------------------------------------

    private var refreshJob: Job? = null

    /**
     * While the app is in front, re-plan the Trip tab every [REFRESH_MS] so delays stay
     * current. One request each time, and only in the Trip tab: the car tabs cost up to
     * ten requests per search, so they refresh only when asked (↻).
     */
    fun onVisible(visible: Boolean) {
        refreshJob?.cancel()
        if (!visible) return
        applyRoutine()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(REFRESH_MS)
                val s = _state.value
                if (s.mode == AppMode.TRIP && s.results != null && !s.loading && s.offlineSince == null && s.editing == null) {
                    plan(quiet = true)
                }
            }
        }
    }

    // --- search -----------------------------------------------------------------------

    fun startEditing(field: Field) {
        _state.update { it.copy(editing = field, query = "", suggestions = localSuggestions(""), searchHint = false, places = it.places.copy(pin = null, backupShown = false, notFound = false, offline = false)) }
    }

    fun cancelEditing() {
        typing.cancel()
        _state.update { it.copy(editing = null, query = "", suggestions = emptyList(), searchHint = false, places = it.places.copy(backupShown = false, notFound = false, offline = false)) }
    }

    /** Saved places and recent picks at once; Transitous (then, on a miss, Photon) once typing pauses. */
    fun onQuery(text: String) {
        _state.update { it.copy(query = text, suggestions = localSuggestions(text), searchHint = false, places = it.places.copy(backupShown = false, notFound = false, offline = false)) }
        typing.onText(text, language, locationProvider)
    }

    /** [failed]: no answer at all (offline, Transitous down): said as such, never "not found". */
    private fun showAnswer(q: String, answer: SearchAnswer, failed: Boolean) {
        _state.update { st ->
            if (st.editing == null || st.query.trim() != q) return@update st
            val local = localHits(q)
            st.copy(
                // Exact answers first (Transitous's, or the backup's after a miss), then the closest, labelled.
                suggestions = local.map { toSuggestion(it) } +
                    answer.choices.map { c -> Suggestion(q, c.town.displayName(language), c.town.centre, saved = false, isStop = false, refine = c.query) } +
                    LocalFirst.serverAfter(local, answer.exact).map { m -> toSuggestion(m).copy(backup = m in answer.backup) } +
                    LocalFirst.serverAfter(local, answer.closest).map { m -> toSuggestion(m).copy(backup = m in answer.backup, closest = true, match = answer.note(m)) },
                searchHint = needsFullNameHint(q, answer.all.map { it.name }),
                places = st.places.copy(
                    backupShown = answer.backup.any { it.id.startsWith("photon:") },
                    notFound = !failed && answer.notFound,
                    offline = failed,
                ),
            )
        }
    }

    fun pick(s: Suggestion) {
        s.refine?.let { onQuery(it); return } // which town: search there now (1 request)
        s.stopId?.let { cancelEditing(); openStop(it, s.name); return } // a typed stop number (D6)
        if (!s.saved) picker.remember(SavedPlace(s.name, s.at.lat, s.at.lon))
        setField(PlaceRef.Point(s.name, s.at))
    }

    fun pickMyLocation() = setField(PlaceRef.MyLocation)

    /**
     * A place shared from another app (Phase 10 D6): coordinates are the destination at once (0
     * requests; named by the link, else "Pin · lat, lon"); text opens the search with it typed (one
     * geocode). Short links, other countries and the rest are told by the Activity, not here.
     */
    fun openShared(place: SharedPlace) {
        _state.update { it.copy(mode = AppMode.TRIP, chainStops = emptyList(), chain = null, stopSheet = null, error = null, selected = 0) }
        when (place) {
            is SharedPlace.Point -> viewModelScope.launch {
                // Shared into a closed app: the map has no fix yet, so wait for it as the Home tile does (review D6).
                if (_state.value.from == PlaceRef.MyLocation && locationProvider() == null) {
                    _state.update { it.copy(loading = true) }
                    kotlinx.coroutines.withTimeoutOrNull(FIX_WAIT_MS) { while (locationProvider() == null) delay(250) }
                    _state.update { it.copy(loading = false) }
                }
                _state.update { it.copy(editing = Field.TO) }
                setField(PlaceRef.Point(place.label, place.at))
            }
            is SharedPlace.Text -> { startEditing(Field.TO); onQuery(place.query) }
            else -> Unit
        }
    }

    private fun setField(ref: PlaceRef) {
        val field = _state.value.editing ?: Field.TO
        if (field == Field.STOP) {
            // A stop on the way: a fixed point (my location is resolved now), 15 min there by default.
            val at = resolve(ref) ?: return
            val name = (ref as? PlaceRef.Point)?.name
            _state.update {
                // Errands are planned forwards from a departure; "arrive by" would be read as one.
                val arriveBy = it.timeMode == TimeMode.ARRIVE_BY
                it.copy(
                    timeMode = if (arriveBy) TimeMode.NOW else it.timeMode, time = if (arriveBy) null else it.time,
                    chainStops = (it.chainStops + ChainStop(at, name, 15)).take(ChainPlanner.MAX_STOPS),
                    editing = null, query = "", suggestions = emptyList(), searchHint = false, results = null, chain = null,
                )
            }
            if (_state.value.readyToPlan) plan()
            return
        }
        _state.update {
            val next = when (field) {
                Field.FROM -> it.copy(from = ref)
                Field.TO -> it.copy(to = ref)
                Field.DRIVER_TO -> it.copy(driverTo = ref)
                Field.STOP -> it
            }
            next.copy(editing = null, query = "", suggestions = emptyList(), searchHint = false, results = null, betterStart = null, dropOff = null, pickUp = null, parkRide = next.parkRide.cleared())
        }
        if (_state.value.readyToPlan) plan()
    }

    // --- map pin and saving places (search fix S1, S2) -----------------------------------

    /** Long-press on the map: a sheet for that point (From here / To here / Save), named by one reverse geocode. */
    fun onMapLongPress(at: LatLon) = picker.longPress(at)

    fun closePin() = picker.closePin()

    /** "From here" / "To here": the pin fills that field of whichever tab is open. */
    fun pinTo(field: Field) {
        val pin = _state.value.places.pin ?: return
        _state.update { it.copy(editing = field, places = it.places.copy(pin = null)) }
        setField(PlaceRef.Point(pin.name, pin.at))
        // Still naming: the pin's own request names the field when it answers.
        if (pin.name == null && !pin.naming) nameLater(pin.at)
    }

    fun saveFromPin() = picker.saveFromPin()

    fun saveHere() = picker.saveHere(locationProvider())

    fun cancelDraft() = picker.cancelDraft()

    fun saveDraft(name: String, asHome: Boolean) {
        val at = _state.value.places.draft?.at ?: return
        picker.cancelDraft()
        savePlace(name, at)
        if (asHome) updateSettings(_state.value.settings.copy(homePlace = name, homeOffered = true))
    }

    /**
     * "From my calendar" (B3): the event's place becomes the destination, arriving by
     * [arriveBy] (null = too late for the buffer, so leave now). [at] null = the place was not
     * found: its text goes into the search box, to fix by typing.
     */
    fun fromCalendar(place: String, at: LatLon?, arriveBy: Instant?) {
        val time = if (arriveBy != null) TimeMode.ARRIVE_BY else TimeMode.NOW
        _state.update {
            val timed = it.copy(mode = AppMode.TRIP, timeMode = time, time = arriveBy, chainStops = emptyList(), chain = null, results = null, error = null)
            if (at == null) {
                timed.copy(editing = Field.TO, query = place, suggestions = localSuggestions(place), searchHint = false)
            } else {
                timed.copy(to = PlaceRef.Point(place, at), editing = null, routinePlace = null, stopSheet = null)
            }
        }
        if (at != null) plan()
    }

    fun swap() {
        val s = _state.value
        val to = s.to ?: return
        _state.update { it.copy(from = to, to = s.from, results = null, betterStart = null, dropOff = null, pickUp = null, parkRide = it.parkRide.cleared()) }
        plan()
    }

    fun setTime(mode: TimeMode, time: Instant?) {
        _state.update { it.copy(timeMode = mode, time = if (mode == TimeMode.NOW) null else time) }
        if (_state.value.readyToPlan) plan()
    }

    fun setMode(mode: AppMode) {
        if (mode == _state.value.mode) return
        _state.update {
            // Only the plain trip has arrive-by: the car tabs ask "leaving now/at T, where do I get out?"
            val time = if (mode != AppMode.TRIP && it.timeMode == TimeMode.ARRIVE_BY) TimeMode.NOW else it.timeMode
            it.copy(mode = mode, timeMode = time, results = null, betterStart = null, dropOff = null, pickUp = null, parkRide = it.parkRide.cleared(), error = null, selected = 0)
        }
        if (_state.value.readyToPlan) {
            plan()
        } else {
            // Nothing to search on this tab yet: the other tab's search must not finish into it, nor keep the spinner.
            planJob?.cancel()
            _state.update { it.copy(loading = false) }
        }
    }

    fun setMaxDrive(min: Int) {
        if (min == _state.value.maxDriveMin) return
        _state.update { it.copy(maxDriveMin = min) }
        if (_state.value.readyToPlan) plan()
    }

    fun setMaxPickUpDrive(min: Int) {
        if (min == _state.value.maxPickUpDriveMin) return
        _state.update { it.copy(maxPickUpDriveMin = min) }
        if (_state.value.readyToPlan) plan()
    }

    fun setMaxParkRideDrive(min: Int) {
        if (min == _state.value.parkRide.maxDriveMin) return
        _state.update { it.copy(parkRide = it.parkRide.copy(maxDriveMin = min)) }
        if (_state.value.readyToPlan) plan()
    }

    fun setMaxDetour(min: Int) {
        if (min == _state.value.maxDetourMin) return
        _state.update { it.copy(maxDetourMin = min) }
        if (_state.value.readyToPlan) plan()
    }

    // --- planning -----------------------------------------------------------------------

    /** [quiet]: a background refresh — keep the current results on screen and ignore failures. */
    fun plan(quiet: Boolean = false, lastRideHint: Boolean = true) {
        val s = _state.value
        if (!s.readyToPlan) return
        val from = resolve(s.from)
        val to = s.to?.let(::resolve)
        val driverTo = s.driverTo?.let(::resolve)
        if (from == null || to == null || (s.mode == AppMode.DROP_OFF && driverTo == null)) {
            _state.update { it.copy(error = UiError.NO_LOCATION, loading = false) }
            return
        }
        planJob?.cancel()
        if (!quiet) {
            _state.update {
                it.copy(
                    loading = true, error = null, results = null, betterStart = null, dropOff = null, pickUp = null, parkRide = it.parkRide.cleared(),
                    selected = 0, stopSheet = null, offlineSince = null,
                    lastRide = null, lastRideAsked = false, lastRideBack = null, lastRideLoading = false,
                    chain = null, carTime = null, carLoading = false, pages = TripPagesUi(),
                )
            }
        }
        val cacheKey = PlanCache.key("TRIP-${s.timeMode}", from, to)
        planJob = viewModelScope.launch {
            try {
                when (s.mode) {
                    AppMode.TRIP -> if (s.chainStops.isNotEmpty()) {
                        val r = ChainPlanner(BudgetedTransitApi(api, ChainPlanner.BUDGET)).plan(
                            from, s.chainStops, to,
                            departAt = s.time?.takeIf { s.timeMode != TimeMode.NOW } ?: clock.instant(),
                            settings = s.settings, language = language,
                        )
                        _state.update {
                            it.copy(
                                loading = false, chain = r, resultsAt = clock.instant(),
                                selected = if (quiet) it.selected.coerceAtMost((r.legs.size - 1).coerceAtLeast(0)) else 0,
                                error = if (r.legs.isEmpty()) UiError.NO_RESULTS else null,
                            )
                        }
                    } else {
                        val q = TripQuery(Endpoint.Coord(from), Endpoint.Coord(to), s.timeMode, s.time, s.settings, language)
                        if (!quiet) tripQuery = q
                        val fresh = planner.plan(q, clock.instant())
                        // A refresh keeps the Earlier / Later pages already loaded.
                        val r = if (quiet) TripPages.refresh(_state.value.results, fresh, clock.instant(), s.timeMode == TimeMode.ARRIVE_BY) else fresh
                        val empty = r.itineraries.isEmpty() && r.walkOnly == null
                        _state.update {
                            it.copy(
                                loading = false,
                                results = r,
                                resultsAt = clock.instant(),
                                offlineSince = null,
                                // A refresh keeps the bus the user picked, wherever it moved in the list.
                                selected = if (quiet) {
                                    reselect(it.selectedItinerary, sortOptions(r, it.settings.tripSort, it.timeMode == TimeMode.ARRIVE_BY), it.selected)
                                } else {
                                    0
                                },
                                error = if (empty) UiError.NO_RESULTS else null,
                            )
                        }
                        if (!empty) planCache?.put(cacheKey, r)
                        shownKey = cacheKey.takeIf { !empty }
                        persist()
                        if (!empty) rains.run(_state, from) // Phase 10 D9: 1 Open-Meteo forecast, cached 1 h per cell
                        r.itineraries.firstOrNull()?.let { first ->
                            if (!quiet && lastRideHint && LastRideFinder.worthAsking(first.start)) loadLastRide(from, to, first.start, back = false, asked = false)
                        }
                    }
                    AppMode.BETTER_START -> {
                        // A fresh budget per search: a pruning bug becomes an exception, not a flood.
                        val budgeted = BudgetedTransitApi(api, BetterStartPlanner.BUDGET)
                        val r = BetterStartPlanner(budgeted, s.settings.traffic()).plan(
                            BetterStartQuery(
                                origin = from,
                                dest = to,
                                departAt = s.time?.takeIf { s.timeMode == TimeMode.DEPART_AT } ?: clock.instant(),
                                maxDriveMin = s.maxDriveMin,
                                preferences = s.settings.preferences(),
                                language = language,
                            ),
                        )
                        val empty = r.options.isEmpty() && r.baseline == null
                        _state.update { it.copy(loading = false, betterStart = r, error = if (empty) UiError.NO_RESULTS else null).arranged() }
                    }
                    AppMode.DROP_OFF -> {
                        val budgeted = BudgetedTransitApi(api, DropOffPlanner.BUDGET)
                        val r = DropOffPlanner(budgeted, s.settings.traffic()).plan(
                            DropOffQuery(
                                a = from,
                                b = requireNotNull(driverTo),
                                c = to,
                                departAt = s.time?.takeIf { s.timeMode == TimeMode.DEPART_AT } ?: clock.instant(),
                                maxDetourMin = s.maxDetourMin,
                                preferences = s.settings.preferences(),
                                language = language,
                            ),
                        )
                        _state.update { it.copy(loading = false, dropOff = r, error = if (r.options.isEmpty()) UiError.NO_RESULTS else null).arranged() }
                    }
                    AppMode.PICK_UP -> {
                        val budgeted = BudgetedTransitApi(api, PickUpPlanner.BUDGET)
                        val r = PickUpPlanner(budgeted, s.settings.traffic()).plan(
                            PickUpQuery(
                                me = from,
                                home = to,
                                departAt = s.time?.takeIf { s.timeMode == TimeMode.DEPART_AT } ?: clock.instant(),
                                maxDriveMin = s.maxPickUpDriveMin,
                                preferences = s.settings.preferences(),
                                language = language,
                            ),
                        )
                        val empty = r.options.isEmpty() && r.baseline == null
                        _state.update { it.copy(loading = false, pickUp = r, error = if (empty) UiError.NO_RESULTS else null).arranged() }
                    }
                    AppMode.PARK_RIDE -> {
                        val r = planParkRide(api, s, from, to, language)
                        val empty = r.options.isEmpty() && r.baseline == null
                        _state.update { it.copy(loading = false, parkRide = it.parkRide.copy(result = r, origin = from), error = if (empty) UiError.NO_RESULTS else null).arranged() }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (quiet) return@launch
                // No signal: show what this trip looked like last time, clearly marked.
                // (An errand chain is not cached: its saved copy would be another trip.)
                val cached = if (s.mode == AppMode.TRIP && s.chainStops.isEmpty()) planCache?.get(cacheKey) else null
                shownKey = cacheKey.takeIf { cached != null }
                _state.update {
                    if (cached != null) {
                        it.copy(loading = false, results = cached.value, offlineSince = cached.savedAt, offlineNight = cached.isLastNight(clock.instant()), resultsAt = cached.savedAt)
                    } else {
                        it.copy(loading = false, error = UiError.NETWORK)
                    }
                }
            }
        }
    }

    // --- Earlier / Later (Phase 9 C1) ------------------------------------------------------

    /** The search the shown Trip options answer: pages must repeat it, whatever the GPS did since. */
    private var tripQuery: TripQuery? = null

    fun earlier() = loadPage(TripPages.Direction.EARLIER)

    fun later() = loadPage(TripPages.Direction.LATER)

    /** One `plan` request per tap (none for a cursor already loaded: the guard cache), merged
     *  into the list; a failure keeps the list and says so under the buttons. */
    private fun loadPage(direction: TripPages.Direction) {
        val s = _state.value
        val current = s.results ?: return
        // Options restored after process death (D11) have no query yet: the same search, rebuilt.
        val q = tripQuery ?: restoredQuery(s) ?: return
        if (s.mode != AppMode.TRIP || s.chain != null || s.loading || s.pages.loading != null) return
        _state.update { it.copy(pages = TripPagesUi(loading = direction)) }
        viewModelScope.launch {
            val merged = try {
                planner.page(q, current, direction, clock.instant()) // E7: what already left is dropped, as in the search
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.update {
                when (val o = TripPages.outcome(current, it.results, merged)) {
                    TripPages.PageOutcome.Stale -> it.copy(pages = TripPagesUi())
                    TripPages.PageOutcome.Failed -> it.copy(pages = TripPagesUi(failed = true))
                    is TripPages.PageOutcome.Merged -> it.copy(
                        results = o.result,
                        // The option the user picked stays picked, wherever the new page put it.
                        selected = reselect(it.selectedItinerary, sortOptions(o.result, it.settings.tripSort, it.timeMode == TimeMode.ARRIVE_BY), it.selected),
                        pages = TripPagesUi(),
                    )
                }
            }
        }
    }

    private fun restoredQuery(s: UiState): TripQuery? {
        val from = resolve(s.from) ?: return null
        val to = s.to?.let(::resolve) ?: return null
        return TripQuery(Endpoint.Coord(from), Endpoint.Coord(to), s.timeMode, s.time, s.settings, language).also { tripQuery = it }
    }

    // --- "time to leave" reminder ----------------------------------------------------------

    /** Arms a reminder for the selected Trip itinerary. False if it has no transit leg. */
    fun remindSelected(): Boolean {
        val s = _state.value
        val itin = s.selectedItinerary ?: return false
        val from = resolve(s.from) ?: return false
        val to = s.to?.let(::resolve) ?: return false
        // An errand leg is re-checked on its own stops; the chain's A → B would never find its bus.
        val r = (if (s.chain != null) Reminder.forOwnEndpoints(itin, s.settings) else Reminder.from(itin, from, to, s.settings, nameOf(s.from), s.to?.let(::nameOf)))
            ?: return false
        reminders?.cancel() // a countdown still showing for the old one goes, with its alarms
        viewModelScope.launch { store.setReminder(r) }
        reminders?.schedule(r)
        // Phase 11 E1: after the first reminder, once: "Check that reminders will ring on this phone".
        if (!s.settings.reminderCheckHintShown) {
            updateSettings(s.settings.copy(reminderCheckHintShown = true))
            reminderCheck(il.transit.planner.ui.screens.ReminderCheckView.HINT)
        }
        return true
    }

    // --- riding: "get off at the next stop" + history ------------------------------------------

    /** Starts tracking the selected itinerary and records it in the history. */
    fun startRide(): Boolean {
        val s = _state.value
        val itin = s.selectedItinerary ?: return false
        if (itin.firstTransitLeg == null) return false
        rides?.start(itin, s.settings.speakAlerts)
        val record = TripRecord.from(itin, nameOf(s.from), s.to?.let(::nameOf).orEmpty(), s.mode.name, clock.instant(), savedMinOfSelected(s))
        viewModelScope.launch { historyStore?.add(record) }
        return true
    }

    /** "Start trip" on the leave-now countdown (C5): the reminder's own option, as the in-app button rides it. */
    fun startReminderRide() = viewModelScope.launch {
        val r = store.reminder.first() ?: return@launch
        val itin = r.itinerary ?: return@launch
        rides?.start(itin, store.settings.first().speakAlerts)
        historyStore?.add(TripRecord.from(itin, r.fromName.orEmpty(), r.toName.orEmpty(), AppMode.TRIP.name, clock.instant(), null))
        cancelReminder()
    }

    fun stopRide() {
        rides?.stop()
    }

    fun showHistory(show: Boolean) = _state.update { it.copy(showHistory = show) }

    // --- favourite lines ------------------------------------------------------------------

    /** ☆ on a departure-board row: pin (or unpin) that line in that direction at this stop. */
    fun toggleFavorite(sheet: StopSheet, row: DepartureRow) = viewModelScope.launch {
        val fav = FavoriteLine(sheet.stopId, sheet.name, row.line, row.headsign)
        val now = _state.value.favorites
        store.setFavorites(if (fav in now) now - fav else now + fav)
    }

    fun removeFavorite(fav: FavoriteLine) = viewModelScope.launch {
        store.setFavorites(_state.value.favorites - fav)
    }

    /** Open the pinned lines with their next departures: one `stoptimes` request per stop. */
    fun showFavorites(show: Boolean) {
        _state.update {
            it.copy(showFavorites = show, favoriteBoards = if (show) emptyMap() else it.favoriteBoards, favoritesOfflineSince = null)
        }
        if (!show) return
        _state.value.favorites.groupBy { it.stopId }.forEach { (stopId, favs) ->
            viewModelScope.launch {
                val live = try {
                    api.stopTimes(stopId, null, 60, language).stopTimes.map(::departureRow)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (live != null) departureCache?.put(stopId, live)
                // No signal: the board saved last time, departures still ahead only.
                val saved = if (live == null) departureCache?.upcoming(stopId) else null
                val rows = live ?: saved?.second
                _state.update { st ->
                    st.copy(
                        favoriteBoards = st.favoriteBoards + favs.associateWith { f -> rows?.filter(f::matches)?.take(3) },
                        favoritesOfflineSince = saved?.first?.let { a -> st.favoritesOfflineSince?.let { minOf(it, a) } ?: a }
                            ?: st.favoritesOfflineSince,
                    )
                }
            }
        }
    }

    // --- way back -------------------------------------------------------------------------

    /**
     * The return trip of the selected option: from where it arrives back to where it started,
     * leaving [stayMin] after arriving, with the same settings.
     */
    fun wayBack(stayMin: Int) {
        val s = _state.value
        val arrive = s.selectedItinerary?.end ?: return
        val origin = s.from
        // Park & ride: back to the station where the car is, as a plain trip (one request).
        val parked = if (s.mode == AppMode.PARK_RIDE) s.parkRide.selected(s.selected) else null
        val back: PlaceRef = when {
            parked != null -> PlaceRef.Point(parked.station.name, parked.stationAt)
            origin is PlaceRef.Point -> origin
            else -> PlaceRef.Point(null, resolve(origin) ?: return)
        }
        val dest = s.to ?: return
        _state.update {
            it.copy(
                from = dest, to = back, routinePlace = null, timeMode = TimeMode.DEPART_AT, time = arrive.plusSeconds(stayMin * 60L),
                mode = if (parked != null) AppMode.TRIP else it.mode,
                parkRide = if (parked != null) it.parkRide.copy(parked = parked) else it.parkRide,
            )
        }
        plan()
        if (back is PlaceRef.Point && back.name == null) nameLater(back.at)
    }

    /** Fill in a dropped point's name once reverse geocoding answers. */
    private fun nameLater(at: LatLon) = viewModelScope.launch {
        val name = try {
            api.reverseGeocode(at, language, 1).firstOrNull()?.name
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (name != null) {
            _state.update { st ->
                st.copy(
                    to = (st.to as? PlaceRef.Point)?.takeIf { it.at == at && it.name == null }?.copy(name = name) ?: st.to,
                    from = (st.from as? PlaceRef.Point)?.takeIf { it.at == at && it.name == null }?.copy(name = name) ?: st.from,
                )
            }
        }
    }

    fun dismissUpdate() { updater?.dismiss() ?: _state.update { it.copy(update = null) } }
    fun checkForUpdates() { updater?.checkNow() }
    fun startUpdate() { updater?.start() }
    fun updatePermissionReturned() { updater?.permissionReturned() }
    fun updateScreenShown() { updater?.shown() }
    fun updateInstallFinished(resultCode: Int, installCode: Int?) { updater?.installFinished(resultCode, installCode) }

    fun clearHistory() = viewModelScope.launch { historyStore?.clear() }

    /** "" stands for "my location"; the history screen shows it localized. */
    private fun nameOf(ref: PlaceRef): String = when (ref) {
        PlaceRef.MyLocation -> ""
        is PlaceRef.Point -> ref.name.orEmpty()
    }

    /** Minutes the chosen car + transit option saves against doing it without the car. */
    private fun savedMinOfSelected(s: UiState): Int? {
        fun min(a: Instant, b: Instant) = ((a.epochSecond - b.epochSecond) / 60).toInt()
        return when (s.mode) {
            AppMode.TRIP -> null
            AppMode.BETTER_START -> s.betterStart?.let { r -> r.options.getOrNull(s.selected)?.let { o -> r.baseline?.let { b -> min(b.end, o.arrival) } } }
            AppMode.PICK_UP -> s.pickUp?.let { r -> r.options.getOrNull(s.selected)?.let { o -> r.baseline?.let { b -> min(b.end, o.arrival) } } }
            AppMode.PARK_RIDE -> s.parkRide.result?.let { r -> r.options.getOrNull(s.selected)?.let { o -> r.baseline?.let { b -> min(b.end, o.arrival) } } }
            AppMode.DROP_OFF -> s.dropOff?.let { r ->
                val o = r.options.getOrNull(s.selected) ?: return@let null
                val noCar = r.options.firstOrNull { it.payload.kind == il.transit.core.features.DropOffKind.TRANSIT_FROM_START }
                noCar?.let { b -> min(b.arrival, o.arrival) }
            }
        }
    }

    fun cancelReminder() {
        reminders?.cancel()
        viewModelScope.launch { store.setReminder(null) }
    }

    fun select(index: Int) {
        _state.update { it.copy(selected = index) }
        bigStops.run(_state) // Phase 10 D5: 0 requests, or 2 `trip` for a change at a small stop
    }

    /** A route tapped on the map (D11): its card is selected and the list scrolls to it. */
    fun selectFromMap(index: Int) {
        if (index !in _state.value.options.indices || index == _state.value.selected) return
        _state.update { it.copy(mapPick = it.mapPick + 1) }
        select(index)
    }

    private val bigStops = BigStopCheck(viewModelScope, api) { language }
    private val rains = RainCheck(viewModelScope, weather)

    /** "Use this change" / "Back to the original change" (Phase 10 D5). */
    fun useBigStop(use: Boolean) = _state.update { applyBigStop(it, use) }

    // --- last trip ------------------------------------------------------------------------

    /** Remembered per route and service day, so a refresh or a second look costs nothing. */
    private val lastRides = HashMap<String, LastRide>()

    /** "Last trip today?" — the user asked, so the answer is shown whatever it is. */
    fun checkLastRide() = lastRideFor(back = false)

    /** "Last trip back" — from the destination to the origin, the same day. */
    fun checkLastRideBack() = lastRideFor(back = true)

    private fun lastRideFor(back: Boolean) {
        val s = _state.value
        val from = resolve(s.from) ?: return
        val to = s.to?.let(::resolve) ?: return
        val at = s.selectedItinerary?.start ?: s.time ?: clock.instant()
        loadLastRide(from, to, at, back, asked = true)
    }

    private fun loadLastRide(from: LatLon, to: LatLon, at: Instant, back: Boolean, asked: Boolean) {
        val day = LastRideFinder.serviceDay(at)
        val a = if (back) to else from
        val b = if (back) from else to
        val key = PlanCache.key("LAST-$day", a, b)
        val prefs = _state.value.settings.preferences()
        val route = _state.value.let { it.from to it.to }
        fun show(lr: LastRide) = _state.update {
            when {
                (it.from to it.to) != route -> it // the user moved on to another route meanwhile
                back -> it.copy(lastRideBack = lr, lastRideLoading = false)
                else -> it.copy(lastRide = lr, lastRideAsked = it.lastRideAsked || asked, lastRideLoading = false)
            }
        }
        lastRides[key]?.let { show(it); return }
        _state.update { it.copy(lastRideLoading = true) }
        viewModelScope.launch {
            try {
                val lr = LastRideFinder(BudgetedTransitApi(api, LastRideFinder.BUDGET))
                    .find(Endpoint.Coord(a), Endpoint.Coord(b), day, prefs, language)
                lastRides[key] = lr
                show(lr)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A hint, not the answer: a failed lookup just leaves it out.
                _state.update { it.copy(lastRideLoading = false) }
            }
        }
    }

    fun clearResults() = _state.update { it.copy(results = null, betterStart = null, dropOff = null, pickUp = null, parkRide = it.parkRide.cleared(), error = null, loading = false) }

    private fun resolve(ref: PlaceRef): LatLon? = when (ref) {
        PlaceRef.MyLocation -> locationProvider()
        is PlaceRef.Point -> ref.at
    }

    // --- stops ----------------------------------------------------------------------------

    fun onViewport(view: BBox, zoom: Double) {
        if (zoom < StopsViewport.MIN_ZOOM) {
            if (loadedStops != null) {
                loadedStops = null
                _stops.value = MapData.EMPTY
            }
            return
        }
        if (!StopsViewport.needsFetch(view, zoom, loadedStops)) return
        val box = StopsViewport.fetchBox(view)
        stopsJob?.cancel()
        stopsJob = viewModelScope.launch {
            try {
                val found = stopsCache?.load(box) { b -> api.stops(b, null, language) } ?: api.stops(box, null, language)
                loadedStops = box
                _stops.value = MapData.stops(found)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Stops are decoration; a failed load just leaves the layer as it was.
            }
        }
    }

    fun openStop(stopId: String, name: String) {
        _state.update { it.copy(stopSheet = StopSheet(stopId, name, loading = true, rows = emptyList(), failed = false)) }
        viewModelScope.launch {
            val resp = try {
                api.stopTimes(stopId, null, 12, language)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val rows = resp?.stopTimes?.map(::departureRow)
            val saved = if (rows == null) dayTimetable.savedBoard(stopId) else null // D7: no signal
            _state.update { st ->
                val sheet = st.stopSheet
                if (sheet?.stopId != stopId) st
                else st.copy(stopSheet = sheet.copy(loading = false, rows = rows ?: saved?.rows.orEmpty(), failed = rows == null && saved == null, platform = resp?.place?.let { p -> stopPlatform(p) } ?: StopPlatform(), saved = saved))
            }
        }
    }

    fun closeStop() = _state.update { it.copy(stopSheet = null) }

    // Phase 10 D7: a line's day timetable at the open stop, 2 `stoptimes` on open, 1 per "More".
    private val dayTimetable = DayTimetableHolder(viewModelScope, api, timetables, { language }, clock)

    fun timetable(action: TimetableAction) = dayTimetable.handle(_state, action)

    // --- saved places, trips, settings ---------------------------------------------------

    fun savePlace(name: String, at: LatLon) = viewModelScope.launch {
        val old = _state.value.savedPlaces.firstOrNull { it.name == name }
        val rest = _state.value.savedPlaces.filterNot { it.name == name }
        store.setPlaces(rest + SavedPlace(name, at.lat, at.lon, routine = old?.routine))
        _state.update { st -> if ((st.to as? PlaceRef.Point)?.at == at) st.copy(to = PlaceRef.Point(name, at)) else st }
    }

    fun deletePlace(p: SavedPlace) = viewModelScope.launch {
        store.setPlaces(_state.value.savedPlaces - p)
    }

    // --- routines -------------------------------------------------------------------------

    /** Set or clear (null) when [place] is usually the destination. */
    fun setRoutine(place: SavedPlace, routine: PlaceRoutine?) = viewModelScope.launch {
        store.setPlaces(_state.value.savedPlaces.map { if (it.name == place.name) it.copy(routine = routine) else it })
    }

    /** "Routine: University ✕": the user does not want it this time. */
    fun dismissRoutine() {
        val s = _state.value
        dismissedRoutine = s.routinePlace?.let { routineKey(it) }
        _state.update { it.copy(to = null, routinePlace = null, results = null, error = null, lastRide = null, lastRideBack = null) }
    }

    private fun routineKey(name: String) = "$name|${LastRideFinder.serviceDay(clock.instant())}"

    /**
     * Open on the routine's destination when its window is on: only in the Trip tab, never
     * over a destination the user chose, at most once per place and day after a dismissal.
     */
    private fun applyRoutine() {
        val s = _state.value
        if (s.mode != AppMode.TRIP || s.editing != null || s.loading || tripHomeAsked) return
        val routineSet = s.activeRoutine != null
        if (s.to != null && !routineSet) return
        val p = Routines.active(s.savedPlaces, clock.instant()) ?: return
        if (routineKey(p.name) == dismissedRoutine) return
        if (routineSet && s.routinePlace == p.name && s.results != null) return // already showing it
        _state.update { it.copy(from = PlaceRef.MyLocation, to = PlaceRef.Point(p.name, p.latLon), routinePlace = p.name, timeMode = TimeMode.NOW, time = null) }
        // Location may not be ready at launch; the search then waits for the user's tap.
        if (locationProvider() != null) plan()
    }

    // --- errands and car -------------------------------------------------------------------

    fun removeChainStop(index: Int) {
        _state.update { it.copy(chainStops = it.chainStops.filterIndexed { i, _ -> i != index }, chain = null) }
        if (_state.value.readyToPlan) plan()
    }

    fun setStay(index: Int, minutes: Int) {
        _state.update { it.copy(chainStops = it.chainStops.mapIndexed { i, c -> if (i == index) c.copy(stayMin = minutes) else c }) }
        if (_state.value.readyToPlan) plan()
    }

    /** "🚗 By car?": the same trip by car at the selected option's time; one request. */
    fun checkCar() {
        val s = _state.value
        val from = resolve(s.from) ?: return
        val to = s.to?.let(::resolve) ?: return
        val at = s.selectedItinerary?.start ?: s.time ?: clock.instant()
        val route = s.from to s.to
        _state.update { it.copy(carLoading = true) }
        viewModelScope.launch {
            val car = try {
                CarCompare(BudgetedTransitApi(api, CarCompare.BUDGET)).drive(from, to, at, s.settings.traffic())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            // The user may have searched another route meanwhile; its car answer is not this one.
            _state.update { if ((it.from to it.to) != route) it else it.copy(carTime = car, carLoading = false) }
        }
    }

    /** An app-icon shortcut: the trip to the saved place called [name], if it still exists. */
    fun goToPlaceNamed(name: String) {
        _state.value.savedPlaces.firstOrNull { it.name == name }?.let(::goTo)
    }

    fun goTo(p: SavedPlace) {
        _state.update { it.copy(to = PlaceRef.Point(p.name, p.latLon), editing = null, results = null, betterStart = null, dropOff = null, pickUp = null, parkRide = it.parkRide.cleared()) }
        plan()
    }

    /** True while [openTripHome] runs: the user asked for home, so [applyRoutine] stays out of it. */
    private var tripHomeAsked = false

    /**
     * "Next trip home" (Quick Settings tile, Phase 9 C4; C5's alert reuses it): the Trip tab
     * from here to Home, NOW, first option selected. One `plan` (no last-ride hint). No fix
     * within [FIX_WAIT_MS] (permission denied, cold start) → from the cached trip's origin;
     * offline → that cached trip, under the usual banner. No Home → Settings. The decision is
     * core's `CacheLookup.start` (tested); this only applies it.
     */
    fun openTripHome() = viewModelScope.launch {
        tripHomeAsked = true // a routine opening at the same cold start must not plan too
        try {
            val places = store.places.first()
            val home = il.transit.core.user.Home.of(store.settings.first(), places)
            if (home == null) {
                _state.update { it.copy(showSettings = true) }
                return@launch
            }
            // Home shows at once, with the spinner, while the first fix comes in.
            _state.update {
                it.copy(
                    mode = AppMode.TRIP, to = PlaceRef.Point(home.name, home.latLon), timeMode = TimeMode.NOW, time = null,
                    chainStops = emptyList(), editing = null, showSettings = false, showHistory = false, stopSheet = null,
                    results = null, error = null, loading = true,
                )
            }
            val fix = kotlinx.coroutines.withTimeoutOrNull(FIX_WAIT_MS) {
                while (locationProvider() == null) delay(250)
                locationProvider()
            }
            val cached = planCache?.entries()?.let { il.transit.core.plan.CacheLookup.tripHome(it, home.latLon, clock.instant()) }
            val from = when (val start = il.transit.core.plan.CacheLookup.start(true, fix, cached)) {
                is il.transit.core.plan.CacheLookup.Start.FromCache -> {
                    val near = places.firstOrNull { il.transit.core.geo.Geo.distanceM(it.latLon, start.at) <= il.transit.core.plan.CacheLookup.HOME_RADIUS_M }
                    PlaceRef.Point(near?.name, start.at)
                }
                else -> PlaceRef.MyLocation // with no fix this ends in the usual "no location" error, not a spinner
            }
            _state.update { it.copy(from = from, loading = false) }
            plan(lastRideHint = false)
            planJob?.join()
            // Offline from a spot a little off the cached origin: the key missed, the trip home did not.
            val next = cached?.takeIf { c -> c.next != null }
            if (_state.value.error == UiError.NETWORK && next != null) {
                _state.update {
                    it.copy(error = null, results = next.entry.value, offlineSince = next.entry.savedAt, offlineNight = next.entry.isLastNight(clock.instant()), resultsAt = next.entry.savedAt, selected = 0)
                }
            }
        } finally {
            // Home is now the destination, so a routine would not replace it anyway; later launches get routines back.
            tripHomeAsked = false
        }
    }

    /** Saves the current from/to as a one-tap trip. "My location" stays "my location". */
    fun saveTrip(name: String) = viewModelScope.launch {
        val s = _state.value
        val to = s.to as? PlaceRef.Point ?: return@launch
        val from = (s.from as? PlaceRef.Point)?.let { SavedPlace(it.name ?: name, it.at.lat, it.at.lon) }
        val trip = SavedTrip(name, from, SavedPlace(to.name ?: name, to.at.lat, to.at.lon))
        store.setTrips(s.savedTrips.filterNot { it.name == name } + trip)
    }

    fun deleteTrip(t: SavedTrip) = viewModelScope.launch {
        store.setTrips(_state.value.savedTrips - t)
    }

    fun runTrip(t: SavedTrip) {
        _state.update {
            it.copy(
                mode = AppMode.TRIP,
                from = t.from?.let { f -> PlaceRef.Point(f.name, f.latLon) } ?: PlaceRef.MyLocation,
                to = PlaceRef.Point(t.to.name, t.to.latLon),
                timeMode = TimeMode.NOW,
                time = null,
                editing = null,
            )
        }
        plan()
    }

    fun showSettings(show: Boolean) = _state.update { it.copy(showSettings = show) }

    fun reminderCheck(view: il.transit.planner.ui.screens.ReminderCheckView) = _state.update { it.copy(reminderCheck = view) }

    fun updateSettings(s: UserSettings) = viewModelScope.launch { store.setSettings(s) }

    /** Re-order the options in every tab (D12); the opened option stays opened ([resorted]). */
    fun setTripSort(sort: TripSort) {
        _state.update { it.resorted(sort) }
        updateSettings(_state.value.settings)
    }

    // --- helpers -----------------------------------------------------------------------------

    private fun localHits(text: String): List<LocalHit> = _state.value.let { s ->
        LocalFirst.suggest(text, s.savedPlaces, s.settings.homePlace, s.places.recents)
    }

    /** Home, saved places, recent picks that match [text]: shown before any request answers. */
    private fun localSuggestions(text: String): List<Suggestion> = localHits(text).map { toSuggestion(it) }

    private fun toSuggestion(h: LocalHit) = Suggestion(
        h.place.name, null, h.place.latLon,
        saved = h.kind != LocalHit.Kind.RECENT, isStop = false,
        home = h.kind == LocalHit.Kind.HOME, recent = h.kind == LocalHit.Kind.RECENT,
    )

    private fun toSuggestion(m: GeocodeMatch): Suggestion {
        return Suggestion(m.name, geocodeDetail(m), LatLon(m.lat, m.lon), saved = false, isStop = m.type == "STOP", stopId = m.id.takeIf { m.category == StopCode.CATEGORY })
    }

    // --- process death (Phase 10 D11) -----------------------------------------------------------

    /** The Trip cache entry the shown options were saved under; restoring reads it, 0 requests. */
    private var shownKey: String? = null

    /** The open trip sheet's leg in the selected option (the Activity tells, [rideSheet]). */
    private var rideLeg: Int? = null

    /** A trip sheet that was open before the process died, waiting for its option to come back. */
    private var restoreRide: Int? = null

    /** While the saved search is being restored, the half-restored state is not saved over it. */
    private var restoring = false
    private var lastSaved: SavedUi? = null

    /** The Activity reports the open trip sheet: its leg index in the selected option, or null. */
    fun rideSheet(legIndex: Int?) {
        if (legIndex == rideLeg) return
        rideLeg = legIndex
        if (legIndex != null) restoreRide = null
        persist()
    }

    /** The trip sheet to open again after a restore, once (null = none). */
    fun takeRestoredRide(): Int? = restoreRide.also { if (_state.value.selectedItinerary != null) restoreRide = null }

    private fun persist() {
        val h = saved ?: return
        if (restoring) return
        val now = SavedUi.of(_state.value, shownKey, rideLeg ?: restoreRide, optionId(_state.value.selectedItinerary))
        if (now == lastSaved) return
        lastSaved = now
        now.write { k, v -> h[k] = v }
    }

    /**
     * Back from process death: the search fields at once; the options from the Trip cache (never a
     * new search), the chosen one found again by its times; the open sheet after them.
     */
    private fun restore(r: SavedUi) {
        restoring = true
        _state.update { it.copy(mode = r.mode, from = r.from, to = r.to, driverTo = r.driverTo, timeMode = r.timeMode, time = r.time) }
        (r.sheet as? SavedUi.Sheet.Stop)?.let { openStop(it.stopId, it.name) }
        restoreRide = (r.sheet as? SavedUi.Sheet.Ride)?.legIndex
        val key = r.cacheKey
        if (key == null || planCache == null) {
            restoreRide = null
            restoring = false
            persist()
            return
        }
        viewModelScope.launch {
            val entry = try { planCache.get(key, clock.instant()) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            // The sort order is a setting: wait for the stored one before finding the option again.
            val loaded = _state.first { it.settingsLoaded }
            if (entry != null && loaded.results == null && !loaded.loading && loaded.to == r.to) {
                shownKey = key
                _state.update {
                    val sorted = sortOptions(entry.value, it.settings.tripSort, it.timeMode == TimeMode.ARRIVE_BY)
                    val pick = sorted.indexOfFirst { o -> optionId(o) == r.optionId }.takeIf { i -> i >= 0 }
                        ?: r.selected.coerceAtMost((sorted.size - 1).coerceAtLeast(0))
                    it.copy(results = entry.value, resultsAt = entry.savedAt, selected = pick)
                }
            } else {
                restoreRide = null
            }
            restoring = false
            persist()
        }
    }

    init {
        // Last in the class: openStop and the checks it uses must be initialised first.
        saved?.let { h -> SavedUi.read { k -> h.get<Any?>(k) } }?.let(::restore)
        if (saved != null) viewModelScope.launch { _state.collect { persist() } }
    }

    companion object {
        private const val REFRESH_MS = 120_000L

        /** The selected option, recognised again in a re-read list: its times and leg count. */
        internal fun optionId(it: Itinerary?): String? = it?.let { o -> "${o.start.epochSecond}-${o.end.epochSecond}-${o.legs.size}" }

        /** How long [openTripHome] waits for a first fix (a cold start loads the map first) before using the cached origin. */
        private const val FIX_WAIT_MS = 5_000L

        fun factory(app: TransitApp) = viewModelFactory {
            initializer {
                val saved = createSavedStateHandle()
                // Phase 10 D8: monthly report files live a day in cacheDir/reports/.
                Thread { il.transit.planner.ui.screens.ReportFiles.prune(app.cacheDir) }.start()
                MainViewModel(app.api, app.store, { app.language }, app.planCache, app.reminders, app.rides, app.history, app.updates, app.stopsCache, app.departureCache, app.clock, app.backupGeocoder, app.timetables, app.weather, saved)
            }
        }
    }
}
