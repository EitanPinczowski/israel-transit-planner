package il.transit.planner.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import il.transit.core.plan.CacheLookup
import il.transit.core.present.TileText
import il.transit.core.user.Home
import il.transit.planner.MainActivity
import il.transit.planner.R
import il.transit.planner.TransitApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile "Home" (Phase 9 C4): the next option home from the Trip cache, drawn
 * with 0 requests each time the panel opens; a tap opens the app planning home (1 request).
 * No background work: the tile never refreshes itself.
 */
class NextTripTile : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { runCatching { draw() } }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun draw() {
        val app = application as TransitApp
        val home = Home.of(app.store.settings.first(), app.store.places.first())
        val hit = home?.let { CacheLookup.tripHome(app.planCache.entries(), it.latLon, app.clock.instant()) }
        val text = TileText.of(home != null, hit)
        val tile = qsTile ?: return
        val label = getString(if (text == TileText.NoHome) R.string.tile_set_home else R.string.tile_home)
        val subtitle = when (text) {
            TileText.NoHome -> null
            TileText.TapToPlan -> getString(R.string.tile_tap_to_plan)
            is TileText.Next -> text.times
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = label
            tile.subtitle = subtitle
        } else {
            tile.label = subtitle?.let { getString(R.string.tile_label_with, label, it) } ?: label
        }
        tile.state = if (text is TileText.Next) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // the Intent form is the only one below API 34
    override fun onClick() {
        super.onClick()
        val open = Runnable {
            val intent = tripHomeIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun(open) else open.run()
    }

    companion object {
        /** Opens [MainActivity] on the trip home (or Settings with no Home). C5's alert reuses it. */
        const val EXTRA_TRIP_HOME = "trip_home"

        fun tripHomeIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_TRIP_HOME, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
