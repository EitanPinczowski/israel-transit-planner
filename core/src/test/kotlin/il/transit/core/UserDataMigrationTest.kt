package il.transit.core

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 11 T1: what RELEASED versions wrote to the phone (`fixtures/userdata/<version>.json`, made by
 * `tools/gen_userdata_sample.sh` from each release tag's own codec, never by hand) must still decode with
 * today's code to the values the user had. Every release adds its sample (skill `release-apk`); a sample
 * for a version without an entry below is expected to read like the newest one.
 */
class UserDataMigrationTest {
    private val dir = File(javaClass.getResource("/fixtures/userdata/0.9.0.json")!!.toURI()).parentFile
    private fun samples(): List<File> = dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }

    private fun parts(f: File): Map<String, String> =
        Json.parseToJsonElement(f.readText()).jsonObject.mapValues { (_, v) -> v.toString() }

    private val home = SavedPlace("Home", 31.2437, 34.7976)
    private val work = SavedPlace("עבודה", 31.2622, 34.8013, PlaceRoutine(listOf(7, 1, 2, 3, 4), 420, 600))
    private val uni = SavedPlace("BGU \"main gate\"", 31.2621, 34.8009, PlaceRoutine(listOf(7, 2), 1320, 120))

    private fun version(f: File) = f.name.removeSuffix(".json")

    /** Versions compare as numbers, not text: 0.10.0 is after 0.9.0. */
    private fun atLeast(v: String, other: String): Boolean {
        val a = v.split('.').map { it.toInt() }
        val b = other.split('.').map { it.toInt() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return true
    }

    private fun expectedSettings(v: String) = UserSettings(
        maxTransfers = 2, maxWalkMin = 20, modeFilter = ModeFilter.NO_BUSES, walkSpeed = WalkSpeed.SLOW, peakFactor = 1.5,
        fareProfile = FareProfile.HALF, accessible = true, speakAlerts = false, tripSort = TripSort.FEWEST_TRANSFERS,
        calendarBufferMin = 20, homePlace = "Home", homeOffered = true, nightRefresh = false, lastTripAlert = true,
        language = "he", theme = "DARK", tourSeen = true,
        // v0.8.0 had no such field, v0.8.6 saved the old "Off" as null: both load as the 1-minute minimum.
        transferBufferMin = if (atLeast(v, "0.9.0")) 5 else UserSettings.MIN_TRANSFER_BUFFER_MIN,
        // v0.9.0 added the rain note; before it the setting is missing, so it takes its default (on).
        rainAware = if (atLeast(v, "0.9.0")) false else true,
    )

    private fun expectedHistory(v: String) = listOf(
        TripRecord(
            1_791_000_000L, "Home", "עבודה", "TRIP", 42, 9, 1, savedMin = null, totalMin = 55, fareAgorot = 1450, fareBand = 1,
            withTrain = false, fromCell = "31.244,34.798", toCell = "31.262,34.801", lines = if (atLeast(v, "0.9.0")) "470 · 5" else null,
        ),
        TripRecord(
            1_791_100_000L, "מיתר", "Tel Aviv", "PARK_RIDE", 70, 4, 0, savedMin = 18, totalMin = 95, fareAgorot = 2100, fareBand = 1,
            withTrain = true,
        ),
        TripRecord(1_791_200_000L, "A", "B", "DROP_OFF", 10, 0, 0),
    )

    @Test fun `there is a sample for every released version we promise to migrate`() {
        val names = samples().map(::version)
        for (v in listOf("0.8.0", "0.8.6", "0.9.0")) assertTrue("missing fixtures/userdata/$v.json in $names", v in names)
    }

    @Test fun `every released version's saved data decodes to what the user had`() {
        for (f in samples()) {
            val v = version(f)
            val p = parts(f)
            val where = "userdata/$v"
            assertEquals("$where settings", expectedSettings(v), UserJson.decodeSettings(p.getValue("settings")))
            assertEquals("$where places", listOf(home, work, uni), UserJson.decodePlaces(p.getValue("places")))
            assertEquals("$where trips", listOf(SavedTrip("To work", home, work), SavedTrip("Back", null, home)), UserJson.decodeTrips(p.getValue("trips")))
            assertEquals(
                "$where my lines",
                listOf(
                    FavoriteLine("il-Israel-MOT_37314", "Rager/Oren", "470", "Tel Aviv"),
                    FavoriteLine("il-Israel-MOT_26760", "עומר/מרכז", "369", "באר שבע"),
                ),
                UserJson.decodeFavorites(p.getValue("favorites")),
            )
            assertEquals("$where history", expectedHistory(v), History.decode(p.getValue("history")))
        }
    }

    @Test fun `what is saved today reads back as itself, so the next release can migrate it`() {
        val s = expectedSettings("99.0.0")
        assertEquals(s, UserJson.decodeSettings(UserJson.encodeSettings(s)))
        assertEquals(expectedHistory("99.0.0"), History.decode(History.encode(expectedHistory("99.0.0"))))
    }

    @Test fun `Home and Work stay what the settings point at`() {
        for (f in samples()) {
            val p = parts(f)
            val settings = UserJson.decodeSettings(p.getValue("settings"))
            val places = UserJson.decodePlaces(p.getValue("places"))
            assertTrue("${version(f)}: Home is a saved place", places.any { it.name == settings.homePlace })
        }
    }
}
