package il.transit.planner.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import il.transit.core.api.Itinerary
import il.transit.core.fare.FareProfile
import il.transit.core.fare.PassAdvisor
import il.transit.core.history.TripEnd
import il.transit.core.history.TripRecord
import il.transit.core.history.UsualTrip
import il.transit.core.user.PlaceRoutine
import il.transit.core.user.SavedPlace
import il.transit.planner.R
import il.transit.planner.ui.PlaceRef
import il.transit.planner.ui.UiState
import java.time.Instant
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.roundToInt

// History insights (Phase 9 C3): computed on the phone from local history, no requests.

/** "Usually 52 min · this one +8" on the selected option, when the same trip was taken 3+ times lately. */
@Composable
internal fun UsualLine(state: UiState, itin: Itinerary, now: Instant = Instant.now()) {
    if (state.chain != null || state.history.isEmpty()) return
    val from = TripEnd(itin.legs.firstOrNull()?.from?.latLon, (state.from as? PlaceRef.Point)?.name.orEmpty())
    val to = TripEnd(itin.legs.lastOrNull()?.to?.latLon, (state.to as? PlaceRef.Point)?.name.orEmpty())
    val usual = remember(state.history, itin) { UsualTrip.usual(state.history, from, to, now) } ?: return
    val diff = (itin.duration / 60.0).roundToInt() - usual.medianMin
    val text = when {
        diff > 0 -> stringResource(R.string.insights_usual_slower, usual.medianMin, diff)
        diff < 0 -> stringResource(R.string.insights_usual_faster, usual.medianMin, -diff)
        else -> stringResource(R.string.insights_usual_same, usual.medianMin)
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "Quickest when you leave around 07:15 (usually 41 min)", for the days picked in the routine dialog. */
@Composable
internal fun BestLeaveLine(history: List<TripRecord>, place: SavedPlace, days: List<Int>) {
    val best = remember(history, place, days) { UsualTrip.bestLeave(history, PlaceRoutine(days, 0, 0), place) } ?: return
    Text(
        stringResource(R.string.insights_best_leave, hhmmOf(best.leaveMin), best.medianMin),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.tertiary,
    )
}

/** The history section's monthly pass advice for the last finished month; nothing when it can't tell. */
@Composable
internal fun PassAdviceLine(history: List<TripRecord>, profile: FareProfile, now: Instant) {
    val a = remember(history, profile, now) { PassAdvisor.advise(history, profile, now) } ?: return
    val month = a.month.month.getDisplayName(TextStyle.FULL_STANDALONE, LocalConfiguration.current.locales[0])
    val shekels = ((abs(a.passSavesAgorot) + 50) / 100).toString()
    val line = when {
        shekels == "0" -> stringResource(R.string.insights_pass_same, month)
        a.passSavesAgorot > 0 -> stringResource(R.string.insights_pass_saves, shekels, month)
        else -> stringResource(R.string.insights_single_cheaper, shekels, month)
    }
    Text(line, style = MaterialTheme.typography.bodyMedium)
    Text(
        pluralStringResource(R.plurals.insights_based_on, a.trips, a.trips),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
