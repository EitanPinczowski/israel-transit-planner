package il.transit.planner.ui

import il.transit.core.geo.LatLon
import il.transit.core.search.MatchLevel
import il.transit.core.search.MatchNote

/**
 * The open search, kept across process death (Phase 11 F8; rotation keeps it in the ViewModel):
 * which field, the typed text and the answers on screen, so the search comes back as it was with
 * no request. Part of [SavedUi]; small values only, one key per answer field, at most [MAX_ANSWERS].
 */
data class SavedSearch(
    val field: Field,
    val query: String,
    val answers: List<Suggestion> = emptyList(),
    val searchHint: Boolean = false,
    val backupShown: Boolean = false,
    val notFound: Boolean = false,
    val offline: Boolean = false,
) {
    /** Every key is written, null included, so a search closed since the last save is cleared there too. */
    fun write(set: (String, Any?) -> Unit) = write(this, set)

    companion object {
        /** More than a screen of rows; the rest are dropped (the field still holds the text). */
        const val MAX_ANSWERS = 16

        private const val P = "f8.search."
        private const val FIELD = P + "field"
        private const val QUERY = P + "query"
        private const val FLAGS = P + "flags"
        private const val COUNT = P + "count"

        private const val HINT = 1
        private const val BACKUP_SHOWN = 2
        private const val NOT_FOUND = 4
        private const val OFFLINE = 8

        private const val SAVED = 1
        private const val STOP = 2
        private const val HOME = 4
        private const val RECENT = 8
        private const val BACKUP = 16
        private const val CLOSEST = 32

        private fun bit(on: Boolean, b: Int) = if (on) b else 0

        fun of(s: UiState): SavedSearch? = s.editing?.let { f ->
            SavedSearch(f, s.query, s.suggestions.take(MAX_ANSWERS), s.searchHint, s.places.backupShown, s.places.notFound, s.places.offline)
        }

        /** [s] null clears every key. */
        fun write(s: SavedSearch?, set: (String, Any?) -> Unit) {
            set(FIELD, s?.field?.name)
            set(QUERY, s?.query)
            set(FLAGS, s?.let { bit(it.searchHint, HINT) or bit(it.backupShown, BACKUP_SHOWN) or bit(it.notFound, NOT_FOUND) or bit(it.offline, OFFLINE) })
            set(COUNT, s?.answers?.size)
            for (i in 0 until MAX_ANSWERS) {
                val a = s?.answers?.getOrNull(i)
                val k = "${P}a$i."
                set(k + "name", a?.name)
                set(k + "detail", a?.detail)
                set(k + "lat", a?.at?.lat)
                set(k + "lon", a?.at?.lon)
                set(
                    k + "flags",
                    a?.let { bit(it.saved, SAVED) or bit(it.isStop, STOP) or bit(it.home, HOME) or bit(it.recent, RECENT) or bit(it.backup, BACKUP) or bit(it.closest, CLOSEST) },
                )
                set(k + "level", a?.match?.level?.name)
                set(k + "house", a?.match?.house)
                set(k + "refine", a?.refine)
                set(k + "stopId", a?.stopId)
            }
        }

        /** Null when no search was open. */
        fun read(get: (String) -> Any?): SavedSearch? {
            val field = (get(FIELD) as? String)?.let { f -> Field.entries.firstOrNull { it.name == f } } ?: return null
            val flags = get(FLAGS) as? Int ?: 0
            val n = (get(COUNT) as? Int ?: 0).coerceIn(0, MAX_ANSWERS)
            val answers = (0 until n).mapNotNull { i ->
                val k = "${P}a$i."
                val name = get(k + "name") as? String ?: return@mapNotNull null
                val lat = get(k + "lat") as? Double ?: return@mapNotNull null
                val lon = get(k + "lon") as? Double ?: return@mapNotNull null
                val f = get(k + "flags") as? Int ?: 0
                val level = (get(k + "level") as? String)?.let { l -> MatchLevel.entries.firstOrNull { it.name == l } }
                Suggestion(
                    name = name,
                    detail = get(k + "detail") as? String,
                    at = LatLon(lat, lon),
                    saved = f and SAVED != 0,
                    isStop = f and STOP != 0,
                    home = f and HOME != 0,
                    recent = f and RECENT != 0,
                    backup = f and BACKUP != 0,
                    closest = f and CLOSEST != 0,
                    match = level?.let { MatchNote(it, get(k + "house") as? String) },
                    refine = get(k + "refine") as? String,
                    stopId = get(k + "stopId") as? String,
                )
            }
            return SavedSearch(
                field, get(QUERY) as? String ?: "", answers,
                searchHint = flags and HINT != 0, backupShown = flags and BACKUP_SHOWN != 0, notFound = flags and NOT_FOUND != 0, offline = flags and OFFLINE != 0,
            )
        }
    }
}
