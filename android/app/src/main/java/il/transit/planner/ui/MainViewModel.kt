package il.transit.planner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import il.transit.core.api.Endpoint
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.Itinerary
import il.transit.core.api.TransitApi
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import il.transit.core.geo.MapData
import il.transit.core.geo.StopsViewport
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripQuery
import il.transit.core.plan.TripResult
import il.transit.core.present.DepartureRow
import il.transit.core.present.departureRow
import il.transit.core.user.SavedPlace
import il.transit.core.user.SavedTrip
import il.transit.core.user.UserSettings
import il.transit.planner.TransitApp
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

enum class Field { FROM, TO }

enum class UiError { NO_LOCATION, NETWORK, NO_RESULTS }

data class Suggestion(val name: String, val detail: String?, val at: LatLon, val saved: Boolean, val isStop: Boolean)

data class StopSheet(val stopId: String, val name: String, val loading: Boolean, val rows: List<DepartureRow>, val failed: Boolean)

data class UiState(
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
) {
    /** Transit options, then walking-only if offered — the list the results panel shows. */
    val options: List<Itinerary>
        get() = results?.let { it.itineraries + listOfNotNull(it.walkOnly) }.orEmpty()

    val selectedItinerary: Itinerary? get() = options.getOrNull(selected)
}

class MainViewModel(
    private val api: TransitApi,
    private val store: UserStore,
    private val language: String,
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
            val next = if (field == Field.FROM) it.copy(from = ref) else it.copy(to = ref)
            next.copy(editing = null, query = "", suggestions = emptyList(), results = null)
        }
        if (_state.value.to != null) plan()
    }

    /** Long-press on the map: that point becomes the destination, named once reverse geocoding answers. */
    fun setDestinationFromMap(at: LatLon) {
        _state.update { it.copy(to = PlaceRef.Point(null, at), editing = null, results = null, stopSheet = null) }
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
        _state.update { it.copy(from = to, to = s.from, results = null) }
        plan()
    }

    fun setTime(mode: TimeMode, time: Instant?) {
        _state.update { it.copy(timeMode = mode, time = if (mode == TimeMode.NOW) null else time) }
        if (_state.value.to != null) plan()
    }

    // --- planning -----------------------------------------------------------------------

    fun plan() {
        val s = _state.value
        val toRef = s.to ?: return
        val from = resolve(s.from)
        val to = resolve(toRef)
        if (from == null || to == null) {
            _state.update { it.copy(error = UiError.NO_LOCATION, loading = false) }
            return
        }
        planJob?.cancel()
        _state.update { it.copy(loading = true, error = null, results = null, selected = 0, stopSheet = null) }
        planJob = viewModelScope.launch {
            try {
                val r = planner.plan(
                    TripQuery(Endpoint.Coord(from), Endpoint.Coord(to), s.timeMode, s.time, s.settings, language),
                )
                val empty = r.itineraries.isEmpty() && r.walkOnly == null
                _state.update { it.copy(loading = false, results = r, error = if (empty) UiError.NO_RESULTS else null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = UiError.NETWORK) }
            }
        }
    }

    fun select(index: Int) = _state.update { it.copy(selected = index) }

    fun clearResults() = _state.update { it.copy(results = null, error = null, loading = false) }

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
        _state.update { it.copy(to = PlaceRef.Point(p.name, p.latLon), editing = null, results = null) }
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

        fun factory(app: TransitApp) = viewModelFactory {
            initializer { MainViewModel(app.api, app.store, app.language) }
        }
    }
}
