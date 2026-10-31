package il.transit.planner.ui.vm

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.Itinerary
import il.transit.core.api.MotisJson
import il.transit.core.api.Place
import il.transit.core.api.PlanRequest
import il.transit.core.api.PlanResponse
import il.transit.core.api.ReplayTransitApi
import il.transit.core.api.StopTimesResponse
import il.transit.core.api.TransitApi
import il.transit.core.api.TripSegment
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import il.transit.core.weather.GuardedWeather
import il.transit.planner.data.PlanCacheStore
import il.transit.planner.data.UpdateChecker
import il.transit.planner.data.UserStore
import il.transit.planner.ui.Field
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.PlaceRef
import il.transit.planner.ui.Suggestion
import il.transit.planner.ui.UiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections
import java.util.concurrent.CancellationException

/** A DataStore that lives in memory and answers at once: no files, no threads, nothing to wait for. */
class MemoryPrefs : DataStore<Preferences> {
    private val current = MutableStateFlow<Preferences>(emptyPreferences())
    override val data: Flow<Preferences> = current

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(current.value)
        current.value = next
        return next
    }
}

/**
 * A [TransitApi] in front of [inner] (usually [ReplayTransitApi]) that the test steers:
 *  - [hold]: every `plan` waits until [release] lets it go, so a search can be seen "searching";
 *  - [fault]: throws what the real client throws, by call kind and its 1-based number;
 *  - [planAnswer]: replaces what `plan` answers (empty, a recorded page, ...);
 *  - every call is counted by kind, whatever happens to it.
 */
class ScriptedApi(private val inner: TransitApi) : TransitApi {
    private val calls: MutableList<String> = Collections.synchronizedList(ArrayList())
    val planRequests: MutableList<PlanRequest> = Collections.synchronizedList(ArrayList())

    @Volatile var hold = false

    @Volatile var fault: (kind: String, n: Int) -> Throwable? = { _, _ -> null }

    @Volatile var planAnswer: ((PlanRequest) -> PlanResponse)? = null

    private val waiting = ArrayList<CompletableDeferred<Unit>>()

    /** Plans that were held and then cancelled (a newer search replaced them). */
    @Volatile var cancelledPlans = 0

    val pendingPlans: Int get() = synchronized(waiting) { waiting.size }

    fun count(kind: String): Int = synchronized(calls) { calls.count { it == kind } }

    val total: Int get() = calls.size

    /** Lets the [n] oldest held plans go on. */
    fun release(n: Int = 1) {
        repeat(n) { synchronized(waiting) { if (waiting.isEmpty()) null else waiting.removeAt(0) }?.complete(Unit) }
    }

    /** Lets every held plan go on, including the ones a planner sends once the first ones are answered. */
    fun releaseAll() {
        var rounds = 0
        while (pendingPlans > 0 && rounds++ < 100) release()
    }

    private fun note(kind: String): Int = synchronized(calls) {
        calls += kind
        calls.count { it == kind }
    }

    private inline fun <T> call(kind: String, answer: () -> T): T {
        fault(kind, note(kind))?.let { throw it }
        return answer()
    }

    override suspend fun plan(req: PlanRequest): PlanResponse {
        val n = note("plan")
        planRequests += req
        if (hold) {
            val gate = CompletableDeferred<Unit>()
            synchronized(waiting) { waiting += gate }
            try {
                gate.await()
            } catch (e: CancellationException) {
                synchronized(waiting) { waiting.remove(gate) }
                cancelledPlans++
                throw e
            }
        }
        fault("plan", n)?.let { throw it }
        return planAnswer?.invoke(req) ?: inner.plan(req)
    }

    override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean): List<Int?> =
        call("oneToMany") { inner.oneToMany(one, many, mode, maxSeconds, arriveBy) }

    override suspend fun stops(box: BBox, modes: Set<String>?, language: String): List<Place> =
        call("stops") { inner.stops(box, modes, language) }

    override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> =
        call("geocode") { inner.geocode(text, language, near, max) }

    override suspend fun reverseGeocode(at: LatLon, language: String, max: Int): List<GeocodeMatch> =
        call("reverseGeocode") { inner.reverseGeocode(at, language, max) }

    override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String, pageCursor: String?): StopTimesResponse =
        call("stopTimes") { inner.stopTimes(stopId, time, n, language, pageCursor) }

    override suspend fun trip(tripId: String, language: String): Itinerary =
        call("trip") { inner.trip(tripId, language) }

    override suspend fun mapTrips(box: BBox, start: Instant, end: Instant, zoom: Double, language: String): List<TripSegment> =
        call("mapTrips") { inner.mapTrips(box, start, end, zoom, language) }
}

