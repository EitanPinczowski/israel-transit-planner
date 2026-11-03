package il.transit.core

import il.transit.core.api.GeocodeArea
import il.transit.core.api.GeocodeMatch
import il.transit.core.api.MotisJson
import il.transit.core.api.TransitApi
import il.transit.core.geo.LatLon
import il.transit.core.search.Aliases
import il.transit.core.search.BackupGeocoder
import il.transit.core.search.MatchLevel
import il.transit.core.search.PlaceSearch
import il.transit.core.search.QueryText
import il.transit.core.search.Stations
import il.transit.core.search.Towns
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/**
 * Phase 11 F8: the search fixes from T9's corpus. Israel only (every source), Israel's centre as the bias when nothing
 * else says where, a weak answer (the town only) falls through to the backups, transit first for a station query,
 * Latin short forms of towns, and train stations by name from the bundled list. No request is added: still 1 geocode
 * and at most 1 backup request per search ([PlaceSearch.BACKUP_PER_SEARCH]).
 */
class SearchF8Test {
    private class Geo(val answer: (String) -> List<GeocodeMatch>) : TransitApi by FakeTransitApi() {
        val sent: MutableList<Pair<String, LatLon?>> = Collections.synchronizedList(ArrayList())
        override suspend fun geocode(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
            sent += text to near
            return answer(text)
        }
    }

    private class Backup(val answer: (String) -> List<GeocodeMatch> = { emptyList() }) : BackupGeocoder {
        val calls: MutableList<String> = Collections.synchronizedList(ArrayList())
        override suspend fun search(text: String, language: String, near: LatLon?, max: Int): List<GeocodeMatch> {
            calls += text
            return answer(text)
        }
    }

    private fun m(name: String, lat: Double, lon: Double, type: String = "PLACE", town: String? = null, category: String? = null, street: String? = null, house: String? = null) =
        GeocodeMatch(type, name, "id:$name:$lat", lat, lon, street = street, houseNumber = house, category = category, areas = listOfNotNull(town?.let { GeocodeArea(it, 8.0) }))

    private fun recorded(hash: String): List<GeocodeMatch> =
        MotisJson.decodeFromString(ListSerializer(GeocodeMatch.serializer()), javaClass.getResource("/fixtures/search/geo_$hash.json")!!.readText())

    // --- Israel only ------------------------------------------------------------------------------------

    @Test fun `answers outside Israel are dropped from Transitous too`() = runBlocking {
        val api = Geo { listOf(m("TLV", 59.931, 30.366), m("Zetz TLV", 32.062, 34.775, town = "Tel-Aviv"), m("Falafel TLV", 22.28, 114.15)) }
        val a = PlaceSearch(api, null).search("zetz", "en", null)
        assertEquals(listOf("Zetz TLV"), a.all.map { it.name })
    }

    @Test fun `the box has a margin - Eilat, the Golan and the border stay in`() {
        assertTrue(PlaceSearch.inIsrael(LatLon(29.55, 34.95))) // Eilat
        assertTrue(PlaceSearch.inIsrael(LatLon(33.33, 35.77))) // Golan
        assertTrue(PlaceSearch.inIsrael(LatLon(29.37, 34.9))) // 3 km south of the box
        assertFalse(PlaceSearch.inIsrael(LatLon(30.06, 31.25))) // Cairo
        assertFalse(PlaceSearch.inIsrael(LatLon(33.9, 35.5))) // Beirut
        assertFalse(PlaceSearch.inIsrael(LatLon(40.64, 22.93))) // Thessaloniki
    }

    @Test fun `only answers abroad is a miss - the backup is asked, once`() = runBlocking {
        val backup = Backup { listOf(m("ראשון לציון", 31.97, 34.79)) }
        val a = PlaceSearch(Geo { listOf(m("Chaina", 34.5, 108.9)) }, backup).search("Xyzzy town", "en", null)
        assertEquals(1, backup.calls.size)
        assertTrue(a.all.none { it.name == "Chaina" })
    }

    // --- the bias -----------------------------------------------------------------------------------------

    @Test fun `no town, no neighbourhood, no fix - Israel's centre is the bias`() = runBlocking {
        val api = Geo { emptyList() }
        val s = PlaceSearch(api, null)
        s.search("קניון", "he", null)
        assertEquals(PlaceSearch.ISRAEL_CENTRE, api.sent.last().second)
        // The phone's position wins over it, and a typed town over both.
        val here = LatLon(31.25, 34.79)
        s.search("קניון", "he", here)
        assertEquals(here, api.sent.last().second)
        s.search("קניון חיפה", "he", here)
        assertEquals(Towns.named("חיפה")!!.centre, api.sent.last().second)
        assertEquals(3, api.sent.size)
    }

