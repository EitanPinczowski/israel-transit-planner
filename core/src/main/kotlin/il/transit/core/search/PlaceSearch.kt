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

/**
 * What a search found: Transitous's answers and the backup geocoder's (empty unless
 * Transitous [missed]). [all] is the order shown: after a miss the backup's answers come first —
 * it was asked because none of Transitous's names what was typed (the address corpus, 2026-10-07:
 * "טבנקין 15 רעננה" showed "סירקין" first). [town]: the town the text named; every answer lies in
 * it. Nothing at all ([notFound]): the screen says so and offers the map.
 */
data class SearchAnswer(
    val primary: List<GeocodeMatch>,
    val backup: List<GeocodeMatch> = emptyList(),
    val town: Town? = null,
    val missed: Boolean = false,
    /** The text as searched (abbreviations and aliases spelled out). */
    val query: String = "",
) {
    private val ordered: List<GeocodeMatch> get() = if (missed) backup + primary else primary + backup

    /** Answers that are what was typed ([PlaceSearch.exact]); only these may be shown as the answer. */
    val exact: List<GeocodeMatch> get() = ordered.filter { PlaceSearch.exact(query, it, town) }

    /** The rest, near but not it ("שדרות המגינים 100" for שדרות הנשיא 100): shown under "Closest matches". */
    val closest: List<GeocodeMatch> get() = ordered.filterNot { PlaceSearch.exact(query, it, town) }

    val all: List<GeocodeMatch> get() = exact + closest

    /** No exact answer: the screen says so, offers the map, and labels any closest matches. */
    val notFound: Boolean get() = exact.isEmpty()
}

/**
 * Place search: Transitous first; the [backup] geocoder only when Transitous has nothing that
 * matches the street (or place) typed — "טבנקין 15 רעננה" is not in Transitous's index at all
 * (2026-10-07), it answers with other places in Ra'anana. At most one backup request per
 * search ([BACKUP_PER_SEARCH], pinned by `PlaceSearchTest`); its answers, minus any Transitous
 * already gave, go first ([SearchAnswer.all]). A typed house number that no Transitous answer
 * carries ("רוטשילד 1" → רוטשילד 114) is a miss too; an answer with the exact number moves up. When the text names a town ([Towns.typedIn]),
 * that town biases every request and **no answer from another town is kept**, from any source:
 * OSM has no טבנקין in Ra'anana either, and Photon's best guess is a טבנקין 15 in Tel Aviv, which
 * must never stand in for it silently. Abbreviations are spelled out first ([QueryText]). A
 * backup failure is never the search's failure: the Transitous answers still show.
 */
