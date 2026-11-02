package il.transit.core.search

import il.transit.core.api.GeocodeArea
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.Place
import il.transit.core.geo.LatLon
import il.transit.core.geo.RailStations

/**
 * A train station asked for by name ("Rehovot railway station", "תחנת רכבת באר שבע מרכז", "תחנת רכבת
 * תל אביב סבידור"), answered from the bundled [RailStations] list: 0 requests (Phase 11 F8).
 *
 * Transitous's index has the stations under their GTFS names ("רחובות", "באר שבע מרכז"), so an English
 * "... train station" answered stations abroad (Leipzig, Thessaloniki) and a Hebrew one with "סבידור"
 * other Israeli stations (T9's corpus, 2026-11-02). Here the kind words go, the typed town picks its
 * stations (by name, else within the town), and the rest of the words pick among them: "Center" /
 * "מרכז", "North" / "צפון"… A few names people use that the GTFS doesn't are in [NICKNAMES]. Never light
 * rail ("רכבת קלה", "light rail": those stops are in Transitous under their own names), and never
 * without a town or a nickname: "תחנת רכבת" alone is left to the search.
 */
object Stations {
    /** A word that asks for a train station. */
    private val RAIL = setOf("רכבת", "הרכבת", "train", "railway", "rail")

    /** Light rail is not here (its stops are not in [RailStations] as "RAIL"). */
    private val LIGHT = setOf("קלה", "הקלה", "light", "lrt")

    /** Words that say "station", not which one. */
    private val KIND = RAIL + setOf("תחנת", "תחנה", "התחנה", "ת", "station", "the", "of")

    /** Typed words for the parts of a station's name ("Be'er Sheva Center" is באר שבע מרכז). */
    private val PARTS = mapOf(
        "center" to "מרכז", "centre" to "מרכז", "central" to "מרכז", "merkaz" to "מרכז",
        "north" to "צפון", "tzafon" to "צפון", "south" to "דרום", "darom" to "דרום",
        "east" to "מזרח", "mizrah" to "מזרח", "west" to "מערב", "maarav" to "מערב",
        "university" to "האוניברסיטה",
    )

    /** Station names people say that the GTFS name lacks: Savidor is "תל אביב מרכז". Keep it tiny. */
    private val NICKNAMES = mapOf("סבידור" to "תל אביב מרכז", "savidor" to "תל אביב מרכז")

    /** At most this many stations for one query. */
    const val MAX = 3

    private val rail: List<Place> get() = RailStations.ALL.filter { "RAIL" in it.modes.orEmpty() }

    private fun words(s: String): List<String> =
        PlaceSearch.normalize(s).split(Regex("[\\s,./()]+")).filter { it.isNotEmpty() }

    /** The stations [text] asks for, best first, as answers in [language]; empty when it is not a station query. */
    fun find(text: String, language: String): List<GeocodeMatch> {
        val all = words(text)
        if (all.none { it in RAIL } || all.any { it in LIGHT }) return emptyList()
        val rest = all.filterNot { it in KIND }
        rest.firstNotNullOfOrNull { NICKNAMES[it] }?.let { name ->
            return rail.filter { it.name == name }.map { answer(it, null, language) }
        }
        val (town, start, len) = townIn(rest) ?: return emptyList()
        val parts = (rest.subList(0, start) + rest.subList(start + len, rest.size)).map { PARTS[it] ?: it }
        val prefixes = town.names.map(PlaceSearch::normalize).filter { n -> n.any { it in 'א'..'ת' } }
        fun named(p: Place) = prefixes.any { PlaceSearch.normalize(p.name).startsWith(it) }
        val inTown = rail.filter(::named).ifEmpty { rail.filter { town.contains(LatLon(it.lat, it.lon)) } }
        fun fits(p: Place) = parts.isNotEmpty() && words(p.name).let { n -> parts.all { it in n } }
        return inTown.sortedWith(compareByDescending<Place>(::fits).thenByDescending { it.importance ?: 0.0 })
            .take(MAX).map { answer(it, town, language) }
    }

    /** The longest run of [ws] that names a town: (town, start, length). */
    private fun townIn(ws: List<String>): Triple<Town, Int, Int>? {
        for (len in minOf(4, ws.size) downTo 1) {
            for (start in 0..ws.size - len) {
                Towns.named(ws.subList(start, start + len).joinToString(" "))?.let { return Triple(it, start, len) }
            }
        }
        return null
    }

    private fun answer(p: Place, town: Town?, language: String): GeocodeMatch {
        val t = town ?: Towns.ALL.firstOrNull { it.contains(LatLon(p.lat, p.lon)) }
        return GeocodeMatch(
            type = "STOP",
            name = if (language == "en" && t != null) english(p.name, t) else p.name,
            id = p.stopId ?: "rail:${p.name}",
            lat = p.lat,
            lon = p.lon,
            modes = listOf("RAIL"),
            areas = listOfNotNull(t?.let { GeocodeArea(it.displayName(language), 8.0) }),
        )
    }

    /** "באר שבע מרכז" → "Beersheba Center" (the town table's English name); a name with other Hebrew words stays as it is. */
    private fun english(name: String, town: Town): String {
        val prefix = town.names.firstOrNull { n -> n.any { it in 'א'..'ת' } && name.startsWith(n) } ?: return name
        val rest = name.removePrefix(prefix).trim().split(' ').filter { it.isNotEmpty() }
        val back = PARTS.entries.groupBy { it.value }.mapValues { it.value.first().key.replaceFirstChar(Char::uppercase) }
        if (rest.any { it !in back }) return name
        return (listOf(town.displayName("en")) + rest.map { back.getValue(it) }).joinToString(" ")
    }
}
