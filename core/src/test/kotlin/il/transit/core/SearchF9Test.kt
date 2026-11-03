package il.transit.core

import il.transit.core.api.GeocodeArea
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.MotisJson
import il.transit.core.api.TransitApi
import il.transit.core.geo.LatLon
import il.transit.core.search.BackupGeocoder
import il.transit.core.search.MatchLevel
import il.transit.core.search.PlaceSearch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 11 F9 (owner's v0.10.0 bug bash on the Pixel): "Herzl 12 Be'er Sheva", "Jaffa Road 97 Jerusalem" and "Allenby 40
 * Tel Aviv" were not found. Transitous answers only the town (recorded 2026-11-02, T9), so the phone's Geocoder is asked.
 * Its answers here are what Google's Geocoder returned on the google_apis emulator (probe run 41596558603, 2026-11-03,
 * the fields `AndroidGeocoder.toMatch` reads), in English and in Hebrew.
 */
class SearchF9Test {
    private class Geo(val answer: List<GeocodeMatch>) : TransitApi by FakeTransitApi() {
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> = answer
    }

    private class Phone(val answer: List<GeocodeMatch>) : BackupGeocoder {
        var calls = 0
        override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
            calls++
            return answer
        }
    }

    private fun recorded(hash: String): List<GeocodeMatch> =
        MotisJson.decodeFromString(ListSerializer(GeocodeMatch.serializer()), javaClass.getResource("/fixtures/search/geo_$hash.json")!!.readText())

    /** As `AndroidGeocoder.toMatch` builds it: "street number", the street, the house, the locality at level 8. */
    private fun google(street: String, house: String, locality: String, lat: Double, lon: Double, country: String = "Israel") = GeocodeMatch(
        "ADDRESS",
        "$street $house",
        "android:$lat,$lon",
        lat,
        lon,
        street = street,
        houseNumber = house,
        areas = listOf(GeocodeArea(country, 2.0), GeocodeArea(locality, 8.0, default = true)),
    )

    private val yafoEn = google("Yafo", "97", "Jerusalem", 31.784294, 35.214896)
    private val yafoHe = google("יפו", "97", "ירושלים", 31.784294, 35.214896, "ישראל")

    @Test fun `Jaffa Road is the street Google calls Yafo, in English and in Hebrew`() {
        assertEquals(MatchLevel.HOUSE, PlaceSearch.level("Jaffa Road 97 Jerusalem", yafoEn))
        assertEquals(MatchLevel.HOUSE, PlaceSearch.level("Jaffa Road 97 Jerusalem", yafoHe))
        assertEquals(MatchLevel.HOUSE, PlaceSearch.level("Jaffa St 97, Jerusalem", yafoEn))
        // Another number is the street, not the house; another street is not Jaffa Road.
        assertEquals(MatchLevel.STREET, PlaceSearch.level("Jaffa Road 12 Jerusalem", yafoEn))
        assertTrue(!PlaceSearch.level("Jaffa Road 97 Jerusalem", google("King George", "97", "Jerusalem", 31.78, 35.217)).isAnswer)
    }

    @Test fun `Jaffa Road 97 Jerusalem - the phone's Yafo 97 is the answer, above the town`() = runBlocking {
        val town = recorded("f7ef2af80d54")
        for ((lang, answer) in listOf("en" to yafoEn, "he" to yafoHe)) {
            val phone = Phone(listOf(answer))
            val a = PlaceSearch(Geo(town), phone).search("Jaffa Road 97 Jerusalem", lang, null)
            assertEquals(lang, PlaceSearch.BACKUP_PER_SEARCH, phone.calls)
            assertEquals(lang, answer.name, a.all.first().name)
            assertEquals(lang, listOf(answer.name), a.exact.map { it.name })
            assertTrue(lang, !a.notFound)
        }
    }

    @Test fun `Herzl 12 and Allenby 40 - Google's own answers are the first answer`() = runBlocking {
        val cases = listOf(
            Triple("Herzl 12 Be'er Sheva", "06b7ee0b54f2", google("Herzl Street", "12", "Be'er Sheva", 31.238587, 34.7872408)),
            Triple("Herzl 12 Be'er Sheva", "06b7ee0b54f2", google("הרצל", "12", "באר שבע", 31.238587, 34.7872408, "ישראל")),
            Triple("Allenby 40 Tel Aviv", "ad4bc7d4ea20", google("Allenby Street", "40", "Tel Aviv-Yafo", 32.0723638, 34.7687838)),
            Triple("Allenby 40 Tel Aviv", "ad4bc7d4ea20", google("אלנבי", "40", "תל אביב-יפו", 32.0723638, 34.7687838, "ישראל")),
        )
        for ((q, hash, answer) in cases) {
            val a = PlaceSearch(Geo(recorded(hash)), Phone(listOf(answer))).search(q, "en", null)
            assertEquals(q, answer.name, a.all.first().name)
            assertEquals(q, MatchLevel.HOUSE, a.level(a.all.first()))
        }
    }
}
