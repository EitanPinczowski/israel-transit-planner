package il.transit.planner.ui

import il.transit.core.geo.LatLon
import il.transit.core.weather.GuardedWeather
import il.transit.core.weather.RainAdvice
import il.transit.core.weather.RainForecast
import il.transit.core.weather.RainNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Rain on the walking parts (Phase 10 D9): the forecast fetched for the Trip results of
 * [forResults] (their [UiState.resultsAt]). The note itself is worked out from the options
 * the list shows ([rainNoteFor]), so Earlier / Later and every sort see the same answer.
 */
data class RainUi(val forResults: Instant? = null, val forecast: RainForecast? = null)

/** The note to show above the Trip options, or null (none, off, another tab, errands, offline copy). */
fun rainNoteFor(s: UiState): RainNote? {
    val r = s.rain
    if (r.forResults == null || r.forResults != s.resultsAt || !s.settings.rainAware) return null
    if (s.mode != AppMode.TRIP || s.chain != null || s.loading || s.error != null) return null
    return RainAdvice.advise(s.options, r.forecast)
}

/**
 * After a Trip search: one Open-Meteo forecast for the rounded start ([GuardedWeather]: 1 per
 * search, cached 1 h per cell, only for trips in the next 48 h). Never touches the plan; a
 * failure shows nothing. A new search cancels the one still running.
 */
class RainCheck(private val scope: CoroutineScope, private val weather: GuardedWeather?) {
    private var job: Job? = null

    fun run(state: MutableStateFlow<UiState>, from: LatLon) {
        job?.cancel()
        val s = state.value
        val at = s.resultsAt ?: return
        if (weather == null || !s.settings.rainAware || s.mode != AppMode.TRIP || s.chain != null || s.offlineSince != null) return
        val start = (s.results?.itineraries.orEmpty() + listOfNotNull(s.results?.walkOnly)).minOfOrNull { it.start } ?: return
        job = scope.launch {
            val f = weather.forTrip(start, from) ?: return@launch
            state.update { if (it.resultsAt == at) it.copy(rain = RainUi(at, f)) else it }
        }
    }
}
