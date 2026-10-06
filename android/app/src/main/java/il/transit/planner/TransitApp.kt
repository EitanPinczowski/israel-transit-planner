package il.transit.planner

import android.app.Application
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.MotisClient
import il.transit.core.api.TransitApi
import il.transit.core.api.Itinerary
import il.transit.core.plan.NightRefresh
import il.transit.core.remind.Reminder
import il.transit.core.user.Home
import il.transit.planner.data.ApkInstaller
import il.transit.planner.data.CrashLogStore
import il.transit.planner.data.HistoryStore
import il.transit.planner.data.PlanCacheStore
import il.transit.planner.data.StopsStore
import il.transit.planner.data.UpdateChecker
import il.transit.planner.data.UserStore
import il.transit.planner.remind.ReminderScheduler
import il.transit.planner.ride.RideService
import il.transit.planner.work.NightRefreshWorker
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.util.Locale

/**
 * Process-wide singletons. One guarded client, so the cache and the concurrency cap are shared.
 * Open so the `uitest` build (src/uitest) can swap in recorded answers, a fixed clock and a
 * blank map: UI tests, monkey runs and Test Lab crawlers then never reach Transitous.
 */
open class TransitApp : Application() {
    /** Local crash log (Settings → Share crash log). Installed first, so it sees every crash. */
    val crashLog: CrashLogStore by lazy { CrashLogStore(File(filesDir, "crash_log.txt"), BuildConfig.VERSION_NAME) }

    override fun onCreate() {
        super.onCreate()
        crashLog.install()
        keepNightRefreshScheduled()
    }

    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    /**
     * The night refresh is enqueued only while the setting is on and some night has a trip
     * to plan; any change to settings, places, trips or history re-checks that.
     */
    private fun keepNightRefreshScheduled() {
        appScope.launch {
            history.load()
            combine(store.settings, store.places, store.trips, history.records) { s, p, t, h ->
                s.nightRefresh && NightRefresh.anyJobs(p, t, Home.of(s, p), h, java.time.Instant.now())
            }.distinctUntilChanged().collect { wanted -> NightRefreshWorker.sync(this@TransitApp, wanted) }
        }
    }

    /** "Now" for searches and labels. */
    open val clock: Clock = Clock.systemUTC()
    open val api: TransitApi by lazy { GuardedTransitApi(MotisClient()) }
    val store: UserStore by lazy { UserStore(this) }
    val planCache: PlanCacheStore by lazy { PlanCacheStore(File(filesDir, "trip_cache.json"), clock) }
    val departureCache: il.transit.planner.data.DepartureCacheStore by lazy {
        il.transit.planner.data.DepartureCacheStore(File(filesDir, "departures_cache.json"))
    }
    val stopsCache: StopsStore by lazy { StopsStore(File(filesDir, "stops_cache_$language.json")) }

    val history: HistoryStore by lazy { HistoryStore(File(filesDir, "history.json")) }
    open val updates: UpdateChecker by lazy { UpdateChecker(store, BuildConfig.VERSION_NAME, ApkInstaller(this)) }

    /** The MapLibre style: OpenFreeMap, light or dark. */
    open fun mapStyle(night: Boolean): String = if (night) MainActivity.MAP_STYLE_DARK else MainActivity.MAP_STYLE

    val rides: Rides = object : Rides {
        override val active: StateFlow<Boolean> = RideService.active
        override val progress: StateFlow<il.transit.core.ride.RideProgress?> = RideService.progress
        override fun start(itinerary: Itinerary, speak: Boolean) = RideService.start(this@TransitApp, itinerary, speak)
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

    /** Live progress of the ride ("3 stops left · arrive 08:47"); null between fixes or when idle. */
    val progress: StateFlow<il.transit.core.ride.RideProgress?>
    /** [speak]: also say the get-off alert out loud. */
    fun start(itinerary: Itinerary, speak: Boolean)
    fun stop()
}
