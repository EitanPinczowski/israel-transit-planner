package il.transit.core.user

import il.transit.core.api.Preferences
import il.transit.core.fare.FareProfile
import il.transit.core.features.TrafficProfile
import il.transit.core.geo.LatLon
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A named point the user saved: home, work, university... */
@Serializable
data class SavedPlace(
    val name: String,
    val lat: Double,
    val lon: Double,
    /** When the user usually heads here: the app then opens on "to <name>". */
    val routine: PlaceRoutine? = null,
) {
    val latLon: LatLon get() = LatLon(lat, lon)
}

/**
 * "University: Sun–Thu 07:00–10:00". [days] are ISO day numbers (1 = Monday … 7 = Sunday);
 * minutes are Israel local time. [toMin] < [fromMin] is an overnight window (22:00–02:00),
 * which belongs to the day it starts on.
 */
@Serializable
data class PlaceRoutine(val days: List<Int>, val fromMin: Int, val toMin: Int) {
    fun contains(t: java.time.ZonedDateTime): Boolean {
        val m = t.hour * 60 + t.minute
        return if (fromMin <= toMin) {
            t.dayOfWeek.value in days && m >= fromMin && m < toMin
        } else {
            (t.dayOfWeek.value in days && m >= fromMin) || (t.minusDays(1).dayOfWeek.value in days && m < toMin)
        }
    }

    val lengthMin: Int get() = if (fromMin <= toMin) toMin - fromMin else 24 * 60 - fromMin + toMin

    companion object {
        /** The Israeli week, for the day chips: Sunday first. */
        val WEEK: List<Int> = listOf(7, 1, 2, 3, 4, 5, 6)
    }
}

object Routines {
    /**
     * Saved places for the app-icon shortcuts: the routine on right now first, then other
     * places with a routine, then the rest; at most [max].
     */
    fun shortcutOrder(places: List<SavedPlace>, now: java.time.Instant, max: Int = 3): List<SavedPlace> {
        val active = active(places, now)
        return places.sortedWith(compareBy<SavedPlace>({ it != active }, { it.routine == null })).take(max)
    }

    /** The saved place whose routine covers [now] (Israel time); the narrowest window wins. */
    fun active(places: List<SavedPlace>, now: java.time.Instant): SavedPlace? {
        val t = now.atZone(il.transit.core.features.ISRAEL)
        return places.filter { it.routine?.contains(t) == true }.minByOrNull { it.routine!!.lengthMin }
    }
}

/**
 * A pinned line at a stop ("3 to Ramot from Rager/Oren"), to see its next departures in one
 * tap. Matched against departure rows by line and headsign, so the other direction of the
 * same line at the same stop is not mixed in.
 */
@Serializable
data class FavoriteLine(val stopId: String, val stopName: String, val line: String, val headsign: String) {
    fun matches(row: il.transit.core.present.DepartureRow): Boolean = row.line == line && row.headsign == headsign
}

/** A one-tap trip. [from] null means "from wherever I am when I tap it". */
@Serializable
data class SavedTrip(val name: String, val from: SavedPlace?, val to: SavedPlace)

@Serializable
enum class ModeFilter(val transitModes: Set<String>?) {
    ALL(null),
    TRAINS_ONLY(setOf("RAIL")),
    NO_BUSES(setOf("RAIL", "TRAM", "SUBWAY", "FUNICULAR", "AERIAL_LIFT", "FERRY")),
}

/** null = MOTIS's own default walking speed. */
@Serializable
enum class WalkSpeed(val metersPerSecond: Double?) { SLOW(1.0), NORMAL(null), FAST(1.6) }

