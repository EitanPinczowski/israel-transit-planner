package il.transit.core.plan

import il.transit.core.api.BudgetExceededException
import il.transit.core.api.BudgetedTransitApi
import il.transit.core.api.Endpoint
import il.transit.core.api.TransitApi
import il.transit.core.features.ISRAEL
import il.transit.core.geo.LatLon
import il.transit.core.history.TripRecord
import il.transit.core.user.SavedPlace
import il.transit.core.user.SavedTrip
import il.transit.core.user.UserSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Each night, plan tomorrow's usual trips into the Trip cache, so that with no signal the
 * next morning the Trip tab still has them ("Offline · planned last night at 02:14").
 *
 * The app runs it from a WorkManager job (Wi-Fi + charging + battery not low), at most
 * [MAX_TRIPS] `plan` requests a night under a [BudgetedTransitApi]; that is the worst case
 * per day. A trip that fails is skipped until the next night: nothing is retried.
 */
object NightRefresh {
    /** At most this many trips, so this many `plan` requests, per night. */
    const val MAX_TRIPS = 6

    /** The worker does nothing when it wakes outside 22:00–06:00 Israel time. */
    val WINDOW_START: LocalTime = LocalTime.of(22, 0)
    val WINDOW_END: LocalTime = LocalTime.of(6, 0)

    /** WorkManager's flex window lands the run in 00:00–05:00 Israel time. */
    val FLEX_START: LocalTime = LocalTime.MIDNIGHT
    val FLEX: Duration = Duration.ofHours(5)
    val PERIOD: Duration = Duration.ofHours(24)

    /** Cache mode of a NOW search on the Trip tab: what tomorrow's offline search looks up. */
    const val CACHE_MODE = "TRIP-NOW"

    /** One trip to plan: from → to, departing at [at]. */
    data class Job(val from: LatLon, val to: LatLon, val at: Instant, val label: String) {
        val key: String get() = PlanCache.key(CACHE_MODE, from, to)
    }

    /**
     * "Tomorrow": the service day that starts at the next 04:00 ([LastRideFinder.DAY_STARTS]),
     * so a run at 23:50 and one at 00:10 plan the same day.
     */
    fun tomorrow(now: Instant): LocalDate = LastRideFinder.serviceDay(now).plusDays(1)

    fun inWindow(now: Instant): Boolean {
        val t = now.atZone(ISRAEL).toLocalTime()
        return t >= WINDOW_START || t < WINDOW_END
    }

    /**
     * The initial delay of the periodic work so that its flex window, the last [FLEX] of
     * each [PERIOD], opens at the next [FLEX_START] (Israel time). WorkManager then keeps
     * the 24-h rhythm from there.
     */
    fun initialDelay(now: Instant): Duration {
        val z = now.atZone(ISRAEL)
        var flexOpens = z.toLocalDate().atTime(FLEX_START).atZone(ISRAEL)
        if (!flexOpens.isAfter(z)) flexOpens = flexOpens.plusDays(1)
        var d = Duration.between(z, flexOpens).minus(PERIOD.minus(FLEX))
        if (d.isNegative) d = d.plus(PERIOD)
        return d
    }

    /**
     * Tomorrow's trips, earliest first, one per cache key, at most [MAX_TRIPS]:
     *  - Home → every saved place whose routine includes [tomorrow], at the routine's start
     *    (skipped with no [home]);
     *  - every saved trip, from its `from` or else Home, at the destination's routine start
     *    tomorrow, or else the time of day of the latest history record of that trip
     *    (skipped with neither).
     * A day with no service (Shabbat, a holiday) is planned anyway: [TripPlanner] drops the
     * trips that wait it out, and an empty answer is not cached.
     */
    fun jobs(
        places: List<SavedPlace>,
        trips: List<SavedTrip>,
        home: SavedPlace?,
        history: List<TripRecord>,
        tomorrow: LocalDate,
    ): List<Job> {
        val day = tomorrow.dayOfWeek.value
        fun at(min: Int): Instant = tomorrow.atStartOfDay(ISRAEL).plusMinutes(min.toLong()).toInstant()
        fun routineStart(p: SavedPlace): Int? {
            val r = (places.firstOrNull { it.name == p.name } ?: p).routine ?: return null
            return r.fromMin.takeIf { day in r.days }
        }

        val out = ArrayList<Job>()
        if (home != null) {
            for (p in places) {
                if (p.name == home.name) continue
                val start = routineStart(p) ?: continue
                out += Job(home.latLon, p.latLon, at(start), p.name)
            }
        }
        for (t in trips) {
            val from = t.from ?: home ?: continue
            val start = routineStart(t.to) ?: lastTimeOfDay(t, home, history) ?: continue
            out += Job(from.latLon, t.to.latLon, at(start), t.name)
        }
        return out.sortedBy { it.at }.distinctBy { it.key }.take(MAX_TRIPS)
    }

    /** Minutes after midnight of the latest history record of [t] (from "my location" or Home when [t] has no `from`). */
    private fun lastTimeOfDay(t: SavedTrip, home: SavedPlace?, history: List<TripRecord>): Int? {
        val froms = if (t.from != null) setOf(t.from.name) else setOfNotNull("", home?.name)
        val last = history.filter { it.to == t.to.name && it.from in froms }.maxByOrNull { it.startedAtEpoch } ?: return null
        val local = last.startedAt.atZone(ISRAEL)
        return local.hour * 60 + local.minute
    }
}

/**
 * One night's run. [lastRunDay] is the service day the previous run planned for: a second
 * wake-up the same night sends nothing. Returns the day to store, or null when it did not
 * run (outside the window, or already done).
 */
class NightRefresher(private val api: TransitApi) {
    data class Outcome(val day: LocalDate, val planned: Int, val requests: Int)

    suspend fun run(
        jobs: (LocalDate) -> List<NightRefresh.Job>,
        lastRunDay: LocalDate?,
        now: Instant,
        settings: UserSettings,
        language: String,
        save: suspend (key: String, result: TripResult) -> Unit,
    ): Outcome? {
        if (!NightRefresh.inWindow(now)) return null
        val day = NightRefresh.tomorrow(now)
        if (day == lastRunDay) return null
        val budgeted = BudgetedTransitApi(api, NightRefresh.MAX_TRIPS)
        val planner = TripPlanner(budgeted)
        var planned = 0
        for (job in jobs(day).filter { it.at > now }) {
            val r = try {
                planner.plan(
                    TripQuery(Endpoint.Coord(job.from), Endpoint.Coord(job.to), TimeMode.DEPART_AT, job.at, settings, language),
                    now,
                )
            } catch (e: BudgetExceededException) {
                break
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                continue // skipped until the next night
            }
            if (r.itineraries.isEmpty() && r.walkOnly == null) continue
            save(job.key, r)
            planned++
        }
        return Outcome(day, planned, budgeted.used)
    }
}
