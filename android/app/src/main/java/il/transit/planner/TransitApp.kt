package il.transit.planner

import android.app.Application
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.MotisClient
import il.transit.core.api.TransitApi
import il.transit.core.remind.Reminder
import il.transit.planner.data.PlanCacheStore
import il.transit.planner.data.UserStore
import il.transit.planner.remind.ReminderScheduler
import java.io.File
import java.util.Locale

/** Process-wide singletons. One guarded client, so the cache and the concurrency cap are shared. */
class TransitApp : Application() {
    val api: TransitApi by lazy { GuardedTransitApi(MotisClient()) }
    val store: UserStore by lazy { UserStore(this) }
    val planCache: PlanCacheStore by lazy { PlanCacheStore(File(filesDir, "trip_cache.json")) }

    /** What the ViewModel needs to arm and disarm alarms, without holding a Context. */
    val reminders: Reminders = object : Reminders {
        override fun schedule(r: Reminder) = ReminderScheduler.schedule(this@TransitApp, r)
        override fun cancel() = ReminderScheduler.cancel(this@TransitApp)
    }

    /** Language for stop names and geocoding: Hebrew unless the phone is set to something else. */
    val language: String
        get() = when (Locale.getDefault().language) {
            "iw", "he" -> "he"
            else -> "en"
        }
}

interface Reminders {
    fun schedule(r: Reminder)
    fun cancel()
}
