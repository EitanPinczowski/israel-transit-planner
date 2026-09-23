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
import il.transit.core.plan.PlanCache
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripQuery
import il.transit.core.plan.TripResult
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
import il.transit.planner.data.HistoryStore
import il.transit.planner.TransitApp
import il.transit.planner.data.PlanCacheStore
import il.transit.planner.data.UserStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** Where a trip starts or ends. */
sealed interface PlaceRef {
    data object MyLocation : PlaceRef

    /** [name] is null until reverse geocoding names a dropped pin. */
    data class Point(val name: String?, val at: LatLon) : PlaceRef
}

/** DRIVER_TO is the drop-off tab's third field: where the car is going (B). */
enum class Field { FROM, TO, DRIVER_TO }

/** The tabs on top of the search card. */
enum class AppMode { TRIP, BETTER_START, DROP_OFF, PICK_UP }

enum class UiError { NO_LOCATION, NETWORK, NO_RESULTS }

data class Suggestion(val name: String, val detail: String?, val at: LatLon, val saved: Boolean, val isStop: Boolean)

data class StopSheet(val stopId: String, val name: String, val loading: Boolean, val rows: List<DepartureRow>, val failed: Boolean)

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
    val from: PlaceRef = PlaceRef.MyLocation,
    val to: PlaceRef? = null,
    val editing: Field? = null,
    val query: String = "",
    val suggestions: List<Suggestion> = emptyList(),
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
    val reminder: Reminder? = null,
    /** A "get off at the next stop" ride is being tracked. */
    val riding: Boolean = false,
    val history: List<TripRecord> = emptyList(),
    val showHistory: Boolean = false,
) {
    val historyStats: HistoryStats get() = History.stats(history, Instant.now())

    /** The itineraries the results panel lists, for whichever tab is showing. */
    val options: List<Itinerary>
        get() = when (mode) {
            AppMode.TRIP -> results?.let { it.itineraries + listOfNotNull(it.walkOnly) }.orEmpty()
            AppMode.BETTER_START -> betterStart?.options?.map { it.payload.itinerary }.orEmpty()
            AppMode.DROP_OFF -> dropOff?.options?.map { it.payload.transit }.orEmpty()
            AppMode.PICK_UP -> pickUp?.options?.map { it.payload.itinerary }.orEmpty()
        }

    val hasResults: Boolean get() = results != null || betterStart != null || dropOff != null || pickUp != null

    /** Everything the current tab needs before it can search. */
    val readyToPlan: Boolean get() = to != null && (mode != AppMode.DROP_OFF || driverTo != null)

    val selectedItinerary: Itinerary? get() = options.getOrNull(selected)

    /** The car part to draw before [selectedItinerary] (drop-off tab only). */
    val selectedCarPath: List<LatLon>
        get() = if (mode == AppMode.DROP_OFF) dropOff?.options?.getOrNull(selected)?.payload?.carPath.orEmpty() else emptyList()
}

