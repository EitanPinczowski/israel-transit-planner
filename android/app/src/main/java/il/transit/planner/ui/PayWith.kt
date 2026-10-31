package il.transit.planner.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import il.transit.core.present.PayApp
import il.transit.core.present.PayApps

/**
 * "Pay with…" (Phase 11 E4): we never take a payment, we open another app. Its launcher intent
 * when it is installed (the manifest's `<queries>` lists each package), else its Play Store page:
 * the store app's `market://` link, falling back to the https page.
 */
object PayWith {
    fun installed(context: Context): Set<String> =
        PayApps.ALL
            .map { it.packageName }
            .filter { context.packageManager.getLaunchIntentForPackage(it) != null }
            .toSet()

    /** The intent that opens [app]: the app itself, or its store page. */
    fun intent(context: Context, app: PayApp): Intent =
        context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse(app.marketUri))

    /** Opens [app], or its store page; false when not even a browser could open it. */
    fun open(context: Context, app: PayApp): Boolean {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return try {
            context.startActivity(intent(context, app).addFlags(flags))
            true
        } catch (e: ActivityNotFoundException) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.webStoreUrl)).addFlags(flags)) }.isSuccess
        }
    }
}
