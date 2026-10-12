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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
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
    /** Transitous's answers while the backup is still being asked: not yet "not found". */
    val pending: Boolean = false,
    /** A neighbourhood in several towns, nothing asked yet: one row per town, nearest first ([TownChoice]). */
    val choices: List<TownChoice> = emptyList(),
) {
    private val ordered: List<GeocodeMatch> get() = if (missed) backup + primary else primary + backup

    /** How much of [query] [m] is ([PlaceSearch.level]). */
    fun level(m: GeocodeMatch): MatchLevel = PlaceSearch.level(query, m, town)

    /** What [m]'s row says: its level, and the typed house a street-only answer lacks. */
    fun note(m: GeocodeMatch): MatchNote = level(m).let { l -> MatchNote(l, if (l == MatchLevel.STREET) PlaceSearch.houseOf(query)?.label else null) }

    /**
     * Answers that are what was typed ([MatchLevel.isAnswer]); only these may be shown as the answer.
     * One named exactly as typed first: "רמות" before "רמות אשכול".
     */
    val exact: List<GeocodeMatch> get() = ordered.filter { level(it).isAnswer }.sortedByDescending { PlaceSearch.normalize(it.name) == PlaceSearch.normalize(query) }

    /**
     * The rest, near but not it ("שדרות המגינים 100" for שדרות הנשיא 100): shown under "Closest
     * matches", the typed street first, then the town, then the others.
     */
    val closest: List<GeocodeMatch> get() = ordered.filterNot { level(it).isAnswer }.sortedBy { level(it).ordinal }

    val all: List<GeocodeMatch> get() = exact + closest

    /** No exact answer (and nothing still being asked): the screen says so, offers the map, and labels any closest matches. */
    val notFound: Boolean get() = !pending && exact.isEmpty() && choices.isEmpty()
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
    /**
     * [interim] gets Transitous's answers at once (`pending`) when the backup is about to be
     * asked, so the screen is never empty while it answers; the backup gets [BACKUP_DEADLINE_MS]
     * in all (phone, then Photon), then the search answers without it.
     */
    suspend fun search(
        text: String,
        language: String,
        near: LatLon?,
        max: Int = 8,
        interim: ((SearchAnswer) -> Unit)? = null,
    ): SearchAnswer {
        // "דירה 4", "קומה 2": no geocoder knows apartments, and the corpus shares one answer with or without them.
        val typed = APARTMENT.replace(text, " ").replace(Regex("\\s+"), " ").trim().trimEnd(',')
        val q = Aliases.rewrite(QueryText.expand(typed))
        val town = Towns.typedIn(q).firstOrNull()
        // A neighbourhood and no town ("רמת אביב"): its town.
        val area = if (town == null) Neighbourhoods.typedIn(typed).firstOrNull()?.let { Neighbourhoods.townsByDistance(it, near) }.orEmpty() else emptyList()
        // In several towns ("רמות"): which one first, from the table, 0 requests (owner, 2026-10-12).
        if (area.size > 1) return SearchAnswer(emptyList(), query = q, choices = area.map { TownChoice(it, "$typed, ${it.displayName(language)}") })
        // A typed town (or a neighbourhood's) says where better than the phone's position does.
        val bias = town?.centre ?: area.firstOrNull()?.centre ?: near
        fun inTown(m: GeocodeMatch) = town == null || Towns.holds(town, m)
        // Answers in the neighbourhood's town first; the rest after (it may be a street name too).
        fun areaRank(m: GeocodeMatch) = area.indexOfFirst { Towns.holds(it, m) }.let { if (it < 0) area.size else it }
        val house = houseOf(q)
        val primary = rankByTypedTown(transit.geocode(q, language, bias, max)).filter(::inTown)
            .sortedWith(compareBy<GeocodeMatch>(::areaRank).thenByDescending { house != null && house.matches(it) })
        if (backup == null || !needsBackup(q, primary, town)) return SearchAnswer(primary, town = town, query = q)
        interim?.invoke(SearchAnswer(primary, town = town, query = q, pending = true))
        val extra = try {
            // The bias only needs to say "around here"; rounded, so the day cache hits.
            withTimeoutOrNull(BACKUP_DEADLINE_MS) { backup.search(q, language, bias?.let(::roundBias), BACKUP_MAX) }.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }.filter { inIsrael(LatLon(it.lat, it.lon)) } // the phone's Geocoder knows the world
        // A town the table doesn't know, but the answers do ("…, Neve Ativ"): the same rule by name.
        val named = if (town == null) typedTowns(q, primary + extra) else emptySet()
        val kept = extra.filter { b ->
            primary.none { sameAnswer(it, b) } && inTown(b) && (named.isEmpty() || townOf(b) in named)
        }
        return SearchAnswer(primary, kept.sortedWith(compareBy<GeocodeMatch>(::areaRank).thenByDescending { house != null && house.matches(it) }), town, missed = true, query = q)
    }

    /**
     * A typed house: "15"; "12א" (the letter must be the answer's too: 12ב is another house);
     * "12-14" (any number in the range). "12/3", "12 דירה 4" are house 12.
     */
    data class House(val from: Int, val to: Int = from, val letter: Char? = null) {
        /** As typed: "15", "12א", "12–14". */
        val label: String get() = if (to != from) "$from–$to" else "$from${letter ?: ""}"

        fun matches(m: GeocodeMatch): Boolean {
            val own = listOfNotNull(m.houseNumber) + m.name.split(Regex("[\\s,/]+")).filter { it.firstOrNull()?.isDigit() == true }
            return own.any { w ->
                val (n, l) = parse(w) ?: return@any false
                n in from..to && (letter == null || l == letter)
            }
        }

        companion object {
            private val WORD = Regex("^(\\d{1,4})([א-תa-z])?(?:([-–/])(\\d{1,4}))?$")

            fun parse(w: String): Pair<Int, Char?>? =
                Regex("^(\\d{1,4})\\s*([א-תa-z])?").find(w.lowercase())?.let { it.groupValues[1].toInt() to it.groupValues[2].firstOrNull() }

            fun of(word: String): House? {
                val g = WORD.find(word.lowercase())?.groupValues ?: return null
                val n = g[1].toInt()
                val letter = g[2].firstOrNull()
                val second = g[4].toIntOrNull()
                return if (g[3] != "/" && second != null && second > n && second - n <= 20) House(n, second) else House(n, letter = letter)
            }
        }
    }

    companion object {
        /** Requests to the backup geocoder per search: one, and only after a Transitous miss. */
        const val BACKUP_PER_SEARCH = 1
        const val BACKUP_MAX = 5

        /** The backup stage in all (phone 3 s, then Photon): no longer than this (review F5, 2026-10-11). */
        const val BACKUP_DEADLINE_MS = 4_000L

        /** Shorter than this, any answer is a guess and the "type the full name" hint covers it. */
        const val MIN_BACKUP_CHARS = 3

        /** Same name within this distance: one place, shown once. */
        const val SAME_PLACE_M = 150.0

        /** Words that name the kind of street, not the street: "רחוב טבנקין" is טבנקין. */
        private val FILLER = setOf(
            "רחוב", "רח", "שדרות", "שד", "שדרת", "דרך", "st", "street", "rd", "road", "ave", "avenue", "blvd", "boulevard",
            "boul", "blv", "way", "lane", "ln", "hwy", "highway", "sderot",
        )

        /** "דירה 4", "קומה 2", "apt 3": not part of the address the geocoders know. */
        private val APARTMENT = Regex("(?<![\\p{L}])(דירה|דיר|קומה|כניסה|apt\\.?|apartment|floor|entrance)\\s*\\S+", RegexOption.IGNORE_CASE)

        /** Words of a street name people leave out: first names and titles ("ויצמן" is חיים ויצמן). */
        private val OPTIONAL = setOf(
            "חיים", "יצחק", "משה", "מנחם", "זאב", "בנימין", "אהרון", "יוסף", "שמואל", "אברהם", "יעקב", "שלמה", "דוד", "אליעזר",
            "הרב", "רב", "רבי", "דר", "פרופ", "פרופסור", "rabbi", "dr", "prof", "chaim", "haim", "yitzhak", "moshe", "menachem",
            "zeev", "david", "yosef", "shmuel", "avraham", "yaakov", "eliezer",
        )

        /**
         * True when none of Transitous's [answers] is [exact] for what was typed. A typed town
         * with no answer in it is a miss, too.
         */
        fun needsBackup(query: String, answers: List<GeocodeMatch>, typed: Town? = Towns.typedIn(query).firstOrNull()): Boolean {
            if (query.trim().length < MIN_BACKUP_CHARS) return false
            if (typed != null && answers.isEmpty()) return true
            return answers.none { exact(query, it, typed) }
        }

        /** Place words that say what kind of place, not which one: "בית חולים איכילוב" is איכילוב. */
        private val GENERIC = setOf(
            "בית", "חולים", "מרכז", "רפואי", "רפואית", "קריה", "קריית", "קרית", "קמפוס", "campus", "תחנה", "תחנת", "מרכזית", "חדשה", "רכבת", "קניון", "אוניברסיטת",
            "אוניברסיטה", "רחוב", "hospital", "medical", "center", "centre", "station", "central", "mall", "university", "new",
        )

        /** Kind words an answer may leave out: "ת.מרכזית תל אביב" is the new central station. */
        private val WEAK = setOf("חדשה", "new", "רפואי", "medical")

        private fun isGeneric(w: String) = w in GENERIC || (w.startsWith("ה") && w.drop(1) in GENERIC)

        /** Name words: "בי״ח" is בית חולים, "ת.מרכזית" is תחנה מרכזית. */
        private fun tokens(s: String): List<String> =
            normalize(s).split(Regex("[\\s,/.()]+")).filter { it.isNotEmpty() }.flatMap {
                when (it) {
                    "ת" -> listOf("תחנה")
                    "ביח", "ביהח", "בה\"ח" -> listOf("בית", "חולים")
                    else -> listOf(it)
                }
            }

        private fun isNumber(w: String) = w.first().isDigit()

        /** [words] without the first run that spells [name] (several words), if any. */
        private fun without(words: List<String>, names: List<String>): List<String>? {
            for (n in names.map { tokens(it) }.filter { it.isNotEmpty() }.sortedByDescending { it.size }) {
                for (i in words.size - n.size downTo 0) {
                    if (words.subList(i, i + n.size) == n) return words.subList(0, i) + words.subList(i + n.size, words.size)
                }
            }
            return null
        }

        private fun answerTowns(m: GeocodeMatch) = m.areas.filter { it.adminLevel >= 7.0 }.map { it.name }

        /**
         * [m] is what was typed, not just near it (address corpus, owner 2026-10-07: never a wrong
         * place first; review 2026-10-11):
         * - a typed house number (letter, range) is its number;
         * - every word that names it (not the typed town, numbers, street or kind-of-place
         *   words) is a whole word of its name or street — one letter off for words of 4+, a ה/ב/ל/מ
         *   prefix aside, a Latin word against its Hebrew spelling ("Rothschild" = רוטשילד);
         * - an address's street has no word that wasn't typed ("יהודה" is not בן יהודה), first
         *   names and titles aside; a place is named by a typed word first ("הטכניון" is not
         *   "אובליסק הטכניון");
         * - nothing but kind words ("קניון רעננה"): a place of that kind, named by it; nothing
         *   but the town ("רעננה"): the town itself.
         * Otherwise it is only a closest match, shown as such.
         */
        fun exact(query: String, m: GeocodeMatch, typed: Town? = Towns.typedIn(query).firstOrNull()): Boolean =
            level(query, m, typed).isAnswer

        /**
         * How much of [query] [m] is (Phase 10 D2): the [exact] rules, with the near misses told
         * apart. The street with another number (or none) is [MatchLevel.STREET]; the typed town
         * alone, when more was typed, is [MatchLevel.TOWN].
         */
        fun level(query: String, m: GeocodeMatch, typed: Town? = Towns.typedIn(query).firstOrNull()): MatchLevel {
            val clean = APARTMENT.replace(QueryText.expand(query), " ")
            val house = houseOf(clean)
            val houseOk = house == null || house.matches(m)
            var words = tokens(clean).filter { it.length >= 2 && !isNumber(it) && it !in FILLER }
            if (typed != null) {
                words = without(words, typed.names) ?: words
            } else {
                // An untyped town the answer lies in ("…, Neve Ativ"); never all that was typed ("חיפה 10" is a street).
                without(words, answerTowns(m))?.takeIf { rest -> rest.any { !isGeneric(it) } }?.let { words = it }
                // A neighbourhood of the answer's town, typed with a street ("איינשטיין 40 רמת אביב"): where, not which.
                Neighbourhoods.typedIn(APARTMENT.replace(query, " ")).firstOrNull { n -> n.towns.any { Towns.holds(it, m) } }?.let { n ->
                    without(words, n.names)?.takeIf { rest -> rest.any { !isGeneric(it) } }?.let { words = it }
                }
            }
            val kinds = words.filter(::isGeneric)
            val names = words.filterNot(::isGeneric)
            val hay = tokens(listOfNotNull(m.name, m.street).joinToString(" "))
            // Town words aren't the place's name, unless typed as its name ("מודיעין" in "ת. רכבת מודיעין מרכז").
            val towns = (answerTowns(m) + typed?.names.orEmpty()).flatMap(::tokens).toSet() - words.toSet()
            val isTypedTown = typed != null && Towns.named(m.name.substringBefore(',')) == typed
            val town = if (isTypedTown) MatchLevel.TOWN else MatchLevel.NONE
            if (names.isEmpty()) {
                if (kinds.isEmpty()) return if (!isTypedTown) MatchLevel.NONE else if (house == null) MatchLevel.PLACE else MatchLevel.TOWN
                val needed = kinds.filter { it !in WEAK && it.removePrefix("ה") !in WEAK }
                val named = needed.all { k -> hay.any { same(k, it) } } && lead(m.name, towns)?.let(::isGeneric) == true
                return if (named && houseOk) MatchLevel.PLACE else town
            }
            if (!names.all { w -> hay.any { same(w, it) } }) return town
            return if (m.type == "ADDRESS") {
                val street = tokens(m.street ?: m.name).filter { it.length >= 2 && !isNumber(it) && it !in FILLER && !isGeneric(it) && it !in towns }
                val onStreet = street.all { s -> s in OPTIONAL || words.any { same(it, s) } }
                when {
                    !onStreet -> town
                    houseOk -> MatchLevel.HOUSE
                    else -> MatchLevel.STREET
                }
            } else {
                // A stop is named by its street and the crossing one ("הרצל/יואל"): either may be the one typed.
                val named = m.name.split('/').any { part -> lead(part, towns, skipKinds = true)?.let { l -> names.any { same(it, l) } } ?: false }
                if (named && houseOk) MatchLevel.PLACE else town
            }
        }

        /** The first word of [name] that says which place (not a number, a town or, with [skipKinds], a kind word). */
        private fun lead(name: String, towns: Set<String>, skipKinds: Boolean = false): String? =
            tokens(name).firstOrNull { it.length >= 2 && !isNumber(it) && it !in FILLER && it !in towns && !(skipKinds && isGeneric(it)) }

        /** Typed word [w] is answer word [t]: the same, one prefix letter apart, one letter off (4+), or its Hebrew spelling. */
        private fun same(w: String, t: String): Boolean =
            w == t ||
                (t.length == w.length + 1 && t[0] in PREFIX && t.endsWith(w)) ||
                (w.length == t.length + 1 && w[0] in PREFIX && w.endsWith(t)) ||
                (w.length >= 4 && oneOff(w, t)) ||
                (w.length >= 3 && w.all { it in 'a'..'z' } && t.any { it in 'א'..'ת' } && Translit.latin(w).let { it.isNotEmpty() && it == Translit.hebrew(t) })

        private const val PREFIX = "הבלמ"

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

        /** The house typed: the last number word ("15", "12א", "12-14"); "דירה 4" and the like aside. */
        fun houseOf(query: String): House? =
            APARTMENT.replace(query, " ").split(Regex("[\\s,]+")).lastOrNull { House.of(it) != null }?.let { House.of(it) }

        fun sameAnswer(a: GeocodeMatch, b: GeocodeMatch): Boolean =
            normalize(a.name) == normalize(b.name) && Geo.distanceM(LatLon(a.lat, a.lon), LatLon(b.lat, b.lon)) <= SAME_PLACE_M

        /** Inside [PhotonClient.ISRAEL_BBOX]: Israel, the Golan and Eilat, a little sea. */
        fun inIsrael(p: LatLon): Boolean = p.lat in 29.45..33.35 && p.lon in 34.2..35.95

        /** 0.1° (~10 km): plenty for a bias. */
        fun roundBias(p: LatLon) = LatLon((p.lat * 10).roundToInt() / 10.0, (p.lon * 10).roundToInt() / 10.0)

        /** Lower case, no quote marks, no hyphens or dashes: "צה״ל" = "צהל", "באר-שבע" = "באר שבע". */
        fun normalize(s: String): String =
            s.lowercase().filterNot { it in "\"'״׳`" }.replace(Regex("[-־–]+"), " ").replace(Regex("\\s+"), " ").trim()
    }
}

