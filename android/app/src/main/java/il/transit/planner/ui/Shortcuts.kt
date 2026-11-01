package il.transit.planner.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import il.transit.core.user.Routines
import il.transit.core.user.SavedPlace
import il.transit.planner.OwnIntents
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
        // The app's own symbols, so the long-press menu is not a column of identical buses.
        val placeIcon = IconCompat.createWithResource(context, R.mipmap.ic_shortcut_place)
        val lines = ShortcutInfoCompat.Builder(context, "lines")
            .setShortLabel(context.getString(R.string.shortcut_lines))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_shortcut_lines))
            .setIntent(intent(context).putExtra(EXTRA_SHORTCUT, LINES))
            .build()
        val placeShortcuts = Routines.shortcutOrder(places, now).mapIndexed { i, p ->
            ShortcutInfoCompat.Builder(context, "place-$i")
                .setShortLabel(p.name)
                .setIcon(placeIcon)
                .setRank(i + 1)
                .setIntent(intent(context).putExtra(EXTRA_PLACE, p.name))
                .build()
        }
        val all = listOf(lines) + placeShortcuts
        val ids = all.map { it.id }.toSet()
        val sm = context.getSystemService(ShortcutManager::class.java)
        val pinned = runCatching { sm?.pinnedShortcuts.orEmpty() }.getOrDefault(emptyList())
        // A pinned shortcut disabled below must be enabled before its id can be published again.
        val back = pinned.filter { !it.isEnabled && it.id in ids }.map { it.id }
        if (back.isNotEmpty()) runCatching { sm?.enableShortcuts(back) }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, all) }
        // F5: publishing updates a pinned shortcut with the same id. One no longer published (fewer places,
        // or pinned from an older version) would keep its old target, so it is disabled.
        val stale = pinned.filter { it.isEnabled && it.id !in ids }.map { it.id }
        if (stale.isNotEmpty()) runCatching { sm?.disableShortcuts(stale) }
    }

    private fun intent(context: Context) =
        OwnIntents.to(context).setAction(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