class MainViewModel(
    private val api: TransitApi,
    private val store: UserStore,
    private val language: String,
    private val planCache: PlanCacheStore? = null,
    private val reminders: Reminders? = null,
    private val rides: Rides? = null,
    private val historyStore: HistoryStore? = null,
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

    init {
        viewModelScope.launch {
            combine(store.settings, store.places, store.trips) { s, p, t -> Triple(s, p, t) }.collect { (s, p, t) ->
                _state.update { it.copy(settings = s, savedPlaces = p, savedTrips = t) }
            }
        }
        viewModelScope.launch { store.reminder.collect { r -> _state.update { it.copy(reminder = r) } } }
        rides?.let { r -> viewModelScope.launch { r.active.collect { a -> _state.update { it.copy(riding = a) } } } }
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
        _state.update { it.copy(editing = field, query = "", suggestions = savedSuggestions("")) }
    }

    fun cancelEditing() = _state.update { it.copy(editing = null, query = "", suggestions = emptyList()) }

    fun onQuery(text: String) {
        _state.update { it.copy(query = text, suggestions = savedSuggestions(text)) }
        searchJob?.cancel()
        val q = text.trim()
        if (q.length < 2) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val found = try {
                api.geocode(q, language, locationProvider(), 8)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            _state.update { st -> st.copy(suggestions = savedSuggestions(q) + found.map(::toSuggestion)) }
        }
    }

    fun pick(s: Suggestion) = setField(PlaceRef.Point(s.name, s.at))

    fun pickMyLocation() = setField(PlaceRef.MyLocation)

    private fun setField(ref: PlaceRef) {
        val field = _state.value.editing ?: Field.TO
        _state.update {
            val next = when (field) {
                Field.FROM -> it.copy(from = ref)
                Field.TO -> it.copy(to = ref)
                Field.DRIVER_TO -> it.copy(driverTo = ref)
            }
            next.copy(editing = null, query = "", suggestions = emptyList(), results = null, betterStart = null, dropOff = null, pickUp = null)
        }
        if (_state.value.readyToPlan) plan()
    }

    /** Long-press on the map: that point becomes the destination, named once reverse geocoding answers. */
    fun setDestinationFromMap(at: LatLon) {
        _state.update { it.copy(to = PlaceRef.Point(null, at), editing = null, results = null, betterStart = null, dropOff = null, pickUp = null, stopSheet = null) }
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

    fun swap() {
        val s = _state.value
        val to = s.to ?: return
        _state.update { it.copy(from = to, to = s.from, results = null, betterStart = null, dropOff = null, pickUp = null) }
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
            it.copy(mode = mode, timeMode = time, results = null, betterStart = null, dropOff = null, pickUp = null, error = null, selected = 0)
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

    fun setMaxDetour(min: Int) {
        if (min == _state.value.maxDetourMin) return
        _state.update { it.copy(maxDetourMin = min) }
        if (_state.value.readyToPlan) plan()
    }

    // --- planning -----------------------------------------------------------------------

    /** [quiet]: a background refresh — keep the current results on screen and ignore failures. */
    fun plan(quiet: Boolean = false) {
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
                    loading = true, error = null, results = null, betterStart = null, dropOff = null, pickUp = null,
                    selected = 0, stopSheet = null, offlineSince = null,
                )
            }
        }
        val cacheKey = PlanCache.key("TRIP-${s.timeMode}", from, to)
        planJob = viewModelScope.launch {
            try {
                when (s.mode) {
                    AppMode.TRIP -> {
                        val r = planner.plan(
                            TripQuery(Endpoint.Coord(from), Endpoint.Coord(to), s.timeMode, s.time, s.settings, language),
                        )
                        val empty = r.itineraries.isEmpty() && r.walkOnly == null
                        _state.update {
                            it.copy(
                                loading = false,
                                results = r,
                                resultsAt = Instant.now(),
                                offlineSince = null,
                                // A refresh keeps the user's selection when it still exists.
                                selected = if (quiet) it.selected.coerceAtMost((r.itineraries.size - 1).coerceAtLeast(0)) else 0,
                                error = if (empty) UiError.NO_RESULTS else null,
                            )
                        }
                        if (!empty) planCache?.put(cacheKey, r)
                    }
                    AppMode.BETTER_START -> {
                        // A fresh budget per search: a pruning bug becomes an exception, not a flood.
                        val budgeted = BudgetedTransitApi(api, BetterStartPlanner.BUDGET)
                        val r = BetterStartPlanner(budgeted, s.settings.traffic()).plan(
                            BetterStartQuery(
                                origin = from,
                                dest = to,
                                departAt = s.time?.takeIf { s.timeMode == TimeMode.DEPART_AT } ?: Instant.now(),
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
                                departAt = s.time?.takeIf { s.timeMode == TimeMode.DEPART_AT } ?: Instant.now(),
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
                                departAt = s.time?.takeIf { s.timeMode == TimeMode.DEPART_AT } ?: Instant.now(),
                                maxDriveMin = s.maxPickUpDriveMin,
                                preferences = s.settings.preferences(),
                                language = language,
                            ),
                        )
                        val empty = r.options.isEmpty() && r.baseline == null
                        _state.update { it.copy(loading = false, pickUp = r, error = if (empty) UiError.NO_RESULTS else null) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (quiet) return@launch
                // No signal: show what this trip looked like last time, clearly marked.
                val cached = if (s.mode == AppMode.TRIP) planCache?.get(cacheKey) else null
                _state.update {
                    if (cached != null) {
                        it.copy(loading = false, results = cached.value, offlineSince = cached.savedAt, resultsAt = cached.savedAt)
                    } else {
                        it.copy(loading = false, error = UiError.NETWORK)
                    }
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
        val r = Reminder.from(itin, from, to, s.settings) ?: return false
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
        rides?.start(itin)
        val record = TripRecord.from(itin, nameOf(s.from), s.to?.let(::nameOf).orEmpty(), s.mode.name, Instant.now(), savedMinOfSelected(s))
        viewModelScope.launch { historyStore?.add(record) }
        return true
    }

    fun stopRide() {
        rides?.stop()
    }

    fun showHistory(show: Boolean) = _state.update { it.copy(showHistory = show) }

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

    fun clearResults() = _state.update { it.copy(results = null, betterStart = null, dropOff = null, pickUp = null, error = null, loading = false) }

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
                val found = api.stops(box, null, language)
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
            val rows = try {
                api.stopTimes(stopId, null, 12, language).stopTimes.map(::departureRow)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.update { st ->
                val sheet = st.stopSheet
                if (sheet?.stopId != stopId) st
                else st.copy(stopSheet = sheet.copy(loading = false, rows = rows.orEmpty(), failed = rows == null))
            }
        }
    }

    fun closeStop() = _state.update { it.copy(stopSheet = null) }

    // --- saved places, trips, settings ---------------------------------------------------

    fun savePlace(name: String, at: LatLon) = viewModelScope.launch {
        val rest = _state.value.savedPlaces.filterNot { it.name == name }
        store.setPlaces(rest + SavedPlace(name, at.lat, at.lon))
        _state.update { st -> if ((st.to as? PlaceRef.Point)?.at == at) st.copy(to = PlaceRef.Point(name, at)) else st }
    }

    fun deletePlace(p: SavedPlace) = viewModelScope.launch {
        store.setPlaces(_state.value.savedPlaces - p)
    }

    fun goTo(p: SavedPlace) {
        _state.update { it.copy(to = PlaceRef.Point(p.name, p.latLon), editing = null, results = null, betterStart = null, dropOff = null, pickUp = null) }
        plan()
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

    // --- helpers -----------------------------------------------------------------------------

    private fun savedSuggestions(text: String): List<Suggestion> =
        _state.value.savedPlaces
            .filter { text.isBlank() || it.name.contains(text.trim(), ignoreCase = true) }
            .map { Suggestion(it.name, null, it.latLon, saved = true, isStop = false) }

    private fun toSuggestion(m: GeocodeMatch): Suggestion {
        val street = m.street?.let { s -> listOfNotNull(s, m.houseNumber).joinToString(" ") }
        return Suggestion(m.name, street?.takeIf { it != m.name }, LatLon(m.lat, m.lon), saved = false, isStop = m.type == "STOP")
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 350L
        private const val REFRESH_MS = 120_000L

        fun factory(app: TransitApp) = viewModelFactory {
            initializer {
                MainViewModel(app.api, app.store, app.language, app.planCache, app.reminders, app.rides, app.history)
            }
        }
    }
}
