package il.transit.planner.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.Itinerary
import il.transit.core.api.MotisJson
import il.transit.core.api.Place
import il.transit.core.api.PlanRequest
import il.transit.core.api.PlanResponse
import il.transit.core.api.StopTimesResponse
import il.transit.core.api.TransitApi
import il.transit.core.api.TripSegment
import il.transit.core.features.ISRAEL
import il.transit.core.geo.BBox
import il.transit.core.geo.LatLon
import il.transit.core.plan.PlanCache
import il.transit.core.plan.TripPages
import il.transit.core.plan.TripResult
import il.transit.core.user.SavedPlace
import il.transit.core.user.UserSettings
import il.transit.planner.data.PlanCacheStore
import il.transit.planner.data.UserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.Executors

/**
 * The Quick Settings tile's tap (Phase 9 C4): `openTripHome()` costs exactly one `plan`, and
 * none with no Home or no place to start from. Plain JVM, a real DataStore in a temp folder.
 * (A cold start through MainActivity needs a device: the uitest journeys.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripHomeViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Counts plans and answers with the recorded Saturday-night trip; anything else is a bug. */
    private class CountingApi(private val offline: Boolean = false) : TransitApi {
        var plans = 0
        override suspend fun plan(req: PlanRequest): PlanResponse {
            plans++
            if (offline) throw IOException("no signal")
            return recorded
        }
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> = error("geocode")
        override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean): List<Int?> = error("oneToMany")
        override suspend fun stops(box: BBox, modes: Set<String>?, language: String): List<Place> = error("stops")
        override suspend fun reverseGeocode(at: LatLon, language: String, max: Int): List<GeocodeMatch> = error("reverseGeocode")
        override suspend fun stopTimes(stopId: String, time: java.time.Instant?, n: Int, language: String): StopTimesResponse = error("stopTimes")
        override suspend fun trip(tripId: String, language: String): Itinerary = error("trip")
        override suspend fun mapTrips(box: BBox, start: java.time.Instant, end: java.time.Instant, zoom: Double, language: String): List<TripSegment> = error("mapTrips")
    }

    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val io = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 23:20 Saturday night, just before the recorded options. */
    private val now = LocalDateTime.parse("2026-10-03T23:20").atZone(ISRAEL).toInstant()
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val bs = LatLon(31.262, 34.801) // the recorded origin
    private val homePlace = SavedPlace("Home", 32.053978, 34.78484) // the recorded destination

    @Before fun setUp() = Dispatchers.setMain(main)
    /** Every ViewModel made, so its collectors stop before Main is reset (else they resume on Android's Main). */
    private val vms = mutableListOf<MainViewModel>()

    @After fun tearDown() {
        vms.forEach { it.viewModelScope.cancel() }
        io.cancel()
        Dispatchers.resetMain()
        main.close()
    }

    private fun vm(api: TransitApi, withHome: Boolean = true, cached: Boolean = false, fix: LatLon?): MainViewModel = runBlocking {
        val store = UserStore(PreferenceDataStoreFactory.create(scope = io) { tmp.newFile("user.preferences_pb").also { it.delete() } })
        if (withHome) {
            store.setPlaces(listOf(homePlace))
            store.setSettings(UserSettings(homePlace = "Home"))
        }
        val cache = PlanCacheStore(tmp.newFile("trip_cache.json").also { it.delete() }, clock)
        if (cached) cache.put(PlanCache.key("TRIP-NOW", bs, homePlace.latLon), recordedResult)
        MainViewModel(api, store, "en", planCache = cache, clock = clock).also { it.locationProvider = { fix }; vms += it }
    }

    @Test fun `a tap with a fix plans home once, from my location, first option selected`() = runBlocking {
        val api = CountingApi()
        val vm = vm(api, fix = LatLon(31.25, 34.79))
        vm.openTripHome().join()
        val s = vm.state.value
        assertEquals(1, api.plans)
        assertEquals(PlaceRef.MyLocation, s.from)
        assertEquals("Home", (s.to as PlaceRef.Point).name)
        assertEquals(AppMode.TRIP, s.mode)
        assertNotNull(s.results)
        assertEquals(0, s.selected)
    }

    @Test fun `location denied - one plan from the cached trip's origin`() = runBlocking {
        val api = CountingApi()
        val vm = vm(api, cached = true, fix = null)
        vm.openTripHome().join()
        assertEquals(1, api.plans)
        assertEquals(bs, (vm.state.value.from as PlaceRef.Point).at)
    }

    @Test fun `location denied and nothing cached - no request, the no-location error, no spinner`() = runBlocking {
        val api = CountingApi()
        val vm = vm(api, fix = null)
        vm.openTripHome().join()
        val s = vm.state.value
        assertEquals(0, api.plans)
        assertEquals(UiError.NO_LOCATION, s.error)
        assertFalse(s.loading)
    }

    @Test fun `no Home (tile added first) - Settings, no request`() = runBlocking {
        val api = CountingApi()
        val vm = vm(api, withHome = false, fix = LatLon(31.25, 34.79))
        vm.openTripHome().join()
        assertEquals(0, api.plans)
        assertTrue(vm.state.value.showSettings)
    }

    @Test fun `already at Home - still exactly one plan`() = runBlocking {
        val api = CountingApi()
        val vm = vm(api, fix = homePlace.latLon)
        vm.openTripHome().join()
        assertEquals(1, api.plans)
    }

    @Test fun `offline a little off the cached origin - one try, then the cached trip home with the banner`() = runBlocking {
        val api = CountingApi(offline = true)
        val vm = vm(api, cached = true, fix = LatLon(31.27, 34.81)) // ~1 km away: another cache key
        vm.openTripHome().join()
        val s = vm.state.value
        assertEquals(1, api.plans)
        assertNotNull(s.results)
        assertEquals(now, s.offlineSince)
        assertEquals(null, s.error)
    }

    companion object {
        private val recorded: PlanResponse = MotisJson.decodeFromString(
            PlanResponse.serializer(),
            TripHomeViewModelTest::class.java.getResource("/fixtures/plan_now_bs_hahagana.json")!!.readText(),
        )
        private val recordedResult = TripResult(TripPages.order(TripPages.sane(recorded.itineraries), false), null)
    }
}
