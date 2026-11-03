package il.transit.planner.ui

import il.transit.core.geo.LatLon
import il.transit.core.search.MatchLevel
import il.transit.core.search.MatchNote
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The open search, kept across process death (Phase 11 F8; rotation keeps it in the ViewModel):
 * which field, the typed text and the answers on screen, so the search comes back as it was with
 * no request. Part of [SavedUi], as ONE string under [KEY] (a small JSON object), at most
 * [MAX_ANSWERS] rows and [MAX_QUERY] characters of text.
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
    companion object {
        /** More than a screen of rows; the rest are dropped (the field still holds the text). */
        const val MAX_ANSWERS = 16

        /** A search is a few words; a longer text is cut, so the saved bundle stays small. */
        const val MAX_QUERY = 200

        const val KEY = "f8.search"

        fun of(s: UiState): SavedSearch? = s.editing?.let { f ->
            SavedSearch(f, s.query.take(MAX_QUERY), s.suggestions.take(MAX_ANSWERS), s.searchHint, s.places.backupShown, s.places.notFound, s.places.offline)
        }

        /** [s] null clears the key. */
        fun write(s: SavedSearch?, set: (String, Any?) -> Unit) = set(KEY, s?.let(::encode))

        /** Null when no search was open (or the saved text can't be read). */
        fun read(get: (String) -> Any?): SavedSearch? = (get(KEY) as? String)?.let { runCatching { decode(it) }.getOrNull() }

        internal fun encode(s: SavedSearch): String = buildJsonObject {
            put("field", s.field.name)
            put("query", s.query.take(MAX_QUERY))
            put("hint", s.searchHint)
            put("backupShown", s.backupShown)
            put("notFound", s.notFound)
            put("offline", s.offline)
            put(
                "answers",
                buildJsonArray {
                    s.answers.take(MAX_ANSWERS).forEach { a -> add(row(a)) }
                },
            )
        }.toString()

        private fun row(a: Suggestion): JsonObject = buildJsonObject {
            put("name", a.name)
            a.detail?.let { put("detail", it) }
            put("lat", a.at.lat)
            put("lon", a.at.lon)
            if (a.saved) put("saved", true)
            if (a.isStop) put("stop", true)
            if (a.home) put("home", true)
            if (a.recent) put("recent", true)
            if (a.backup) put("backup", true)
            if (a.closest) put("closest", true)
            a.match?.let { m ->
                put("level", m.level.name)
                m.house?.let { put("house", it) }
            }
            a.refine?.let { put("refine", it) }
            a.stopId?.let { put("stopId", it) }
        }

        private fun decode(text: String): SavedSearch? {
            val o = Json.parseToJsonElement(text).jsonObject
            fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
            fun JsonObject.flag(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull == true
            val field = o.str("field")?.let { f -> Field.entries.firstOrNull { it.name == f } } ?: return null
            val rows = (o["answers"] as? JsonArray).orEmpty().take(MAX_ANSWERS).mapNotNull { e ->
                val r = e as? JsonObject ?: return@mapNotNull null
                val name = r.str("name") ?: return@mapNotNull null
                val lat = r["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val lon = r["lon"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val level = r.str("level")?.let { l -> MatchLevel.entries.firstOrNull { it.name == l } }
                Suggestion(
                    name = name,
                    detail = r.str("detail"),
                    at = LatLon(lat, lon),
                    saved = r.flag("saved"),
                    isStop = r.flag("stop"),
                    home = r.flag("home"),
                    recent = r.flag("recent"),
                    backup = r.flag("backup"),
                    closest = r.flag("closest"),
                    match = level?.let { MatchNote(it, r.str("house")) },
                    refine = r.str("refine"),
                    stopId = r.str("stopId"),
                )
            }
            return SavedSearch(
                field = field,
                query = o.str("query").orEmpty().take(MAX_QUERY),
                answers = rows,
                searchHint = o.flag("hint"),
                backupShown = o.flag("backupShown"),
                notFound = o.flag("notFound"),
                offline = o.flag("offline"),
            )
        }
    }
}
