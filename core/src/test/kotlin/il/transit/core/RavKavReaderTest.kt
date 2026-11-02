package il.transit.core

import il.transit.core.card.Apdu
import il.transit.core.card.CardFile
import il.transit.core.card.CardReport
import il.transit.core.card.FileOutcome
import il.transit.core.card.RavKavReader
import il.transit.core.card.StatusWord
import il.transit.core.card.SwMeaning
import il.transit.core.card.hexGroups
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Phase 11 E6: the Rav-Kav lab's commands and reading loop, on byte arrays built from
 * ISO/IEC 7816-4's command layouts (never from a real card).
 */
class RavKavReaderTest {
    private fun hex(s: String): ByteArray {
        val digits = s.replace(" ", "")
        return ByteArray(digits.length / 2) { digits.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
    }

    private val sw9000 = hex("9000")

    /**
     * A pretend card: [files] maps an SFI to its records; an SFI listed in [refuse] answers that
     * status word; any other SFI answers 6A82. [cla] is the only class byte it accepts.
     */
    private class FakeCard(
        val files: Map<Int, List<ByteArray>> = emptyMap(),
        val refuse: Map<Int, Int> = emptyMap(),
        val cla: Int = Apdu.CLA_ISO,
        val selectSw: Int = 0x9000,
        val wantLe: Int? = null,
    ) {
        val sent = ArrayList<ByteArray>()
        fun answer(c: ByteArray): ByteArray {
            sent += c
            fun sw(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())
            if ((c[0].toInt() and 0xFF) != cla) return sw(0x6E00)
            return when (c[1].toInt() and 0xFF) {
                0xA4 -> byteArrayOf(0x6F, 0x00) + sw(selectSw) // an FCI the reader must not keep
                0xB2 -> {
                    val record = c[2].toInt() and 0xFF
                    val sfi = (c[3].toInt() and 0xFF) shr 3
                    refuse[sfi]?.let { return sw(it) }
                    val recs = files[sfi] ?: return sw(0x6A82)
                    if (record > recs.size) return sw(0x6A83)
                    val le = c[4].toInt() and 0xFF
                    if (wantLe != null && le != wantLe) return sw(0x6C00 or wantLe)
                    recs[record - 1] + sw(0x9000)
                }
                else -> sw(0x6D00)
            }
        }
    }

    private fun CardReport.Answered.file(f: CardFile) = files.single { it.file == f }

    private fun clock(vararg ns: Long): () -> Long {
        val it = ns.iterator()
        return { it.next() }
    }

    @Test fun selectByNameMatchesIso7816Layout() {
        assertArrayEquals(hex("00 A4 04 00 08 31 54 49 43 2E 49 43 41 00"), Apdu.selectByName(Apdu.CALYPSO_AID))
        assertArrayEquals(hex("94 A4 04 00 08 31 54 49 43 2E 49 43 41 00"), Apdu.selectByName(Apdu.CALYPSO_AID, Apdu.CLA_CALYPSO))
    }

    @Test fun readRecordPutsSfiInP2() {
        // P2 = SFI << 3 | 100b: SFI 9 → 4C, SFI 1E → F4, SFI 1 → 0C.
        assertArrayEquals(hex("00 B2 01 4C 00"), Apdu.readRecord(1, 0x09))
        assertArrayEquals(hex("94 B2 03 F4 1D"), Apdu.readRecord(3, 0x1E, Apdu.CLA_CALYPSO, le = 0x1D))
        assertArrayEquals(hex("00 B2 FE 0C 00"), Apdu.readRecord(254, 1))
    }

    @Test fun builderRejectsOutOfRangeArguments() {
        val bad: List<() -> Unit> = listOf(
            { Apdu.readRecord(0, 9) },
            { Apdu.readRecord(255, 9) },
            { Apdu.readRecord(1, 0) },
            { Apdu.readRecord(1, 31) },
            { Apdu.readRecord(1, 9, le = 256) },
            { Apdu.readRecord(1, 9, le = -1) },
            { Apdu.selectByName(ByteArray(0)) },
            { Apdu.selectByName(ByteArray(17)) },
        )
        bad.forEach { f ->
            try {
                f()
                throw AssertionError("expected a rejection")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test fun statusWordsAndMeanings() {
        assertNull(StatusWord.of(hex("90")))
        assertEquals(StatusWord(0x9000), StatusWord.of(hex("01 02 90 00")))
        assertEquals("6A82", StatusWord(0x6A82).toString())
        assertEquals(0x6C, StatusWord(0x6C1D).sw1)
        assertEquals(0x1D, StatusWord(0x6C1D).sw2)
        assertTrue(StatusWord(0x6C1D).wrongLength)
        assertEquals(SwMeaning.OK, StatusWord(0x9000).meaning)
        assertEquals(SwMeaning.NOT_FOUND, StatusWord(0x6A82).meaning)
        assertEquals(SwMeaning.NO_RECORD, StatusWord(0x6A83).meaning)
        listOf(0x6982, 0x6985, 0x6986).forEach { assertEquals(SwMeaning.NEEDS_KEYS, StatusWord(it).meaning) }
        assertEquals(SwMeaning.WRONG_CLASS, StatusWord(0x6E00).meaning)
        assertEquals(SwMeaning.OTHER, StatusWord(0x6D00).meaning)
    }

    @Test fun readsEveryAllowedFileAndNeverTheHolder() {
        val contract = hex("01 02 03 04 05")
        val card = FakeCard(
            files = mapOf(0x1E to listOf(hex("AA")), 0x09 to listOf(contract, contract), 0x08 to listOf(hex("0B"), hex("0C"), hex("0D")), 0x07 to listOf(hex("FF"))),
            refuse = mapOf(0x19 to 0x6982),
        )
        val r = RavKavReader(card::answer, clock(0, 412_000_000)).read() as CardReport.Answered
        assertEquals(StatusWord(0x9000), r.selectStatus)
        assertEquals(Apdu.CLA_ISO, r.cla)
        assertEquals(412L, r.millis)
        val byFile = r.files.associateBy { it.file }
        assertEquals(CardFile.entries.toSet(), byFile.keys)
        assertEquals(2, byFile.getValue(CardFile.CONTRACTS).records.size)
        assertArrayEquals(contract, byFile.getValue(CardFile.CONTRACTS).records[0])
        assertEquals(FileOutcome.READ, byFile.getValue(CardFile.EVENT_LOG).outcome)
        assertEquals(StatusWord(0x6A83), byFile.getValue(CardFile.EVENT_LOG).status)
        assertEquals(FileOutcome.NEEDS_KEYS, byFile.getValue(CardFile.COUNTERS).outcome)
        assertEquals(FileOutcome.NOT_FOUND, byFile.getValue(CardFile.SPECIAL_EVENTS).outcome)
        // No READ RECORD ever names SFI 07 (environment + holder), and only plain commands are sent.
        val reads = card.sent.filter { (it[1].toInt() and 0xFF) == 0xB2 }
        val readSfis = reads.map { (it[3].toInt() and 0xFF) shr 3 }.toSet()
        assertFalse(CardFile.HOLDER_SFI in readSfis)
        assertEquals(CardFile.entries.map { it.sfi }.toSet(), readSfis)
        assertTrue(card.sent.all { (it[1].toInt() and 0xFF) in setOf(0xA4, 0xB2) })
    }

    @Test fun emptyFileAndOtherRefusals() {
        val card = FakeCard(files = mapOf(0x09 to emptyList(), 0x1E to listOf(hex("01"))), refuse = mapOf(0x08 to 0x6B00))
        val r = RavKavReader(card::answer).read() as CardReport.Answered
        val byFile = r.files.associateBy { it.file }
        assertEquals(FileOutcome.EMPTY, byFile.getValue(CardFile.CONTRACTS).outcome)
        assertEquals(FileOutcome.REFUSED, byFile.getValue(CardFile.EVENT_LOG).outcome)
    }

    @Test fun fallsBackToTheCalypsoClassByte() {
        val card = FakeCard(files = mapOf(0x09 to listOf(hex("42"))), cla = Apdu.CLA_CALYPSO)
        val r = RavKavReader(card::answer).read() as CardReport.Answered
        assertEquals(Apdu.CLA_CALYPSO, r.cla)
        assertEquals(1, r.file(CardFile.CONTRACTS).records.size)
    }

    @Test fun retriesOnceWithTheLengthTheCardAsksFor() {
        val card = FakeCard(files = mapOf(0x09 to listOf(hex("0102"))), wantLe = 0x1D)
        val r = RavKavReader(card::answer).read() as CardReport.Answered
        assertArrayEquals(hex("0102"), r.file(CardFile.CONTRACTS).records.single())
    }

    @Test fun stopsAtMaxRecords() {
        val card = FakeCard(files = mapOf(0x08 to List(40) { hex("01") }))
        val r = RavKavReader(card::answer).read() as CardReport.Answered
        assertEquals(RavKavReader.MAX_RECORDS, r.file(CardFile.EVENT_LOG).records.size)
    }

    @Test fun selectFailsButAFileAnswers() {
        val card = FakeCard(files = mapOf(0x09 to listOf(hex("01"))), selectSw = 0x6A82)
        val r = RavKavReader(card::answer).read() as CardReport.Answered
        assertEquals(StatusWord(0x6A82), r.selectStatus)
    }

    @Test fun cantReadWhenEverythingIsRefused() {
        assertEquals(CardReport.CantRead, RavKavReader(FakeCard(selectSw = 0x6A82)::answer).read())
        // A card that rejects both class bytes.
        assertEquals(CardReport.CantRead, RavKavReader(FakeCard(cla = 0x80)::answer).read())
    }

    @Test fun cantReadOnMalformedAnswersOrALostCard() {
        assertEquals(CardReport.CantRead, RavKavReader({ ByteArray(1) }).read())
        assertEquals(CardReport.CantRead, RavKavReader({ throw IOException("tag lost") }).read())
        // SELECT fine, then a one-byte answer to READ RECORD.
        val okThenShort = listOf(sw9000, ByteArray(1)).iterator()
        assertEquals(CardReport.CantRead, RavKavReader({ okThenShort.next() }).read())
        // Asks for a new length, then answers garbage.
        val lengthThenGarbage = listOf(sw9000, hex("6C10"), ByteArray(0)).iterator()
        assertEquals(CardReport.CantRead, RavKavReader({ lengthThenGarbage.next() }).read())
        // The Calypso-class SELECT answers garbage.
        val wrongClassThenGarbage = listOf(hex("6E00"), ByteArray(0)).iterator()
        assertEquals(CardReport.CantRead, RavKavReader({ wrongClassThenGarbage.next() }).read())
    }

    @Test fun hexGroupsOfFourBytes() {
        assertEquals("05A12B00 FF", hex("05 A1 2B 00 FF").hexGroups())
        assertEquals("", ByteArray(0).hexGroups())
    }
}