/** Everything the user can set on the settings screen. Stored as JSON in DataStore. */
@Serializable
data class UserSettings(
    /** null = no limit. */
    val maxTransfers: Int? = null,
    val maxWalkMin: Int = 15,
    val modeFilter: ModeFilter = ModeFilter.ALL,
    val walkSpeed: WalkSpeed = WalkSpeed.NORMAL,
    /** Rush-hour multiplier for drive times; see [TrafficProfile]. */
    val peakFactor: Double = 1.3,
    /** Which fare the estimate shows (regular, 50% discount, free). */
    val fareProfile: FareProfile = FareProfile.REGULAR,
    /** Wheelchair / stroller: step-free walking parts. */
    val accessible: Boolean = false,
    /** Say "next stop: X, get off" out loud during a tracked ride, besides buzzing. */
    val speakAlerts: Boolean = true,
    /** How the Trip tab orders its options. */
    val tripSort: il.transit.core.plan.TripSort = il.transit.core.plan.TripSort.FASTEST,
    /** "From my calendar": arrive this many minutes before the event starts. */
    val calendarBufferMin: Int = il.transit.core.plan.CalendarSuggest.DEFAULT_BUFFER_MIN,
    /** The saved place that is Home (its name); see [Home]. */
    val homePlace: String? = null,
    /** A place called "Home" / "בית" was offered as Home and the user answered: don't ask again. */
    val homeOffered: Boolean = false,
    /** Plan tomorrow's usual trips each night on Wi-Fi + charging (owner, 2026-10-05: on). */
    val nightRefresh: Boolean = true,
    /** "Last trip home" evening alert (Phase 9 C5; owner, 2026-10-05: opt-in, off). */
    val lastTripAlert: Boolean = false,
    /** App language, a BCP 47 tag ("he", "en"); null = the phone's (Phase 9 C6). See [Appearance]. */
    val language: String? = null,
    /** "SYSTEM", "LIGHT" or "DARK"; see [Appearance.dark]. */
    val theme: String = Appearance.SYSTEM,
    /** The welcome tour was finished or skipped; Settings sets it back to false to show it again. */
    val tourSeen: Boolean = false,
) {
    fun preferences() = Preferences(
        transitModes = modeFilter.transitModes,
        maxTransfers = maxTransfers,
        pedestrianSpeedMps = walkSpeed.metersPerSecond,
        maxWalkSec = maxWalkMin * 60,
        wheelchair = accessible,
    )

    fun traffic() = TrafficProfile(peakFactor = peakFactor)

    companion object {
        val WALK_CHOICES = listOf(5, 10, 15, 20, 30)
        val TRANSFER_CHOICES = listOf(null, 0, 1, 2)
        val PEAK_RANGE = 1.0..1.8
    }
}

/**
 * JSON codec for what the app persists. Unknown keys are ignored and missing keys take
 * their defaults, so adding a field never wipes a user's saved data.
 */
object UserJson {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    fun encodeSettings(s: UserSettings): String = json.encodeToString(UserSettings.serializer(), s)
    fun decodeSettings(s: String?): UserSettings =
        s?.let { runCatching { json.decodeFromString(UserSettings.serializer(), it) }.getOrNull() } ?: UserSettings()

    fun encodePlaces(p: List<SavedPlace>): String = json.encodeToString(ListSerializer(SavedPlace.serializer()), p)
    fun decodePlaces(s: String?): List<SavedPlace> =
        s?.let { runCatching { json.decodeFromString(ListSerializer(SavedPlace.serializer()), it) }.getOrNull() }.orEmpty()

    fun encodeReminder(r: il.transit.core.remind.Reminder?): String =
        r?.let { json.encodeToString(il.transit.core.remind.Reminder.serializer(), it) } ?: ""
    fun decodeReminder(s: String?): il.transit.core.remind.Reminder? =
        s?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(il.transit.core.remind.Reminder.serializer(), it) }.getOrNull() }

    fun encodeFavorites(f: List<FavoriteLine>): String = json.encodeToString(ListSerializer(FavoriteLine.serializer()), f)
    fun decodeFavorites(s: String?): List<FavoriteLine> =
        s?.let { runCatching { json.decodeFromString(ListSerializer(FavoriteLine.serializer()), it) }.getOrNull() }.orEmpty()

    fun encodeTrips(t: List<SavedTrip>): String = json.encodeToString(ListSerializer(SavedTrip.serializer()), t)
    fun decodeTrips(s: String?): List<SavedTrip> =
        s?.let { runCatching { json.decodeFromString(ListSerializer(SavedTrip.serializer()), it) }.getOrNull() }.orEmpty()
}
