package il.transit.core.search

import il.transit.core.api.GeocodeMatch
import il.transit.core.api.TransitApi
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import il.transit.core.present.rankByTypedTown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What a search found: Transitous's answers, then the backup geocoder's (empty unless Transitous missed). */
data class SearchAnswer(val primary: List<GeocodeMatch>, val backup: List<GeocodeMatch> = emptyList()) {
    val all: List<GeocodeMatch> get() = primary + backup
}

/**
 * Place search: Transitous first; the [backup] geocoder only when Transitous has nothing that
 * matches the street (or place) typed — "טבנקין 15 רעננה" is not in Transitous's index at all
 * (2026-10-07), it answers with other places in Ra'anana. At most one backup request per
 * search ([BACKUP_PER_SEARCH], pinned by `PlaceSearchTest`); its answers go below
 * Transitous's, minus any Transitous already gave. A backup failure is never the search's
 * failure: the Transitous answers still show.
 */
class PlaceSearch(private val transit: TransitApi, private val backup: BackupGeocoder?) {
    suspend fun search(text: String, language: String, near: LatLon?, max: Int = 8): SearchAnswer {
        val q = text.trim()
        val primary = rankByTypedTown(transit.geocode(q, language, near, max))
        if (backup == null || !needsBackup(q, primary)) return SearchAnswer(primary)
        val extra = try {
            // The bias only needs to say "around here"; rounded, so the day cache hits.
            backup.search(q, language, near?.let(::roundBias), BACKUP_MAX)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        return SearchAnswer(primary, extra.filterNot { b -> primary.any { sameAnswer(it, b) } })
    }

    companion object {
        /** Requests to the backup geocoder per search: one, and only after a Transitous miss. */
        const val BACKUP_PER_SEARCH = 1
        const val BACKUP_MAX = 5

        /** Shorter than this, any answer is a guess and the "type the full name" hint covers it. */
        const val MIN_BACKUP_CHARS = 3

        /** Same name within this distance: one place, shown once. */
        const val SAME_PLACE_M = 150.0

        /** Words that name the kind of street, not the street: "רחוב טבנקין" is טבנקין. */
        private val FILLER = setOf("רחוב", "רח", "שדרות", "שד", "שדרת", "דרך", "st", "street", "rd", "road", "ave", "avenue", "blvd")

        /**
         * True when none of Transitous's [answers] names what was typed: some word of [query]
         * that is not a number, not a town one of the answers lies in, and not "street" is
         * missing from every answer's name and street.
         */
        fun needsBackup(query: String, answers: List<GeocodeMatch>): Boolean {
            if (query.trim().length < MIN_BACKUP_CHARS) return false
            val towns = answers.flatMap { a -> a.areas.filter { it.adminLevel >= 7.0 }.map { normalize(it.name) } }.toSet()
            val words = query.split(Regex("[\\s,]+")).map(::normalize)
                .filter { w -> w.length >= 2 && !w.all(Char::isDigit) && w !in FILLER && towns.none { it.contains(w) } }
            if (words.isEmpty()) return false
            return answers.none { a ->
                val hay = normalize(listOfNotNull(a.name, a.street).joinToString(" "))
                words.all { hay.contains(it) }
            }
        }

        fun sameAnswer(a: GeocodeMatch, b: GeocodeMatch): Boolean =
            normalize(a.name) == normalize(b.name) && Geo.distanceM(LatLon(a.lat, a.lon), LatLon(b.lat, b.lon)) <= SAME_PLACE_M

        /** 0.1° (~10 km): plenty for a bias. */
        fun roundBias(p: LatLon) = LatLon((p.lat * 10).roundToInt() / 10.0, (p.lon * 10).roundToInt() / 10.0)

        /** Lower case, no quote marks, no hyphens: "צה״ל" = "צהל", "תל-אביב" = "תל אביב". */
        fun normalize(s: String): String =
            s.lowercase().filterNot { it in "\"'״׳`" }.replace('-', ' ').replace('־', ' ').trim()
    }
}

/**
 * Search-as-you-type: each [onText] cancels the search still waiting, and a search runs only
 * once typing pauses for [debounceMs] — so a burst of keys costs one Transitous request (and
 * at most one backup request), whatever its length. Shorter than [MIN_CHARS]: no request.
 * A failed search answers empty ([onAnswer] gets `failed = true`).
 */
class TypingSearch(
    private val scope: CoroutineScope,
    private val search: PlaceSearch,
    private val debounceMs: Long = DEBOUNCE_MS,
    private val onAnswer: (query: String, answer: SearchAnswer, failed: Boolean) -> Unit,
) {
    private var job: Job? = null

    fun onText(text: String, language: String, near: () -> LatLon?) {
        job?.cancel()
        val q = text.trim()
        if (q.length < MIN_CHARS) return
        job = scope.launch {
            delay(debounceMs)
            val (answer, failed) = try {
                search.search(q, language, near()) to false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchAnswer(emptyList()) to true
            }
            onAnswer(q, answer, failed)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    companion object {
        const val DEBOUNCE_MS = 350L
        const val MIN_CHARS = 2
    }
}
