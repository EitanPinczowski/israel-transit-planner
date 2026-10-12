package il.transit.core

import il.transit.core.geo.LatLon
import il.transit.core.present.SharedPlace
import il.transit.core.search.PlaceSearch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 10 D6: places shared from other apps. Coordinates cost 0 requests; text, one geocode. */
class SharedPlaceTest {
    private fun point(s: String): SharedPlace.Point {
        val p = SharedPlace.parse(s)
        assertTrue("$s → $p", p is SharedPlace.Point)
        return p as SharedPlace.Point
    }

    private fun near(p: SharedPlace.Point, lat: Double, lon: Double) {
        assertEquals(lat, p.at.lat, 1e-4)
        assertEquals(lon, p.at.lon, 1e-4)
    }

    private fun text(s: String): String {
        val p = SharedPlace.parse(s)
        assertTrue("$s → $p", p is SharedPlace.Text)
        return (p as SharedPlace.Text).query
    }

    // --- geo: ---------------------------------------------------------------------------

    @Test fun `geo links`() {
        near(point("geo:32.0853,34.7818"), 32.0853, 34.7818)
        near(point("geo:32.0853,34.7818?z=15"), 32.0853, 34.7818)
        near(point("geo:32.0853,34.7818;u=35"), 32.0853, 34.7818)
        point("geo:0,0?q=32.0745,34.7920(Azrieli)").let { near(it, 32.0745, 34.7920); assertEquals("Azrieli", it.label) }
        point("geo:0,0?q=32.0745,34.7920(%D7%A2%D7%96%D7%A8%D7%99%D7%90%D7%9C%D7%99)").let { assertEquals("עזריאלי", it.label) }
        assertEquals("הרצל 15 תל אביב", text("geo:0,0?q=הרצל+15+תל+אביב"))
        assertEquals("הרצל 15", text("geo:0,0?q=%D7%94%D7%A8%D7%A6%D7%9C%2015"))
        assertEquals(SharedPlace.Unreadable, SharedPlace.parse("geo:0,0"))
        assertEquals(SharedPlace.Unreadable, SharedPlace.parse("geo:999,999"))
    }

    // --- Google Maps --------------------------------------------------------------------

    @Test fun `google maps links`() {
        near(point("https://maps.google.com/?q=32.0853,34.7818"), 32.0853, 34.7818)
        near(point("https://maps.google.com/maps?ll=32.08,34.78&z=15"), 32.08, 34.78)
        near(point("https://www.google.com/maps/@32.08,34.78,15z"), 32.08, 34.78)
        near(point("https://www.google.com/maps/search/?api=1&query=31.25,34.79"), 31.25, 34.79)
        near(point("https://www.google.com/maps/dir/?api=1&destination=31.25%2C34.79"), 31.25, 34.79)
        // The place's own pin (!3d!4d), not the camera (@).
        near(point("https://www.google.com/maps/place/Azrieli+Center/@32.0750,34.7900,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d32.0744!4d34.7922"), 32.0744, 34.7922)
        near(point("https://www.google.co.il/maps/@31.7683,35.2137,14z"), 31.7683, 35.2137)
        assertEquals("Dizengoff Center", text("https://www.google.com/maps?q=Dizengoff+Center"))
        assertEquals("קניון עזריאלי", text("https://www.google.com/maps/place/%D7%A7%D7%A0%D7%99%D7%95%D7%9F+%D7%A2%D7%96%D7%A8%D7%99%D7%90%D7%9C%D7%99/"))
        // Google, but not a map.
        assertEquals(SharedPlace.Unreadable, SharedPlace.parse("https://www.google.com/search?q=32.1,34.8"))
    }

    // --- Waze -----------------------------------------------------------------------------

    @Test fun `waze links`() {
        near(point("https://waze.com/ul?ll=32.0853,34.7818&navigate=yes"), 32.0853, 34.7818)
        near(point("https://www.waze.com/ul?ll=32.0853%2C34.7818&navigate=yes&zoom=17"), 32.0853, 34.7818)
        near(point("https://www.waze.com/live-map/directions?to=ll.32.0853,34.7818&latlng=32.0853,34.7818"), 32.0853, 34.7818)
        // The geohash form Waze's "share location" sends: Tel Aviv.
        near(point("https://waze.com/ul/hsv8wrvf3r"), 32.06795, 34.79711)
        assertEquals("Azrieli", text("https://waze.com/ul?q=Azrieli&navigate=yes"))
    }

