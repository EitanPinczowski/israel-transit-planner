package il.transit.planner.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import il.transit.core.user.Home
import il.transit.core.user.SavedPlace
import il.transit.core.user.UserSettings
import il.transit.planner.R

/**
 * Settings → "Last trip home alert" (Phase 9 C5), off by default. Turning it on asks for
 * notifications (API 33+) after our own rationale; refused, it stays off and says why.
 * Without an Activity (screenshot tests) it only draws.
 */
@Composable
internal fun LastTripSetting(s: UserSettings, places: List<SavedPlace>, set: (UserSettings) -> Unit) {
    if (LocalActivityResultRegistryOwner.current == null) {
        LastTripSection(s, places, denied = false) { set(s.copy(lastTripAlert = it)) }
        return
    }
    val context = LocalContext.current
    var denied by rememberSaveable { mutableStateOf(false) }
    var rationale by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) set(s.copy(lastTripAlert = true))
    }
    LastTripSection(s, places, denied) { on ->
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            !on -> set(s.copy(lastTripAlert = false))
            needsAsk -> rationale = true
            // Blocked in the phone's settings (or below API 33): asking would not help.
            !NotificationManagerCompat.from(context).areNotificationsEnabled() -> denied = true
            else -> {
                denied = false
                set(s.copy(lastTripAlert = true))
            }
        }
    }
    if (rationale) {
        AlertDialog(
            onDismissRequest = { rationale = false },
            title = { Text(stringResource(R.string.last_trip_rationale_title)) },
            text = { Text(stringResource(R.string.last_trip_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    rationale = false
                    ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.last_trip_rationale_ok)) }
            },
            dismissButton = { TextButton(onClick = { rationale = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The switch, what it does, and why it is off or cannot work yet. */
@Composable
internal fun LastTripSection(s: UserSettings, places: List<SavedPlace>, denied: Boolean, onToggle: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.last_trip_section), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.last_trip_toggle), Modifier.weight(1f))
            Switch(checked = s.lastTripAlert, onCheckedChange = onToggle)
        }
        Text(stringResource(R.string.last_trip_help), style = MaterialTheme.typography.bodySmall)
        if (denied && !s.lastTripAlert) {
            Text(stringResource(R.string.last_trip_denied), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (s.lastTripAlert && Home.of(s, places) == null) {
            Text(stringResource(R.string.last_trip_no_home), style = MaterialTheme.typography.bodySmall)
        }
    }
}