    // --- a weak answer falls through ---------------------------------------------------------------------------

    @Test fun `street and number answered only by the town falls through to the backup, and the town still shows`() = runBlocking {
        // Recorded 2026-11-02 (T9): "Herzl 12 Be'er Sheva" answers the city, its city hall, a theatre... no street.
        val town = recorded("06b7ee0b54f2")
        assertTrue(town.none { it.name.contains("Herzl") })
        val street = m("Herzl 12", 31.243, 34.791, type = "ADDRESS", town = "Be'er Sheva", street = "Herzl", house = "12")
        val backup = Backup { listOf(street) }
        val a = PlaceSearch(Geo { town }, backup).search("Herzl 12 Be'er Sheva", "en", null)
        assertEquals(PlaceSearch.BACKUP_PER_SEARCH, backup.calls.size)
        assertEquals("Herzl 12", a.exact.first().name)
        // Nothing from the backups: the town answers stay, as closest matches ("Town only"), not as the answer.
        val none = PlaceSearch(Geo { town }, Backup()).search("Herzl 12 Be'er Sheva", "en", null)
        assertTrue(none.exact.isEmpty())
        assertTrue(none.notFound)
        assertEquals(MatchLevel.TOWN, none.level(none.closest.first { it.name == "Be'er Sheva" }))
    }

    // --- transit first ----------------------------------------------------------------------------------------

    @Test fun `a station query lists the stops before a shelter of the same name`() = runBlocking {
        val shelter = m("ת. מרכזית תל אביב מקלט", 32.0555, 34.7792, town = "תל אביב-יפו", category = "shelter_14")
        val stop = m("ת. מרכזית תל אביב/לוינסקי", 32.0569, 34.7791, type = "STOP", town = "תל אביב-יפו")
        val a = PlaceSearch(Geo { listOf(shelter, stop) }, null).search("ת. מרכזית תל אביב", "he", null)
        assertEquals(listOf(stop.name, shelter.name), a.exact.map { it.name })
        // Not a station query: Transitous's own order.
        val b = PlaceSearch(Geo { listOf(m("דיזנגוף סנטר", 32.075, 34.775, category = "mall"), m("דיזנגוף סנטר", 32.076, 34.776, type = "STOP")) }, null)
            .search("דיזנגוף סנטר", "he", null)
        assertEquals("PLACE", b.exact.first().type)
    }

    @Test fun `the new central station alias searches the name its stops have`() {
        assertEquals("ת. מרכזית תל אביב", Aliases.rewrite("תחנה מרכזית חדשה תל אביב"))
        assertEquals("ת. מרכזית תל אביב", Aliases.rewrite("התחנה המרכזית החדשה"))
        assertEquals("ת. מרכזית תל אביב", Aliases.rewrite("Tel Aviv Central Bus Station"))
        assertEquals("ת. מרכזית תל אביב", Aliases.rewrite(QueryText.expand("תחנה מרכזית חדשה ת\"א")))
        // Another town's central station is left alone.
        assertEquals("התחנה המרכזית ירושלים", Aliases.rewrite("התחנה המרכזית ירושלים"))
    }

    // --- short forms ----------------------------------------------------------------------------------------------

    @Test fun `TLV, JLM and Chaifa are their towns, wherever typed`() {
        assertEquals("Tel Aviv", QueryText.expand("TLV"))
        assertEquals("Tel Aviv", QueryText.expand("tlv"))
        assertEquals("Haifa", QueryText.expand("Chaifa"))
        assertEquals("Jerusalem", QueryText.expand("JLM"))
        assertEquals("Dizengoff 50 Tel Aviv", QueryText.expand("Dizengoff 50 TLV"))
        assertEquals("דיזנגוף 50 תל אביב", QueryText.expand("דיזנגוף 50 TLV"))
        assertEquals("Herzl 5, Haifa", QueryText.expand("Herzl 5, Chaifa"))
        // Not a word inside another, and the airport keeps its own alias.
        assertEquals("TLVX", QueryText.expand("TLVX"))
        assertEquals("Ben Gurion Airport", Aliases.rewrite(QueryText.expand("TLV airport")))
        assertEquals(Towns.named("תל אביב"), Towns.typedIn(QueryText.expand("Allenby 40 TLV")).first())
    }

