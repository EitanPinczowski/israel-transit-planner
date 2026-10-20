package il.transit.planner.data

import il.transit.core.features.PickUpResult
import il.transit.core.plan.TabResult
import il.transit.planner.ui.vm.VmTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Phase 11 R1: the car tabs' and errands' store on disk: its size, its own file, and whole writes. */
class TabCacheStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    /** When `plan_bgu_telaviv` was recorded: its options are all ahead. */
    private val clock = Clock.fixed(Instant.parse("2026-10-05T04:55:00Z"), ZoneOffset.UTC)
    private val answer = TabResult.PickUp(PickUpResult(VmTest.planFixture("plan_bgu_telaviv").itineraries.first(), emptyList()))

    @Test fun `seven answers kept, six come back from the file and the oldest is gone`() {
        val file = File(tmp.root, "tab_cache.json")
        runBlocking { (0 until 7).forEach { i -> PlanCacheStore.tabs(file, clock).put("PICK_UP-NOW-30:k$i", answer) } }
        val reloaded = PlanCacheStore.tabs(file, clock)
        val keys = runBlocking { reloaded.entries() }.map { it.key }
        assertEquals(PlanCacheStore.TABS_CAPACITY, keys.size)
        assertEquals((6 downTo 1).map { "PICK_UP-NOW-30:k$it" }, keys)
        assertNull("the oldest is gone", runBlocking { reloaded.get("PICK_UP-NOW-30:k0") })
        assertEquals(answer, runBlocking { reloaded.get("PICK_UP-NOW-30:k6") }?.value)
    }

    @Test fun `the tab store never touches the Trip cache's file`() {
        val trip = File(tmp.root, "trip_cache.json").apply { writeText("[\"the Trip cache\"]") }
        val stamp = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()
        trip.setLastModified(stamp)
        val store = PlanCacheStore.tabs(File(tmp.root, "tab_cache.json"), clock)
        runBlocking {
            store.put("PICK_UP-NOW-30:a", answer)
            assertNotNull(store.get("PICK_UP-NOW-30:a"))
            store.clear()
        }
        assertEquals("[\"the Trip cache\"]", trip.readText())
        assertEquals(stamp, trip.lastModified())
        assertEquals(setOf("trip_cache.json"), tmp.root.list()!!.toSet())
    }

    @Test fun `a save is whole - no temporary file left, and a stale one does not matter`() {
        val file = File(tmp.root, "tab_cache.json")
        File(tmp.root, "tab_cache.json.tmp").writeText("[{\"key\":\"cut") // a kill mid-write, last time
        runBlocking { PlanCacheStore.tabs(file, clock).put("PICK_UP-NOW-30:a", answer) }
        assertFalse(File(tmp.root, "tab_cache.json.tmp").exists())
        assertEquals(answer, runBlocking { PlanCacheStore.tabs(file, clock).get("PICK_UP-NOW-30:a") }?.value)
    }

    @Test fun `writing over an existing file replaces it whole`() {
        val file = File(tmp.root, "x.json").apply { writeText("a much longer old content than the new one") }
        PlanCacheStore.writeAtomically(file, "new")
        assertEquals("new", file.readText())
        assertTrue(tmp.root.list()!!.none { it.endsWith(".tmp") })
    }
}
