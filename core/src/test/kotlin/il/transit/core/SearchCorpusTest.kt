package il.transit.core

import il.transit.core.api.GeocodeArea
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.MotisClient
import il.transit.core.api.MotisJson
import il.transit.core.api.TransitApi
import il.transit.core.geo.LatLon
import il.transit.core.search.BackupGeocoder
import il.transit.core.search.FallbackGeocoder
import il.transit.core.search.PlaceSearch
import il.transit.core.search.SearchAnswer
import il.transit.core.search.TypingSearch
import il.transit.core.search.parsePhoton
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Phase 11 T9: about a hundred real Israeli place queries (`fixtures/search/search_corpus.tsv`, a file the owner can
 * add rows to) typed through [TypingSearch] and [PlaceSearch] with every answer REPLAYED: Transitous from recordings
 * (`fixtures/search/geo_<hash>.json`; keyed by the text sent, not the bias: the 2026-11-02 recordings of texts with no town were
 * made without one, the app sends Israel's centre since F8, and the rows F8 fixed were re-recorded with it), the phone's Geocoder from `phone_geocoder.tsv` (a stub: it is an Android
 * class), Photon from `photon_replay.tsv` (names of older recordings). Nothing touches the network.
 *
 * A row: id | query | lang | acceptable names | acceptable area S,W,N,E | source | status | note.
 * For each row: an acceptable answer (a name contains one of the alternatives, and lies in the area) is among the
 * first 3 shown; the FIRST acceptable one came from `source` (`BUNDLED`: the app's own station list, F8); and one typing pause sent at most 1 Transitous geocode,
 * 1 phone Geocoder and 1 Photon request, even with the query typed key by key. `BAD` rows are real answers that
 * are wrong: the test pins that they still are (a fix flips the row to OK) and the reason is in the note.
 */
class SearchCorpusTest {
    private data class Row(val line: Int, val id: String, val query: String, val lang: String, val names: List<String>, val box: DoubleArray, val source: String, val bad: Boolean, val note: String, val near: LatLon? = null)

    private val dir = "/fixtures/search"
    private val missing = linkedMapOf<String, String>()

    private fun key(text: String, language: String): String {
        val words = PlaceSearch.normalize(text).replace(",", " ")
        return words.replace(Regex("\\s+"), " ").trim() + "|" + language
    }

    private fun hash(key: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }
    private fun resource(path: String) = javaClass.getResource(path)?.readText()
    private fun table(name: String) = resource("$dir/$name")!!.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }

    private class Counts { var geocode = 0; var phone = 0; var photon = 0; var unrecorded = false }

    private inner class Replay(val counts: Counts) : TransitApi by FakeTransitApi() {
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
            counts.geocode++
            // A row typed from a place (column 9, F10) replays the recording made with that bias, when there is one.
            val biased = near?.let { "geo_" + hash(key(text, language) + "|" + it.comma()) }?.takeIf { resource("$dir/$it.json") != null }
            val name = biased ?: ("geo_" + hash(key(text, language)))
            val body = resource("$dir/$name.json")
            if (body == null) {
                counts.unrecorded = true
                val builder = "http://x/api/v1/geocode".toHttpUrl().newBuilder()
                builder.addQueryParameter("text", text)
                builder.addQueryParameter("language", language)
                builder.addQueryParameter("numResults", max.toString())
                near?.let {
                    builder.addQueryParameter("place", it.comma())
                    builder.addQueryParameter("placeBias", MotisClient.PLACE_BIAS.toString())
                }
                val url = builder.build()
                missing.putIfAbsent(name, "python tools/record_fixture.py search/$name '${url.encodedPath}?${url.encodedQuery}' && sleep 1.5  # ${key(text, language)}")
                throw IOException("not recorded: $text")
            }
            return MotisJson.decodeFromString(ListSerializer(GeocodeMatch.serializer()), body)
        }
    }

    private inner class Phone(val counts: Counts) : BackupGeocoder {
        private val stubs = table("phone_geocoder.tsv").associate {
            // Columns 5-7 (F9): street, house, locality, as AndroidGeocoder.toMatch fills them.
            val locality = it.getOrNull(6)?.takeIf(String::isNotBlank)
            val areas = listOfNotNull(locality?.let { town -> GeocodeArea(town, 8.0, default = true) })
            it[0] to GeocodeMatch("ADDRESS", it[1], "phone:" + it[1], it[2].toDouble(), it[3].toDouble(), street = it.getOrNull(4), houseNumber = it.getOrNull(5), areas = areas)
        }
        override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
            counts.phone++
            return listOfNotNull(stubs[key(text, language)])
        }
    }

    private inner class Photon(val counts: Counts) : BackupGeocoder {
        private val replay = table("photon_replay.tsv").associate { it[0] to it[1] }
        override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
            counts.photon++
            val file = replay[key(text, language)] ?: return emptyList()
            return parsePhoton(resource("/fixtures/$file.json")!!).take(max)
        }
    }

    private fun rows(): List<Row> =
        resource("$dir/search_corpus.tsv")!!.lines().mapIndexedNotNull { i, line ->
            if (line.isBlank() || line.startsWith("#")) return@mapIndexedNotNull null
            val c = line.split('\t') + List(9) { "" }
            val near = c[8]
                .takeIf { it.isNotBlank() }
                ?.split(',')
                ?.let { (a, b) -> LatLon(a.trim().toDouble(), b.trim().toDouble()) }
            Row(i + 1, c[0], c[1], c[2], c[3].split('|').filter { it.isNotBlank() && it != "-" }, c[4].split(',').map { it.toDouble() }.toDoubleArray(), c[5], c[6] == "BAD", c[7], near)
        }

    private fun acceptable(r: Row, m: GeocodeMatch): Boolean {
        val n = PlaceSearch.normalize(m.name + " " + (m.street ?: ""))
        val named = r.names.isEmpty() || r.names.any { n.contains(PlaceSearch.normalize(it)) }
        val inBox = m.lat in r.box[0]..r.box[2] && m.lon in r.box[1]..r.box[3]
        return named && inBox
    }

    private class Outcome(val shown: List<GeocodeMatch>, val answer: SearchAnswer?, val counts: Counts, val answers: Int)

    /** Types [query] key by key, 60 ms apart, then waits: what one typing pause does. */
    private fun type(query: String, lang: String, near: LatLon? = null): Outcome {
        val counts = Counts()
        var last: SearchAnswer? = null
        var settled = 0
        kotlinx.coroutines.test.runTest {
            val search = PlaceSearch(Replay(counts), FallbackGeocoder(Phone(counts), Photon(counts)))
            val typing = TypingSearch(this, search) { _, a, failed -> if (!a.pending && !failed) { last = a; settled++ } }
            for (i in 1..query.length) {
                typing.onText(query.substring(0, i), lang) { near }
                advanceTimeBy(60)
            }
            advanceUntilIdle()
        }
        return Outcome(last?.all.orEmpty(), last, counts, settled)
    }

    @Test fun corpus() {
        val rows = rows()
        val failures = mutableListOf<String>()
        val observed = mutableListOf<String>()
        val ids = HashSet<String>()
        for (r in rows) {
            if (!ids.add(r.id)) failures += "line ${r.line}: duplicate id ${r.id}"
            val o = type(r.query, r.lang, r.near)
            val top = o.shown.take(3)
            val hit = top.firstOrNull { acceptable(r, it) }
            val source = when {
                hit == null -> "NONE"
                o.answer != null && hit in o.answer.stations -> "BUNDLED"
                o.answer != null && hit in o.answer.backup -> if (o.counts.phone > 0 && hit.id.startsWith("phone:")) "PHONE" else "PHOTON"
                else -> "TRANSITOUS"
            }
            observed += listOf(r.id, r.query, "top=" + top.joinToString(" / ") { "${it.name} (%.3f,%.3f)".format(it.lat, it.lon) }, "source=$source", "geocode=${o.counts.geocode} phone=${o.counts.phone} photon=${o.counts.photon}").joinToString("\t")
            // A search text with no recording makes the replay throw, which reads as "no answer": a BAD row would pass on that.
            if (o.counts.unrecorded) {
                failures += "line ${r.line} ${r.id}: its search text is not recorded (see the SEARCH-MISSING list)"
                continue
            }
            if (o.counts.geocode == 0 && o.answer == null) { failures += "line ${r.line} ${r.id}: no answer (unrecorded? see core/build/search-missing.sh)"; continue }
            if (o.counts.geocode > 1 || o.counts.phone > 1 || o.counts.photon > 1) failures += "line ${r.line} ${r.id}: one typing pause sent ${o.counts.geocode} geocode / ${o.counts.phone} phone / ${o.counts.photon} photon"
            if (o.answers != 1) failures += "line ${r.line} ${r.id}: ${o.answers} settled answers for one pause"
            if (r.bad) {
                if (hit != null) failures += "line ${r.line} ${r.id}: marked BAD but ${hit.name} is now in the top 3 (source $source): flip the row to OK"
                if (r.note.isBlank()) failures += "line ${r.line} ${r.id}: a BAD row needs its reason"
                // BAD means "answered, and wrongly": an empty or missing answer is something else (a broken replay).
                if (o.answer == null || o.shown.isEmpty()) failures += "line ${r.line} ${r.id}: a BAD row must have answers (it got none)"
                if (r.source != "NONE") failures += "line ${r.line} ${r.id}: a BAD row's source is NONE, the row says ${r.source}"
            } else {
                if (hit == null) {
                    failures += "line ${r.line} ${r.id} '${r.query}': no acceptable answer in the top 3: ${top.joinToString { it.name }}"
                } else if (source != r.source) {
                    failures += "line ${r.line} ${r.id}: answered by $source, row says ${r.source}"
                }
            }
        }
        val observedText = observed.joinToString("\n", postfix = "\n")
        val out = File("build").apply { mkdirs() }
        File(out, "search-missing.sh").writeText(missing.values.joinToString("\n", postfix = "\n"))
        File(out, "search-observed.tsv").writeText(observedText)
        // Build output is unreadable to the agents by design (.claude/settings.json), so the list also goes to stdout between
        // markers (`./gradlew -p core test -i --tests '*SearchCorpusTest*'`) and, when SEARCH_MISSING_OUT / SEARCH_OBSERVED_OUT name a file, there.
        val missingText = missing.values.joinToString("\n", postfix = "\n")
        System.getenv("SEARCH_MISSING_OUT")?.takeIf { it.isNotBlank() }?.let { File(it).writeText(missingText) }
        System.getenv("SEARCH_OBSERVED_OUT")?.takeIf { it.isNotBlank() }?.let { File(it).writeText(observedText) }
        println("=== SEARCH-MISSING BEGIN ===\n" + missingText + "=== SEARCH-MISSING END ===")
        println("=== SEARCH-OBSERVED BEGIN ===\n" + observedText + "=== SEARCH-OBSERVED END ===")
        println("=== SEARCH-FAILURES BEGIN ===\n" + failures.joinToString("\n", postfix = "\n") + "=== SEARCH-FAILURES END ===")
        assertTrue(
            "${failures.size} of ${rows.size} rows fail:\n" + failures.joinToString("\n") +
                (if (missing.isEmpty()) "" else "\nRECORD (≥ 1.5 s apart; ${missing.size} new searches):\n" + missing.values.joinToString("\n")),
            failures.isEmpty(),
        )
    }

    @Test fun `a row typed from a place replays its biased recording`() {
        // F10: the owner near Be'er Sheva typed "תחנה מרכזית" and got a station in the north; these rows say where they were typed.
        val typed = rows().filter { it.near != null }
        assertTrue(typed.size >= 3)
        for (r in typed) {
            val bias = PlaceSearch.normalize(if (r.lang == "en") "תחנה מרכזית" else r.query)
            assertTrue(r.id, resource("$dir/geo_" + hash("$bias|${r.lang}|${r.near!!.comma()}") + ".json") != null)
        }
    }

    @Test fun `corpus is about a hundred rows in both languages with all the kinds`() {
        val rows = rows()
        assertTrue("${rows.size} rows", rows.size in 100..140)
        assertTrue(rows.any { it.lang == "he" } && rows.any { it.lang == "en" })
        for (kind in listOf("cities", "translit", "typo", "niqqud", "abbr", "street", "citystreet", "rail", "lr", "bus", "lm", "amb", "mixed", "backup")) {
            assertTrue("no $kind rows", rows.any { it.id.startsWith("$kind-") })
        }
        assertTrue(rows.all { it.source in setOf("TRANSITOUS", "PHONE", "PHOTON", "BUNDLED", "NONE") })
    }
}
