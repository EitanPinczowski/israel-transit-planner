package il.transit.planner.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import il.transit.core.plan.NightRefresh
import il.transit.core.plan.NightRefresher
import il.transit.core.user.Home
import il.transit.planner.TransitApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Plans tomorrow's usual trips into the Trip cache (core `NightRefresh`). At most
 * [NightRefresh.MAX_TRIPS] `plan` requests a night, under a budget; a failed trip waits for
 * the next night, so this never returns [Result.retry].
 */
class NightRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TransitApp
        try {
            val settings = app.store.settings.first()
            if (!settings.nightRefresh) return Result.success()
            val places = app.store.places.first()
            val trips = app.store.trips.first()
            app.history.load()
            val history = app.history.records.value
            val home = Home.of(settings, places)
            val dayFile = File(app.filesDir, DAY_FILE)
            val lastDay = runCatching { LocalDate.parse(dayFile.readText().trim()) }.getOrNull()
            val out = NightRefresher(app.api).run(
                jobs = { day -> NightRefresh.jobs(places, trips, home, history, day) },
                lastRunDay = lastDay,
                now = Instant.now(),
                settings = settings,
                language = app.language,
                save = app.planCache::putNight,
            )
            if (out != null) runCatching { dayFile.writeText(out.day.toString()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing to retry: tomorrow night tries again.
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "night-refresh"

        /** The service day the last run planned, so a second wake-up the same night sends nothing. */
        private const val DAY_FILE = "night_refresh_day.txt"

        /**
         * Enqueue the nightly work when [wanted] (the setting is on and some night has a
         * trip), else cancel it. KEEP: an already-scheduled night is left where it is.
         */
        fun sync(context: Context, wanted: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!wanted) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<NightRefreshWorker>(NightRefresh.PERIOD, NightRefresh.FLEX)
                .setConstraints(constraints)
                .setInitialDelay(NightRefresh.initialDelay(Instant.now()))
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
