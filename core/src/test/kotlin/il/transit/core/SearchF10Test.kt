package il.transit.core

import il.transit.core.api.GeocodeArea
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.MotisJson
import il.transit.core.api.TransitApi
import il.transit.core.geo.Geo
import il.transit.core.geo.LatLon
import il.transit.core.search.LocalFirst
import il.transit.core.search.LocalHit
import il.transit.core.search.MatchLevel
import il.transit.core.search.PlaceSearch
import il.transit.core.search.SearchAnswer
import il.transit.core.user.SavedPlace
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 11 F10 (owner's v0.10.0 bug bash on the Pixel, near Be'er Sheva, app in English): "תחנה מרכזית" gave a trip to a
 * central station in the far north. Transitous, biased to the phone's fix, answers Be'er Sheva's first (recorded
 * 2026-11-03, `search/geo_5d2702b537ad.json` and `geo_3f5021c9068a.json`, place=31.25,34.79); our side lost it twice:
 * - Be'er Sheva's is the stop "שדרות דוד חכם/תחנה מרכזית": it was only a "closest match", and the exact-name rule put
 *   "תחנה מרכזית" (Ashdod's stop) first among the answers;
 * - a recent pick (or saved place) with that name anywhere in Israel came before every server answer, and the keyboard's
 *   Search key picks the first row (the unbiased recording, `geo_1cffe54e16d0`, has "תחנה מרכזית" stops in Rosh Pina and
 *   Kiryat Shmona: one picked once is a recent forever).
 * A generic text (kind words only, no town) now ranks by distance from the fix (else the map's centre), local hits too.
 */
class SearchF10Test {
    private val beerSheva = LatLon(31.25, 34.79)
    private val haifa = LatLon(32.79, 34.99)
    private val jerusalem = LatLon(31.78, 35.21)

    /** Be'er Sheva's central station (the GTFS stop's place, also `ת.מרכזית באר שבע/הורדה` in the address corpus). */
    private val bsCentral = LatLon(31.2430, 34.7961)

    private fun recorded(hash: String): List<GeocodeMatch> =
        MotisJson.decodeFromString(ListSerializer(GeocodeMatch.serializer()), javaClass.getResource("/fixtures/search/geo_$hash.json")!!.readText())

    /** Answers by language, as Transitous does for the same text; records what was sent. */
    private class Replay(val answers: (String) -> List<GeocodeMatch>) : TransitApi by FakeTransitApi() {
        val sent = mutableListOf<Triple<String, String, LatLon?>>()
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
            sent += Triple(text, language, near)
            return answers(language)
        }
    }

    private val nearBeerSheva = Replay { lang -> recorded(if (lang == "en") "3f5021c9068a" else "5d2702b537ad") }

    private fun search(api: TransitApi, text: String, lang: String, near: LatLon?): SearchAnswer =
        runBlocking { PlaceSearch(api, backup = null).search(text, lang, near) }

    private fun at(m: GeocodeMatch) = LatLon(m.lat, m.lon)

    @Test fun `near Be'er Sheva, the first answer is Be'er Sheva's central station, in Hebrew and in English`() {
        for ((text, lang) in listOf("תחנה מרכזית" to "he", "תחנה מרכזית" to "en", "central station" to "en", "Central Bus Station" to "en", "central bus station" to "en")) {
            val api = Replay(nearBeerSheva.answers)
            val a = search(api, text, lang, beerSheva)
            val first = a.exact.first()
            assertTrue("$text/$lang: ${first.name} ${first.lat},${first.lon}", Geo.distanceM(at(first), bsCentral) < 300)
            assertEquals(beerSheva, a.around)
            // One request, the Hebrew text (the index has no English names for these stops), biased to the fix.
            assertEquals(listOf(Triple("תחנה מרכזית", lang, beerSheva)), api.sent)
            // Nearest first, all the way down.
            val d = a.exact.map { Geo.distanceM(beerSheva, at(it)) }
            assertEquals(d.sorted(), d)
        }
    }

    @Test fun `Be'er Sheva's stop, named by its street and the station, is the answer, not a closest match`() {
        val stop = recorded("5d2702b537ad").first { it.name == "שדרות דוד חכם/תחנה מרכזית" }
        assertEquals(MatchLevel.PLACE, PlaceSearch.level("תחנה מרכזית", stop))
        // A stop on a street named like a kind word is still not a station: the part after "/" must lead with the kind.
        val other = stop.copy(name = "תחנה מרכזית/יהודה הלוי")
        assertEquals(MatchLevel.PLACE, PlaceSearch.level("תחנה מרכזית", other))
        assertFalse(PlaceSearch.level("תחנה מרכזית", stop.copy(name = "הרצל/בן יהודה")).isAnswer)
    }

    /**
     * Near Haifa and Jerusalem: the unbiased recording (far stations first, the worst order) plus each city's own central
     * stations at the end. These three are written by hand in Transitous's shape (not recorded: one fixture per
     * bug), at the stations' real places.
     */
    private val cities = listOf(
        GeocodeMatch("STOP", "ת. מרכזית חוף הכרמל", "hand:haifa1", 32.7935, 34.9580, modes = listOf("BUS"), areas = listOf(GeocodeArea("חיפה", 8.0))),
        GeocodeMatch("STOP", "ת. מרכזית המפרץ", "hand:haifa2", 32.7930, 35.0365, modes = listOf("BUS"), areas = listOf(GeocodeArea("חיפה", 8.0))),
        GeocodeMatch("STOP", "ת. מרכזית ירושלים/יפו", "hand:jlm", 31.7890, 35.2030, modes = listOf("BUS"), areas = listOf(GeocodeArea("ירושלים", 8.0))),
    )
    private val countrywide = Replay { recorded("1cffe54e16d0") + cities }

    @Test fun `near Haifa and near Jerusalem, their own central station comes first, in Hebrew and in English`() {
        for ((text, lang) in listOf("תחנה מרכזית" to "he", "central station" to "en", "Central Bus Station" to "en")) {
            val h = search(countrywide, text, lang, haifa).exact.first()
            assertEquals("$text/$lang near Haifa", "ת. מרכזית חוף הכרמל", h.name)
            val j = search(countrywide, text, lang, jerusalem).exact.first()
            assertEquals("$text/$lang near Jerusalem", "ת. מרכזית ירושלים/יפו", j.name)
            // Never a station in the north for someone in Be'er Sheva: Arad's (40 km) before Ashdod's or Rosh Pina's.
            val b = search(countrywide, text, lang, beerSheva).exact.first()
            assertTrue("$text/$lang near Be'er Sheva: ${b.name} ${b.lat}", b.lat < 31.6)
        }
    }

    @Test fun `a far recent pick of that name goes after the nearer answers, a near one stays first`() {
        val a = search(Replay(nearBeerSheva.answers), "תחנה מרכזית", "he", beerSheva)
        val roshPina = LocalHit(SavedPlace("תחנה מרכזית", 32.970, 35.550), LocalHit.Kind.RECENT)
        val kiryatShmona = LocalHit(SavedPlace("תחנה מרכזית", 33.210, 35.570), LocalHit.Kind.RECENT)
        val here = LocalHit(SavedPlace("תחנה מרכזית באר שבע", 31.2431, 34.7962), LocalHit.Kind.RECENT)
        val (first, later) = LocalFirst.aroundSplit(listOf(kiryatShmona, roshPina, here), a.exact, a.around)
        assertEquals(listOf(here), first)
        assertEquals(listOf(roshPina, kiryatShmona), later)
        // Not a generic search (or no place known): local hits first, as before.
        assertEquals(listOf(roshPina) to emptyList<LocalHit>(), LocalFirst.aroundSplit(listOf(roshPina), a.exact, null))
    }

    @Test fun `only a generic text is ranked by distance, and never from Israel's centre`() {
        assertTrue(PlaceSearch.isGenericQuery("תחנה מרכזית"))
        assertTrue(PlaceSearch.isGenericQuery("התחנה המרכזית"))
        assertTrue(PlaceSearch.isGenericQuery("ת. מרכזית"))
        assertFalse(PlaceSearch.isGenericQuery("תחנה מרכזית באר שבע"))
        assertFalse(PlaceSearch.isGenericQuery("ת. מרכזית תל אביב"))
        assertFalse(PlaceSearch.isGenericQuery("הרצל"))
        assertFalse(PlaceSearch.isGenericQuery("תחנה מרכזית מרכז הכרמל"))
        // The English is sent as the Hebrew only when no town is typed with it.
        assertEquals("תחנה מרכזית", il.transit.core.search.Aliases.rewrite("Central Bus Station"))
        assertEquals("Jerusalem central bus station", il.transit.core.search.Aliases.rewrite("Jerusalem central bus station"))
        assertEquals("ת. מרכזית תל אביב", il.transit.core.search.Aliases.rewrite("Tel Aviv Central Bus Station"))
        // A town typed: the town's answers, no distance ranking.
        assertNull(search(Replay(nearBeerSheva.answers), "תחנה מרכזית באר שבע", "he", beerSheva).around)
        // No fix and no map: Transitous's order (with Israel's centre as its bias), nothing re-ranked.
        assertNull(search(Replay(nearBeerSheva.answers), "תחנה מרכזית", "he", null).around)
    }
}
