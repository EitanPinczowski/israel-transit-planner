package il.transit.planner.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import il.transit.core.remind.Reminder
import il.transit.core.user.SavedPlace
import il.transit.core.user.SavedTrip
import il.transit.core.user.UserJson
import il.transit.core.user.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.userData by preferencesDataStore(name = "user")

/**
 * Settings, saved places and saved trips, as JSON strings in one DataStore file. The lists
 * are small (tens of items), so JSON in DataStore is simpler than a database and the codec
 * lives in core (UserJson), where it is tested.
 */
class UserStore(private val prefs: DataStore<Preferences>) {
    constructor(context: Context) : this(context.userData)

    private val data: Flow<Preferences> = prefs.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<UserSettings> = data.map { UserJson.decodeSettings(it[SETTINGS]) }
    val places: Flow<List<SavedPlace>> = data.map { UserJson.decodePlaces(it[PLACES]) }
    val trips: Flow<List<SavedTrip>> = data.map { UserJson.decodeTrips(it[TRIPS]) }

    /** Places picked from search lately, newest first (core `Recents`, at most 10). */
    val recents: Flow<List<SavedPlace>> = data.map { UserJson.decodePlaces(it[RECENTS]) }
    val favorites: Flow<List<il.transit.core.user.FavoriteLine>> = data.map { UserJson.decodeFavorites(it[FAVORITES]) }

    /** The one active "time to leave" reminder, if any. */
    val reminder: Flow<Reminder?> = data.map { UserJson.decodeReminder(it[REMINDER]) }

    suspend fun setSettings(s: UserSettings): Boolean =
        write { it[SETTINGS] = UserJson.encodeSettings(s) }

    suspend fun setPlaces(p: List<SavedPlace>): Boolean =
        write { it[PLACES] = UserJson.encodePlaces(p) }

    suspend fun setRecents(p: List<SavedPlace>): Boolean =
        write { it[RECENTS] = UserJson.encodePlaces(p) }

    /** When the app last asked GitHub for a newer release (epoch seconds). */
    val lastUpdateCheck: Flow<Long?> = data.map { it[LAST_UPDATE_CHECK] }

    suspend fun setLastUpdateCheck(epochSec: Long): Boolean =
        write { it[LAST_UPDATE_CHECK] = epochSec }

    suspend fun setReminder(r: Reminder?): Boolean =
        write { if (r == null) it.remove(REMINDER) else it[REMINDER] = UserJson.encodeReminder(r) }

    suspend fun setTrips(t: List<SavedTrip>): Boolean =
        write { it[TRIPS] = UserJson.encodeTrips(t) }

    suspend fun setFavorites(f: List<il.transit.core.user.FavoriteLine>): Boolean =
        write { it[FAVORITES] = UserJson.encodeFavorites(f) }

    /**
     * A write that a full disk (ENOSPC) or another I/O error refuses: DataStore writes a temp file and
     * renames it, so the old file stays whole. Thrown, it would end the app from a `viewModelScope.launch`
     * (nightly `low_storage`, Phase 11 F2). The value is not saved and the caller gets false, so the
     * screen can say so ("Not saved: phone storage is full"). No `ReplaceFileCorruptionHandler`: a
     * `CorruptionException` (also an IOException) must not silently wipe the saved places.
     */
    private suspend fun write(change: suspend (MutablePreferences) -> Unit): Boolean =
        try {
            prefs.edit(change)
            true
        } catch (e: IOException) {
            runCatching { Log.w(TAG, "not saved (${e.javaClass.simpleName}): ${e.message}") }
            false
        }

    private companion object {
        const val TAG = "UserStore"

        val SETTINGS = stringPreferencesKey("settings")
        val PLACES = stringPreferencesKey("places")
        val TRIPS = stringPreferencesKey("trips")
        val RECENTS = stringPreferencesKey("recent_places")
        val FAVORITES = stringPreferencesKey("favorite_lines")
        val REMINDER = stringPreferencesKey("reminder")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
    }
}