class PlaceSearch(private val transit: TransitApi, private val backup: BackupGeocoder?) {
    suspend fun search(text: String, language: String, near: LatLon?, max: Int = 8): SearchAnswer {
        val q = Aliases.rewrite(QueryText.expand(text))
        val town = Towns.typedIn(q).firstOrNull()
        // A typed town says where better than the phone's position does.
        val bias = town?.centre ?: near
        fun inTown(m: GeocodeMatch) = town == null || Towns.holds(town, m)
        val house = houseNumberOf(q)
        val primary = rankByTypedTown(transit.geocode(q, language, bias, max)).filter(::inTown)
            .sortedByDescending { house != null && hasHouse(it, house) }
        if (backup == null || !needsBackup(q, primary, town)) return SearchAnswer(primary, town = town, query = q)
        val extra = try {
            // The bias only needs to say "around here"; rounded, so the day cache hits.
            backup.search(q, language, bias?.let(::roundBias), BACKUP_MAX)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        // A town the table doesn't know, but the answers do ("…, Neve Ativ"): the same rule by name.
        val named = if (town == null) typedTowns(q, primary + extra) else emptySet()
        val kept = extra.filter { b ->
            primary.none { sameAnswer(it, b) } && inTown(b) && (named.isEmpty() || townOf(b) in named)
        }
        return SearchAnswer(primary, kept.sortedByDescending { house != null && hasHouse(it, house) }, town, missed = true, query = q)
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
         * True when none of Transitous's [answers] is [exact] for what was typed. A typed town
         * with no answer in it is a miss, too.
         */
        fun needsBackup(query: String, answers: List<GeocodeMatch>, typed: Town? = null): Boolean {
            if (query.trim().length < MIN_BACKUP_CHARS) return false
            if (typed != null && answers.isEmpty()) return true
            return answers.none { exact(query, it, typed) }
        }

        /** Place words that say what kind of place, not which one: "בית חולים איכילוב" is איכילוב. */
        private val GENERIC = setOf(
            "בית", "חולים", "מרכז", "רפואי", "תחנה", "תחנת", "מרכזית", "חדשה", "רכבת", "קניון", "אוניברסיטת",
            "אוניברסיטה", "רחוב", "hospital", "medical", "center", "centre", "station", "central", "mall", "university",
        )

        /**
         * The words of [query] that pick out the place: not numbers, not the town ([typed] or one
         * [m] lies in), not "street" and not kind-of-place words.
         */
        private fun distinctive(query: String, m: GeocodeMatch, typed: Town?): List<String> {
            val towns = m.areas.filter { it.adminLevel >= 7.0 }.map { normalize(it.name) } + typed?.names.orEmpty().map(::normalize)
            return query.split(Regex("[\\s,]+")).map(::normalize).filter { w ->
                w.length >= 2 && w.first().isLetter() && w !in FILLER && w.removePrefix("ה") !in GENERIC && w !in GENERIC &&
                    towns.none { it.split(' ').contains(w) || it == w }
            }
        }

        /**
         * [m] is what was typed, not just near it (address corpus, owner 2026-10-07: never a wrong
         * place first): every distinctive word is in its name or street (one letter off allowed,
         * "טבנקן" = טבנקין), and a typed house number is its number. Otherwise it is only a
         * closest match, shown as such.
         */
        fun exact(query: String, m: GeocodeMatch, typed: Town? = null): Boolean {
            val house = houseNumberOf(query)
            if (house != null && !hasHouse(m, house)) return false
            val hay = normalize(listOfNotNull(m.name, m.street).joinToString(" "))
            val tokens = hay.split(Regex("[\\s,/.]+")).filter { it.isNotEmpty() }
            return distinctive(query, m, typed).all { w -> hay.contains(w) || (w.length >= 4 && tokens.any { oneOff(w, it) }) }
        }

        /** Same word but for one letter added, dropped or changed. */
        private fun oneOff(a: String, b: String): Boolean {
            if (kotlin.math.abs(a.length - b.length) > 1 || a == b) return a == b
            var i = 0
            while (i < minOf(a.length, b.length) && a[i] == b[i]) i++
            return when {
                a.length == b.length -> a.substring(i + 1) == b.substring(i + 1)
                a.length > b.length -> a.substring(i + 1) == b.substring(i)
                else -> a.substring(i) == b.substring(i + 1)
            }
        }

        /**
         * The towns [query] names, normalized: any town (admin level ≥ 7) of [answers] whose
         * name appears in the text as whole words ("טבנקין 15 רעננה" → רעננה). Transitous does
         * not mark a typed town `matched` for such a text (2026-10-07), hence the words.
         */
        fun typedTowns(query: String, answers: List<GeocodeMatch>): Set<String> {
            val text = " " + normalize(query).replace(Regex("[\\s,]+"), " ") + " "
            return answers.flatMap { a -> a.areas.filter { it.adminLevel >= 7.0 }.map { normalize(it.name) } }
                .filter { it.isNotEmpty() && text.contains(" $it ") }
                .toSet()
        }

        /** An answer's town (admin level 8, else the deepest level ≥ 7), normalized; null if none. */
        fun townOf(m: GeocodeMatch): String? =
            (m.areas.lastOrNull { it.adminLevel == 8.0 } ?: m.areas.lastOrNull { it.adminLevel >= 7.0 })?.name?.let(::normalize)

        /** The house number typed ("15", "12א" → "12"): a number word of 1–4 digits, the last one. */
        fun houseNumberOf(query: String): String? =
            query.split(Regex("[\\s,]+")).lastOrNull { Regex("\\d{1,4}[א-ת]?").matches(it) }?.takeWhile(Char::isDigit)

        /** [m] is that house: its house number, or a number word in its name ("טבנקין 15"). */
        fun hasHouse(m: GeocodeMatch, house: String): Boolean =
            m.houseNumber?.takeWhile(Char::isDigit) == house ||
                m.name.split(Regex("[\\s,/]+")).any { it.takeWhile(Char::isDigit) == house && it.firstOrNull()?.isDigit() == true }

        fun sameAnswer(a: GeocodeMatch, b: GeocodeMatch): Boolean =
            normalize(a.name) == normalize(b.name) && Geo.distanceM(LatLon(a.lat, a.lon), LatLon(b.lat, b.lon)) <= SAME_PLACE_M

        /** 0.1° (~10 km): plenty for a bias. */
        fun roundBias(p: LatLon) = LatLon((p.lat * 10).roundToInt() / 10.0, (p.lon * 10).roundToInt() / 10.0)

        /** Lower case, no quote marks, no hyphens or dashes: "צה״ל" = "צהל", "באר-שבע" = "באר שבע". */
        fun normalize(s: String): String =
            s.lowercase().filterNot { it in "\"'״׳`" }.replace(Regex("[-־–]+"), " ").replace(Regex("\\s+"), " ").trim()
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
