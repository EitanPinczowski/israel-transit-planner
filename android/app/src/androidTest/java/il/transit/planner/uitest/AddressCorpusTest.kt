package il.transit.planner.uitest

import android.location.Geocoder
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.GuardedTransitApi
import il.transit.core.api.MotisClient
import il.transit.core.present.geocodeTown
import il.transit.core.search.FallbackGeocoder
import il.transit.core.search.GuardedGeocoder
import il.transit.core.search.PhotonClient
import il.transit.core.search.PlaceSearch
import il.transit.core.search.SearchAnswer
import il.transit.core.search.Towns
import il.transit.planner.data.AndroidGeocoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The owner's "no address left undefined or wrong" (2026-10-07): ~60 real queries
 * (`assets/address_corpus.tsv`) through the app's real chain — Transitous → the phone's
 * Geocoder → Photon, with the typed-town rule — against the LIVE services. Saved and recent
 * places are left out. Run on demand only (`.github/workflows/address-corpus.yml`), one query a
 * second: about 60–120 requests a run.
 *
 * Per query: the first EXACT answer (`SearchAnswer.exact`; anything else is only shown under
 * "Closest matches") is in the expected town (by the town it names, else inside the town's
 * radius), on the street and at the house number when given. A wrong exact answer always fails;
 * no exact answer passes only for a row whose known-gap column says why (it shows as not found). Logs which source answered and how long it took; writes the table to
 * `files/address_corpus.md`. Fails on a miss that is not listed as a known gap.
 */
@RunWith(AndroidJUnit4::class)
class AddressCorpusTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val corpus = InstrumentationRegistry.getInstrumentation().context.assets

    private data class Row(val query: String, val language: String, val town: String, val streets: List<String>, val house: String, val gap: String)

    @Test fun corpus() = runBlocking {
        val device = if (Geocoder.isPresent()) GuardedGeocoder(AndroidGeocoder(target)) else null
        val search = PlaceSearch(GuardedTransitApi(MotisClient()), FallbackGeocoder(device, GuardedGeocoder(PhotonClient())))
        val rows = corpus.open("address_corpus.tsv").bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val c = line.split('\t') + List(6) { "" }
                Row(c[0], c[1], c[2], c[3].split('|').filter { it.isNotBlank() }, c[4], c[5])
            }
        val out = StringBuilder("| # | query | source | ms | first answer | town | result |\n|---|---|---|---|---|---|---|\n")
        var passed = 0
        val unexpected = mutableListOf<String>()
        rows.forEachIndexed { i, r ->
            val t0 = System.nanoTime()
            val outcome: Pair<SearchAnswer?, String?> = try {
                search.search(r.query, r.language, null) to null
            } catch (e: Exception) {
                null to "${e.javaClass.simpleName}: ${e.message}"
            }
            val (answer, error) = outcome
            val ms = (System.nanoTime() - t0) / 1_000_000
            // Only an exact answer is ever shown as the answer; the rest are labelled "closest".
            val first = answer?.exact?.firstOrNull()
            val problems = if (error == null && first == null && answer != null && r.gap.isNotEmpty()) emptyList() else check(r, first, error)
            val ok = problems.isEmpty()
            if (ok) passed++ else if (r.gap.isEmpty()) unexpected += "${r.query}: ${problems.joinToString("; ")}"
            val result = if (ok) "✅" else "❌ " + problems.joinToString("; ") + (if (r.gap.isNotEmpty()) " (known gap: ${r.gap})" else "")
            val line = "| ${i + 1} | ${r.query} | ${first?.let(::source) ?: "—"} | $ms | ${first?.name ?: "—"} | ${first?.let { geocodeTown(it) ?: it.name } ?: "—"} | $result |"
            out.append(line).append('\n')
            Log.i(TAG, line)
            delay(1_000) // light traffic: one query a second
        }
        val summary = "**$passed / ${rows.size} passed** (phone Geocoder present: ${device != null})"
        out.append('\n').append(summary).append('\n')
        Log.i(TAG, summary)
        File(target.getExternalFilesDir(null), "address_corpus.md").writeText(out.toString())
        assertTrue("unexpected misses:\n" + unexpected.joinToString("\n"), unexpected.isEmpty())
    }

    private fun check(r: Row, first: GeocodeMatch?, error: String?): List<String> {
        if (error != null) return listOf("error $error")
        if (first == null) return listOf("no exact answer (shown as not found)")
        val problems = mutableListOf<String>()
        val town = Towns.named(r.town) ?: return listOf("corpus: unknown town ${r.town}")
        if (!Towns.holds(town, first)) problems += "town ${geocodeTown(first) ?: "?"} (${"%.4f".format(first.lat)}, ${"%.4f".format(first.lon)}), want ${r.town}"
        if (r.streets.isNotEmpty()) {
            val hay = PlaceSearch.normalize(listOfNotNull(first.name, first.street).joinToString(" "))
            if (r.streets.none { hay.contains(PlaceSearch.normalize(it)) }) problems += "street, want ${r.streets.joinToString("/")}"
        }
        if (r.house.isNotEmpty()) {
            val number = first.houseNumber ?: Regex("\\d+").findAll(first.name).lastOrNull()?.value
            if (number?.takeWhile(Char::isDigit) != r.house.takeWhile(Char::isDigit)) problems += "house ${number ?: "none"}, want ${r.house}"
        }
        return problems
    }

    private fun source(m: GeocodeMatch) = when {
        m.id.startsWith("android:") -> "phone"
        m.id.startsWith("photon:") -> "Photon"
        else -> "Transitous"
    }

    private companion object {
        const val TAG = "AddressCorpus"
    }
}
