package il.transit.core.search

import il.transit.core.api.GeocodeMatch
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import il.transit.core.user.SavedPlace
import java.util.Locale

/** A suggestion from the phone itself: no request. */
data class LocalHit(val place: SavedPlace, val kind: Kind) {
    enum class Kind { HOME, SAVED, RECENT }
}

/**
 * While typing, what the user already has comes first: Home, then the other saved places,
 * then places picked recently, each kept when it matches the text (every typed word appears in
 * its name; blank text matches all). Server answers follow, minus the ones that are a local
 * hit already. Pure and request-free (owner's pick 20, 2026-10-07).
 */
object LocalFirst {
    /** Recent picks shown at most, typed or not. */
    const val MAX_RECENT_SHOWN = 4

    /** A recent pick this close to a saved place with the same name is that place. */
    private const val SAME_PLACE_M = 100.0

    fun suggest(text: String, saved: List<SavedPlace>, homeName: String?, recents: List<SavedPlace>): List<LocalHit> {
        val home = saved.firstOrNull { it.name == homeName }
        val savedHits = buildList {
            home?.takeIf { matches(text, it.name) }?.let { add(LocalHit(it, LocalHit.Kind.HOME)) }
            saved.filter { it !== home && matches(text, it.name) }.forEach { add(LocalHit(it, LocalHit.Kind.SAVED)) }
        }
        val recentHits = recents
            .filter { r -> matches(text, r.name) && saved.none { same(it, r) } }
            .take(MAX_RECENT_SHOWN)
            .map { LocalHit(it, LocalHit.Kind.RECENT) }
        return savedHits + recentHits
    }

    /** [server] answers without the ones a [local] hit already shows. */
    fun serverAfter(local: List<LocalHit>, server: List<GeocodeMatch>): List<GeocodeMatch> =
        server.filterNot { m -> local.any { same(it.place, SavedPlace(m.name, m.lat, m.lon)) } }

    fun matches(text: String, name: String): Boolean {
        val words = text.split(Regex("[\\s,]+")).map(PlaceSearch::normalize).filter { it.isNotEmpty() }
        val n = PlaceSearch.normalize(name)
        return words.all { n.contains(it) }
    }

    private fun same(a: SavedPlace, b: SavedPlace) =
        PlaceSearch.normalize(a.name) == PlaceSearch.normalize(b.name) && Geo.distanceM(a.latLon, b.latLon) <= SAME_PLACE_M
}

/** The places picked from search lately, newest first: what [LocalFirst] offers as "recent". */
object Recents {
    const val MAX = 10

    /** Within this, the same name is the same place: picking it again moves it to the top. */
    private const val SAME_PLACE_M = 100.0

    fun add(recents: List<SavedPlace>, picked: SavedPlace): List<SavedPlace> =
        (listOf(picked) + recents.filterNot {
            PlaceSearch.normalize(it.name) == PlaceSearch.normalize(picked.name) && Geo.distanceM(it.latLon, picked.latLon) <= SAME_PLACE_M
        }).take(MAX)
}

/** Naming a point picked on the map (long-press) or a location fix. */
object PinName {
    /** The reverse geocoder's nearest answer, else null (offline, nothing near). */
    fun of(reverse: List<GeocodeMatch>): String? = reverse.firstOrNull()?.name?.takeIf { it.isNotBlank() }

    /** "32.1801, 34.8712": the fallback name's coordinates, the same in every language. */
    fun coords(at: LatLon): String = String.format(Locale.US, "%.4f, %.4f", at.lat, at.lon)
}