    // --- short links, outside Israel, nothing ------------------------------------------

    @Test fun `short links are not opened`() {
        assertEquals(SharedPlace.ShortLink, SharedPlace.parse("https://maps.app.goo.gl/AbCdEf12345"))
        assertEquals(SharedPlace.ShortLink, SharedPlace.parse("https://goo.gl/maps/xyz"))
        assertEquals(SharedPlace.ShortLink, SharedPlace.parse("תראה כאן https://maps.app.goo.gl/AbCdEf12345"))
        // Google's own share text carries the address next to its short link: the address.
        assertEquals(
            "Derech Menachem Begin 132, Tel Aviv-Yafo",
            text("Azrieli Center\nDerech Menachem Begin 132, Tel Aviv-Yafo\nhttps://maps.app.goo.gl/AbCdEf12345"),
        )
    }

    @Test fun `outside Israel is refused`() {
        assertEquals(SharedPlace.OutsideIsrael, SharedPlace.parse("geo:48.8584,2.2945"))
        assertEquals(SharedPlace.OutsideIsrael, SharedPlace.parse("https://www.google.com/maps/@40.7128,-74.0060,12z"))
        assertEquals(SharedPlace.OutsideIsrael, SharedPlace.parse("https://waze.com/ul?ll=51.5,-0.12"))
    }

    @Test fun `nothing to open`() {
        assertEquals(SharedPlace.Unreadable, SharedPlace.parse(null))
        assertEquals(SharedPlace.Unreadable, SharedPlace.parse("   "))
        assertEquals(SharedPlace.Unreadable, SharedPlace.parse("https://example.com/page"))
        assertEquals(SharedPlace.Unreadable, SharedPlace.parse("מה שלומך? מתגעגעים אליך מאוד, נדבר בערב כשתחזור מהעבודה"))
    }

    // --- WhatsApp texts -----------------------------------------------------------------

    @Test fun `an address inside a message`() {
        assertEquals("רחוב הרצל 15 בתל אביב", text("היי! מחר נפגשים ברחוב הרצל 15 בתל אביב בשמונה בערב. תביא את הספר 🙂"))
        assertEquals("Dizengoff 50, Tel Aviv", text("Hey, dinner tonight at Dizengoff 50, Tel Aviv. See you at 8!"))
        assertEquals("הרצל 15, רעננה", text("כתובת: הרצל 15, רעננה"))
        assertEquals("טבנקין 15 רעננה", text("טבנקין 15 רעננה"))
        assertEquals("קניון עזריאלי", text("קניון עזריאלי"))
        // A link inside a message wins over its words.
        near(point("תבוא לפה https://www.google.com/maps?q=32.1,34.85 אני מחכה"), 32.1, 34.85)
    }

    @Test fun `a long WhatsApp message`() {
        val msg = """
            שלום לכולם!
            תזכורת: יום ההולדת של סבתא ביום שישי הקרוב, מתחילים ב-12:00.
            הכתובת: שדרות רגר 120, באר שבע (קומה 3, דירה 9)
            מי שמגיע ברכבת - יש הסעה מהתחנה, תכתבו לי.
            מביאים עוגות ושתייה, אל תשכחו מתנה 🎁🎂
        """.trimIndent()
        val q = text(msg)
        assertTrue(q, q.startsWith("שדרות רגר 120, באר שבע"))
        assertTrue(q.length <= SharedPlace.MAX_QUERY)
    }

    // --- requests -------------------------------------------------------------------------

    @Test fun `coordinates send 0 requests, text one geocode`() = runTest {
        val api = FakeTransitApi()
        val search = PlaceSearch(api, null)
        suspend fun open(s: String) {
            (SharedPlace.parse(s) as? SharedPlace.Text)?.let { search.search(it.query, "he", LatLon(32.08, 34.78)) }
        }
        listOf(
            "geo:32.0853,34.7818", "geo:0,0?q=32.07,34.79(Azrieli)", "https://maps.google.com/?q=32.0853,34.7818",
            "https://www.google.com/maps/@32.08,34.78,15z", "https://waze.com/ul?ll=32.0853,34.7818", "https://waze.com/ul/hsv8wrvf3r",
            "https://maps.app.goo.gl/AbCdEf12345", "geo:48.8584,2.2945",
        ).forEach { open(it) }
        assertEquals(emptyList<String>(), api.calls)
        open("כתובת: הרצל 15, רעננה")
        assertEquals(listOf("geocode"), api.calls)
    }
}