/**
 * Cancels [scopes] and waits until every coroutine in them has ended, before a test resets Main.
 * Cancelling is not enough: a coroutine inside `withContext(Dispatchers.IO)` (the Trip cache file) finishes
 * its block first, then resumes through Dispatchers.Main, by then concurrently with the next test's
 * `setMain` ("Dispatchers.Main is used concurrently with setting it", the "screenshots" job, run 39490612517).
 */
fun cancelAndJoin(scopes: List<CoroutineScope>) = runBlocking {
    val jobs = scopes.map { it.coroutineContext.job }
    jobs.forEach { it.cancel() }
    withTimeout(10_000) { jobs.joinAll() }
}

/** Where the screen is, in one word: the sequence of these is what the tests pin down. */
fun phase(s: UiState): String = when {
    s.loading -> "searching"
    s.error != null -> "error:${s.error}"
    s.offlineSince != null -> "offline"
    s.hasResults -> "results"
    else -> "idle"
}

/**
 * Base of the ViewModel state tests. Main is a virtual-time [UnconfinedTestDispatcher], so a
 * ViewModel's coroutines run the moment they are launched or resumed, on the test's own thread:
 * no sleeping, no races. [scheduler] moves virtual time (debounces, retry waits, the refresh loop).
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class VmTest {
    protected val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val made = ArrayList<ViewModel>()

    /** What "my location" is for the ViewModels made by [newVm]; null = no fix. */
    protected var fix: LatLon? = BGU

    @Before fun mainOn() = Dispatchers.setMain(dispatcher)

    @After fun mainOff() {
        cancelAndJoin(made.map { it.viewModelScope } + scope)
        Dispatchers.resetMain()
    }

    protected fun <T : ViewModel> track(vm: T): T = vm.also { made += it }

    protected fun replay(clock: Clock = CLOCK) = ReplayTransitApi(clock) { name -> fixture(name) }

    protected fun newVm(
        api: TransitApi,
        clock: Clock = CLOCK,
        store: UserStore = UserStore(MemoryPrefs()),
        planCache: PlanCacheStore<il.transit.core.plan.TripResult>? = null,
        weather: GuardedWeather? = null,
        updates: UpdateChecker? = null,
        saved: SavedStateHandle? = null,
        rides: il.transit.planner.Rides? = null,
        reminders: il.transit.planner.Reminders? = null,
        tabCache: PlanCacheStore<il.transit.core.plan.TabResult>? = null,
    ): MainViewModel =
        track(MainViewModel(api, store, { "en" }, planCache = planCache, reminders = reminders, rides = rides, updates = updates, clock = clock, weather = weather, saved = saved, tabCache = tabCache))
            .also { it.locationProvider = { fix } }

    /** Every distinct [phase] the ViewModel's state goes through, from now on (the current one first). */
    protected fun phasesOf(vm: MainViewModel): MutableList<String> = record(vm.state.map(::phase))

    /** Every distinct value [flow] takes from now on, the current one first. */
    protected fun <T> record(flow: Flow<T>): MutableList<T> {
        val seen: MutableList<T> = Collections.synchronizedList(ArrayList())
        scope.launch { flow.distinctUntilChanged().collect { seen += it } }
        return seen
    }

    protected fun advance(ms: Long) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    /** For the few steps that hop to a real IO thread (the Trip cache file): waits for [cond]. */
    protected fun settle(timeoutMs: Long = 5_000, cond: () -> Boolean) {
        val until = System.currentTimeMillis() + timeoutMs
        while (!cond() && System.currentTimeMillis() < until) Thread.sleep(10)
        assertTrue("timed out waiting", cond())
    }

    protected fun MainViewModel.choose(field: Field, name: String, at: LatLon) {
        startEditing(field)
        pick(Suggestion(name, null, at, saved = true, isStop = false))
    }

    companion object {
        /** Sunday 2026-10-18 08:00 in Israel (UTC+3): a plain weekday morning, no evening hint. */
        val NOW: Instant = Instant.parse("2026-10-18T05:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

        /**
         * Thursday 2026-09-24 08:00 in Israel, before every recorded plan (the earliest boards on 3 Oct):
         * for the tests that answer with a recording as it is. At [CLOCK] those trips have already left,
         * and the live Trip search drops what already left (Phase 11 E7).
         */
        val RECORDED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-24T05:00:00Z"), ZoneOffset.UTC)

        val BGU = LatLon(31.262, 34.801)
        val SAVIDOR = LatLon(32.08, 34.79)
        val HAIFA = LatLon(32.794, 34.989)
        val MEITAR = LatLon(31.3236, 34.9381)
        val TEL_AVIV = LatLon(32.0839, 34.7983)
        val RAMAT_GAN = LatLon(32.08, 34.81)

        fun fixture(name: String): String = VmTest::class.java.getResource("/fixtures/$name.json")!!.readText()

        fun planFixture(name: String): PlanResponse = MotisJson.decodeFromString(PlanResponse.serializer(), fixture(name))

        fun point(name: String, at: LatLon) = PlaceRef.Point(name, at)
    }
}
