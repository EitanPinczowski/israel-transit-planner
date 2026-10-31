package il.transit.planner.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import il.transit.core.api.UsageSummary
import il.transit.core.present.hhmm
import il.transit.core.user.LowData
import il.transit.core.user.UserSettings
import il.transit.planner.R
import il.transit.planner.TransitApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Nothing counted yet: the card's first day. */
internal val NO_USAGE = UsageSummary(0, 0, 0, 0, 0, null, 0, 0)

/**
 * Settings → "Data & battery" (Phase 11 E4): what the app sent today, the map cache, the last night
 * refresh and the ride GPS minutes, plus the low-data switch. Reads the app's [TransitApp.usage] and
 * the size of MapLibre's cache file; changes neither. 0 requests.
 */
@Composable
internal fun DataBatterySection(s: UserSettings, set: (UserSettings) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? TransitApp
    val usage = app?.usage
    val tick = usage?.changes?.collectAsState()?.value
    val summary = remember(tick) { usage?.summary() } ?: NO_USAGE
    val mapMb by produceState<Double?>(null, app) {
        value = if (app == null) null else withContext(Dispatchers.IO) { mapCacheMb(app) }
    }
    DataCardContent(summary, mapMb, s.lowData) { set(s.copy(lowData = it)) }
}

/** MapLibre's ambient cache (with any offline area in it): its database file, read, never changed. */
internal fun mapCacheMb(context: Context): Double? =
    runCatching { File(context.filesDir, "mbgl-offline.db").takeIf { it.exists() }?.length()?.let { it / 1_048_576.0 } }.getOrNull()

/** The card on its own, for screenshot tests. */
@Composable
internal fun DataCardContent(u: UsageSummary, mapCacheMb: Double?, lowData: Boolean, onLowData: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.data_title), style = MaterialTheme.typography.labelLarge)
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.data_today), style = MaterialTheme.typography.titleSmall)
                val line = MaterialTheme.typography.bodyMedium
                if (u.transitous == 0 && u.weather == 0 && u.address == 0) {
                    Text(stringResource(R.string.data_empty), style = line)
                } else {
                    Text(pluralStringResource(R.plurals.data_transitous, u.transitous, u.transitous, u.transitousCache), style = line)
                    Text(stringResource(R.string.data_weather, u.weather), style = line)
                    Text(stringResource(R.string.data_address, u.address), style = line)
                }
                mapCacheMb?.let { Text(stringResource(R.string.data_map_cache, String.format(Locale.US, "%.1f", it)), style = line) }
                val night = u.nightAt
                Text(
                    if (night != null) pluralStringResource(R.plurals.data_night, u.nightRequests, hhmm(night), u.nightRequests) else stringResource(R.string.data_night_none),
                    style = line,
                )
                Text(stringResource(R.string.data_ride_gps, u.rideGpsMin), style = line)
            }
        }
        Text(stringResource(R.string.data_help), style = MaterialTheme.typography.bodySmall)
        // The whole row is the switch: one TalkBack control, a target of at least 48 dp.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = lowData, role = Role.Switch, onValueChange = onLowData),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.low_data_toggle), Modifier.weight(1f))
            Switch(checked = lowData, onCheckedChange = null)
        }
        Text(stringResource(R.string.low_data_help), style = MaterialTheme.typography.bodySmall)
    }
}

/** Whether the phone's current network is Wi-Fi (or Ethernet): the offline map then never asks. */
internal fun onWifi(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching false
    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}.getOrDefault(false)

/**
 * The offline map's Download in low-data mode (owner decision 2): off Wi-Fi it asks first; on Wi-Fi,
 * or with the mode off, it downloads at once as before.
 */
@Composable
internal fun rememberOfflineDownload(lowData: Boolean, download: () -> Unit): () -> Unit {
    val context = LocalContext.current
    var asking by rememberSaveable { mutableStateOf(false) }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.low_data_ask_title)) },
            text = { Text(stringResource(R.string.low_data_ask_text)) },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    download()
                }) { Text(stringResource(R.string.offline_download)) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    return { if (LowData.askBeforeOfflineDownload(lowData, onWifi(context))) asking = true else download() }
}
