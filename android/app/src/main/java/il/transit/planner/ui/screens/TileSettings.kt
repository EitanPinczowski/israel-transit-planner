package il.transit.planner.ui.screens

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.transit.planner.R
import il.transit.planner.tile.NextTripTile

/**
 * Settings: the Quick Settings tile "Home" (Phase 9 C4). Android 13+ adds it from a button
 * (the system asks to confirm); older phones get a one-line hint to add it by editing the panel.
 */
@Composable
internal fun QuickTileSection(sdk: Int = Build.VERSION.SDK_INT) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.tile_title), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.tile_help), style = MaterialTheme.typography.bodySmall)
        if (sdk >= Build.VERSION_CODES.TIRAMISU) {
            TextButton(onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestAdd(context) }) {
                Text(stringResource(R.string.tile_add))
            }
        } else {
            Text(stringResource(R.string.tile_add_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The system's own "Add tile?" prompt; whatever the answer, nothing else to do here. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestAdd(context: Context) {
    runCatching {
        context.getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(context, NextTripTile::class.java),
            context.getString(R.string.tile_home),
            Icon.createWithResource(context, R.drawable.ic_notification),
            context.mainExecutor,
        ) { }
    }
}
