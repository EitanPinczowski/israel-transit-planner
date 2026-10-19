package il.transit.planner.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.transit.core.geo.LatLon
import il.transit.core.plan.MissedIt
import il.transit.core.present.hhmm
import il.transit.planner.R
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.PlaceRef
import il.transit.planner.ui.UiState
import java.time.Instant

/** What the "Missed it?" banner says (Phase 11 E2), for the destination [to] it was asked for. */
data class MissedItUi(val kind: Kind, val to: LatLon, val at: Instant? = null, val wasArriveBy: Boolean = false) {
    enum class Kind { PLANNED, ALREADY_THERE, NEED_START }

    companion object {
        fun of(out: MissedIt.Outcome, to: LatLon) = when (out) {
            is MissedIt.Outcome.Planned -> MissedItUi(Kind.PLANNED, to, out.at, out.wasArriveBy)
            MissedIt.Outcome.AlreadyThere -> MissedItUi(Kind.ALREADY_THERE, to)
            MissedIt.Outcome.NeedStart -> MissedItUi(Kind.NEED_START, to)
        }
    }
}

/**
 * "From your location at 07:46" above the options, while the Trip tab still shows the tap's
 * destination; "You're already there"; or "Where are you?" until a start is picked.
 */
@Composable
internal fun MissedItBanner(state: UiState) {
    val m = state.missedIt ?: return
    if (state.mode != AppMode.TRIP || state.loading || (state.to as? PlaceRef.Point)?.at != m.to) return
    if (m.kind == MissedItUi.Kind.NEED_START && state.results != null) return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
    ) {
        Column(Modifier.padding(8.dp)) {
            val text = when (m.kind) {
                MissedItUi.Kind.PLANNED -> stringResource(R.string.missed_it_from, hhmm(m.at ?: Instant.now()))
                MissedItUi.Kind.ALREADY_THERE -> stringResource(R.string.missed_it_there)
                MissedItUi.Kind.NEED_START -> stringResource(R.string.missed_it_where)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (m.kind == MissedItUi.Kind.PLANNED && m.wasArriveBy) {
                Text(stringResource(R.string.missed_it_arrive_by), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
