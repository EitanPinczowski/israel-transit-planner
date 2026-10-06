package il.transit.planner.data

import android.content.Context
import androidx.datastore.core.DataStore
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
    val favorites: Flow<List<il.transit.core.user.FavoriteLine>> = data.map { UserJson.decodeFavorites(it[FAVORITES]) }

    /** The one active "time to leave" reminder, if any. */
    val reminder: Flow<Reminder?> = data.map { UserJson.decodeReminder(it[REMINDER]) }

    suspend fun setSettings(s: UserSettings) {
        prefs.edit { it[SETTINGS] = UserJson.encodeSettings(s) }
    }

    suspend fun setPlaces(p: List<SavedPlace>) {
        prefs.edit { it[PLACES] = UserJson.encodePlaces(p) }
    }

    /** When the app last asked GitHub for a newer release (epoch seconds). */
    val lastUpdateCheck: Flow<Long?> = data.map { it[LAST_UPDATE_CHECK] }

    suspend fun setLastUpdateCheck(epochSec: Long) {
        prefs.edit { it[LAST_UPDATE_CHECK] = epochSec }
    }

    suspend fun setReminder(r: Reminder?) {
        prefs.edit { if (r == null) it.remove(REMINDER) else it[REMINDER] = UserJson.encodeReminder(r) }
    }

    suspend fun setTrips(t: List<SavedTrip>) {
        prefs.edit { it[TRIPS] = UserJson.encodeTrips(t) }
    }

    suspend fun setFavorites(f: List<il.transit.core.user.FavoriteLine>) {
        prefs.edit { it[FAVORITES] = UserJson.encodeFavorites(f) }
    }

    private companion object {
        val SETTINGS = stringPreferencesKey("settings")
        val PLACES = stringPreferencesKey("places")
        val TRIPS = stringPreferencesKey("trips")
        val FAVORITES = stringPreferencesKey("favorite_lines")
        val REMINDER = stringPreferencesKey("reminder")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
    }
}
