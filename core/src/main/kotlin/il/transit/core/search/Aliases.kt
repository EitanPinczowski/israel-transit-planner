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
        // Transitous knows it as "בי״ח איכילוב" (2026-10-07); searching "סוראסקי" found the university library.
        Alias(listOf("איכילוב", "ichilov", "בית חולים איכילוב", "ichilov hospital", "סוראסקי", "sourasky"), "בית חולים איכילוב תל אביב", "Ichilov Hospital, Tel Aviv"),
        Alias(listOf("שיבא", "sheba", "בית חולים תל השומר", "tel hashomer hospital"), "המרכז הרפואי שיבא רמת גן", "Sheba Medical Center, Ramat Gan"),
        Alias(listOf("בילינסון", "beilinson", "בית חולים בילינסון"), "בית החולים בילינסון פתח תקווה", "Beilinson Hospital, Petah Tikva"),
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

    private val byName: Map<String, Alias> by lazy {
        ALL.flatMap { a -> a.names.map { PlaceSearch.normalize(it) to a } }.toMap()
    }

    /**
     * [text] searched as the map name when it is a known everyday name, alone or with "בית חולים"
     * / a town after it ("בית חולים איכילוב", "Ichilov hospital"); otherwise unchanged.
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
                if (rest.all { it in FILLER || Towns.named(it) != null || rest.joinToString(" ").let(Towns::named) != null }) return pick(a, text)
            }
        }
        return text
    }

    private val FILLER = setOf("בית", "חולים", "בית החולים", "hospital", "medical", "center", "המרכז", "הרפואי", "מרכז", "רפואי")

    private fun pick(a: Alias, text: String) = if (text.any { it in 'א'..'ת' }) a.he else a.en
}
