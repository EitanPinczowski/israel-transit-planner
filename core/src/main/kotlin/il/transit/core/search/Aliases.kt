package il.transit.core.search

/**
 * Well-known places people type without their town or by another name ("בית חולים תל השומר" is
 * Sheba, in Ramat Gan): searched by the name the geocoders know, with its town, so the typed-town
 * rule keeps every other town's place out (address corpus, 2026-10-07: Photon offered Tel
 * HaShomer for Ichilov). 0 requests; bundled.
 */
object Aliases {
    private class Alias(val names: List<String>, val he: String, val en: String)

    private val ALL = listOf(
        // Transitous knows it as "בי״ח איכילוב" (live, 2026-10-07): "איכילוב תל אביב" finds it; with "בית חולים"
        // in front it finds other hospitals, and "סוראסקי" finds the university library.
        Alias(listOf("איכילוב", "ichilov", "בית חולים איכילוב", "ichilov hospital", "סוראסקי", "sourasky"), "איכילוב תל אביב", "Ichilov Hospital, Tel Aviv"),
        Alias(listOf("שיבא", "sheba", "בית חולים תל השומר", "tel hashomer hospital"), "המרכז הרפואי שיבא רמת גן", "Sheba Medical Center, Ramat Gan"),
        Alias(listOf("בילינסון", "beilinson", "בית חולים בילינסון"), "בילינסון פתח תקווה", "Beilinson Hospital, Petah Tikva"),
        Alias(listOf("השרון בית חולים", "בית חולים השרון"), "בית החולים השרון פתח תקווה", "Hasharon Hospital, Petah Tikva"),
        Alias(listOf("שניידר", "schneider", "בית חולים שניידר"), "שניידר פתח תקווה", "Schneider Children's Medical Center, Petah Tikva"),
        Alias(listOf("סורוקה", "soroka", "בית חולים סורוקה"), "המרכז הרפואי סורוקה באר שבע", "Soroka Medical Center, Beersheba"),
        Alias(listOf("רמבם", "rambam", "בית חולים רמבם"), "הקריה הרפואית רמב\"ם חיפה", "Rambam Health Care Campus, Haifa"),
        Alias(listOf("הדסה עין כרם", "hadassah ein kerem"), "הדסה עין כרם ירושלים", "Hadassah Ein Kerem, Jerusalem"),
        Alias(listOf("הדסה הר הצופים", "hadassah mount scopus"), "הדסה הר הצופים ירושלים", "Hadassah Mount Scopus, Jerusalem"),
        Alias(listOf("שערי צדק", "shaare zedek"), "שערי צדק ירושלים", "Shaare Zedek Medical Center, Jerusalem"),
        Alias(listOf("וולפסון", "wolfson"), "וולפסון חולון", "Wolfson Medical Center, Holon"),
        Alias(listOf("אסף הרופא", "asaf harofeh"), "המרכז הרפואי שמיר אסף הרופא", "Shamir Medical Center"),
        Alias(listOf("בית חולים מאיר", "meir hospital"), "המרכז הרפואי מאיר כפר סבא", "Meir Medical Center, Kfar Saba"),
        Alias(listOf("לניאדו", "laniado"), "לניאדו נתניה", "Laniado Hospital, Netanya"),
        Alias(listOf("ברזילי", "barzilai"), "ברזילי אשקלון", "Barzilai Medical Center, Ashkelon"),
        Alias(listOf("בית חולים העמק", "haemek hospital"), "בית החולים העמק עפולה", "HaEmek Medical Center, Afula"),
        Alias(listOf("הלל יפה", "hillel yaffe"), "הלל יפה חדרה", "Hillel Yaffe Medical Center, Hadera"),
        Alias(listOf("נתבג", "נמל התעופה בן גוריון", "ben gurion airport", "tlv airport"), "נמל התעופה בן גוריון", "Ben Gurion Airport"),
        Alias(listOf("הטכניון", "technion"), "הטכניון חיפה", "Technion, Haifa"),
        Alias(listOf("האוניברסיטה העברית", "hebrew university"), "האוניברסיטה העברית הר הצופים ירושלים", "Hebrew University Mount Scopus, Jerusalem"),
        Alias(listOf("עזריאלי תל אביב", "מרכז עזריאלי", "azrieli center"), "מרכז עזריאלי תל אביב", "Azrieli Center, Tel Aviv"),
        Alias(listOf("התחנה המרכזית החדשה", "תחנה מרכזית חדשה"), "התחנה המרכזית החדשה תל אביב", "Tel Aviv Central Bus Station, Tel Aviv"),
    )

