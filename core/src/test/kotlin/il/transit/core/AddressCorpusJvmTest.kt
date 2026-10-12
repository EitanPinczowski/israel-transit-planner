package il.transit.core

import il.transit.core.api.GeocodeMatch
import il.transit.core.api.MotisClient
import il.transit.core.api.MotisJson
import il.transit.core.api.TransitApi
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import il.transit.core.search.BackupGeocoder
import il.transit.core.search.MatchLevel
import il.transit.core.search.PlaceSearch
import il.transit.core.search.SearchAnswer
import il.transit.core.search.Towns
import il.transit.core.search.parsePhoton
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Phase 10 D2: the address corpus on the JVM, every push. Each row of `fixtures/corpus/corpus.tsv`
 * goes through the real [PlaceSearch] (aliases, acronyms, towns, neighbourhoods, match levels)
 * with Transitous and the backup geocoder REPLAYED from recordings, so it runs in milliseconds and
 * never touches the network.
 *
 * Replay: an answer is found by what was sent, normalized ([key]: no marks, commas or extra
 * spaces, + the language), so variants that search the same text share one recording ("ת״א",
 * "ת'א" and "תל אביב" all send "תל אביב"). The bias is not part of the key: each recording was made
 * with the bias the app sends for it (the typed town's or neighbourhood's centre, else none).
 * Transitous answers are `corpus/geo_<hash>.json`, Photon's `corpus/photon_<hash>.json`; an
 * unrecorded backup answer is a backup failure (the search goes on without it, as in the app).
 *
 * A row: query | language | level | expected name (contains; alternatives with `|`) | lat,lon
 * (within 150 m) | known gap. HOUSE/PLACE: the first answer shown as THE answer is that place at
 * that level. STREET/TOWN: nothing is shown as the answer, and the first closest match is that
 * place at that level. NONE: nothing is shown as the answer. A known gap passes with no answer,
 * but a wrong answer fails it too.
 *
 * Adding a row: append it; if its search text is new, the test fails and writes the
 * `tools/record_fixture.py` commands for it to `core/build/corpus-missing.sh` (Transitous: ≥ 1 s
 * apart; Photon: from a machine that can reach photon.komoot.io). See the `transitous-api` skill.
 */
class AddressCorpusJvmTest {
    private data class Row(val line: Int, val query: String, val language: String, val level: MatchLevel, val names: List<String>, val at: LatLon?, val gap: String)

    private val dir = "/fixtures/corpus"
    private val missing = linkedMapOf<String, String>()
    private val missingPhoton = linkedSetOf<String>()

    private fun key(text: String, language: String) =
        PlaceSearch.normalize(text).replace(",", " ").replace(Regex("\\s+"), " ").trim() + "|" + language

