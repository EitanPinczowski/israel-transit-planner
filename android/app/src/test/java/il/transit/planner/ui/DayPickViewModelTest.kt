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
import il.transit.core.present.DayPick
import il.transit.core.user.SavedPlace
import il.transit.planner.data.UserStore
import il.transit.planner.ui.vm.cancelAndJoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Pick a day (Phase 11 E7, `DayPickTest` on the app side): picking a day sends nothing; the search
 * then sends its usual one `plan`, at the picked day; Earlier / Later stay on that day. Plain JVM,
 * a real DataStore in a temp folder, the recorded Be'er Sheva → Tel Aviv pages of Tue 13 Oct 2026.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayPickViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Records every plan; answers the first search and its Earlier / Later pages from recordings. */
    private class PagesApi : TransitApi {
        val plans: MutableList<PlanRequest> = Collections.synchronizedList(ArrayList())
        override suspend fun plan(req: PlanRequest): PlanResponse {
            plans += req
            return when (req.pageCursor) {
                null -> first
                first.previousPageCursor -> earlier
                first.nextPageCursor -> later
                else -> error("cursor ${req.pageCursor}")
            }
        }
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> = error("geocode")
        override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean): List<Int?> = error("oneToMany")
        override suspend fun stops(box: BBox, modes: Set<String>?, language: String): List<Place> = error("stops")
        override suspend fun reverseGeocode(at: LatLon, language: String, max: Int): List<GeocodeMatch> = error("reverseGeocode")
        override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String, pageCursor: String?): StopTimesResponse = error("stopTimes")
        override suspend fun trip(tripId: String, language: String): Itinerary = error("trip")
        override suspend fun mapTrips(box: BBox, start: Instant, end: Instant, zoom: Double, language: String): List<TripSegment> = error("mapTrips")
    }

    private val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val io = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val now = LocalDateTime.parse("2026-10-12T18:00").atZone(ISRAEL).toInstant() // Monday evening
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val vms = mutableListOf<MainViewModel>()

    @Before fun setUp() = Dispatchers.setMain(main)

    @After fun tearDown() {
        cancelAndJoin(vms.map { it.viewModelScope } + io) // see VmSupport.cancelAndJoin
        Dispatchers.resetMain()
        main.close()
    }

    private fun vm(api: TransitApi): MainViewModel {
        val store = UserStore(PreferenceDataStoreFactory.create(scope = io) { tmp.newFile("user.preferences_pb").also { it.delete() } })
        return MainViewModel(api, store, { "en" }, clock = clock).also { it.locationProvider = { LatLon(31.262, 34.801) }; vms += it }
    }

    private fun waitFor(what: String, ok: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!ok() && System.currentTimeMillis() < until) Thread.sleep(20)
        assertTrue(what, ok())
        Thread.sleep(300) // a second request would show by now
    }

    private fun days(vm: MainViewModel): Set<LocalDate> {
        val results = vm.state.value.results!!
        val options = results.itineraries
        return options.mapTo(HashSet()) { DayPick.today(it.start) }
    }

    @Test fun `tomorrow 08 00 - picking sends nothing, the search one plan at that day, Earlier and Later stay on it`() = runBlocking {
        val api = PagesApi()
        val vm = vm(api)
        val tomorrow = DayPick.days(now)[1].date
        val at = DayPick.at(tomorrow, LocalTime.of(8, 0))
        assertEquals(LocalDate.parse("2026-10-13"), tomorrow)

        ViewModelActions(vm).setTime(TimeMode.DEPART_AT, at) // no destination yet: nothing to plan
        Thread.sleep(300)
        assertEquals(emptyList<PlanRequest>(), api.plans.toList())
        assertEquals(DayPick.Word.TOMORROW, DayPick.label(vm.state.value.time!!, now).word)

        vm.goTo(SavedPlace("Tel Aviv", 32.053978, 34.78484))
        waitFor("the search answered") { api.plans.isNotEmpty() && !vm.state.value.loading && vm.state.value.results != null }
        // The evening last-ride hint may ask on its own, at other times; the search itself is one plan, at 08:00 tomorrow.
        val searched = api.plans.filter { it.pageCursor == null && it.time == at }
        assertEquals(1, searched.size)
        assertEquals(false, searched.single().arriveBy)
        assertEquals(setOf(tomorrow), days(vm))

        fun page(cursor: String?) = api.plans.filter { it.pageCursor == cursor }
        vm.earlier()
        waitFor("Earlier merged") { page(first.previousPageCursor).isNotEmpty() && vm.state.value.pages.loading == null }
        assertEquals(at, page(first.previousPageCursor).single().time) // the page repeats the search's own time
        assertEquals(setOf(tomorrow), days(vm))

        vm.later()
        waitFor("Later merged") { page(first.nextPageCursor).isNotEmpty() && vm.state.value.pages.loading == null }
        assertEquals(at, page(first.nextPageCursor).single().time)
        assertEquals(setOf(tomorrow), days(vm))
        assertEquals(at, vm.state.value.time)
        assertEquals(TimeMode.DEPART_AT, vm.state.value.timeMode)
    }

    companion object {
        private fun fixture(name: String): PlanResponse = MotisJson.decodeFromString(
            PlanResponse.serializer(),
            DayPickViewModelTest::class.java.getResource("/fixtures/$name.json")!!.readText(),
        )
        private val first = fixture("plan_pages_first")
        private val earlier = fixture("plan_pages_earlier")
        private val later = fixture("plan_pages_later")
    }
}
