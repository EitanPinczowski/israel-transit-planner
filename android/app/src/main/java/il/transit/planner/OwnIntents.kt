package il.transit.planner

import android.content.Context
import android.content.Intent

/**
 * The door for the app's own actions (Phase 11 F5): notification buttons, the Quick Settings
 * tile and the launcher shortcuts open [MainActivity] through the activity-alias [ALIAS], which
 * the manifest declares `exported="false"`. Android lets only this app reach it, through its own
 * PendingIntents and shortcuts; any other app (or the shell) is refused before the Activity sees
 * anything. MainActivity stays exported for the launcher and for SEND/VIEW shares, and it acts on
 * the own-action extras only when [isOurs] says the intent came through the alias.
 */
object OwnIntents {
    /** `<activity-alias android:name=".ActionEntry">` in the manifest (namespace + name). */
    const val ALIAS = "il.transit.planner.ActionEntry"

    /** An explicit intent to the alias, for a PendingIntent or a shortcut. */
    fun to(context: Context): Intent = Intent().setClassName(context, ALIAS)

    /** True only for an intent Android delivered through the alias. */
    fun isOurs(intent: Intent?): Boolean = intent?.component?.className == ALIAS
}
