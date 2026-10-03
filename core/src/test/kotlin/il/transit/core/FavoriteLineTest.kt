package il.transit.core

import il.transit.core.api.MotisJson
import il.transit.core.api.StopTimesResponse
import il.transit.core.present.departureRow
import il.transit.core.user.FavoriteLine
import il.transit.core.user.UserJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteLineTest {
    // Be'er Sheva North, Monday 2026-10-05 from 08:10 (recorded).
    private val rows = MotisJson.decodeFromString(
        StopTimesResponse.serializer(),
        javaClass.getResource("/fixtures/stoptimes_beersheva_north.json")!!.readText(),
    ).stopTimes.map(::departureRow)

    @Test fun `a pinned train direction picks only its own departures`() {
        val toHerzliya = rows.first { it.headsign == "הרצליה" }
        val fav = FavoriteLine("il-Israel-MOT_37314", "באר שבע צפון", toHerzliya.line, toHerzliya.headsign)
        assertEquals(listOf("08:19", "08:41"), rows.filter(fav::matches).map { it.time })
        // The same station's trains to Be'er Sheva Center are another direction: not mixed in.
        assertTrue(rows.filter { it.headsign == "באר שבע מרכז" }.none(fav::matches))
    }

    @Test fun `favourites round-trip and garbage loads as none`() {
        val favs = listOf(FavoriteLine("s1", "רגר/אורן", "3", "רמות"), FavoriteLine("s2", "באר שבע צפון", "", "הרצליה"))
        assertEquals(favs, UserJson.decodeFavorites(UserJson.encodeFavorites(favs)))
        assertTrue(UserJson.decodeFavorites("{").isEmpty())
        assertTrue(UserJson.decodeFavorites(null).isEmpty())
    }
}
