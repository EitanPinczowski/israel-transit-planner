package il.transit.core

import il.transit.core.api.GeocodeMatch
import il.transit.core.api.MotisJson
import il.transit.core.geo.LatLon
import il.transit.core.plan.CalendarDestination
import il.transit.core.plan.CalendarEvent
import il.transit.core.plan.CalendarSuggest
import il.transit.core.user.UserJson
import il.transit.core.user.UserSettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSuggestTest {
    // NOON = Wednesday 2026-09-23 12:00 Israel time.
    private fun at(hours: Double) = NOON.plusSeconds((hours * 3600).toLong())
    private fun event(title: String, inHours: Double, location: String = "הרצל 12, באר שבע", allDay: Boolean = false, declined: Boolean = false) =
        CalendarEvent(title, location, at(inHours), at(inHours + 1), allDay, declined)

    // --- which events ---

    @Test fun `offers upcoming events with a location, soonest first`() {
        val picked = CalendarSuggest.pick(
            listOf(event("Gym", 5.0), event("Dentist", 1.0), event("Lunch", 3.0)),
            NOON,
        )
        assertEquals(listOf("Dentist", "Lunch", "Gym"), picked.map { it.title })
    }

    @Test fun `skips started, all-day, declined, location-less and too-far events`() {
        val picked = CalendarSuggest.pick(
            listOf(
                event("Started", -0.25),
                event("Now", 0.0), // starts exactly now: already started
                event("Holiday", 2.0, allDay = true),
                event("Declined", 2.0, declined = true),
                event("Call", 2.0, location = "  "),
                event("Next week", 24.5),
                event("Tomorrow morning", 21.0),
            ),
            NOON,
        )
        assertEquals(listOf("Tomorrow morning"), picked.map { it.title })
    }

    @Test fun `an event in two calendars is listed once, and the list is capped`() {
        val twice = listOf(event("Dentist", 1.0), event("Dentist", 1.0))
        assertEquals(1, CalendarSuggest.pick(twice, NOON).size)
        val many = (1..10).map { event("E$it", it.toDouble()) }
        assertEquals(CalendarSuggest.MAX, CalendarSuggest.pick(many, NOON).size)
    }

    @Test fun `row shows Israel time, and marks tomorrow`() {
        val today = CalendarSuggest.row(event(" Dentist ", 1.0 + 50 / 60.0), NOON)
        assertEquals("13:50", today.time)
        assertEquals("Dentist", today.title)
        assertEquals("הרצל 12, באר שבע", today.location)
        assertFalse(today.tomorrow)
        // 21:00 later = 09:00 Thursday.
        val tomorrow = CalendarSuggest.row(event("Class", 21.0), NOON)
        assertEquals("09:00", tomorrow.time)
        assertTrue(tomorrow.tomorrow)
    }

    // --- arrive by ---

    @Test fun `arrive-by is the start minus the buffer`() {
        val e = event("Dentist", 2.0)
        assertEquals(at(2.0 - 10 / 60.0), CalendarSuggest.arriveBy(e, 10, NOON))
        assertEquals(e.begin, CalendarSuggest.arriveBy(e, 0, NOON))
    }

    @Test fun `no arrive-by when the buffer is already past - leave now instead`() {
        assertNull(CalendarSuggest.arriveBy(event("Soon", 5 / 60.0), 10, NOON))
    }

    // --- coordinates ---

    @Test fun `reads coordinates written in the location`() {
        val bgu = LatLon(31.2622, 34.8013)
        assertEquals(bgu, CalendarSuggest.parseLatLon("31.2622,34.8013"))
        assertEquals(bgu, CalendarSuggest.parseLatLon("31.2622, 34.8013"))
        assertEquals(bgu, CalendarSuggest.parseLatLon("geo:31.2622,34.8013"))
        assertEquals(bgu, CalendarSuggest.parseLatLon("https://maps.google.com/?q=31.2622,34.8013"))
        assertEquals(bgu, CalendarSuggest.parseLatLon("BGU building 28 (31.2622, 34.8013)"))
    }

    @Test fun `addresses and bad numbers are not coordinates`() {
        assertNull(CalendarSuggest.parseLatLon("הרצל 12, באר שבע"))
        assertNull(CalendarSuggest.parseLatLon("Herzl 12, 3rd floor"))
        assertNull(CalendarSuggest.parseLatLon("95.0, 34.8")) // latitude out of range
    }

    @Test fun `geocode bias is rounded so the day cache hits across town`() {
        assertEquals(LatLon(31.3, 34.8), CalendarSuggest.biasPoint(LatLon(31.2622, 34.8013)))
        assertEquals(LatLon(31.3, 34.8), CalendarSuggest.biasPoint(LatLon(31.2549, 34.7951)))
        assertNull(CalendarSuggest.biasPoint(null))
    }

    // --- where (requests) ---

    @Test fun `coordinates cost no request`() = runTest {
        val api = FakeTransitApi()
        val d = CalendarSuggest.resolve(api, event("Dentist", 1.0, location = "31.2622,34.8013"), "he", null)
        assertEquals(CalendarDestination("Dentist", LatLon(31.2622, 34.8013), "31.2622,34.8013", approximate = false), d)
        val named = CalendarSuggest.resolve(api, event("Class", 1.0, location = "BGU (31.2622, 34.8013)"), "he", null)
        assertEquals("BGU (31.2622, 34.8013)", named?.name)
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test fun `an address costs one geocode with the location text only - never the title`() = runTest {
        val api = FakeTransitApi()
        val sent = mutableListOf<String>()
        api.onGeocode = { text -> sent += text; recorded("geocode_calendar_herzl_he") }
        val d = CalendarSuggest.resolve(api, event("Dentist", 1.0, location = " הרצל 12, באר שבע "), "he", LatLon(31.2622, 34.8013))
        assertEquals(listOf("geocode"), api.calls)
        assertEquals(listOf("הרצל 12, באר שבע"), sent)
        assertFalse(sent.any { "Dentist" in it })
        // Recorded 2026-10-04: the house number is matched loosely (12 → 126), same street.
        assertEquals("הרצל 126", d?.name)
        assertEquals(31.2432, d!!.at.lat, 1e-3)
        // ...so it is shown next to the event's own text, marked approximate.
        assertEquals("הרצל 12, באר שבע", d.source)
        assertTrue(d.approximate)
    }

    // --- is the answer really that place? ---

    private fun address(name: String, house: String?, type: String = "ADDRESS", category: String? = null) =
        GeocodeMatch(type = type, name = name, id = "", lat = 31.24, lon = 34.79, houseNumber = house, category = category)

    @Test fun `recorded mismatches are approximate - another house number, or only the town`() {
        assertTrue(CalendarSuggest.isApproximate("הרצל 12, באר שבע", recorded("geocode_calendar_herzl_he").first()))
        assertTrue(CalendarSuggest.isApproximate("Herzl St 12, Be'er Sheva, Israel", recorded("geocode_calendar_herzl_en").first()))
        assertTrue(CalendarSuggest.isApproximate("Herzl 12, Be'er Sheva", recorded("geocode_calendar_herzl_en_short").first()))
    }

    @Test fun `a town answer is approximate even when the text has no number`() {
        assertTrue(CalendarSuggest.isApproximate("Be'er Sheva", address("Be'er Sheva", null, "PLACE", "place_6")))
    }

    @Test fun `a number in the text that the answer lacks is approximate - street, stop or POI`() {
        assertTrue(CalendarSuggest.isApproximate("הרצל 12", address("הרצל", null)))
        assertTrue(CalendarSuggest.isApproximate("הרצל 12", address("רמב\"ם/הרצל", null, "STOP")))
    }

    @Test fun `same house number, or no number to compare, is exact`() {
        assertFalse(CalendarSuggest.isApproximate("הרצל 12, באר שבע", address("הרצל 12", "12")))
        assertFalse(CalendarSuggest.isApproximate("הרצל 12א, באר שבע", address("הרצל 12א", "12א")))
        assertFalse(CalendarSuggest.isApproximate("Office, Herzl 12, floor 3", address("הרצל 12", "12")))
        assertFalse(CalendarSuggest.isApproximate("סורוקה", address("סורוקה", null, "PLACE", "hospital")))
        // A postcode (7 digits) is not a house number.
        assertFalse(CalendarSuggest.isApproximate("הרצל 12, באר שבע 8410501", address("הרצל 12", "12")))
    }

    @Test fun `a maps short link or no answer gives null - the user types the place`() = runTest {
        val api = FakeTransitApi()
        assertNull(CalendarSuggest.resolve(api, event("X", 1.0, location = "https://maps.app.goo.gl/abc123"), "he", null))
        assertEquals(emptyList<String>(), api.calls)
        api.onGeocode = { emptyList() }
        assertNull(CalendarSuggest.resolve(api, event("X", 1.0, location = "nowhere at all"), "he", null))
    }

    @Test fun `English street names are not in the geocoder - only the city comes back`() {
        // Recorded 2026-10-04, with and without "St" and ", Israel": Be'er Sheva itself, no street.
        for (name in listOf("geocode_calendar_herzl_en", "geocode_calendar_herzl_en_short")) {
            val top = recorded(name).first()
            assertEquals("PLACE", top.type)
            assertEquals("Be'er Sheva", top.name)
        }
    }

    // --- setting ---

    @Test fun `buffer setting defaults to 10 and survives the JSON round trip`() {
        assertEquals(10, UserSettings().calendarBufferMin)
        val s = UserSettings(calendarBufferMin = 20)
        assertEquals(s, UserJson.decodeSettings(UserJson.encodeSettings(s)))
        // Settings saved before the field existed read the default.
        assertEquals(10, UserJson.decodeSettings("""{"maxWalkMin":20}""").calendarBufferMin)
    }

    private fun recorded(name: String): List<GeocodeMatch> = MotisJson.decodeFromString(
        ListSerializer(GeocodeMatch.serializer()),
        javaClass.getResource("/fixtures/$name.json")!!.readText(),
    )
}
