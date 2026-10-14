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
import org.junit.Test
import java.io.File

// Phase 11 T1. NOT a test of this repo: tools/gen_userdata_sample.sh copies this file into a git worktree of a
// release tag and runs it there, so the JSON below is what THAT release's codec wrote. Lines ending in
// `//@since X.Y.Z` / `//@until X.Y.Z` exist only in releases from / up to that version (the fields did not exist before).
// The values must stay the same across versions: UserDataMigrationTest expects them.
class UserDataSampleGen {
    @Test fun write() {
        val home = SavedPlace("Home", 31.2437, 34.7976)
        val work = SavedPlace("עבודה", 31.2622, 34.8013, PlaceRoutine(listOf(7, 1, 2, 3, 4), 420, 600))
        val uni = SavedPlace("BGU \"main gate\"", 31.2621, 34.8009, PlaceRoutine(listOf(7, 2), 1320, 120))
        val places = listOf(home, work, uni)
        val trips = listOf(SavedTrip("To work", home, work), SavedTrip("Back", null, home))
        val favorites = listOf(
            FavoriteLine("il-Israel-MOT_37314", "Rager/Oren", "470", "Tel Aviv"),
            FavoriteLine("il-Israel-MOT_26760", "עומר/מרכז", "369", "באר שבע"),
        )
        val settings = UserSettings(
            maxTransfers = 2,
            maxWalkMin = 20,
            modeFilter = ModeFilter.NO_BUSES,
            walkSpeed = WalkSpeed.SLOW,
            peakFactor = 1.5,
            fareProfile = FareProfile.HALF,
            accessible = true,
            speakAlerts = false,
            tripSort = TripSort.FEWEST_TRANSFERS,
            calendarBufferMin = 20,
            homePlace = "Home",
            homeOffered = true,
            nightRefresh = false,
            lastTripAlert = true,
            language = "he",
            theme = "DARK",
            tourSeen = true,
            transferBufferMin = null, //@since 0.8.6 //@until 0.8.6
            transferBufferMin = 5, //@since 0.9.0
            rainAware = false, //@since 0.9.0
        )
        val history = listOf(
            TripRecord(
                startedAtEpoch = 1_791_000_000L, from = "Home", to = "עבודה", mode = "TRIP", transitMin = 42, walkMin = 9, transfers = 1,
                savedMin = null, totalMin = 55, fareAgorot = 1450, fareBand = 1, withTrain = false,
                fromCell = "31.244,34.798", toCell = "31.262,34.801",
                lines = "470 · 5", //@since 0.9.0
            ),
            TripRecord(
                startedAtEpoch = 1_791_100_000L, from = "מיתר", to = "Tel Aviv", mode = "PARK_RIDE", transitMin = 70, walkMin = 4, transfers = 0,
                savedMin = 18, totalMin = 95, fareAgorot = 2100, fareBand = 1, withTrain = true,
            ),
            TripRecord(startedAtEpoch = 1_791_200_000L, from = "A", to = "B", mode = "DROP_OFF", transitMin = 10, walkMin = 0, transfers = 0),
        )
        val out = """{"version":"${System.getenv("USERDATA_VERSION")}","settings":${UserJson.encodeSettings(settings)},"places":${UserJson.encodePlaces(places)},""" +
            """"trips":${UserJson.encodeTrips(trips)},"favorites":${UserJson.encodeFavorites(favorites)},"history":${History.encode(history)}}"""
        File(System.getenv("USERDATA_OUT")!!).writeText(out)
    }
}
