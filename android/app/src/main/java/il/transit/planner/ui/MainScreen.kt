@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package il.transit.planner.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.transit.core.geo.LatLon
import il.transit.planner.R
import il.transit.planner.ui.screens.AttributionChip
import il.transit.planner.ui.screens.FavoritesDialog
import il.transit.planner.ui.screens.HistoryDialog
import il.transit.planner.ui.screens.NameDialog
import il.transit.planner.ui.screens.ResultsPanel
import il.transit.planner.ui.screens.SavedChips
import il.transit.planner.ui.screens.SearchCard
import il.transit.planner.ui.screens.SettingsDialog
import il.transit.planner.ui.screens.StopPanel
import il.transit.planner.ui.screens.SuggestionList

/** What only the Activity can do: permissions, the map camera, the offline store. */
class ScreenActions(
    val offline: OfflineState,
    val downloadOffline: () -> Unit,
    val deleteOffline: () -> Unit,
    /** Asks for the notification permission if needed, then arms the reminder. */
    val remind: () -> Unit,
    /** Same permission dance, then starts the "get off at the next stop" ride. */
    val startRide: () -> Unit,
)

@Composable
fun MainScreen(state: UiState, vm: MainViewModel, actions: ScreenActions, map: @Composable () -> Unit) {
    var savingPlace by remember { mutableStateOf<LatLon?>(null) }
    var savingTrip by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        map()

        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.update?.let { UpdateBanner(it, vm) }
            SearchCard(state, vm, onSavePlace = { savingPlace = it })
            when {
                state.editing != null -> SuggestionList(state, vm)
                !state.hasResults && !state.loading && state.stopSheet == null -> SavedChips(state, vm)
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            AttributionChip(Modifier.padding(8.dp))
            when {
                state.stopSheet != null -> StopPanel(state.stopSheet, state.favorites, vm)
                state.loading || state.hasResults || state.error != null ->
                    ResultsPanel(state, vm, actions, onSaveTrip = { savingTrip = true })
                else -> Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    if (state.showSettings) SettingsDialog(state, vm, actions)
    if (state.showHistory) HistoryDialog(state, vm)
    if (state.showFavorites) FavoritesDialog(state, vm)
    savingPlace?.let { at ->
        NameDialog(R.string.save_place, onDismiss = { savingPlace = null }) { name -> vm.savePlace(name, at); savingPlace = null }
    }
    if (savingTrip) {
        NameDialog(R.string.save_trip, onDismiss = { savingTrip = false }) { name -> vm.saveTrip(name); savingTrip = false }
    }
}

@Composable
private fun UpdateBanner(latest: il.transit.core.update.LatestRelease, vm: MainViewModel) {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.update_available, latest.version), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.apkUrl ?: latest.pageUrl)))
            }) { Text(stringResource(R.string.update_download)) }
            IconButton(onClick = vm::dismissUpdate) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
        }
    }
}
