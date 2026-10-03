package il.transit.planner.ui

import il.transit.core.geo.LatLon
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripSort
import il.transit.core.present.DepartureRow
import il.transit.core.user.FavoriteLine
import il.transit.core.user.PlaceRoutine
import il.transit.core.user.SavedPlace
import il.transit.core.user.SavedTrip
import il.transit.core.user.UserSettings
import java.time.Instant

/**
 * Everything [MainScreen] asks the ViewModel to do. The screen depends on this, not on
 * [MainViewModel], so screenshot tests and previews can render it from a plain [UiState].
 */
interface MainActions {
    fun dismissUpdate()
    fun startEditing(field: Field)
    fun cancelEditing()
    fun onQuery(text: String)
    fun pick(s: Suggestion)
    fun pickMyLocation()
    fun swap()
    fun setTime(mode: TimeMode, time: Instant?)
    fun setMode(mode: AppMode)
    fun setMaxDrive(min: Int)
    fun setMaxDetour(min: Int)
    fun setMaxPickUpDrive(min: Int)
    fun plan()
    fun select(index: Int)
    fun clearResults()
    fun cancelReminder()
    fun stopRide()
    fun setTripSort(sort: TripSort)
    fun checkLastRide()
    fun checkLastRideBack()
    fun closeStop()
    fun showSettings(show: Boolean)
    fun showHistory(show: Boolean)
    fun updateSettings(s: UserSettings)
    fun runTrip(t: SavedTrip)
    fun goTo(p: SavedPlace)
    fun savePlace(name: String, at: LatLon)
    fun saveTrip(name: String)
    fun deletePlace(p: SavedPlace)
    fun deleteTrip(t: SavedTrip)
    fun clearHistory()
    fun showFavorites(show: Boolean)
    fun toggleFavorite(sheet: StopSheet, row: DepartureRow)
    fun removeFavorite(fav: FavoriteLine)
    fun setRoutine(place: SavedPlace, routine: PlaceRoutine?)
    fun dismissRoutine()
    fun removeChainStop(index: Int)
    fun setStay(index: Int, minutes: Int)
    fun checkCar()
    fun wayBack(stayMin: Int)
}

/** The real thing: forwards to the ViewModel (whose methods may return Jobs; ignored). */
class ViewModelActions(private val vm: MainViewModel) : MainActions {
    override fun dismissUpdate() { vm.dismissUpdate() }
    override fun startEditing(field: Field) { vm.startEditing(field) }
    override fun cancelEditing() { vm.cancelEditing() }
    override fun onQuery(text: String) { vm.onQuery(text) }
    override fun pick(s: Suggestion) { vm.pick(s) }
    override fun pickMyLocation() { vm.pickMyLocation() }
    override fun swap() { vm.swap() }
    override fun setTime(mode: TimeMode, time: Instant?) { vm.setTime(mode, time) }
    override fun setMode(mode: AppMode) { vm.setMode(mode) }
    override fun setMaxDrive(min: Int) { vm.setMaxDrive(min) }
    override fun setMaxDetour(min: Int) { vm.setMaxDetour(min) }
    override fun setMaxPickUpDrive(min: Int) { vm.setMaxPickUpDrive(min) }
    override fun plan() { vm.plan() }
    override fun select(index: Int) { vm.select(index) }
    override fun clearResults() { vm.clearResults() }
    override fun cancelReminder() { vm.cancelReminder() }
    override fun stopRide() { vm.stopRide() }
    override fun setTripSort(sort: TripSort) { vm.setTripSort(sort) }
    override fun checkLastRide() { vm.checkLastRide() }
    override fun checkLastRideBack() { vm.checkLastRideBack() }
    override fun closeStop() { vm.closeStop() }
    override fun showSettings(show: Boolean) { vm.showSettings(show) }
    override fun showHistory(show: Boolean) { vm.showHistory(show) }
    override fun updateSettings(s: UserSettings) { vm.updateSettings(s) }
    override fun runTrip(t: SavedTrip) { vm.runTrip(t) }
    override fun goTo(p: SavedPlace) { vm.goTo(p) }
    override fun savePlace(name: String, at: LatLon) { vm.savePlace(name, at) }
    override fun saveTrip(name: String) { vm.saveTrip(name) }
    override fun deletePlace(p: SavedPlace) { vm.deletePlace(p) }
    override fun deleteTrip(t: SavedTrip) { vm.deleteTrip(t) }
    override fun clearHistory() { vm.clearHistory() }
    override fun showFavorites(show: Boolean) { vm.showFavorites(show) }
    override fun toggleFavorite(sheet: StopSheet, row: DepartureRow) { vm.toggleFavorite(sheet, row) }
    override fun removeFavorite(fav: FavoriteLine) { vm.removeFavorite(fav) }
    override fun setRoutine(place: SavedPlace, routine: PlaceRoutine?) { vm.setRoutine(place, routine) }
    override fun dismissRoutine() { vm.dismissRoutine() }
    override fun removeChainStop(index: Int) { vm.removeChainStop(index) }
    override fun setStay(index: Int, minutes: Int) { vm.setStay(index, minutes) }
    override fun checkCar() { vm.checkCar() }
    override fun wayBack(stayMin: Int) { vm.wayBack(stayMin) }
}

/** Does nothing: for previews and screenshot tests. */
object NoActions : MainActions {
    override fun dismissUpdate() = Unit
    override fun startEditing(field: Field) = Unit
    override fun cancelEditing() = Unit
    override fun onQuery(text: String) = Unit
    override fun pick(s: Suggestion) = Unit
    override fun pickMyLocation() = Unit
    override fun swap() = Unit
    override fun setTime(mode: TimeMode, time: Instant?) = Unit
    override fun setMode(mode: AppMode) = Unit
    override fun setMaxDrive(min: Int) = Unit
    override fun setMaxDetour(min: Int) = Unit
    override fun setMaxPickUpDrive(min: Int) = Unit
    override fun plan() = Unit
    override fun select(index: Int) = Unit
    override fun clearResults() = Unit
    override fun cancelReminder() = Unit
    override fun stopRide() = Unit
    override fun setTripSort(sort: TripSort) = Unit
    override fun checkLastRide() = Unit
    override fun checkLastRideBack() = Unit
    override fun closeStop() = Unit
    override fun showSettings(show: Boolean) = Unit
    override fun showHistory(show: Boolean) = Unit
    override fun updateSettings(s: UserSettings) = Unit
    override fun runTrip(t: SavedTrip) = Unit
    override fun goTo(p: SavedPlace) = Unit
    override fun savePlace(name: String, at: LatLon) = Unit
    override fun saveTrip(name: String) = Unit
    override fun deletePlace(p: SavedPlace) = Unit
    override fun deleteTrip(t: SavedTrip) = Unit
    override fun clearHistory() = Unit
    override fun showFavorites(show: Boolean) = Unit
    override fun toggleFavorite(sheet: StopSheet, row: DepartureRow) = Unit
    override fun removeFavorite(fav: FavoriteLine) = Unit
    override fun setRoutine(place: SavedPlace, routine: PlaceRoutine?) = Unit
    override fun dismissRoutine() = Unit
    override fun removeChainStop(index: Int) = Unit
    override fun setStay(index: Int, minutes: Int) = Unit
    override fun checkCar() = Unit
    override fun wayBack(stayMin: Int) = Unit
}
