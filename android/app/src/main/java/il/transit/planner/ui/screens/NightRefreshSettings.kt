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
import androidx.compose.ui.unit.dp
import il.transit.core.plan.NightRefresh
import il.transit.core.user.Home
import il.transit.core.user.SavedPlace
import il.transit.core.user.UserSettings
import il.transit.planner.R

/** The offline Trip banner: a night-refresh copy says when it was planned. */
@Composable
internal fun offlineBanner(night: Boolean, time: String): String =
    stringResource(if (night) R.string.night_offline_planned else R.string.offline_showing, time)

/** "Refresh my trips at night": an on/off switch, what it does, and what it needs. */
@Composable
internal fun NightRefreshSection(s: UserSettings, places: List<SavedPlace>, set: (UserSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.night_refresh_title), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.night_refresh_toggle), Modifier.weight(1f))
            Switch(checked = s.nightRefresh, onCheckedChange = { set(s.copy(nightRefresh = it)) })
        }
        Text(stringResource(R.string.night_refresh_help, NightRefresh.MAX_TRIPS), style = MaterialTheme.typography.bodySmall)
        if (s.nightRefresh && Home.of(s, places) == null) {
            Text(stringResource(R.string.night_refresh_no_home), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A place called "Home" / "בית" and no Home set: offered once. Either answer is final. */
@Composable
internal fun HomeSuggestion(s: UserSettings, places: List<SavedPlace>, set: (UserSettings) -> Unit) {
    val p = Home.suggestion(s, places) ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.home_suggest, p.name), style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = { set(s.copy(homePlace = p.name, homeOffered = true)) }) { Text(stringResource(R.string.home_suggest_yes)) }
                TextButton(onClick = { set(s.copy(homeOffered = true)) }) { Text(stringResource(R.string.home_suggest_no)) }
            }
        }
    }
}
