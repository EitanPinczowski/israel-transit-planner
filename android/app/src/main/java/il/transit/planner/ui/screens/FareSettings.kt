package il.transit.planner.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import il.transit.core.fare.FareProfile
import il.transit.core.user.UserSettings
import il.transit.planner.R

// Settings → Fare (Phase 10 D8): whose fare the estimates and the pass advice use. No requests.

/** The profile's chip label, also written in the monthly report. */
internal fun fareProfileLabel(p: FareProfile): Int = when (p) {
    FareProfile.REGULAR -> R.string.fare_regular
    FareProfile.SENIOR -> R.string.fare_profile_senior
    FareProfile.STUDENT -> R.string.fare_profile_student
    FareProfile.YOUTH -> R.string.fare_profile_youth
    FareProfile.SOLDIER -> R.string.fare_profile_soldier
    FareProfile.HALF -> R.string.fare_profile_half
    FareProfile.FREE -> R.string.fare_profile_free
}

private fun fareProfileHelp(p: FareProfile): Int = when (p) {
    FareProfile.REGULAR -> R.string.fare_help_regular
    FareProfile.SENIOR -> R.string.fare_help_senior
    FareProfile.STUDENT -> R.string.fare_help_student
    FareProfile.YOUTH -> R.string.fare_help_youth
    FareProfile.SOLDIER -> R.string.fare_help_soldier
    FareProfile.HALF -> R.string.fare_help_half
    FareProfile.FREE -> R.string.fare_help_free
}

/** The chips, then what the chosen profile gets and where the numbers come from. */
@Composable
internal fun FareSection(s: UserSettings, set: (UserSettings) -> Unit) {
    Column {
        Section(R.string.fare_profile) {
            FareProfile.entries.forEach { f ->
                FilterChip(s.fareProfile == f, { set(s.copy(fareProfile = f)) }, label = { Text(stringResource(fareProfileLabel(f))) })
            }
        }
        Text(
            stringResource(fareProfileHelp(s.fareProfile)) + " " + stringResource(R.string.fare_help_source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
