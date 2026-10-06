package il.transit.planner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import il.transit.core.api.Leg
import il.transit.core.api.TransitApi
import il.transit.core.present.TripDetails
import il.transit.core.present.TripDetailsSession
import il.transit.core.present.VehicleMark
import il.transit.planner.TransitApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/** The open trip sheet. [details] is null while the `trip` request runs. */
data class TripSheet(
    val leg: Leg,
    val details: TripDetails?,
    val vehicle: VehicleMark? = null,
    /** The `trip` request failed: [details] shows the leg's own stops only. */
    val legOnly: Boolean = false,
)

/**
 * The trip-details sheet (Phase 8 B1). All traffic decisions live in core's
 * [TripDetailsSession] (one `trip` per tap, one `map/trips` per 30 s while in front); this
 * class only ticks it and exposes state.
 */
class TripDetailsViewModel(private val api: TransitApi, private val language: () -> String) : ViewModel() {
    private val _sheet = MutableStateFlow<TripSheet?>(null)
    val sheet: StateFlow<TripSheet?> = _sheet

    private var job: Job? = null

    @Volatile private var visible = true

    /** [color]: the leg's colour in its itinerary (`LegPalette`), so the sheet matches the chip. */
    fun open(leg: Leg, color: String? = null) {
        if (!leg.isTransit) return
        job?.cancel()
        _sheet.value = TripSheet(leg, details = null)
        val session = TripDetailsSession(api, leg, language())
        job = viewModelScope.launch {
            fun TripDetails.tinted() = if (color != null) copy(color = color) else this
            val d = session.open(Instant.now()).tinted()
            _sheet.update { it?.copy(details = d, legOnly = session.legOnly) }
            while (isActive) {
                val now = Instant.now()
                val mark = session.tick(now, visible)
                _sheet.update { s -> s?.let { it.copy(details = session.at(now)?.tinted() ?: it.details, vehicle = mark) } }
                delay(TICK_MS)
            }
        }
    }

    fun close() {
        job?.cancel()
        job = null
        _sheet.value = null
    }

    /** From the Activity's onResume/onPause: no `map/trips` while the app is in the background. */
    fun onVisible(v: Boolean) {
        visible = v
    }

    companion object {
        /** How often the mark moves along the polyline. Not a request rate: see TripDetailsSession. */
        private const val TICK_MS = 2_000L

        fun factory(app: TransitApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TripDetailsViewModel(app.api) { app.language } as T
        }
    }
}
