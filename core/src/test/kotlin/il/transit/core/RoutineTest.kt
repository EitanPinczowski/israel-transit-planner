package il.transit.core

import il.transit.core.features.ISRAEL
import il.transit.core.user.PlaceRoutine
import il.transit.core.user.Routines
import il.transit.core.user.SavedPlace
import il.transit.core.user.UserJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class RoutineTest {
    private val sunToThu = listOf(7, 1, 2, 3, 4)
    private val uni = SavedPlace("University", 31.262, 34.8015, PlaceRoutine(sunToThu, 7 * 60, 10 * 60))
    private val home = SavedPlace("Home", 31.279, 34.82, PlaceRoutine(sunToThu, 16 * 60, 21 * 60))
    private val mom = SavedPlace("Mom", 31.9, 34.8, PlaceRoutine(listOf(5), 12 * 60, 15 * 60)) // Friday
    private val late = SavedPlace("Dorm", 31.26, 34.80, PlaceRoutine(listOf(4), 22 * 60, 2 * 60)) // Thu night
    private val places = listOf(uni, home, mom, late)

    /** A local Israel time, e.g. "2026-10-04T08:15". */
    private fun il(local: String): Instant = LocalDateTime.parse(local).atZone(ISRAEL).toInstant()

    @Test fun `weekday windows`() {
        assertEquals(uni, Routines.active(places, il("2026-10-04T08:15"))) // Sunday 08:15
        assertEquals(home, Routines.active(places, il("2026-10-05T17:00"))) // Monday 17:00
        assertNull(Routines.active(places, il("2026-10-05T12:00"))) // Monday noon: nothing
        assertNull(Routines.active(places, il("2026-10-05T10:00"))) // the end is exclusive
        assertNull(Routines.active(places, il("2026-10-10T08:15"))) // Saturday
        assertEquals(mom, Routines.active(places, il("2026-10-09T13:00"))) // Friday
    }

    @Test fun `overnight window belongs to the day it starts`() {
        assertEquals(late, Routines.active(places, il("2026-10-08T23:30"))) // Thursday 23:30
        assertEquals(late, Routines.active(places, il("2026-10-09T01:30"))) // Friday 01:30, still Thursday night
        assertNull(Routines.active(places, il("2026-10-09T23:30"))) // Friday 23:30
        assertEquals(240, late.routine!!.lengthMin)
    }

    @Test fun `overlapping routines - the narrowest wins`() {
        val wide = SavedPlace("Campus", 31.26, 34.80, PlaceRoutine(sunToThu, 6 * 60, 18 * 60))
        assertEquals(uni, Routines.active(listOf(wide, uni), il("2026-10-04T08:15")))
        assertEquals(wide, Routines.active(listOf(wide, uni), il("2026-10-04T11:00")))
    }

    @Test fun `app-icon shortcuts - the routine on now first, then routines, then the rest`() {
        val gym = SavedPlace("Gym", 31.25, 34.79)
        val order = Routines.shortcutOrder(listOf(gym, mom, home, uni), il("2026-10-05T17:00"))
        assertEquals(listOf(home, mom, uni), order) // Monday 17:00: Home's window; max 3
        assertEquals(listOf(gym), Routines.shortcutOrder(listOf(gym), il("2026-10-05T17:00")))
    }

    @Test fun `saved places from before routines still load, and round-trip`() {
        val old = """[{"name":"Home","lat":31.279,"lon":34.82}]"""
        assertEquals(listOf(SavedPlace("Home", 31.279, 34.82)), UserJson.decodePlaces(old))
        assertEquals(places, UserJson.decodePlaces(UserJson.encodePlaces(places)))
    }
}
