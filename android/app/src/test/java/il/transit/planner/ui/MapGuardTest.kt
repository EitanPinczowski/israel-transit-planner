package il.transit.planner.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MapGuardTest {
    private val logged = mutableListOf<Pair<String, Throwable>>()
    private lateinit var previous: (String, Throwable) -> Unit

    @Before fun swapLog() {
        previous = MapGuard.log
        MapGuard.log = { what, e -> logged += what to e }
    }

    @After fun restoreLog() { MapGuard.log = previous }

    @Test fun passes_the_value_through_and_logs_nothing() {
        assertEquals(42, MapGuard.run("fit") { 42 })
        assertTrue(logged.isEmpty())
    }

    @Test fun an_exception_never_escapes_and_is_logged_with_what_failed() {
        val boom = IllegalStateException("Calling getSourceAs when a newer style is loading/has loaded.")
        val r: Unit? = MapGuard.run("setRoute") { throw boom }
        assertNull(r)
        assertEquals(1, logged.size)
        assertEquals("setRoute", logged[0].first)
        assertSame(boom, logged[0].second)
    }

    @Test(expected = OutOfMemoryError::class)
    fun errors_are_not_caught() {
        MapGuard.run("tiles") { throw OutOfMemoryError() }
    }
}
