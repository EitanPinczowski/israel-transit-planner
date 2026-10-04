@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package il.transit.planner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import il.transit.core.present.DepartureRow
import il.transit.core.present.hhmm
import il.transit.core.user.FavoriteLine
import il.transit.planner.R
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.StopSheet
import il.transit.planner.ui.UiState

/** My lines: each pinned line's next departures at its stop, with live delays. */
@Composable
internal fun FavoritesDialog(state: UiState, vm: MainViewModel) {
    AlertDialog(
        onDismissRequest = { vm.showFavorites(false) },
        confirmButton = { TextButton(onClick = { vm.showFavorites(false) }) { Text(stringResource(R.string.done)) } },
        title = { Text(stringResource(R.string.my_lines)) },
        text = {
            if (state.favorites.isEmpty()) {
                Text(stringResource(R.string.my_lines_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.favoritesOfflineSince?.let { at ->
                        item { Text(stringResource(R.string.my_lines_offline, hhmm(at)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    items(state.favorites) { f ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (f.headsign.isBlank()) f.line else stringResource(R.string.line_to, f.line, f.headsign),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(f.stopName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { vm.removeFavorite(f) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                            }
                            when {
                                f !in state.favoriteBoards -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                state.favoriteBoards[f] == null -> Text(stringResource(R.string.err_network), style = MaterialTheme.typography.bodySmall)
                                state.favoriteBoards[f]!!.isEmpty() -> Text(stringResource(R.string.no_departures), style = MaterialTheme.typography.bodySmall)
                                else -> state.favoriteBoards[f]!!.forEach { DepartureView(it) }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
internal fun StopPanel(sheet: StopSheet, favorites: List<FavoriteLine>, vm: MainViewModel) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sheet.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.departures), style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = vm::closeStop) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
            when {
                sheet.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                sheet.failed -> Text(stringResource(R.string.err_network), Modifier.padding(vertical = 12.dp))
                sheet.rows.isEmpty() -> Text(stringResource(R.string.no_departures), Modifier.padding(vertical = 12.dp))
                else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(sheet.rows) { r ->
                        val pinned = favorites.any { it.stopId == sheet.stopId && it.matches(r) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { DepartureView(r) }
                            // ☆ pins this line in this direction at this stop ("My lines").
                            TextButton(onClick = { vm.toggleFavorite(sheet, r) }) { Text(if (pinned) "★" else "☆") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartureView(r: DepartureRow) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        ) { Text(r.line.ifBlank { kindName(r.kind) }, style = MaterialTheme.typography.labelLarge) }
        Spacer(Modifier.width(10.dp))
        Text(r.headsign, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val status = when {
            r.cancelled -> stringResource(R.string.cancelled)
            r.delayMin == null -> ""
            r.delayMin!! > 0 -> stringResource(R.string.late, r.delayMin!!)
            else -> stringResource(R.string.on_time)
        }
        if (status.isNotEmpty()) {
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                color = if (r.cancelled || (r.delayMin ?: 0) > 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(r.time, style = MaterialTheme.typography.titleSmall)
    }
}
