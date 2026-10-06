package il.transit.planner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import il.transit.core.present.StopPlatform
import il.transit.core.present.stopPlatform
import il.transit.core.present.needsFullNameHint
import il.transit.core.present.geocodeDetail
import il.transit.core.present.rankByTypedTown
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

data class Suggestion(val name: String, val detail: String?, val at: LatLon, val saved: Boolean, val isStop: Boolean)

data class StopSheet(
    val stopId: String,
    val name: String,
    val loading: Boolean,
    val rows: List<DepartureRow>,
    val failed: Boolean,
    /** "Platform 12 · stop 47899" for the header, once the departures answer names the stop. */
    val platform: StopPlatform = StopPlatform(),
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
    val settings: UserSettings = UserSettings(),
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
) {
    val historyStats: HistoryStats get() = History.stats(history, Instant.now())

    /** The routine's place, while the destination is still the one the routine filled in. */
    val activeRoutine: String? get() = routinePlace?.takeIf { (to as? PlaceRef.Point)?.name == it }

    /** The itineraries the results panel lists, for whichever tab is showing. */
    val options: List<Itinerary>
        get() = when (mode) {
            AppMode.TRIP -> chain?.legs ?: results?.let { sortOptions(it.itineraries, settings.tripSort) + listOfNotNull(it.walkOnly) }.orEmpty()
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
    private val language: String,
    private val planCache: PlanCacheStore? = null,
    private val reminders: Reminders? = null,
    private val rides: Rides? = null,
    private val historyStore: HistoryStore? = null,
    private val updates: UpdateChecker? = null,
    private val stopsCache: StopsStore? = null,
    private val departureCache: il.transit.planner.data.DepartureCacheStore? = null,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _stops = MutableStateFlow(MapData.EMPTY)

    /** GeoJSON for the stops layer; empty below [StopsViewport.MIN_ZOOM]. */
    val stops: StateFlow<String> = _stops.asStateFlow()

    /** Set by the Activity once the map's location component is live. */
    var locationProvider: () -> LatLon? = { null }

    private val planner = TripPlanner(api)
    private var searchJob: Job? = null
    private var planJob: Job? = null
    private var stopsJob: Job? = null
    private var loadedStops: BBox? = null

    /** A routine the user dismissed today ("name|day"), so it does not come straight back. */
    private var dismissedRoutine: String? = null

    init {
        viewModelScope.launch {
            combine(store.settings, store.places, store.trips) { s, p, t -> Triple(s, p, t) }.collect { (s, p, t) ->
                val first = _state.value.savedPlaces.isEmpty() && p.isNotEmpty()
                _state.update { it.copy(settings = s, savedPlaces = p, savedTrips = t) }
                if (first) applyRoutine()
            }
        }
        viewModelScope.launch { store.reminder.collect { r -> _state.update { it.copy(reminder = r) } } }
        rides?.let { r -> viewModelScope.launch { r.active.collect { a -> _state.update { it.copy(riding = a) } } } }
        rides?.let { r -> viewModelScope.launch { r.progress.collect { p -> _state.update { it.copy(rideProgress = p) } } } }
        viewModelScope.launch { store.favorites.collect { f -> _state.update { it.copy(favorites = f) } } }
        updates?.let { u -> viewModelScope.launch { u.check()?.let { latest -> _state.update { it.copy(update = latest) } } } }
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
        _state.update { it.copy(editing = field, query = "", suggestions = savedSuggestions(""), searchHint = false) }
    }

    fun cancelEditing() = _state.update { it.copy(editing = null, query = "", suggestions = emptyList(), searchHint = false) }

    fun onQuery(text: String) {
        _state.update { it.copy(query = text, suggestions = savedSuggestions(text), searchHint = false) }
        searchJob?.cancel()
        val q = text.trim()
        if (q.length < 2) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val found = try {
                rankByTypedTown(api.geocode(q, language, locationProvider(), 8))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            _state.update { st ->
                st.copy(
                    suggestions = savedSuggestions(q) + found.map(::toSuggestion),
                    searchHint = needsFullNameHint(q, found.map { it.name }),
                )
            }
        }
    }

    fun pick(s: Suggestion) = setField(PlaceRef.Point(s.name, s.at))

    fun pickMyLocation() = setField(PlaceRef.MyLocation)

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

    /** Long-press on the map: that point becomes the destination, named once reverse geocoding answers. */
    fun setDestinationFromMap(at: LatLon) {
        _state.update { it.copy(to = PlaceRef.Point(null, at), editing = null, results = null, betterStart = null, dropOff = null, pickUp = null, parkRide = it.parkRide.cleared(), stopSheet = null) }
        plan()
        viewModelScope.launch {
            val name = try {
                api.reverseGeocode(at, language, 1).firstOrNull()?.name
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (name != null) {
                _state.update { st -> if ((st.to as? PlaceRef.Point)?.at == at) st.copy(to = PlaceRef.Point(name, at)) else st }
            }
        }
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
                timed.copy(editing = Field.TO, query = place, suggestions = savedSuggestions(place), searchHint = false)
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
        if (_state.value.readyToPlan) plan()
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
                                    reselect(it.selectedItinerary, sortOptions(r.itineraries, it.settings.tripSort) + listOfNotNull(r.walkOnly), it.selected)
                                } else {
                                    0
                                },
                                error = if (empty) UiError.NO_RESULTS else null,
                            )
                        }
                        if (!empty) planCache?.put(cacheKey, r)
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
                        _state.update { it.copy(loading = false, betterStart = r, error = if (empty) UiError.NO_RESULTS else null) }
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
                        _state.update { it.copy(loading = false, dropOff = r, error = if (r.options.isEmpty()) UiError.NO_RESULTS else null) }
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
                        _state.update { it.copy(loading = false, pickUp = r, error = if (empty) UiError.NO_RESULTS else null) }
                    }
                    AppMode.PARK_RIDE -> {
                        val r = planParkRide(api, s, from, to, language)
                        val empty = r.options.isEmpty() && r.baseline == null
                        _state.update { it.copy(loading = false, parkRide = it.parkRide.copy(result = r, origin = from), error = if (empty) UiError.NO_RESULTS else null) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (quiet) return@launch
                // No signal: show what this trip looked like last time, clearly marked.
                // (An errand chain is not cached: its saved copy would be another trip.)
                val cached = if (s.mode == AppMode.TRIP && s.chainStops.isEmpty()) planCache?.get(cacheKey) else null
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
        val q = tripQuery ?: return
        if (s.mode != AppMode.TRIP || s.chain != null || s.loading || s.pages.loading != null) return
        _state.update { it.copy(pages = TripPagesUi(loading = direction)) }
        viewModelScope.launch {
            val merged = try {
                planner.page(q, current, direction)
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
                        selected = reselect(it.selectedItinerary, sortOptions(o.result.itineraries, it.settings.tripSort) + listOfNotNull(o.result.walkOnly), it.selected),
                        pages = TripPagesUi(),
                    )
                }
            }
        }
    }

    // --- "time to leave" reminder ----------------------------------------------------------

    /** Arms a reminder for the selected Trip itinerary. False if it has no transit leg. */
    fun remindSelected(): Boolean {
        val s = _state.value
        val itin = s.selectedItinerary ?: return false
        val from = resolve(s.from) ?: return false
        val to = s.to?.let(::resolve) ?: return false
        // An errand leg is re-checked on its own stops; the chain's A → B would never find its bus.
        val r = (if (s.chain != null) Reminder.forOwnEndpoints(itin, s.settings) else Reminder.from(itin, from, to, s.settings))
            ?: return false
        viewModelScope.launch { store.setReminder(r) }
        reminders?.schedule(r)
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

    fun dismissUpdate() = _state.update { it.copy(update = null) }

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

    fun select(index: Int) = _state.update { it.copy(selected = index) }

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
            _state.update { st ->
                val sheet = st.stopSheet
                if (sheet?.stopId != stopId) st
                else st.copy(stopSheet = sheet.copy(loading = false, rows = rows.orEmpty(), failed = rows == null, platform = resp?.place?.let { p -> stopPlatform(p) } ?: StopPlatform()))
            }
        }
    }

    fun closeStop() = _state.update { it.copy(stopSheet = null) }

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

    /** Set by [openTripHome]: the user asked for home, so [applyRoutine] stays out of it. */
    private var tripHomeAsked = false

    /**
     * "Next trip home" (Quick Settings tile, Phase 9 C4; C5's alert reuses it): the Trip tab
     * from here to Home, NOW, first option selected. One `plan` (no last-ride hint). No fix
     * within a few seconds (permission denied, cold start) → from the cached trip's origin;
     * offline → that cached trip, under the usual banner. No Home → Settings.
     */
    fun openTripHome() = viewModelScope.launch {
        tripHomeAsked = true // a routine opening at the same cold start must not plan too
        val settings = store.settings.first()
        val places = store.places.first()
        val home = il.transit.core.user.Home.of(settings, places)
        if (home == null) {
            _state.update { it.copy(showSettings = true) }
            return@launch
        }
        val fix = kotlinx.coroutines.withTimeoutOrNull(FIX_WAIT_MS) {
            while (locationProvider() == null) delay(250)
            locationProvider()
        }
        val cached = planCache?.entries()?.let { il.transit.core.plan.CacheLookup.tripHome(it, home.latLon, clock.instant()) }
        val from = if (fix == null && cached != null) {
            val near = places.firstOrNull { il.transit.core.geo.Geo.distanceM(it.latLon, cached.from) <= il.transit.core.plan.CacheLookup.HOME_RADIUS_M }
            PlaceRef.Point(near?.name, cached.from)
        } else {
            PlaceRef.MyLocation
        }
        _state.update {
            it.copy(
                mode = AppMode.TRIP, from = from, to = PlaceRef.Point(home.name, home.latLon),
                timeMode = TimeMode.NOW, time = null, chainStops = emptyList(), editing = null,
                showSettings = false, showHistory = false, stopSheet = null,
            )
        }
        plan(lastRideHint = false)
        planJob?.join()
        // Offline from a spot a little off the cached origin: the key missed, the trip home did not.
        val next = cached?.takeIf { c -> c.next != null }
        if (_state.value.error == UiError.NETWORK && next != null) {
            _state.update {
                it.copy(error = null, results = next.entry.value, offlineSince = next.entry.savedAt, offlineNight = next.entry.isLastNight(clock.instant()), resultsAt = next.entry.savedAt, selected = 0)
            }
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

    fun updateSettings(s: UserSettings) = viewModelScope.launch { store.setSettings(s) }

    /** Re-order the Trip tab's options; the first of the new order is selected. */
    fun setTripSort(sort: TripSort) {
        _state.update { it.copy(selected = 0, settings = it.settings.copy(tripSort = sort)) }
        updateSettings(_state.value.settings)
    }

    // --- helpers -----------------------------------------------------------------------------

    private fun savedSuggestions(text: String): List<Suggestion> =
        _state.value.savedPlaces
            .filter { text.isBlank() || it.name.contains(text.trim(), ignoreCase = true) }
            .map { Suggestion(it.name, null, it.latLon, saved = true, isStop = false) }

    private fun toSuggestion(m: GeocodeMatch): Suggestion {
        return Suggestion(m.name, geocodeDetail(m), LatLon(m.lat, m.lon), saved = false, isStop = m.type == "STOP")
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 350L
        private const val REFRESH_MS = 120_000L

        /** How long [openTripHome] waits for a first location fix before using the cached origin. */
        private const val FIX_WAIT_MS = 3_000L

        fun factory(app: TransitApp) = viewModelFactory {
            initializer {
                MainViewModel(app.api, app.store, app.language, app.planCache, app.reminders, app.rides, app.history, app.updates, app.stopsCache, app.departureCache, app.clock)
            }
        }
    }
}