/**
 * A street name's consonants, so a Latin word can be checked against a Hebrew answer
 * ("Rothschild" and רוטשילד are both r-t-s-l-d): vowels, א ע ה י and a middle ו dropped, sounds
 * that are spelled alike merged (b/v/w, k/q/ch/כ/ק/ח, f/p, z/tz/j/צ/ז). Only ever compared whole.
 */
object Translit {
    fun latin(w: String): String {
        val s = w.lowercase().replace("sch", "s").replace("sh", "s").replace("ch", "k").replace("kh", "k")
            .replace("ph", "p").replace("th", "t").replace("tz", "z").replace("zh", "z")
        return collapse(s.mapNotNull {
            when (it) {
                'b', 'v', 'w' -> 'b'; 'c', 'k', 'q' -> 'k'; 'f', 'p' -> 'p'; 'j', 'z' -> 'z'
                'g', 's', 't', 'd', 'l', 'm', 'n', 'r' -> it
                'x' -> 'k'
                else -> null
            }
        })
    }

    fun hebrew(w: String): String = collapse(w.mapIndexedNotNull { i, c ->
        when (c) {
            'ו' -> if (i == 0) 'b' else null
            'ב' -> 'b'; 'ג' -> 'g'; 'ד' -> 'd'; 'ז', 'צ', 'ץ' -> 'z'; 'ח', 'כ', 'ך', 'ק' -> 'k'; 'ט', 'ת' -> 't'
            'ל' -> 'l'; 'מ', 'ם' -> 'm'; 'נ', 'ן' -> 'n'; 'ס', 'ש' -> 's'; 'פ', 'ף' -> 'p'; 'ר' -> 'r'
            else -> null
        }
    })

