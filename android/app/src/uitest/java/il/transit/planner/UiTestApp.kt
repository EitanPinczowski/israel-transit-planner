package il.transit.planner

import il.transit.core.api.ReplayTransitApi
import il.transit.core.api.TransitApi
import il.transit.core.search.BackupGeocoder
import il.transit.core.update.CheckResult
import il.transit.core.update.LatestRelease
import il.transit.core.user.SavedPlace
import il.transit.core.user.UserSettings
import il.transit.planner.data.UpdateChecker
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
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
    /** Moves "now" past [NOW] (J12 rides on the train's last stretch: the get-off alert waits for it, R2); reset to 0. */
    @Volatile var clockShift: Duration = Duration.ZERO

    override val clock: Clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = NOW.plus(clockShift)
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(ProcessDeathProbe) // the process-death journey (D11)
        LeakConfig.install() // framework-only leak paths are library leaks, not failures (Phase 11 T8)
        registerActivityLifecycleCallbacks(PerfProbe) // the nightly jank and ride-battery reports (Phase 11 T8)
        registerActivityLifecycleCallbacks(UpgradeProbe) // the upgrade test reads the old build's data (Phase 11 T8)
    }

    val replay: ReplayTransitApi by lazy {
        ReplayTransitApi(clock) { name -> assets.open("fixtures/$name.json").bufferedReader().use { it.readText() } }
    }

    /** Holds every `plan` this long before answering, so a search can be caught while it loads (Phase 11 T7); 0 = off. */
    @Volatile var planDelayMs: Long = 0

    private val slowReplay: TransitApi by lazy {
        object : TransitApi by replay {
            override suspend fun plan(req: il.transit.core.api.PlanRequest): il.transit.core.api.PlanResponse {
                if (planDelayMs > 0) kotlinx.coroutines.delay(planDelayMs)
                return replay.plan(req)
            }
        }
    }
    override val api: TransitApi get() = network?.api ?: slowReplay

    /**
     * Phase 11 T4: the network-fault mode. Set (with the local mock server's URL) before the Activity
     * starts, the app talks HTTP to that server instead of answering from the replay; [reset] clears it.
     */
    @Volatile var network: MockNetwork? = null

    /** Starts the fault mode against the mock server at [base] (the emulator's localhost). */
    fun useMockServer(base: String): MockNetwork = MockNetwork(replay, clock, base).also { network = it }

    /** No outside geocoder in UI tests: they never touch the network (the fault mode: Photon on the mock server). */
    override val backupGeocoder: BackupGeocoder? get() = network?.backup

    /**
     * A wet morning, 0 requests (Phase 10 D9): 70% every hour around [NOW], so the rain note shows
     * on the Trip results of every phone profile in ci-screens. Trips past 48 h get no note, as live.
     */
    override val weather: il.transit.core.weather.GuardedWeather get() = network?.weather ?: wetMorning

    private val wetMorning: il.transit.core.weather.GuardedWeather by lazy {
        il.transit.core.weather.GuardedWeather(
            { _ ->
                il.transit.core.weather.RainForecast(
                    (-2L until 60L).map { h ->
                        il.transit.core.weather
                            .RainHour(NOW.plusSeconds(h * 3600), 70, 0.3)
                    },
                )
            },
            clock,
        )
    }

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
        network = null
        planDelayMs = 0
        clockShift = Duration.ZERO
        latestRelease = null
        // Journeys start past the first-run welcome tour (C6).
        store.setSettings(UserSettings(tourSeen = true))
        store.setPlaces(places)
        store.setTrips(emptyList())
        store.setReminder(null)
        history.clear()
        planCache.clear()
        tabCache.clear()
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