    private class Acronym(val he: String, val en: String, val bare: Boolean = false)

    /**
     * Acronyms people type, by their letters (Phase 10 D2), with ", ״, ' or ׳ between the letters
     * (ת"א, ת'א); [Acronym.bare] ones also with no mark at all (ראשלצ), where the letters are no
     * other word ("תא" is a cell, "ים" the sea).
     */
    private val ACRONYMS = mapOf(
        "תא" to Acronym("תל אביב", "Tel Aviv"),
        "בש" to Acronym("באר שבע", "Be'er Sheva"),
        "פת" to Acronym("פתח תקווה", "Petah Tikva"),
        "רג" to Acronym("רמת גן", "Ramat Gan"),
        "ראשלצ" to Acronym("ראשון לציון", "Rishon LeZion", bare = true),
        "רשלצ" to Acronym("ראשון לציון", "Rishon LeZion", bare = true),
        "רל" to Acronym("ראשון לציון", "Rishon LeZion"),
        "כס" to Acronym("כפר סבא", "Kfar Saba"),
        "קש" to Acronym("קריית שמונה", "Kiryat Shmona"),
        "קג" to Acronym("קריית גת", "Kiryat Gat"),
        "נסצ" to Acronym("נס ציונה", "Ness Ziona", bare = true),
        "הודש" to Acronym("הוד השרון", "Hod HaSharon", bare = true),
        "בב" to Acronym("בני ברק", "Bnei Brak"),
        "ים" to Acronym("ירושלים", "Jerusalem"),
        "י ם" to Acronym("ירושלים", "Jerusalem"),
        "אתא" to Acronym("אוניברסיטת תל אביב", "Tel Aviv University"),
        "ביח" to Acronym("בית חולים", "hospital", bare = true),
        "ביהח" to Acronym("בית חולים", "hospital", bare = true),
    )

    /**
     * Never spelled out: more than one town (ק"א: קריית אתא or קריית ארבע; ק"ב, ק"מ, ר"ה) or not
     * a place at all. Kept as typed; pinned in `AliasesTest`.
     */
    val AMBIGUOUS = setOf("קא", "קב", "קמ", "קי", "רה", "רח", "גג")

    /**
     * What the acronym [letters] (marks removed) stand for, or null: [marked] says the user typed
     * a mark between them; [english] asks for the English name (an acronym in English text).
     */
    fun acronym(letters: String, marked: Boolean, english: Boolean): String? {
        if (letters in AMBIGUOUS) return null
        val a = ACRONYMS[letters] ?: return null
        if (!marked && !a.bare) return null
        return if (english) a.en else a.he
    }

    private val byName: Map<String, Alias> by lazy {
        ALL.flatMap { a -> a.names.map { PlaceSearch.normalize(it) to a } }.toMap()
    }

    /**
     * [text] searched as the map name when it is a known everyday name, alone or with "בית חולים"
     * / its own town after it ("בית חולים איכילוב", "Ichilov hospital"); otherwise unchanged
     * (another town typed: the street of that name there).
     */
    fun rewrite(text: String): String {
        val n = PlaceSearch.normalize(text).replace(',', ' ').replace(Regex("\\s+"), " ").trim()
        byName[n]?.let { return pick(it, text) }
        // The name plus generic words or a town: "בית חולים איכילוב תל אביב", "Ichilov hospital".
        val words = n.split(' ')
        for (len in minOf(4, words.size) downTo 1) {
            for (start in 0..words.size - len) {
                val a = byName[words.subList(start, start + len).joinToString(" ")] ?: continue
                val rest = words.subList(0, start) + words.subList(start + len, words.size)
                // Only the alias's own town: "ברזילי ירושלים" and "בילינסון תל אביב" are streets (review F2).
                val other = rest.filterNot { it in FILLER }
                if (other.isEmpty() || Towns.named(other.joinToString(" "))?.let { it == townOf(a) } == true) return pick(a, text)
            }
        }
        return text
    }

    private val FILLER = setOf("בית", "חולים", "בית החולים", "hospital", "medical", "center", "המרכז", "הרפואי", "מרכז", "רפואי")

    private fun townOf(a: Alias): Town? = Towns.typedIn(a.he).firstOrNull()

    private fun pick(a: Alias, text: String) = if (text.any { it in 'א'..'ת' }) a.he else a.en
}
