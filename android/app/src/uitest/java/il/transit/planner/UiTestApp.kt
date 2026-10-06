package il.transit.planner

import il.transit.core.api.ReplayTransitApi
import il.transit.core.api.TransitApi
import il.transit.core.update.CheckResult
import il.transit.core.update.LatestRelease
import il.transit.core.user.SavedPlace
import il.transit.core.user.UserSettings
import il.transit.planner.data.UpdateChecker
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The app as the UI tests, monkey runs and Test Lab crawlers see it (build type `uitest`):
 *  - Transitous is replaced by [ReplayTransitApi] over the recorded fixtures — zero requests,
 *  - "now" is Monday 2026-10-05 07:55 Israel time, the morning the fixtures were recorded for,
 *  - the map is a blank local style (no OpenFreeMap tiles),
 *  - the update check answers [latestRelease] instead of asking GitHub.
 * Everything else — stores, alarms, the ride service — is the real app.
 */
class UiTestApp : TransitApp() {
    override val clock: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

    val replay: ReplayTransitApi by lazy {
        ReplayTransitApi(clock) { name -> assets.open("fixtures/$name.json").bufferedReader().use { it.readText() } }
    }
    override val api: TransitApi get() = replay

    override fun mapStyle(night: Boolean): String =
        if (night) "asset://uitest/blank-dark.json" else "asset://uitest/blank.json"

    /** What the update check reports; null = up to date. Set before the Activity starts. */
    @Volatile var latestRelease: LatestRelease? = null

    override val updates: UpdateChecker by lazy {
        object : UpdateChecker(store, BuildConfig.VERSION_NAME) {
            override suspend fun check(now: Instant): LatestRelease? = latestRelease
            override suspend fun checkNow(now: Instant): CheckResult =
                latestRelease?.let(CheckResult::Newer) ?: CheckResult.UpToDate
        }
    }

    /** A clean slate: the saved places below, nothing else saved, replay answering. */
    suspend fun reset(places: List<SavedPlace> = PLACES) {
        replay.failing = false
        latestRelease = null
        store.setSettings(UserSettings())
        store.setPlaces(places)
        store.setTrips(emptyList())
        store.setReminder(null)
        history.clear()
        planCache.clear()
    }

    companion object {
        /** Monday 2026-10-05 07:55 Israel time (UTC+3). */
        val NOW: Instant = Instant.parse("2026-10-05T04:55:00Z")

        // Where the fixtures were recorded (see core ReplayTransitApiTest). Names are what a
        // user would type; Hebrew, as most users' phones are.
        val BGU = SavedPlace("אוניברסיטת בן גוריון", 31.2622, 34.8013)
        val TEL_AVIV = SavedPlace("תל אביב סבידור", 32.0839, 34.7983)
        val MEITAR = SavedPlace("מיתר", 31.3236, 34.9381)
        val REHOVOT = SavedPlace("רחובות", 31.8947, 34.8093)
        val PLACES = listOf(BGU, TEL_AVIV, MEITAR, REHOVOT)
    }
}
