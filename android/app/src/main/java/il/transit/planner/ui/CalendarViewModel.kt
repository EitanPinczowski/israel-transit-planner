package il.transit.planner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import il.transit.core.api.TransitApi
import il.transit.core.geo.LatLon
import il.transit.core.plan.CalendarDestination
import il.transit.core.plan.CalendarEvent
import il.transit.core.plan.CalendarSuggest
import il.transit.planner.TransitApp
import il.transit.planner.data.CalendarSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** "From my calendar": which dialog is open. All null/false = only the chip shows. */
data class CalendarUiState(
    /** Why we ask for the calendar, shown before the system prompt. */
    val rationale: Boolean = false,
    /** The user said no: the feature still works by typing the place. */
    val refused: Boolean = false,
    /** The event list is open (empty = nothing with a place in the next 24 h). */
    val events: List<CalendarEvent>? = null,
    /** A picked event's place is being looked up (one geocode request at most). */
    val resolving: Boolean = false,
    /** Found: shown with the event's own text (and "approximate") before anything is planned. */
    val confirm: CalendarConfirm? = null,
)

/** [arriveBy] null = too late for the buffer: leave now. */
data class CalendarConfirm(val dest: CalendarDestination, val arriveBy: Instant?)

class CalendarViewModel(
    private val api: TransitApi,
    private val source: CalendarSource,
    private val language: String,
) : ViewModel() {
    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    /** The chip: list the events if we may read the calendar, else explain why we ask. */
    fun onChip() {
        if (source.hasPermission()) load() else _state.update { it.copy(rationale = true) }
    }

    fun onPermission(granted: Boolean) {
        _state.update { it.copy(rationale = false, refused = !granted) }
        if (granted) load()
    }

    fun dismiss() = _state.update { CalendarUiState(resolving = it.resolving) }

    private fun load() = viewModelScope.launch {
        val now = Instant.now()
        _state.update { it.copy(events = CalendarSuggest.pick(source.upcoming(now), now)) }
    }

    /**
     * The user picked [e]: find where it is, then show it for a check ([CalendarUiState.confirm]).
     * Not found: [notFound] gets the location text, so it can be typed instead.
     */
    fun pick(e: CalendarEvent, near: LatLon?, bufferMin: Int, notFound: (place: String, at: LatLon?, arriveBy: Instant?) -> Unit) {
        _state.update { CalendarUiState(resolving = true) }
        viewModelScope.launch {
            val dest: CalendarDestination? = try {
                CalendarSuggest.resolve(api, e, language, near)
            } catch (x: CancellationException) {
                throw x
            } catch (x: Exception) {
                null
            }
            val arriveBy = CalendarSuggest.arriveBy(e, bufferMin, Instant.now())
            _state.update { it.copy(resolving = false, confirm = dest?.let { d -> CalendarConfirm(d, arriveBy) }) }
            if (dest == null) notFound(e.location.trim(), null, arriveBy)
        }
    }

    companion object {
        fun factory(app: TransitApp) = viewModelFactory {
            initializer { CalendarViewModel(app.api, CalendarSource(app), app.language) }
        }
    }
}
