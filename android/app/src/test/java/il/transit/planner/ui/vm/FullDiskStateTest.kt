package il.transit.planner.ui.vm

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import il.transit.core.plan.TripSort
import il.transit.core.user.UserSettings
import il.transit.planner.data.UserStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * A full disk (nightly chaos `low_storage`, Phase 11 F2): DataStore's write fails with ENOSPC. That used
 * to escape `viewModelScope.launch` and end the app. Now the value is not saved and the app goes on.
 */
class FullDiskStateTest : VmTest() {
    /** Reads work; every write fails as `write failed: ENOSPC` does on a phone. */
    private class FullDisk : DataStore<Preferences> {
        var writes = 0
        override val data: Flow<Preferences> = MutableStateFlow(emptyPreferences())

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            writes++
            throw IOException("write failed: ENOSPC (No space left on device)")
        }
    }

    @Test fun saving_a_place_on_a_full_disk_does_not_end_the_app() {
        val disk = FullDisk()
        val vm = newVm(replay(), store = UserStore(disk))
        val job = vm.savePlace("Chaos", TEL_AVIV)
        advance(0)
        assertTrue("the save finished", job.isCompleted)
        assertFalse("the save threw out of viewModelScope", job.isCancelled)
        assertEquals(1, disk.writes)
        assertTrue("nothing was saved", runBlocking { UserStore(disk).places.first() }.isEmpty())
    }

    /** Full until [full] is set false; then writes land and [data] emits them, as DataStore does. */
    private class FillableDisk : DataStore<Preferences> {
        var full = true
        override val data = MutableStateFlow(emptyPreferences())

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (full) throw IOException("write failed: ENOSPC (No space left on device)")
            return transform(data.value).also { data.value = it }
        }
    }

    @Test fun a_refused_save_leaves_the_state_and_says_not_saved_then_a_later_save_applies() {
        val disk = FillableDisk()
        val vm = newVm(replay(), store = UserStore(disk))
        advance(0)
        vm.savePlace("Chaos", TEL_AVIV)
        advance(0)
        val shown = vm.state.value
        assertTrue("no place shows as saved", shown.savedPlaces.isEmpty())
        assertTrue("the user is told", vm.state.value.notSaved)
        vm.notSavedShown()
        assertFalse(vm.state.value.notSaved)

        disk.full = false
        vm.savePlace("Chaos", TEL_AVIV)
        advance(0)
        val after = vm.state.value
        assertEquals(listOf("Chaos"), after.savedPlaces.map { it.name })
        assertFalse("a save that worked says nothing", vm.state.value.notSaved)
    }

    @Test fun a_refused_sort_goes_back_to_the_saved_order() {
        val disk = FillableDisk()
        val vm = newVm(replay(), store = UserStore(disk))
        advance(0)
        assertEquals(TripSort.FASTEST, vm.state.value.settings.tripSort)
        vm.setTripSort(TripSort.LEAST_WALKING)
        advance(0)
        assertEquals("the old order is back", TripSort.FASTEST, vm.state.value.settings.tripSort)
        assertTrue(vm.state.value.notSaved)
        vm.notSavedShown()

        disk.full = false
        vm.setTripSort(TripSort.LEAST_WALKING)
        advance(0)
        assertEquals(TripSort.LEAST_WALKING, vm.state.value.settings.tripSort)
        assertFalse(vm.state.value.notSaved)
    }

    @Test fun every_store_write_survives_a_full_disk() = runBlocking {
        val disk = FullDisk()
        val store = UserStore(disk)
        val results = listOf(
            store.setSettings(UserSettings()),
            store.setPlaces(emptyList()),
            store.setRecents(emptyList()),
            store.setLastUpdateCheck(1L),
            store.setReminder(null),
            store.setTrips(emptyList()),
            store.setFavorites(emptyList()),
        )
        assertEquals(7, disk.writes)
        assertTrue("every refused write says so", results.none { it })
    }
}