    private fun collapse(cs: List<Char>): String = buildString { cs.forEach { if (lastOrNull() != it) append(it) } }
}

/**
 * Search-as-you-type: each [onText] cancels the search still waiting, and a search runs only
 * once typing pauses for [debounceMs] — so a burst of keys costs one Transitous request (and
 * at most one backup request), whatever its length. Shorter than [MIN_CHARS]: no request.
 * A failed search answers empty ([onAnswer] gets `failed = true`). After a Transitous miss
 * [onAnswer] is called twice: its answers (`pending`) at once, then with the backup's.
 *
 * Stale answers (Phase 10 D2): cancelling the job cancels the HTTP calls with it (`MotisClient`,
 * `PhotonClient`), but the phone's Geocoder blocks and may still return. So every [onText] and
 * [cancel] takes a new sequence number, and an answer reaches [onAnswer] only while its search's
 * number is still the latest: an old query's answer never replaces a newer one's.
 */
class TypingSearch(
    private val scope: CoroutineScope,
    private val search: PlaceSearch,
    private val debounceMs: Long = DEBOUNCE_MS,
    private val onAnswer: (query: String, answer: SearchAnswer, failed: Boolean) -> Unit,
) {
    private var job: Job? = null
    private val seq = AtomicInteger()

    fun onText(text: String, language: String, near: () -> LatLon?) {
        job?.cancel()
        val mine = seq.incrementAndGet()
        val q = text.trim()
        if (q.length < MIN_CHARS) return
        fun answer(a: SearchAnswer, failed: Boolean) {
            if (seq.get() == mine) onAnswer(q, a, failed)
        }
        job = scope.launch {
            delay(debounceMs)
            val (result, failed) = try {
                // Transitous's answers show at once when the backup is asked; then the full answer.
                search.search(q, language, near(), interim = { answer(it, false) }) to false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchAnswer(emptyList()) to true
            }
            answer(result, failed)
        }
    }

    fun cancel() {
        seq.incrementAndGet()
        job?.cancel()
        job = null
    }

    companion object {
        const val DEBOUNCE_MS = 350L
        const val MIN_CHARS = 2
    }
}