    @Test fun `short forms cost nothing for other words - a long shared text is read word by word, prefix by prefix`() {
        assertEquals(null, Aliases.shortForm("שלום", english = false))
        assertEquals(null, Aliases.shortForm("x".repeat(100_000), english = true))
        assertEquals(null, Aliases.shortForm("TLV1", english = true))
        assertEquals("Tel Aviv", Aliases.shortForm("Tlv", english = true))
        // IntentFuzzTest#send_huge_text's text, as SharedPlace reads it: refused, as before F8.
        assertEquals(il.transit.core.present.SharedPlace.Unreadable, il.transit.core.present.SharedPlace.parse("שלום ".repeat(20_000)))
    }

    @Test fun `TLV and Chaifa search what Tel Aviv and Haifa search - one request`() = runBlocking {
        val api = Geo { listOf(m("Tel Aviv-Yafo", 32.085, 34.782, category = "place_6", town = "Tel Aviv-Yafo")) }
        val a = PlaceSearch(api, Backup()).search("TLV", "en", null)
        assertEquals(listOf("Tel Aviv" to Towns.named("Tel Aviv")!!.centre), api.sent)
        assertEquals("Tel Aviv-Yafo", a.exact.first().name)
    }

    // --- train stations by name -----------------------------------------------------------------------------------

    @Test fun `a train station by name comes from the bundled list`() {
        assertEquals(listOf("Rehovot"), Stations.find("Rehovot railway station", "en").map { it.name }.take(1))
        assertEquals("Beersheba Center", Stations.find("Beer Sheva Center train station", "en").first().name)
        assertEquals("Beersheba North", Stations.find("Be'er Sheva North train station", "en").first().name)
        assertEquals("באר שבע מרכז", Stations.find("תחנת רכבת באר שבע מרכז", "he").first().name)
        assertEquals("באר שבע צפון", Stations.find("תחנת הרכבת באר שבע צפון", "he").first().name)
        assertEquals(listOf("תל אביב מרכז"), Stations.find("תחנת רכבת תל אביב סבידור", "he").map { it.name })
        assertEquals("STOP", Stations.find("Rehovot railway station", "en").first().type)
        assertTrue(Stations.find("תחנת רכבת תל אביב", "he").size in 1..Stations.MAX)
    }

    @Test fun `not a station query - light rail, no town, no rail word`() {
        assertTrue(Stations.find("תחנת רכבת קלה אלנבי", "he").isEmpty())
        assertTrue(Stations.find("Tel Aviv light rail station", "en").isEmpty())
        assertTrue(Stations.find("תחנת רכבת", "he").isEmpty())
        assertTrue(Stations.find("רחובות", "he").isEmpty())
        assertTrue(Stations.find("Tel Aviv Central Bus Station", "en").isEmpty())
    }

    @Test fun `stations answer first, Transitous's duplicate goes, and no backup is asked`() = runBlocking {
        val gtfs = Stations.find("Rehovot railway station", "en").first()
        val api = Geo { listOf(gtfs.copy(name = "רחובות"), m("NEW RAILWAY STATION", 40.644, 22.929, type = "STOP")) }
        val backup = Backup()
        val a = PlaceSearch(api, backup).search("Rehovot railway station", "en", null)
        assertEquals(listOf("Rehovot"), a.all.map { it.name })
        assertEquals(listOf(gtfs), a.stations)
        assertEquals(MatchLevel.PLACE, a.level(gtfs))
        assertEquals(1, api.sent.size)
        assertEquals(0, backup.calls.size)
    }

    // --- a town typed one letter short ------------------------------------------------------------------------------

    @Test fun `a town typed one letter short names the town itself`() {
        // Re-recorded 2026-11-02 with the Israel-centre bias: the city first, then stops named ירושלים in other towns.
        val found = recorded("be9edd6b4c48")
        val city = found.first { it.category == "place_6" && it.name == "ירושלים" }
        assertEquals(MatchLevel.PLACE, PlaceSearch.level("ירושלי", city))
        // A short word still is not: "רמת" is not רמת גן.
        assertEquals(MatchLevel.NONE, PlaceSearch.level("רמ", m("רמת גן", 32.07, 34.82, category = "place_6", town = "רמת גן")))
    }
}
