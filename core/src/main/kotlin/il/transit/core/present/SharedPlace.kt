package il.transit.core.present

import il.transit.core.geo.LatLon
import il.transit.core.search.PlaceSearch
import il.transit.core.search.QueryText
import il.transit.core.search.Towns
import java.net.URLDecoder

/**
 * A place another app handed us (Phase 10 D6): a `geo:` link, a Google Maps or Waze link, or
 * text shared from WhatsApp. Parsing is pure and sends nothing: coordinates become the
 * destination at once, text is searched as if typed (one geocode, through `PlaceSearch`).
 */
sealed interface SharedPlace {
    /** Coordinates, inside Israel. [label]: the name the link carries (`geo:0,0?q=32.1,34.8(Home)`), if any. */
    data class Point(val at: LatLon, val label: String? = null) : SharedPlace

    /** Address or place text to search for ("הרצל 15, תל אביב"). */
    data class Text(val query: String) : SharedPlace

    /** `maps.app.goo.gl` and the like: opening it would ask Google (owner decision 4), so we don't. */
    data object ShortLink : SharedPlace

    /** Coordinates outside Israel's box: refused politely. */
    data object OutsideIsrael : SharedPlace

    /** Nothing that looks like a place. */
    data object Unreadable : SharedPlace

    companion object {
        /** Longest text we search: a sentence, not a message. */
        const val MAX_QUERY = 80

        /**
         * Longest shared text we read (Phase 11 T6): parsing runs on the main thread, and an address
         * search over a megabyte would hang it. A message with an address is far shorter.
         */
        const val MAX_SHARED = 2_000

        /** Bidi controls (LRE…RLO, LRI…PDI): a label or query must not reorder the text around it on screen. */
        private val BIDI = Regex("[\u202A-\u202E\u2066-\u2069]")

        private val URL = Regex("""(?i)\b((?:https?://|geo:)[^\s<>"]+)""")
        private val COORDS = Regex("""^\s*(-?\d{1,3}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)""")
        private val ONLY_COORDS = Regex("""^\s*-?\d{1,3}(?:\.\d+)?\s*,\s*-?\d{1,3}(?:\.\d+)?\s*(?:\(.*\))?\s*$""")
        private val SHORT_HOSTS = listOf("maps.app.goo.gl", "goo.gl", "g.co", "waze.to")
        // google.com, google.co.il, google.com.au, google.de: Google's own country domains, not "google.com.evil.example" (T6).
        private val MAP_HOSTS = Regex("""(?i)^(?:www\.|maps\.)?(?:google\.(?:com|co)(?:\.[a-z]{2})?|google\.[a-z]{2}|waze\.com)$""")

        /** Street words (also after ב/ל/ה/מ): where the address starts in a sentence. */
        private val STREET = Regex("""(?i)(?<![\p{L}])[בלהמ]?(רחוב|רח['׳]|שדרות|שד['׳]|דרך|כיכר|סמטת|סמטה)\s""")
        private val STREET_EN = Regex("""(?i)\b\d{0,4}\s*\p{L}+(?:\s\p{L}+)?\s(st|street|rd|road|ave|avenue|blvd|boulevard)\b""")
        private val HOUSE = Regex("""(?<![\p{L}\d])\p{L}{2,}\s\d{1,4}[א-תa-z]?(?![\d])""")
        private val LABEL = Regex("""(?i)^(?:כתובת|הכתובת|address|location|מיקום|המיקום)\s*(?:היא|is)?\s*[:\-–]\s*""")
        private val AT = Regex("""(?i)\b(?:at|address is|address:)\s+""")

        /** [shared]: an intent's data URI or its `EXTRA_TEXT`. */
        fun parse(shared: String?): SharedPlace {
            val text = shared?.take(MAX_SHARED)?.replace(BIDI, "")?.trim().orEmpty()
            if (text.isEmpty()) return Unreadable
            // A geo: URI is never searched as words, even an empty or broken one ("geo:").
            if (text.startsWith("geo:", ignoreCase = true) && text.none(Char::isWhitespace)) return geo(text.substring(4))
            var short = false
            for (m in URL.findAll(text)) {
                when (val p = link(trimUrl(m.groupValues[1]))) {
                    null -> Unit
                    ShortLink -> short = true
                    else -> return p
                }
            }
            // A short link next to the address ("הרצל 15, תל אביב\nhttps://maps.app.goo.gl/…"): the address.
            val rest = URL.replace(text, " ").trim()
            address(rest, loose = !short)?.let { return Text(it) }
            return if (short) ShortLink else Unreadable
        }

        /** Sentence punctuation after a link is not part of it; a ")" only when it closes nothing ("…(Azrieli)" keeps it). */
        private fun trimUrl(url: String): String {
            var u = url.trimEnd('.', ',', '!', '?')
            while (u.endsWith(")") && u.count { it == ')' } > u.count { it == '(' }) u = u.dropLast(1).trimEnd('.', ',', '!', '?')
            return u
        }

        /** One link: a place, [ShortLink], or null (not a map link). */
        fun link(url: String): SharedPlace? {
            if (url.startsWith("geo:", ignoreCase = true)) return geo(url.substring(4))
            val afterScheme = url.substringAfter("://")
            val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').lowercase()
            val path = afterScheme.removePrefix(afterScheme.substringBefore('/').substringBefore('?'))
            if (host in SHORT_HOSTS) return ShortLink
            if (!MAP_HOSTS.matches(host)) return null
            val google = host.contains("google")
            if (google && !(host.startsWith("maps.") || path.startsWith("/maps"))) return null
            val query = params(path.substringAfter('?', ""))
            // Waze: ll=lat,lon (or the old latlng), q=text, or /ul/h<geohash>.
            if (!google) {
                (query["ll"] ?: query["latlng"])?.let { return point(it) }
                Regex("""/ul/h([0-9b-hjkmnp-z]{5,12})""").find(path)?.let { return checked(Geohash.decode(it.groupValues[1])) }
                query["q"]?.let { return text(it) }
                return null
            }
            // Google: the place's own pin (!3d…!4d…) beats the camera (@lat,lon); q/query/ll/destination.
            Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""").find(path)?.let { return point("${it.groupValues[1]},${it.groupValues[2]}") }
            for (key in listOf("q", "query", "destination", "daddr", "ll", "center")) {
                val v = query[key] ?: continue
                return if (ONLY_COORDS.matches(v)) point(v) else text(v)
            }
            Regex("""/@(-?\d+\.\d+),(-?\d+\.\d+)""").find(path)?.let { return point("${it.groupValues[1]},${it.groupValues[2]}") }
            // /maps/place/<name>/… with no coordinates: the name.
            Regex("""/maps/(?:place|search)/([^/@?]+)""").find(path)?.let { return text(decode(it.groupValues[1])) }
            return null
        }

        /** `geo:lat,lon[;u=…][?q=…]`; `geo:0,0?q=lat,lon(Label)` or `geo:0,0?q=address`. */
        private fun geo(body: String): SharedPlace {
            val head = body.substringBefore('?').substringBefore(';')
            val q = params(body.substringAfter('?', ""))["q"]
            val c = COORDS.find(head)
            val zero = c == null || (c.groupValues[1].toDouble() == 0.0 && c.groupValues[2].toDouble() == 0.0)
            if (q != null && q.isNotBlank()) {
                if (ONLY_COORDS.matches(q)) return point(q)
                if (zero) return text(q)
            }
            if (zero) return Unreadable
            return point(head)
        }

        /** "32.1,34.8" or "32.1,34.8(Label)". */
        private fun point(s: String): SharedPlace {
            val c = COORDS.find(s) ?: return Unreadable
            val lat = c.groupValues[1].toDouble()
            val lon = c.groupValues[2].toDouble()
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return Unreadable
            val label = Regex("""\(([^)]+)\)""").find(s.substring(c.range.last + 1))?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            return checked(LatLon(lat, lon), label)
        }

        private fun checked(at: LatLon?, label: String? = null): SharedPlace = when {
            at == null -> Unreadable
            !PlaceSearch.inIsrael(at) -> OutsideIsrael
            else -> Point(at, label)
        }

        private fun text(s: String): SharedPlace = s.trim().takeIf { it.isNotEmpty() }?.let { Text(it.take(MAX_QUERY).trim()) } ?: Unreadable

        private fun params(q: String): Map<String, String> =
            q.substringBefore('#').split('&').mapNotNull { kv ->
                val k = kv.substringBefore('=', "")
                if (k.isEmpty()) null else k.lowercase() to decode(kv.substringAfter('='))
            }.toMap()

        private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

        /**
         * The address in a message ("נפגשים ברחוב הרצל 15 בתל אביב בשמונה"): the line with a
         * street word, a town we know, or a word and a house number; from its street word (or
         * after "כתובת:" / "at"). A short text with none of these is searched as it is (a place
         * name: "קניון עזריאלי"), unless [loose] is off (words next to a short link: "תראה כאן").
         * Null when there is nothing to search.
         */
        fun address(text: String, loose: Boolean = true): String? {
            val lines = text.split(Regex("""[\n\r]+|(?<=[.!?;])\s+""")).map { it.trim().trim('.', '!', '?', ';', ',') }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return null
            val best = lines.map { it to score(it) }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
            if (best == null) {
                if (!loose) return null
                val only = lines.singleOrNull() ?: return null
                return only.takeIf { it.length <= MAX_QUERY && it.split(' ').size <= 6 && it.any(Char::isLetter) }
            }
            return trimToAddress(best).take(MAX_QUERY).trim().trimEnd(',').takeIf { it.isNotEmpty() }
        }

        private fun score(line: String): Int =
            (if (STREET.containsMatchIn(line) || STREET_EN.containsMatchIn(line)) 3 else 0) +
                (if (townEnd(line) != null) 2 else 0) +
                (if (HOUSE.containsMatchIn(line)) 1 else 0) +
                (if (LABEL.containsMatchIn(line)) 2 else 0)

        private fun names(text: String) = Towns.typedIn(QueryText.expand(text)).isNotEmpty()

        /**
         * Where the address ends: after the first words that end in a town ("רחוב הרצל 15 בתל אביב
         * בשמונה" → "…בתל אביב"); [Towns.typedIn] only reads a town at the end of the text. Null: no town.
         */
        private fun townEnd(line: String): Int? {
            val words = Regex("""\S+""").findAll(line).toList()
            for (n in 2..words.size) {
                val end = words[n - 1].range.last + 1
                if (names(line.substring(0, end).trimEnd(',', '.'))) return end
            }
            return null
        }

        private fun trimToAddress(line: String): String {
            val from = LABEL.find(line)?.let { it.range.last + 1 }
                ?: STREET.find(line)?.let { m ->
                    // Drop a ב/ל/מ glued to the street word: "ברחוב הרצל" is רחוב הרצל.
                    m.range.first + if (m.value.first() in "בלמ" && !m.groupValues[1].startsWith(m.value.first())) 1 else 0
                }
                ?: AT.findAll(line).lastOrNull()?.let { it.range.last + 1 }
                ?: 0
            val rest = line.substring(from)
            return townEnd(rest)?.let { rest.substring(0, it) } ?: rest
        }
    }
}

/** Waze's `/ul/h<geohash>` links: the standard base-32 geohash, decoded to its cell's centre. */
internal object Geohash {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun decode(hash: String): LatLon? {
        var lat = -90.0 to 90.0
        var lon = -180.0 to 180.0
        var even = true
        for (c in hash.lowercase()) {
            val v = BASE32.indexOf(c)
            if (v < 0) return null
            for (bit in 4 downTo 0) {
                val on = (v shr bit) and 1 == 1
                if (even) {
                    val mid = (lon.first + lon.second) / 2
                    lon = if (on) mid to lon.second else lon.first to mid
                } else {
                    val mid = (lat.first + lat.second) / 2
                    lat = if (on) mid to lat.second else lat.first to mid
                }
                even = !even
            }
        }
        return LatLon((lat.first + lat.second) / 2, (lon.first + lon.second) / 2)
    }
}
