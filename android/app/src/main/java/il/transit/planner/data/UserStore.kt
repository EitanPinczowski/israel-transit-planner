package il.transit.planner.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
class UserStore(private val context: Context) {
    private val data: Flow<Preferences> = context.userData.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<UserSettings> = data.map { UserJson.decodeSettings(it[SETTINGS]) }
    val places: Flow<List<SavedPlace>> = data.map { UserJson.decodePlaces(it[PLACES]) }
    val trips: Flow<List<SavedTrip>> = data.map { UserJson.decodeTrips(it[TRIPS]) }

    /** The one active "time to leave" reminder, if any. */
    val reminder: Flow<Reminder?> = data.map { UserJson.decodeReminder(it[REMINDER]) }

    suspend fun setSettings(s: UserSettings) {
        context.userData.edit { it[SETTINGS] = UserJson.encodeSettings(s) }
    }

    suspend fun setPlaces(p: List<SavedPlace>) {
        context.userData.edit { it[PLACES] = UserJson.encodePlaces(p) }
    }

    suspend fun setReminder(r: Reminder?) {
        context.userData.edit { if (r == null) it.remove(REMINDER) else it[REMINDER] = UserJson.encodeReminder(r) }
    }

    suspend fun setTrips(t: List<SavedTrip>) {
        context.userData.edit { it[TRIPS] = UserJson.encodeTrips(t) }
    }

    private companion object {
        val SETTINGS = stringPreferencesKey("settings")
        val PLACES = stringPreferencesKey("places")
        val TRIPS = stringPreferencesKey("trips")
        val REMINDER = stringPreferencesKey("reminder")
    }
}
