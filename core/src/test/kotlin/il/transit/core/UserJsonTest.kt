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

    @Test
    fun `the pass date and the reminder-check hint round-trip, and older files load them as Off and not shown`() {
        val s = UserSettings(passEndsOn = "2026-11-01", reminderCheckHintShown = true)
        val json = UserJson.encodeSettings(s)
        assert("\"passEndsOn\":\"2026-11-01\"" in json) { json }
        assert("\"reminderCheckHintShown\":true" in json) { json }
        assertEquals(s, UserJson.decodeSettings(json))
        val old = UserJson.decodeSettings("""{"maxWalkMin":20,"rainAware":false}""")
        assertEquals(null, old.passEndsOn)
        assertEquals(false, old.reminderCheckHintShown)
        assertEquals(20, old.maxWalkMin)
    }

    @Test
    fun `the payment app and low data round-trip, and older files load them as None and off`() {
        val s = UserSettings(paymentApp = "com.tranzmate", lowData = true)
        val json = UserJson.encodeSettings(s)
        assert("\"paymentApp\":\"com.tranzmate\"" in json) { json }
        assert("\"lowData\":true" in json) { json }
        assertEquals(s, UserJson.decodeSettings(json))
        val old = UserJson.decodeSettings("""{"maxWalkMin":20,"passEndsOn":"2026-11-01"}""")
        assertEquals(null, old.paymentApp)
        assertEquals(false, old.lowData)
        assertEquals("2026-11-01", old.passEndsOn)
    }
}
