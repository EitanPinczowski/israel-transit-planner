package il.transit.planner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import il.transit.core.plan.TripSort
import il.transit.core.present.hhmm
import il.transit.core.user.UserSettings
import il.transit.planner.R
import il.transit.planner.ui.MainActions
import il.transit.planner.ui.UiState
import il.transit.planner.ui.rainNoteFor
import java.util.Locale

/**
 * Phase 10 D9, above the Trip options: "Rain likely around 08:10 (70%). Least walking: 4 min
 * (the 08:05 option)", with a button that switches the sort to Least walking (hidden once it
 * is). It never changes the plan. Credits Open-Meteo, as its licence asks.
 */
@Composable
internal fun RainNoteRow(state: UiState, vm: MainActions) {
    val note = rainNoteFor(state) ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) {
        Column(Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp)) {
            val headline = if (note.byProbability) {
                stringResource(R.string.rain_likely_chance, hhmm(note.at), note.probability ?: 0)
            } else {
                stringResource(R.string.rain_likely_mm, hhmm(note.at), String.format(Locale.US, "%.1f", note.mm ?: 0.0))
            }
            Text(headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 6.dp))
            note.leastWalking?.let { o ->
                Text(stringResource(R.string.rain_least_walking, note.leastWalkMin, hhmm(o.start)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.rain_credit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                )
                if (note.leastWalking != null && state.settings.tripSort != TripSort.LEAST_WALKING) {
                    TextButton(onClick = { vm.setTripSort(TripSort.LEAST_WALKING) }) { Text(stringResource(R.string.rain_sort)) }
                }
            }
        }
    }
}

/** Settings: the rain note on or off, and what it sends. */
@Composable
internal fun RainSetting(s: UserSettings, set: (UserSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.rain_setting_title), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rain_setting_toggle), Modifier.weight(1f))
            Switch(checked = s.rainAware, onCheckedChange = { set(s.copy(rainAware = it)) })
        }
        Text(stringResource(R.string.rain_setting_help), style = MaterialTheme.typography.bodySmall)
    }
}
