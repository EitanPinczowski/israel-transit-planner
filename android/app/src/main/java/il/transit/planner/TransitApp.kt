package il.transit.planner

import android.app.Application
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.MotisClient
import il.transit.core.api.TransitApi
import il.transit.core.api.Itinerary
import il.transit.core.remind.Reminder
import il.transit.planner.data.HistoryStore
import il.transit.planner.data.PlanCacheStore
import il.transit.planner.data.StopsStore
import il.transit.planner.data.UpdateChecker
import il.transit.planner.data.UserStore
import il.transit.planner.remind.ReminderScheduler
import il.transit.planner.ride.RideService
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.Clock
import java.util.Locale

/**
 * Process-wide singletons. One guarded client, so the cache and the concurrency cap are shared.
 * Open so the `uitest` build (src/uitest) can swap in recorded answers, a fixed clock and a
 * blank map: UI tests, monkey runs and Test Lab crawlers then never reach Transitous.
 */
open class TransitApp : Application() {
    /** "Now" for searches and labels. */
    open val clock: Clock = Clock.systemUTC()
    open val api: TransitApi by lazy { GuardedTransitApi(MotisClient()) }
    val store: UserStore by lazy { UserStore(this) }
    val planCache: PlanCacheStore by lazy { PlanCacheStore(File(filesDir, "trip_cache.json"), clock) }
    val stopsCache: StopsStore by lazy { StopsStore(File(filesDir, "stops_cache_$language.json")) }

    val history: HistoryStore by lazy { HistoryStore(File(filesDir, "history.json")) }
    open val updates: UpdateChecker by lazy { UpdateChecker(store, BuildConfig.VERSION_NAME) }

    /** The MapLibre style: OpenFreeMap, light or dark. */
    open fun mapStyle(night: Boolean): String = if (night) MainActivity.MAP_STYLE_DARK else MainActivity.MAP_STYLE

    val rides: Rides = object : Rides {
        override val active: StateFlow<Boolean> = RideService.active
        override fun start(itinerary: Itinerary) = RideService.start(this@TransitApp, itinerary)
        override fun stop() = RideService.stop(this@TransitApp)
    }

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

/** Start/stop the "get off at the next stop" service, without the ViewModel holding a Context. */
interface Rides {
    val active: StateFlow<Boolean>
    fun start(itinerary: Itinerary)
    fun stop()
}
