package il.transit.planner.ui.vm

import androidx.lifecycle.SavedStateHandle
import il.transit.core.api.Endpoint
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.Itinerary
import il.transit.core.api.MotisJson
import il.transit.core.api.TransitApi
import il.transit.core.fare.FareProfile
import il.transit.core.geo.LatLon
import il.transit.core.plan.BigStopChanges
import il.transit.core.plan.CarCompare
import il.transit.core.plan.LastRideFinder
import il.transit.core.plan.PlanCache
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripPlanner
import il.transit.core.plan.TripQuery
import il.transit.core.plan.sortOptions
import il.transit.core.update.CheckResult
import il.transit.core.update.LatestRelease
import il.transit.core.update.UpdateState
import il.transit.core.user.SavedPlace
import il.transit.core.user.UserSettings
import il.transit.core.weather.GuardedWeather
import il.transit.core.weather.RainForecast
import il.transit.core.weather.RainHour
import il.transit.core.weather.WeatherSource
import il.transit.planner.data.PlanCacheStore
import il.transit.planner.data.UpdateChecker
import il.transit.planner.data.UserStore
import il.transit.planner.ui.AppMode
import il.transit.planner.ui.Field
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.PlaceRef
import il.transit.planner.ui.SavedUi
import il.transit.planner.ui.rainNoteFor
import il.transit.planner.ui.screens.ReminderCheckView
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/** What the screens read from the ViewModel besides the tabs: the update banner, saved state, settings, rain, pins, typing. */
class AppStateTest : VmTest() {
    @get:Rule val tmp = TemporaryFolder()

    private fun trip(api: ScriptedApi = ScriptedApi(replay()), saved: SavedStateHandle? = null, weather: GuardedWeather? = null, store: UserStore = UserStore(MemoryPrefs())): Pair<MainViewModel, ScriptedApi> {
        val vm = newVm(api, saved = saved, weather = weather, store = store)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        return vm to api
    }

    // --- the update banner and Settings -> Check for updates -----------------------------------------------

    private val release = LatestRelease("0.9.9", "https://github.com/x/y/releases/tag/v0.9.9", "https://github.com/x/y/a.apk", "ab".repeat(32))

    private class FakeChecker(store: UserStore, var newer: LatestRelease?, var manual: CheckResult) : UpdateChecker(store, "0.9.0") {
        var daily = 0
        var asked = 0
        override suspend fun check(now: Instant): LatestRelease? = newer.also { daily++ }
        override suspend fun checkNow(now: Instant): CheckResult = manual.also { asked++ }
    }

    private fun updating(newer: LatestRelease?, manual: CheckResult = CheckResult.UpToDate): Pair<MainViewModel, FakeChecker> {
        val store = UserStore(MemoryPrefs())
        val checker = FakeChecker(store, newer, manual)
        return newVm(ScriptedApi(replay()), store = store, updates = checker) to checker
    }

    @Test fun `update - the daily check shows the banner, and the X hides it`() {
        val (vm, checker) = updating(release)
        assertEquals(1, checker.daily)
        assertEquals(release, vm.state.value.update)
        assertEquals(UpdateState.Available(release), vm.state.value.updateState)
        vm.dismissUpdate()
        assertNull(vm.state.value.update)
    }

    @Test fun `update - nothing newer means no banner`() {
        val (vm, _) = updating(null)
        assertNull(vm.state.value.update)
        assertEquals(UpdateState.Idle, vm.state.value.updateState)
    }

    @Test fun `update - Check for updates goes checking, then up to date, and a second tap in a minute asks nothing`() {
        val (vm, checker) = updating(null)
        val states = record(vm.state.map { it.updateState })
        vm.checkForUpdates()
        assertEquals(listOf(UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate("0.9.0")), states)
        vm.checkForUpdates()
        assertEquals(1, checker.asked)
        assertEquals(UpdateState.UpToDate("0.9.0", tooSoon = true), vm.state.value.updateState)
        advance(61_000) // the fixed clock does not move: still inside the minute
        vm.checkForUpdates()
        assertEquals(1, checker.asked)
    }

