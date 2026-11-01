package il.transit.planner.ui

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
import il.transit.planner.ui.vm.VmTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * "Plan for <day>" (Phase 10 D3): one tap is exactly one `plan`, at the offered time, and a
 * week-earlier offer keeps an arrive-by search arrive-by.
 *
 * On [VmTest] since Phase 11 F3: virtual time, every coroutine on the test's thread. It had its own
 * single-thread Main and a DataStore on `Dispatchers.IO`, and waited with sleeps: the ViewModel
 * appended to [CountingApi.plans] on its thread while the test iterated the list on another
 * (`filter`/`none` on a synchronized list take no lock), a ConcurrentModificationException (T4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayNoticeViewModelTest : VmTest() {
    /** Records every plan and answers with a recorded trip. */
    private class CountingApi : TransitApi {
        val plans: MutableList<PlanRequest> = ArrayList()
        override suspend fun plan(req: PlanRequest): PlanResponse { plans += req; return recorded }
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> = error("geocode")
        override suspend fun oneToMany(one: LatLon, many: List<LatLon>, mode: String, maxSeconds: Int, arriveBy: Boolean): List<Int?> = error("oneToMany")
        override suspend fun stops(box: BBox, modes: Set<String>?, language: String): List<Place> = error("stops")
        override suspend fun reverseGeocode(at: LatLon, language: String, max: Int): List<GeocodeMatch> = error("reverseGeocode")
        override suspend fun stopTimes(stopId: String, time: Instant?, n: Int, language: String, pageCursor: String?): StopTimesResponse = error("stopTimes")
        override suspend fun trip(tripId: String, language: String): Itinerary = error("trip")
        override suspend fun mapTrips(box: BBox, start: Instant, end: Instant, zoom: Double, language: String): List<TripSegment> = error("mapTrips")
    }

    private val now = LocalDateTime.parse("2026-10-16T18:00").atZone(ISRAEL).toInstant() // Friday evening
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun vm(api: TransitApi): MainViewModel {
        fix = LatLon(31.262, 34.801)
        return newVm(api, clock = clock)
    }

    /**
     * Until a plan matching [sent] went out and its answer is shown (virtual time in 100 ms steps),
     * then 300 ms more of virtual time, so a second plan would have gone out too.
     */
    private fun settle(vm: MainViewModel, api: CountingApi, sent: (PlanRequest) -> Boolean) {
        settle {
            advance(100)
            api.plans.any(sent) && !vm.state.value.loading
        }
        advance(300)
    }

    @Test fun `one tap on Plan for Sat 20 00 sends exactly one plan, leaving then`() {
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

    @Test fun `a week-earlier offer for an arrive-by search stays arrive-by`() {
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
