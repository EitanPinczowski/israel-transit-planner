package il.transit.planner

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import il.transit.core.history.History
import il.transit.core.user.UserJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The reading end of the upgrade test (Phase 11 T8, `tools/ui_upgrade_run.sh`). The old release's uitest APK is installed and given the
 * saved-data sample of its version (`tools/userdata_to_datastore.py`), then the new APK goes over it with `adb install -r`. Started with
 * `am start --ez uitest_upgrade_probe true`, this logs, under [TAG], what the NEW build reads from the OLD build's files:
 * settings, places, saved trips, favourite lines ("My lines"), recents, history, the reminder, the night-refresh work, the channels.
 * The shell compares each line with what it wrote; a value the new build lost fails the run. The alarms are read by the shell
 * (`dumpsys alarm`). uitest build only.
 */
object UpgradeProbe : Application.ActivityLifecycleCallbacks {
    const val EXTRA = "uitest_upgrade_probe"
    const val TAG = "UpgradeProbe"
    private var armed = false

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        armed = activity is MainActivity && savedInstanceState == null && activity.intent.getBooleanExtra(EXTRA, false)
    }

    override fun onActivityResumed(activity: Activity) {
        val main = activity as? MainActivity ?: return
        if (!armed) return
        armed = false
        val app = main.application as UiTestApp
        main.lifecycleScope.launch {
            delay(1_500) // TransitApp.onCreate has synced the night refresh by now
            val store = app.store
            Log.i(TAG, "settings=" + UserJson.encodeSettings(store.settings.first()))
            Log.i(TAG, "places=" + UserJson.encodePlaces(store.places.first()))
            Log.i(TAG, "trips=" + UserJson.encodeTrips(store.trips.first()))
            Log.i(TAG, "favorites=" + UserJson.encodeFavorites(store.favorites.first()))
            Log.i(TAG, "reminder=" + UserJson.encodeReminder(store.reminder.first()))
            app.history.load()
            Log.i(TAG, "history=" + History.encode(app.history.records.value))
            val work = withContext(Dispatchers.IO) {
                runCatching { WorkManager.getInstance(app).getWorkInfosForUniqueWork("night-refresh").get() }.getOrNull().orEmpty()
            }
            Log.i(TAG, "nightRefresh=" + work.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING })
            val nm = app.getSystemService(NotificationManager::class.java)
            Log.i(TAG, "channels=" + nm.notificationChannels.joinToString(",") { it.id + ":" + it.importance }.ifEmpty { "-" })
            Log.i(TAG, "done=1")
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
