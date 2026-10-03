package il.transit.planner.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import il.transit.core.user.Routines
import il.transit.core.user.SavedPlace
import il.transit.planner.MainActivity
import il.transit.planner.R
import java.time.Instant

/**
 * Long-press the app icon: "My lines" and up to three saved places (the routine on right now
 * first), each opening straight on that screen or trip. Rebuilt whenever saved places change.
 */
object Shortcuts {
    const val EXTRA_SHORTCUT = "shortcut"
    const val EXTRA_PLACE = "place"
    const val LINES = "lines"

    fun update(context: Context, places: List<SavedPlace>, now: Instant = Instant.now()) {
        val icon = IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        val lines = ShortcutInfoCompat.Builder(context, "lines")
            .setShortLabel(context.getString(R.string.shortcut_lines))
            .setIcon(icon)
            .setIntent(intent(context).putExtra(EXTRA_SHORTCUT, LINES))
            .build()
        val placeShortcuts = Routines.shortcutOrder(places, now).mapIndexed { i, p ->
            ShortcutInfoCompat.Builder(context, "place-$i")
                .setShortLabel(p.name)
                .setIcon(icon)
                .setRank(i + 1)
                .setIntent(intent(context).putExtra(EXTRA_PLACE, p.name))
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, listOf(lines) + placeShortcuts) }
    }

    private fun intent(context: Context) =
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
