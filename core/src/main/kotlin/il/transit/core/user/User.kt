package il.transit.core.user

import il.transit.core.api.Preferences
import il.transit.core.features.TrafficProfile
import il.transit.core.geo.LatLon
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A named point the user saved: home, work, university... */
@Serializable
data class SavedPlace(val name: String, val lat: Double, val lon: Double) {
    val latLon: LatLon get() = LatLon(lat, lon)
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
) {
    fun preferences() = Preferences(
        transitModes = modeFilter.transitModes,
        maxTransfers = maxTransfers,
        pedestrianSpeedMps = walkSpeed.metersPerSecond,
        maxWalkSec = maxWalkMin * 60,
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

    fun encodeTrips(t: List<SavedTrip>): String = json.encodeToString(ListSerializer(SavedTrip.serializer()), t)
    fun decodeTrips(s: String?): List<SavedTrip> =
        s?.let { runCatching { json.decodeFromString(ListSerializer(SavedTrip.serializer()), it) }.getOrNull() }.orEmpty()
}