    @Test fun `update - a manual check that finds a release offers it, one that fails says so`() {
        val (found, _) = updating(null, CheckResult.Newer(release))
        found.checkForUpdates()
        assertEquals(UpdateState.Available(release), found.state.value.updateState)
        assertEquals(release, found.state.value.update)
        val (failed, _) = updating(null, CheckResult.Failed)
        failed.checkForUpdates()
        assertEquals(UpdateState.CheckFailed(), failed.state.value.updateState)
    }

    @Test fun `update - without an installer Update does nothing`() {
        val (vm, _) = updating(release)
        vm.startUpdate()
        vm.updatePermissionReturned()
        assertEquals(UpdateState.Available(release), vm.state.value.updateState)
    }

    // --- settings -------------------------------------------------------------------------------------------------

    @Test fun `settings - a change is stored and shown, and sends no request by itself`() {
        val (vm, api) = trip()
        val sent = api.total
        val changed = UserSettings(accessible = true, transferBufferMin = 8)
        vm.updateSettings(changed)
        assertEquals(changed, vm.state.value.settings)
        assertEquals(sent, api.total)
    }

    @Test fun `settings - accessible routes and the transfer buffer reach the next search, the fare profile does not`() {
        val (vm, api) = trip()
        assertEquals(UserSettings().preferences(), api.planRequests.last().preferences)
        val changed = UserSettings(accessible = true, transferBufferMin = 8)
        vm.updateSettings(changed)
        vm.plan()
        assertEquals(changed.preferences(), api.planRequests.last().preferences)
        assertTrue(
            api.planRequests
                .last()
                .preferences.wheelchair,
        )
        val fare = changed.copy(fareProfile = FareProfile.entries.first { it != FareProfile.REGULAR })
        vm.updateSettings(fare)
        vm.plan()
        assertEquals(changed.preferences(), api.planRequests.last().preferences)
    }

    @Test fun `settings - the panel opens and closes, history and favourites too`() {
        val vm = newVm(ScriptedApi(replay()))
        vm.showSettings(true)
        assertTrue(vm.state.value.showSettings)
        vm.showSettings(false)
        assertFalse(vm.state.value.showSettings)
        vm.showHistory(true)
        assertTrue(vm.state.value.showHistory)
        vm.showHistory(false)
        assertFalse(vm.state.value.showHistory)
        vm.showFavorites(true)
        assertTrue(vm.state.value.showFavorites)
        vm.showFavorites(false)
        assertFalse(vm.state.value.showFavorites)
    }

    // --- saved places, trips, favourites --------------------------------------------------------------------------

    @Test fun `saved places and trips - save, run, delete`() {
        val (vm, api) = trip()
        vm.savePlace("Work", SAVIDOR)
        assertEquals(
            listOf("Work"),
            vm.state.value.savedPlaces
                .map { it.name },
        )
        assertEquals("Work", (vm.state.value.to as PlaceRef.Point).name) // the open destination takes the name
        vm.saveTrip("Commute")
        val saved = vm.state.value.savedTrips
            .single()
        assertEquals("Commute", saved.name)
        vm.setMode(AppMode.PICK_UP)
        val before = api.count("plan")
        vm.runTrip(saved)
        assertEquals(AppMode.TRIP, vm.state.value.mode)
        assertTrue(api.count("plan") > before)
        vm.deleteTrip(saved)
        assertTrue(
            vm.state.value.savedTrips
                .isEmpty(),
        )
        vm.deletePlace(
            vm.state.value.savedPlaces
                .single(),
        )
        assertTrue(
            vm.state.value.savedPlaces
                .isEmpty(),
        )
    }

