package il.transit.planner.ui

import il.transit.core.api.TransitApi
import il.transit.core.geo.LatLon
import il.transit.core.search.BackupGeocoder
import il.transit.core.search.PinName
import il.transit.core.search.Recents
import il.transit.core.user.SavedPlace
import il.transit.planner.data.UserStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A long-pressed point (S1). [name] stays null while [naming] and when nothing is near (offline). */
data class MapPin(val at: LatLon, val name: String? = null, val naming: Boolean = true)

/**
 * A place about to be saved: from a pin, or from the location fix (S2). [at] null = no fix
 * yet, the dialog says so. [name] is the suggested name, filled in by reverse geocoding.
 */
data class PlaceDraft(val at: LatLon?, val name: String? = null, val naming: Boolean = false, val asHome: Boolean = false)

/** The search fix's state: the map pin sheet, the save dialog, recent picks, and whether Photon answered. */
data class PlacesUi(
    val pin: MapPin? = null,
    val draft: PlaceDraft? = null,
    val recents: List<SavedPlace> = emptyList(),
    /** Some suggestions shown came from Photon (OSM data): its credit shows under them. */
    val backupShown: Boolean = false,
    /** The search finished and no geocoder knows the text: say so and offer the map, never a blank list. */
    val notFound: Boolean = false,
)

/**
 * Long-press sheet, "save my current location" and recent picks. One name lookup per pin or
 * per "save here" tap: the phone's Geocoder ("טבנקין 15, רעננה"), else Transitous's reverse
 * geocode, each cached a day; offline the name falls back to the coordinates (the screen
 * shows "Pin · 32.1801, 34.8712").
 */
internal class PlacePicker(
    private val api: TransitApi,
    /** The phone's Geocoder (then Photon): names a point first; Transitous when it can't. */
    private val backup: BackupGeocoder?,
    private val store: UserStore,
    private val language: () -> String,
    private val scope: CoroutineScope,
    private val edit: ((UiState) -> UiState) -> Unit,
    private val current: () -> UiState,
) {
    private fun places(f: (PlacesUi) -> PlacesUi) = edit { it.copy(places = f(it.places)) }

    fun longPress(at: LatLon) {
        edit { it.copy(places = it.places.copy(pin = MapPin(at)), editing = null, stopSheet = null) }
        scope.launch {
            val found = nameOf(at)
            edit { st ->
                // "From/To here" may have been tapped before the name came: it names that field too.
                fun named(ref: PlaceRef?): PlaceRef? {
                    val point = ref as? PlaceRef.Point ?: return ref
                    return if (found != null && point.at == at && point.name == null) point.copy(name = found) else ref
                }
                st.copy(
                    places = if (st.places.pin?.at == at) st.places.copy(pin = MapPin(at, found, naming = false)) else st.places,
                    from = named(st.from) ?: st.from,
                    to = named(st.to),
                    driverTo = named(st.driverTo),
                )
            }
        }
    }

    fun closePin() = places { it.copy(pin = null) }

    /** "Save as place…": the dialog, with the pin's name to start from. Home is offered ticked when none is set. */
    fun saveFromPin() {
        val pin = current().places.pin ?: return
        places { it.copy(pin = null, draft = PlaceDraft(pin.at, pin.name, naming = pin.naming, asHome = noHome())) }
        if (pin.naming) nameDraft(pin.at)
    }

    /** "Save my current location": the fix now; no fix → the dialog explains. */
    fun saveHere(fix: LatLon?) {
        places { it.copy(draft = PlaceDraft(fix, naming = fix != null, asHome = noHome())) }
        if (fix != null) nameDraft(fix)
    }

    fun cancelDraft() = places { it.copy(draft = null) }

    fun remember(picked: SavedPlace) = scope.launch {
        store.setRecents(Recents.add(current().places.recents, picked))
    }

    private fun noHome() = current().let { s -> s.settings.homePlace == null || s.savedPlaces.none { it.name == s.settings.homePlace } }

    private fun nameDraft(at: LatLon) = scope.launch {
        val name = nameOf(at)
        places { p ->
            val d = p.draft
            if (d != null && d.at == at) p.copy(draft = d.copy(name = d.name ?: name, naming = false)) else p
        }
    }

    private suspend fun nameOf(at: LatLon): String? = backup?.reverseName(at, language()) ?: try {
        PinName.of(api.reverseGeocode(at, language(), 1))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
