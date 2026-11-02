package il.transit.core.card

/**
 * The two ISO/IEC 7816-4 commands the Rav-Kav lab sends (Phase 11 E6). Plain, unsecured
 * commands only: no GET CHALLENGE, no OPEN SECURE SESSION, no keys.
 *
 * Source: ISO/IEC 7816-4:2020, 11.2.2 (SELECT, INS A4) and 11.3.3 (READ RECORD(S), INS B2).
 *
 * Provenance of the Calypso values ([CALYPSO_AID], [CLA_CALYPSO], and the SFIs in [CardFile]):
 * general knowledge of the publicly described Calypso Intercode layout, not any decoder's source
 * (no GPL code was opened). They are only what we ask for: the card's own status words are the
 * evidence of what is there and what is readable.
 */
object Apdu {
    /** ISO class byte (inter-industry). */
    const val CLA_ISO = 0x00

    /** The proprietary class byte older Calypso revisions answer instead (they reply 6E00 to [CLA_ISO]). */
    const val CLA_CALYPSO = 0x94

    /** The Calypso Intercode transport application's name, "1TIC.ICA" in ASCII. */
    val CALYPSO_AID: ByteArray = "1TIC.ICA".toByteArray(Charsets.US_ASCII)

    /** SELECT by DF name: CLA A4 04 00 Lc name 00 (first or only occurrence, FCI asked). */
    fun selectByName(name: ByteArray, cla: Int = CLA_ISO): ByteArray {
        require(name.size in 1..16) { "a DF name is 1 to 16 bytes" }
        return byteArrayOf(cla.toByte(), 0xA4.toByte(), 0x04, 0x00, name.size.toByte()) + name + byteArrayOf(0x00)
    }

    /**
     * READ RECORD: CLA B2 record P2 Le. P2 = SFI in bits 8–4 and 100 in bits 3–1 ("read record
     * number P1"). [le] 0 asks for the whole record (up to 256 bytes).
     */
    fun readRecord(record: Int, sfi: Int, cla: Int = CLA_ISO, le: Int = 0): ByteArray {
        require(record in 1..254) { "record numbers are 1 to 254" }
        require(sfi in 1..30) { "short file identifiers are 1 to 30" }
        require(sfi != CardFile.HOLDER_SFI) { "the holder file is never read" }
        require(le in 0..255)
        return byteArrayOf(cla.toByte(), 0xB2.toByte(), record.toByte(), ((sfi shl 3) or 0x04).toByte(), le.toByte())
    }
}

/** The two trailing bytes of a card's answer (ISO/IEC 7816-4, 5.6). */
@JvmInline
value class StatusWord(val value: Int) {
    val sw1: Int get() = value shr 8
    val sw2: Int get() = value and 0xFF
    val ok: Boolean get() = value == 0x9000

    /** 6Cxx: wrong Le, the card says the right one in SW2. */
    val wrongLength: Boolean get() = sw1 == 0x6C && sw2 != 0

    val meaning: SwMeaning
        get() = when {
            ok -> SwMeaning.OK
            value == 0x6A82 -> SwMeaning.NOT_FOUND
            value == 0x6A83 -> SwMeaning.NO_RECORD
            value == 0x6982 || value == 0x6985 || value == 0x6986 -> SwMeaning.NEEDS_KEYS
            value == 0x6E00 -> SwMeaning.WRONG_CLASS
            else -> SwMeaning.OTHER
        }

    /** "9000", "6A82": always four upper-case hex digits. */
    override fun toString(): String = "%04X".format(value)

    companion object {
        /** The status word at the end of [answer], or null when the answer is too short to have one. */
        fun of(answer: ByteArray): StatusWord? =
            if (answer.size < 2) null else StatusWord(((answer[answer.size - 2].toInt() and 0xFF) shl 8) or (answer.last().toInt() and 0xFF))
    }
}

/** What a status word means for the lab screen. */
enum class SwMeaning {
    OK,

    /** 6A82: no such file on this card. */
    NOT_FOUND,

    /** 6A83: the file has no record with that number (the end of the file). */
    NO_RECORD,

    /** 6982 / 6985 / 6986: the card wants a secure session or a key first. */
    NEEDS_KEYS,

    /** 6E00: the class byte is not supported. */
    WRONG_CLASS,
    OTHER,
}