    @Test fun `saved places - going to one plans once from here`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.savePlace("Home", SAVIDOR)
        vm.goTo(SavedPlace("Home", SAVIDOR.lat, SAVIDOR.lon))
        assertEquals(1, api.count("plan"))
        assertEquals("results", phase(vm.state.value))
    }

    @Test fun `favourites - pin a line from a stop sheet, then one request per pinned stop`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.openStop("il-Israel-MOT_37314", "Be'er Sheva North")
        val sheet = vm.state.value.stopSheet!!
        val row = sheet.rows.first()
        vm.toggleFavorite(sheet, row)
        assertEquals(1, vm.state.value.favorites.size)
        val before = api.count("stopTimes")
        vm.showFavorites(true)
        assertEquals(before + 1, api.count("stopTimes"))
        assertEquals(
            vm.state.value.favorites
                .toSet(),
            vm.state.value.favoriteBoards.keys,
        )
        vm.toggleFavorite(sheet, row)
        assertTrue(
            vm.state.value.favorites
                .isEmpty(),
        )
    }

    // --- reminder, ride, last trip, by car, way back ---------------------------------------------------------------

    @Test fun `reminder - arm for the opened option, cancel clears it`() {
        val (vm, _) = trip()
        assertTrue(vm.remindSelected())
        assertNotNull(vm.state.value.reminder)
        vm.cancelReminder()
        assertNull(vm.state.value.reminder)
    }

    @Test fun `reminder check - the hint shows once after the first reminder, never again`() {
        val (vm, _) = trip()
        assertEquals(ReminderCheckView.NONE, vm.state.value.reminderCheck)
        assertFalse(vm.state.value.settings.reminderCheckHintShown)
        assertTrue(vm.remindSelected())
        assertEquals(ReminderCheckView.HINT, vm.state.value.reminderCheck)
        assertTrue(vm.state.value.settings.reminderCheckHintShown)
        vm.reminderCheck(ReminderCheckView.NONE) // dismissed
        vm.cancelReminder()
        assertTrue(vm.remindSelected())
        assertEquals(ReminderCheckView.NONE, vm.state.value.reminderCheck)
    }

    @Test fun `reminder check - the check opens and closes from Settings, with no request`() {
        val (vm, api) = trip()
        val sent = api.total
        vm.reminderCheck(ReminderCheckView.OPEN)
        assertEquals(ReminderCheckView.OPEN, vm.state.value.reminderCheck)
        vm.reminderCheck(ReminderCheckView.NONE)
        assertEquals(ReminderCheckView.NONE, vm.state.value.reminderCheck)
        assertEquals(sent, api.total)
    }

    @Test fun `ride - starting needs an option with a ride in it`() {
        val vm = newVm(ScriptedApi(replay()))
        assertFalse(vm.startRide())
        assertFalse(vm.remindSelected())
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        assertTrue(vm.startRide())
        vm.stopRide()
    }

    @Test fun `last trip - asked once per route and day, the second look is free`() {
        val (vm, api) = trip()
        val before = api.count("plan")
        vm.checkLastRide()
        val first = api.count("plan") - before
        assertTrue("budget: $first", first <= LastRideFinder.BUDGET)
        assertFalse(vm.state.value.lastRideLoading)
        assertNotNull(vm.state.value.lastRide)
        assertTrue(vm.state.value.lastRideAsked)
        vm.checkLastRide()
        assertEquals(before + first, api.count("plan"))
        vm.checkLastRideBack()
        assertNotNull(vm.state.value.lastRideBack)
    }

    @Test fun `by car - one request, and an answer for another route is not shown`() {
        val (vm, api) = trip()
        val before = api.count("plan")
        vm.checkCar()
        assertEquals(before + CarCompare.BUDGET, api.count("plan"))
        assertNotNull(vm.state.value.carTime)
        assertFalse(vm.state.value.carLoading)
        api.hold = true
        vm.checkCar() // held
        assertTrue(vm.state.value.carLoading)
        vm.choose(Field.TO, "Haifa", HAIFA) // another route while it waits
        api.releaseAll()
        assertNull(vm.state.value.carTime)
        assertFalse(vm.state.value.carLoading)
    }

    @Test fun `way back - the return trip leaves after the stay, from where this one arrives`() {
        val (vm, api) = trip()
        val arrives = vm.state.value.selectedItinerary!!
            .end
        vm.wayBack(30)
        val request = api.planRequests.last()
        assertEquals(TimeMode.DEPART_AT, vm.state.value.timeMode)
        assertEquals(arrives.plusSeconds(30 * 60L), request.time)
        assertEquals(SAVIDOR, (request.from as Endpoint.Coord).at)
        assertEquals(BGU, (request.to as Endpoint.Coord).at)
        assertEquals(1, api.count("reverseGeocode")) // the way back to "my location" gets a name
    }

    // --- rain ------------------------------------------------------------------------------------------------------------

    private class CountingWeather : WeatherSource {
        var calls = 0
        override suspend fun rain(cell: LatLon): RainForecast {
            calls++
            return RainForecast((0 until 12).map { RainHour(Instant.parse("2026-10-18T04:00:00Z").plusSeconds(it * 3600L), 90, 2.0) })
        }
    }

    @Test fun `rain - one forecast per search area, cached, and only for the next 48 hours`() {
        val source = CountingWeather()
        val (vm, _) = trip(weather = GuardedWeather(source, CLOCK))
        assertEquals(1, source.calls)
        assertEquals(vm.state.value.resultsAt, vm.state.value.rain.forResults)
        assertNotNull(vm.state.value.rain.forecast)
        vm.plan()
        assertEquals(1, source.calls) // the same 10 km cell, inside the hour
        vm.setTime(TimeMode.DEPART_AT, NOW.plusSeconds(3 * 86_400L))
        assertEquals(1, source.calls) // three days ahead: not asked at all
    }

    @Test fun `rain - switched off, nothing is asked`() {
        val source = CountingWeather()
        val store = UserStore(MemoryPrefs())
        runBlocking { store.setSettings(UserSettings(rainAware = false)) }
        trip(weather = GuardedWeather(source, CLOCK), store = store)
        assertEquals(0, source.calls)
    }

    @Test fun `rain - errands get no note`() {
        val source = CountingWeather()
        val (vm, _) = trip(weather = GuardedWeather(source, CLOCK))
        vm.choose(Field.STOP, "Soroka", LatLon(31.2589, 34.7992))
        assertNull(rainNoteFor(vm.state.value))
    }

    // --- safer change at a big stop ----------------------------------------------------------------------------------------

    @Test fun `big stop - opening an option with a small-stop change asks for the two trips once`() {
        val recorded = planFixture("plan_bigstop_bgu_telaviv")
        val trips = listOf("trip_bigstop_5", "trip_bigstop_370", "trip_bigstop_171")
            .map { name -> MotisJson.decodeFromString(Itinerary.serializer(), fixture(name)) }
            .associateBy { it.legs.first { l -> l.isTransit }.tripId!! }
        val book = object : TransitApi by replay() {
            override suspend fun trip(tripId: String, language: String): Itinerary = trips[tripId] ?: error("no recording: $tripId")
        }
        val api = ScriptedApi(book)
        api.planAnswer = { recorded }
        val vm = newVm(api, RECORDED_CLOCK)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        val lines = listOf("370", "171")
        val at = vm.state.value.options
            .indexOfFirst { o -> o.legs.filter { it.isTransit }.map { it.routeShortName } == lines }
        assertTrue("the recorded 370 + 171 option", at >= 0)
        vm.select(at)
        assertEquals(BigStopChanges.BUDGET, api.count("trip"))
        vm.select(0)
        vm.select(at) // opened again: remembered for these results
        assertEquals(BigStopChanges.BUDGET, api.count("trip"))
    }

    // --- pins and typing --------------------------------------------------------------------------------------------------------

    @Test fun `pin - a long press opens the sheet, To here fills the destination and plans`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.onMapLongPress(SAVIDOR)
        assertEquals(
            SAVIDOR,
            vm.state.value.places.pin!!
                .at,
        )
        vm.pinTo(Field.TO)
        assertNull(vm.state.value.places.pin)
        assertEquals(SAVIDOR, (vm.state.value.to as PlaceRef.Point).at)
        assertEquals(1, api.count("plan"))
        vm.onMapLongPress(HAIFA)
        vm.closePin()
        assertNull(vm.state.value.places.pin)
    }

    @Test fun `typing - one request per pause, none for one letter, none after closing the box`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.startEditing(Field.TO)
        vm.onQuery("r")
        advance(1_000)
        assertEquals(0, api.count("geocode"))
        vm.onQuery("ra")
        advance(100)
        vm.onQuery("rag")
        advance(100)
        vm.onQuery("rager")
        assertEquals(0, api.count("geocode")) // still typing
        advance(400)
        assertEquals(1, api.count("geocode"))
        assertTrue(
            vm.state.value.suggestions
                .isNotEmpty(),
        )
        vm.onQuery("ragers")
        vm.cancelEditing()
        advance(1_000)
        assertEquals(1, api.count("geocode"))
        assertNull(vm.state.value.editing)
        assertTrue(
            vm.state.value.suggestions
                .isEmpty(),
        )
    }

    @Test fun `typing - picking an answer fills the field and plans`() {
        val api = ScriptedApi(replay())
        val vm = newVm(api)
        vm.startEditing(Field.TO)
        vm.onQuery("rager")
        advance(500)
        val pick = vm.state.value.suggestions
            .first()
        vm.pick(pick)
        assertNull(vm.state.value.editing)
        assertEquals(pick.name, (vm.state.value.to as PlaceRef.Point).name)
        assertEquals(1, api.count("plan"))
    }

    // --- process death ----------------------------------------------------------------------------------------------------------

    @Test fun `saved state - a search and an opened ride are written as they happen`() {
        val handle = SavedStateHandle()
        val (vm, _) = trip(saved = handle)
        val read = { SavedUi.read { k -> handle.get<Any?>(k) } }
        assertEquals(AppMode.TRIP, read()!!.mode)
        assertEquals(PlaceRef.Point("Savidor", SAVIDOR), read()!!.to)
        assertEquals(PlanCache.key("TRIP-NOW", BGU, SAVIDOR), read()!!.cacheKey)
        vm.select(1)
        assertEquals(1, read()!!.selected)
        vm.rideSheet(2)
        assertEquals(SavedUi.Sheet.Ride(2), read()!!.sheet)
        vm.rideSheet(null)
        assertNull(read()!!.sheet)
    }

    private fun savedCopy(): Triple<PlanCacheStore, Itinerary, String> {
        val planned = runBlocking {
            TripPlanner(replay()).plan(TripQuery(Endpoint.Coord(BGU), Endpoint.Coord(SAVIDOR), TimeMode.NOW), NOW)
        }
        val key = PlanCache.key("TRIP-NOW", BGU, SAVIDOR)
        val cache = PlanCacheStore(tmp.newFile("trip_cache.json").also { it.delete() }, CLOCK)
        runBlocking { cache.put(key, planned) }
        return Triple(cache, sortOptions(planned, UserSettings().tripSort, false)[1], key)
    }

    @Test fun `saved state - back from process death the search and the chosen option return with no request`() {
        val (cache, chosen, key) = savedCopy()
        val handle = SavedStateHandle()
        SavedUi(
            mode = AppMode.TRIP,
            from = PlaceRef.Point("BGU", BGU),
            to = PlaceRef.Point("Savidor", SAVIDOR),
            timeMode = TimeMode.NOW,
            selected = 0,
            optionId = MainViewModel.optionId(chosen),
            cacheKey = key,
        ).write { k, v -> handle[k] = v }
        val api = ScriptedApi(replay())
        val vm = newVm(api, planCache = cache, saved = handle)
        assertEquals(PlaceRef.Point("Savidor", SAVIDOR), vm.state.value.to) // the fields at once
        settle { vm.state.value.results != null }
        assertEquals(chosen, vm.state.value.selectedItinerary)
        assertEquals(0, api.total)
    }

    @Test fun `saved state - with no saved copy only the fields come back, and nothing is searched`() {
        val handle = SavedStateHandle()
        SavedUi(mode = AppMode.PICK_UP, from = PlaceRef.MyLocation, to = PlaceRef.Point("Savidor", SAVIDOR), timeMode = TimeMode.NOW)
            .write { k, v -> handle[k] = v }
        val api = ScriptedApi(replay())
        val vm = newVm(api, saved = handle)
        assertEquals(AppMode.PICK_UP, vm.state.value.mode)
        assertEquals(PlaceRef.Point("Savidor", SAVIDOR), vm.state.value.to)
        assertNull(vm.state.value.results)
        assertEquals(0, api.total)
        assertNull(vm.takeRestoredRide())
    }

    @Test fun `saved state - an open stop sheet is asked for again`() {
        val handle = SavedStateHandle()
        SavedUi(sheet = SavedUi.Sheet.Stop("il-Israel-MOT_37314", "Be'er Sheva North")).write { k, v -> handle[k] = v }
        val api = ScriptedApi(replay())
        val vm = newVm(api, saved = handle)
        assertEquals(
            "Be'er Sheva North",
            vm.state.value.stopSheet!!
                .name,
        )
        assertEquals(1, api.count("stopTimes"))
    }

    @Test fun `guarded - the same search twice in a minute is one request through the guard`() {
        val api = ScriptedApi(replay())
        val vm = newVm(GuardedTransitApi(api, CLOCK))
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        vm.choose(Field.TO, "Savidor", SAVIDOR)
        assertEquals(1, api.count("plan"))
    }
}