    private fun hash(key: String) = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)

    private fun fixture(name: String): String? = javaClass.getResource("$dir/$name.json")?.readText()

    private inner class ReplayTransitous : TransitApi by FakeTransitApi() {
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
            val name = "geo_" + hash(key(text, language))
            val body = fixture(name)
            if (body == null) {
                // The same request MotisClient sends, for tools/record_fixture.py.
                val url = "http://x/api/v1/geocode".toHttpUrl().newBuilder().addQueryParameter("text", text).addQueryParameter("language", language)
                    .addQueryParameter("numResults", max.toString()).apply {
                        near?.let { addQueryParameter("place", it.comma()); addQueryParameter("placeBias", MotisClient.PLACE_BIAS.toString()) }
                    }.build()
                missing.putIfAbsent(name, "python tools/record_fixture.py corpus/$name '${url.encodedPath}?${url.encodedQuery}' && sleep 1  # ${key(text, language)}")
                throw IOException("not recorded: $text")
            }
            return MotisJson.decodeFromString(ListSerializer(GeocodeMatch.serializer()), body)
        }
    }

    private inner class ReplayPhoton : BackupGeocoder {
        override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
            val name = "photon_" + hash(key(text, language))
            val body = fixture(name)
            if (body == null) {
                missingPhoton += "$name\t${key(text, language)}\t${near?.comma().orEmpty()}"
                throw IOException("not recorded: $text")
            }
            return parsePhoton(body).take(max)
        }
    }

    private fun rows(): List<Row> =
        javaClass.getResource("$dir/corpus.tsv")!!.readText().lines().mapIndexedNotNull { i, line ->
            if (line.isBlank() || line.startsWith("#")) return@mapIndexedNotNull null
            val c = line.split('\t') + List(6) { "" }
            Row(
                i + 1, c[0], c[1], MatchLevel.valueOf(c[2]), c[3].split('|').filter { it.isNotBlank() },
                c[4].takeIf { it.isNotBlank() }?.split(',')?.let { (a, b) -> LatLon(a.trim().toDouble(), b.trim().toDouble()) }, c[5],
            )
        }

    private fun isPlace(r: Row, m: GeocodeMatch?): Boolean {
        if (m == null) return false
        val name = PlaceSearch.normalize(m.name)
        val named = r.names.isEmpty() || r.names.any { name.contains(PlaceSearch.normalize(it)) }
        val near = r.at == null || Geo.distanceM(r.at, LatLon(m.lat, m.lon)) <= SAME_PLACE_M
        return named && near
    }

    private fun describe(a: SearchAnswer, m: GeocodeMatch?) =
        m?.let { "${a.level(it)} ${it.name} (%.5f,%.5f)".format(it.lat, it.lon) } ?: "nothing"

    private fun check(r: Row, a: SearchAnswer): String? {
        val first = a.exact.firstOrNull()
        if (r.gap.isNotEmpty()) {
            if (first == null) return null
            // A known gap excuses "not found", never a wrong answer.
            if (r.names.isEmpty() && r.at == null) return "known gap answered ${describe(a, first)}: check it and give the row its place"
            return if (isPlace(r, first) && a.level(first) == r.level) null else "known gap, but a wrong answer: ${describe(a, first)}"
        }
        return when (r.level) {
            MatchLevel.HOUSE, MatchLevel.PLACE ->
                if (first != null && a.level(first) == r.level && isPlace(r, first)) null else "want ${r.level} ${r.names} ${r.at}, got ${describe(a, first)}"
            MatchLevel.STREET, MatchLevel.TOWN -> {
                val c = a.closest.firstOrNull()
                when {
                    first != null -> "want no answer (${r.level} only), got ${describe(a, first)}"
                    c == null || a.level(c) != r.level || !isPlace(r, c) -> "want closest ${r.level} ${r.names} ${r.at}, got ${describe(a, c)}"
                    else -> null
                }
            }
            MatchLevel.NONE -> first?.let { "want not found, got ${describe(a, it)}" }
        }
    }

    @Test fun corpus() = runBlocking {
        val search = PlaceSearch(ReplayTransitous(), ReplayPhoton())
        val rows = rows()
        val failures = mutableListOf<String>()
        val observed = mutableListOf<String>()
        for (r in rows) {
            val answer = try {
                search.search(r.query, r.language, near = null)
            } catch (e: IOException) {
                failures += "line ${r.line} ${r.query}: ${e.message}"
                continue
            }
            check(r, answer)?.let { failures += "line ${r.line} ${r.query}: $it" }
            val shown = answer.exact.firstOrNull() ?: answer.closest.firstOrNull()
            observed += listOf(r.query, r.language, shown?.let { answer.level(it).name } ?: "NONE", shown?.name.orEmpty(),
                shown?.let { "%.5f,%.5f".format(it.lat, it.lon) }.orEmpty(), shown?.let { il.transit.core.present.geocodeDetail(it) }.orEmpty(),
                if (shown != null && shown in answer.backup) "backup" else "").joinToString("\t")
        }
        val out = File("build").apply { mkdirs() }
        File(out, "corpus-missing.sh").writeText(missing.values.joinToString("\n", postfix = "\n"))
        // What each row shows first (level, name, lat,lon, detail line, source): for writing and checking rows.
        File(out, "corpus-observed.tsv").writeText(observed.joinToString("\n", postfix = "\n"))
        File(out, "corpus-missing-photon.tsv").writeText(missingPhoton.joinToString("\n", postfix = "\n"))
        assertTrue("${failures.size} of ${rows.size} rows fail (unrecorded searches: core/build/corpus-missing.sh):\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun `at least 200 rows, every level and town known`() {
        val rows = rows()
        assertTrue("${rows.size} rows", rows.size >= 200)
        assertTrue(rows.all { it.language == "he" || it.language == "en" })
        // Every row that names a place says where it is.
        rows.filter { it.level != MatchLevel.NONE && it.gap.isEmpty() }.forEach { assertTrue("line ${it.line}: no lat,lon", it.at != null) }
        assertTrue(Towns.ALL.isNotEmpty())
    }

    private companion object {
        const val SAME_PLACE_M = 150.0
    }
}
