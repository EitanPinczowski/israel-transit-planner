package il.transit.planner.ui

import il.transit.core.features.BetterStartResult
import il.transit.core.features.DropOffResult
import il.transit.core.features.ParkRideResult
import il.transit.core.features.PickUpResult
import il.transit.core.geo.LatLon
import il.transit.core.plan.ChainPlanner
import il.transit.core.plan.ChainResult
import il.transit.core.plan.ChainStop
import il.transit.core.plan.TimeMode
import il.transit.core.plan.TripResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Random

/**
 * Phase 11 T1: the saved-state round trip over many generated states, not just the hand-picked ones of
 * [SavedUiTest]. No property library here (the app's build file belongs to other packages): a seeded
 * [Random] and a fixed number of cases, so CI is deterministic. A failure names the case number and seed;
 * change [SEED] locally to explore.
 */
class SavedUiPropertyTest {
    companion object {
        const val SEED = 20261013L
        const val CASES = 1000
    }

    private val rnd = Random(SEED)

    private fun <T> pick(xs: List<T>): T = xs[rnd.nextInt(xs.size)]

    private val names = listOf("", "בית", "אוניברסיטת בן גוריון", "Home \"quoted\"", "‏רגר‎", "🚌 stop", "a|b", "d11.mode", "x".repeat(300))

    private fun point() = PlaceRef.Point(if (rnd.nextInt(4) == 0) null else pick(names), LatLon(rnd.nextDouble() * 180 - 90, rnd.nextDouble() * 360 - 180))
    private fun place(): PlaceRef? = when (rnd.nextInt(3)) { 0 -> null; 1 -> PlaceRef.MyLocation; else -> point() }

    private fun state(): SavedUi = SavedUi(
        mode = pick(AppMode.entries),
        from = place() ?: PlaceRef.MyLocation,
        to = place(),
        driverTo = place(),
        timeMode = pick(TimeMode.entries),
        time = if (rnd.nextBoolean()) null else Instant.ofEpochMilli(rnd.nextLong() % 4_000_000_000_000L),
        selected = rnd.nextInt(30),
        optionId = if (rnd.nextBoolean()) null else pick(names),
        cacheKey = if (rnd.nextBoolean()) null else pick(names),
        sheet = when (rnd.nextInt(3)) {
            0 -> null
            1 -> SavedUi.Sheet.Ride(rnd.nextInt(8))
            else -> SavedUi.Sheet.Stop("il-Israel-MOT_${rnd.nextInt(99999)}", pick(names))
        },
        stops = List(rnd.nextInt(ChainPlanner.MAX_STOPS + 1)) {
            ChainStop(point().at, if (rnd.nextInt(4) == 0) null else pick(names), rnd.nextInt(240))
        },
        maxDriveMin = rnd.nextInt(60),
        maxDetourMin = rnd.nextInt(60),
        maxPickUpDriveMin = rnd.nextInt(60),
        maxParkRideMin = rnd.nextInt(60),
    )

    private fun write(s: SavedUi, into: MutableMap<String, Any?>) = s.write { k, v -> into[k] = v }

    @Test fun `any state written comes back equal, and only in types a Bundle keeps`() {
        repeat(CASES) { i ->
            val s = state()
            val bundle = HashMap<String, Any?>()
            write(s, bundle)
            bundle.values.filterNotNull().forEach { v -> assertTrue("case $i seed $SEED: $v", v is String || v is Int || v is Long || v is Double) }
            assertEquals("case $i seed $SEED", s, SavedUi.read { bundle[it] })
        }
    }

    @Test fun `a later save replaces the earlier one completely, nothing of the old state leaks`() {
        repeat(CASES) { i ->
            val bundle = HashMap<String, Any?>()
            write(state(), bundle)
            val later = state()
            write(later, bundle) // the same handle, written again: cleared values must be cleared there too
            assertEquals("case $i seed $SEED", later, SavedUi.read { bundle[it] })
        }
    }

    @Test fun `nothing saved is no state, and a damaged bundle never throws`() {
        assertNull(SavedUi.read { null })
        repeat(CASES) { i ->
            val bundle = HashMap<String, Any?>()
            write(state(), bundle)
            // Damage: drop a key, or replace a value with another type.
            val keys = bundle.keys.toList()
            val k = keys[rnd.nextInt(keys.size)]
            if (rnd.nextBoolean()) bundle.remove(k) else bundle[k] = pick(listOf<Any?>("x", 7, 1.5, 3L, true, emptyList<Int>()))
            try {
                SavedUi.read { bundle[it] } // null or some state: either is fine, a crash is not
            } catch (t: Throwable) {
                throw AssertionError("case $i seed $SEED damaged '$k' -> ${bundle[k]}", t)
            }
        }
    }

    /** A state of [mode] showing an (empty) answer of its own: Trip options or an errand chain in Trip. */
    private fun showing(mode: AppMode, errands: Boolean): UiState = when (mode) {
        AppMode.TRIP -> if (errands) UiState(chain = ChainResult(emptyList(), null)) else UiState(results = TripResult(emptyList(), null))
        AppMode.BETTER_START -> UiState(mode = mode, betterStart = BetterStartResult(null, emptyList(), "CAR"))
        AppMode.DROP_OFF -> UiState(mode = mode, dropOff = DropOffResult(null, emptyList(), 0))
        AppMode.PICK_UP -> UiState(mode = mode, pickUp = PickUpResult(null, emptyList()))
        AppMode.PARK_RIDE -> UiState(mode = mode, parkRide = ParkRideUi(result = ParkRideResult(null, emptyList(), 0)))
    }

    @Test fun `a cache key from another tab is dropped even while this tab shows its answer`() {
        val prefixes = listOf("TRIP-", "ERRANDS-", "BETTER_START-", "DROP_OFF-", "PICK_UP-", "PARK_RIDE-")
        repeat(CASES) { i ->
            val s = showing(pick(AppMode.entries), rnd.nextBoolean())
            val own = SavedUi.keyPrefix(s)!!
            val rest = "${pick(TimeMode.entries)}-${rnd.nextInt(60)}:${rnd.nextDouble()},${rnd.nextDouble()}|${pick(names)}"
            val other = pick(prefixes - own) + rest
            assertNull("case $i seed $SEED: ${s.mode} kept '$other'", SavedUi.of(s, other, null, null).cacheKey)
            assertEquals("case $i seed $SEED", own + rest, SavedUi.of(s, own + rest, null, null).cacheKey)
        }
    }
}
