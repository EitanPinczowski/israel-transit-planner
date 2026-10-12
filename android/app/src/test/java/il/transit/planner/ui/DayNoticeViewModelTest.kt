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
import il.transit.core.plan.TimeMode
import il.transit.core.user.SavedPlace
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Collections
import java.util.concurrent.Executors

/**
 * "Plan for <day>" (Phase 10 D3): one tap is exactly one `plan`, at the offered time, and a
 * week-earlier offer keeps an arrive-by search arrive-by. Plain JVM, a real DataStore in a temp folder.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayNoticeViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Records every plan and answers with a recorded trip. */
    private class CountingApi : TransitApi {
        val plans: MutableList<PlanRequest> = Collections.synchronizedList(ArrayList())
        override suspend fun plan(req: PlanRequest): PlanResponse { plans += req; return recorded }
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> = error("geocode")
        override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean): List<Int?> = error("oneToMany")
        override suspend fun stops(box: BBox, modes: Set<String>?, language: String): List<Place> = error("stops")
        override suspend fun reverseGeocode(at: LatLon, language: String, max: Int): List<GeocodeMatch> = error("reverseGeocode")
        override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String): StopTimesResponse = error("stopTimes")
        override suspend fun trip(tripId: String, language: String): Itinerary = error("trip")
        override suspend fun mapTrips(box: BBox, start: Instant, end: Instant, zoom: Double, language: String): List<TripSegment> = error("mapTrips")
    }

    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val io = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val now = LocalDateTime.parse("2026-10-16T18:00").atZone(ISRAEL).toInstant() // Friday evening
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val vms = mutableListOf<MainViewModel>()

    @Before fun setUp() = Dispatchers.setMain(main)

    @After fun tearDown() {
        vms.forEach { it.viewModelScope.cancel() }
        io.cancel()
        Dispatchers.resetMain()
        main.close()
    }

    private fun vm(api: TransitApi): MainViewModel {
        val store = UserStore(PreferenceDataStoreFactory.create(scope = io) { tmp.newFile("user.preferences_pb").also { it.delete() } })
        return MainViewModel(api, store, { "en" }, clock = clock).also { it.locationProvider = { LatLon(31.262, 34.801) }; vms += it }
    }

    /** Waits until a plan matching [sent] went out and the answer is shown, then a little longer so a second one would show. */
    private fun settle(vm: MainViewModel, api: CountingApi, sent: (PlanRequest) -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while ((api.plans.none(sent) || vm.state.value.loading) && System.currentTimeMillis() < until) Thread.sleep(20)
        Thread.sleep(300)
    }

    @Test fun `one tap on Plan for Sat 20 00 sends exactly one plan, leaving then`() = runBlocking {
        val api = CountingApi()
        val vm = vm(api)
        vm.goTo(SavedPlace("Tel Aviv", 32.053978, 34.78484))
        settle(vm, api) { true }
        val resume = LocalDateTime.parse("2026-10-17T20:00").atZone(ISRAEL).toInstant()
        ViewModelActions(vm).planOn(resume)
        settle(vm, api) { it.time == resume }
        // The evening last-ride hint may ask on its own afterwards, at other times; the tap itself is one plan.
        val tapped = api.plans.filter { it.time == resume }
        assertEquals(1, tapped.size)
        assertEquals(false, tapped.single().arriveBy)
        assertEquals(TimeMode.DEPART_AT, vm.state.value.timeMode)
    }

    @Test fun `a week-earlier offer for an arrive-by search stays arrive-by`() = runBlocking {
        val api = CountingApi()
        val vm = vm(api)
        vm.goTo(SavedPlace("Tel Aviv", 32.053978, 34.78484))
        settle(vm, api) { true }
        val instead = LocalDateTime.parse("2026-10-22T08:00").atZone(ISRAEL).toInstant()
        ViewModelActions(vm).planOn(instead, arriveBy = true)
        settle(vm, api) { it.time == instead }
        val tapped = api.plans.filter { it.time == instead }
        assertEquals(1, tapped.size)
        assertEquals(true, tapped.single().arriveBy)
        assertEquals(TimeMode.ARRIVE_BY, vm.state.value.timeMode)
    }

    companion object {
        private val recorded: PlanResponse = MotisJson.decodeFromString(
            PlanResponse.serializer(),
            DayNoticeViewModelTest::class.java.getResource("/fixtures/plan_now_bs_hahagana.json")!!.readText(),
        )
    }
}
