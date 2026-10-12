package il.transit.core

import il.transit.core.user.UserJson
import il.transit.core.user.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/** Settings files written by older versions still load, with today's meaning. */
class UserJsonTest {
    @Test
    fun `an old buffer of Off (null) loads as 1 minute and the plan sends 1`() {
        val old = """{"maxWalkMin":10,"transferBufferMin":null,"tripSort":"FASTEST"}"""
        val s = UserJson.decodeSettings(old)
        assertEquals(1, s.transferBufferMin)
        assertEquals(10, s.maxWalkMin) // the rest of the file is kept
        assertEquals(60, s.preferences().additionalTransferSec)
    }

    @Test
    fun `the JSON field name is unchanged and a chosen buffer round-trips`() {
        val s = UserSettings(transferBufferMin = 8)
        val json = UserJson.encodeSettings(s)
        assert("\"transferBufferMin\":8" in json) { json }
        assertEquals(s, UserJson.decodeSettings(json))
        assertEquals(8, UserJson.decodeSettings("""{"transferBufferMin":8}""").transferBufferMin)
    }

    @Test
    fun `a buffer below 1 in a hand-edited file still sends 1`() {
        assertEquals(60, UserJson.decodeSettings("""{"transferBufferMin":0}""").preferences().additionalTransferSec)
    }
}
