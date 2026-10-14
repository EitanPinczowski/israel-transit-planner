package il.transit.core.prop

import il.transit.core.fare.FareProfile
import il.transit.core.history.History
import il.transit.core.history.TripRecord
import il.transit.core.plan.TripSort
import il.transit.core.user.FavoriteLine
import il.transit.core.user.ModeFilter
import il.transit.core.user.PlaceRoutine
import il.transit.core.user.SavedPlace
import il.transit.core.user.SavedTrip
import il.transit.core.user.UserJson
import il.transit.core.user.UserSettings
import il.transit.core.user.WalkSpeed
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Saved data: decode(encode(x)) == x; unknown fields are ignored; a missing field takes its default. */
class UserJsonPropertyTest {
    // Hebrew, English, RTL marks, quotes, emoji: names are free text.
    private val text: Arb<String> = Arb.string(0..14).map { it + "" }.let { base ->
        Arb.bind(base, Arb.element("", "בית", "עבודה", "Home \"quoted\"", "‏רגר‎", "🚌 stop", "a\\b", "tab\there")) { a, b -> a + b }
    }
    private val coord = Arb.double(-90.0, 90.0)

    private val routine: Arb<PlaceRoutine> = Arb.bind(Arb.list(Arb.int(1..7), 0..7), Arb.int(0..1439), Arb.int(0..1439)) { d, a, b -> PlaceRoutine(d, a, b) }
    private val place: Arb<SavedPlace> = Arb.bind(text, coord, Arb.double(-180.0, 180.0), routine.orNull(0.5)) { n, la, lo, r -> SavedPlace(n, la, lo, r) }
    private val trip: Arb<SavedTrip> = Arb.bind(text, place.orNull(0.3), place) { n, f, t -> SavedTrip(n, f, t) }
    private val favorite: Arb<FavoriteLine> = Arb.bind(text, text, text, text) { a, b, c, d -> FavoriteLine(a, b, c, d) }

    private val settings: Arb<UserSettings> = Arb.bind(
        Arb.int(0..5).orNull(0.4), Arb.int(1..60), Arb.enum<ModeFilter>(), Arb.enum<WalkSpeed>(), Arb.double(1.0, 1.8),
        Arb.enum<FareProfile>(), Arb.boolean(), Arb.boolean(), Arb.enum<TripSort>(), Arb.int(0..90),
    ) { mt, mw, mf, ws, pf, fp, acc, speak, sort, buf ->
        UserSettings(
            maxTransfers = mt, maxWalkMin = mw, modeFilter = mf, walkSpeed = ws, peakFactor = pf, fareProfile = fp,
            accessible = acc, speakAlerts = speak, tripSort = sort, calendarBufferMin = buf,
        )
    }.let { a ->
        Arb.bind(a, text.orNull(0.5), Arb.boolean(), Arb.boolean(), Arb.boolean(), Arb.element("SYSTEM", "LIGHT", "DARK"), Arb.int(1..10)) { s, home, ho, nr, lt, th, tb ->
            s.copy(homePlace = home, homeOffered = ho, nightRefresh = nr, lastTripAlert = lt, theme = th, transferBufferMin = tb, language = if (ho) "he" else null)
        }
    }

    private val record: Arb<TripRecord> = Arb.bind(Arb.long(0L, 4_000_000_000L), text, text, Arb.element("TRIP", "DROP_OFF", "PARK_RIDE"), Arb.int(0..600), Arb.int(0..90), Arb.int(0..5)) { e, f, t, m, tm, w, tr ->
        TripRecord(startedAtEpoch = e, from = f, to = t, mode = m, transitMin = tm, walkMin = w, transfers = tr)
    }.let { a -> Arb.bind(a, Arb.int(0..90).orNull(0.5), Arb.int(0..9000).orNull(0.5), text.orNull(0.5)) { r, s, fare, lines -> r.copy(savedMin = s, fareAgorot = fare, lines = lines) } }

    @Test fun `settings places trips routines and my lines survive an encode and decode`() = runBlocking<Unit> {
        checkAll(cfg(1000), settings) { assertEquals(it, UserJson.decodeSettings(UserJson.encodeSettings(it))) }
        checkAll(cfg(500), Arb.list(place, 0..6)) { assertEquals(it, UserJson.decodePlaces(UserJson.encodePlaces(it))) }
        checkAll(cfg(500), Arb.list(trip, 0..5)) { assertEquals(it, UserJson.decodeTrips(UserJson.encodeTrips(it))) }
        checkAll(cfg(500), Arb.list(favorite, 0..6)) { assertEquals(it, UserJson.decodeFavorites(UserJson.encodeFavorites(it))) }
        checkAll(cfg(500), Arb.list(record, 0..6)) { assertEquals(it, History.decode(History.encode(it))) }
        checkAll(cfg(500), routine) { r ->
            val p = SavedPlace("x", 1.0, 2.0, r)
            assertEquals(r, UserJson.decodePlaces(UserJson.encodePlaces(listOf(p))).single().routine)
        }
    }

    @Test fun `unknown fields are ignored at every level`() = runBlocking<Unit> {
        checkAll(cfg(500), settings, Arb.list(place, 1..3)) { s, places ->
            val sj = Json.parseToJsonElement(UserJson.encodeSettings(s)).jsonObject
            val withExtra = JsonObject(sj + mapOf("fieldFromTheFuture" to JsonPrimitive(42), "nested" to JsonObject(mapOf("a" to JsonPrimitive("b")))))
            assertEquals(s, UserJson.decodeSettings(withExtra.toString()))

            val arr = Json.parseToJsonElement(UserJson.encodePlaces(places)) as kotlinx.serialization.json.JsonArray
            val extended = kotlinx.serialization.json.JsonArray(arr.map { JsonObject(it.jsonObject + ("colour" to JsonPrimitive("red"))) })
            assertEquals(places, UserJson.decodePlaces(extended.toString()))
        }
    }

    @Test fun `a missing field gets its default and leaves every other field alone`() = runBlocking<Unit> {
        val defaults = Json.parseToJsonElement(UserJson.encodeSettings(UserSettings())).jsonObject
        checkAll(cfg(500), settings, Arb.int(0..100)) { s, pick ->
            val full = Json.parseToJsonElement(UserJson.encodeSettings(s)).jsonObject
            val key = full.keys.toList()[pick % full.size]
            val decoded = Json.parseToJsonElement(UserJson.encodeSettings(UserJson.decodeSettings(JsonObject(full - key).toString()))).jsonObject
            for (k in full.keys) assertEquals("field $k after dropping $key", if (k == key) defaults[k] else full[k], decoded[k])
        }
        assertEquals(UserSettings(), UserJson.decodeSettings("{}"))
        assertEquals(emptyList<SavedPlace>(), UserJson.decodePlaces("""[]"""))
    }
}
