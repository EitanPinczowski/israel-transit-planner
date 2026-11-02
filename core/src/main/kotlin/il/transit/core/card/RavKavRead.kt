package il.transit.core.card

/**
 * One file the lab may read. [sfi] is its short file identifier inside the Calypso application.
 * The SFIs follow the public descriptions of the Calypso Intercode structure; the card's own
 * status words say whether each one is there.
 */
enum class CardFile(val sfi: Int) {
    CONTRACT_LIST(0x1E),
    CONTRACTS(0x09),
    COUNTERS(0x19),
    EVENT_LOG(0x08),
    SPECIAL_EVENTS(0x1D),
    ;

    companion object {
        /**
         * Never read, by design: on Calypso cards SFI 07 holds the environment together with the
         * holder (name, ID number, birth date). The brief forbids reading the holder, and one
         * READ RECORD cannot take half a record, so the environment is skipped with it.
         */
        const val HOLDER_SFI = 0x07
    }
}

/** What one file answered. Bytes stay in memory for the screen only. */
class FileRead(
    val file: CardFile,
    /** The status word that ended the reading: 9000 / 6A83 after the last record, or the refusal. */
    val status: StatusWord,
    val records: List<ByteArray>,
) {
    val outcome: FileOutcome
        get() = when {
            records.isNotEmpty() -> FileOutcome.READ
            status.meaning == SwMeaning.NO_RECORD -> FileOutcome.EMPTY
            status.meaning == SwMeaning.NOT_FOUND -> FileOutcome.NOT_FOUND
            status.meaning == SwMeaning.NEEDS_KEYS -> FileOutcome.NEEDS_KEYS
            else -> FileOutcome.REFUSED
        }
}

enum class FileOutcome { READ, EMPTY, NOT_FOUND, NEEDS_KEYS, REFUSED }

/** The result of one tap. */
sealed interface CardReport {
    /**
     * The card answered. [selectStatus] is SELECT's status word (the FCI it sent back holds the
     * card's serial number, so it is neither kept nor shown); [cla] the class byte that worked.
     */
    class Answered(val selectStatus: StatusWord, val cla: Int, val files: List<FileRead>, val millis: Long) : CardReport

    /** Nothing usable: the card answered garbage, refused everything, or left the field. */
    data object CantRead : CardReport
}

/**
 * Reads the public files of a Calypso card (Rav-Kav) with plain commands (Phase 11 E6 spike).
 *
 * [transceive] sends one command and returns the card's answer (data + status word); on the
 * phone it is `IsoDep.transceive`. Any exception it throws ends the read as [CardReport.CantRead],
 * and no exception message ever carries card bytes, so nothing reaches the crash log.
 * Only the [CardFile] list is read; [CardFile.HOLDER_SFI] never is.
 */
class RavKavReader(
    private val transceive: (ByteArray) -> ByteArray,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    fun read(): CardReport {
        val start = nanoTime()
        return try {
            readAll(start)
        } catch (_: Exception) {
            CardReport.CantRead
        }
    }

    private fun readAll(start: Long): CardReport {
        var cla = Apdu.CLA_ISO
        var select = send(Apdu.selectByName(Apdu.CALYPSO_AID, cla)) ?: return CardReport.CantRead
        if (select.meaning == SwMeaning.WRONG_CLASS) {
            cla = Apdu.CLA_CALYPSO
            select = send(Apdu.selectByName(Apdu.CALYPSO_AID, cla)) ?: return CardReport.CantRead
        }
        val files = CardFile.entries.map { readFile(it, cla) ?: return CardReport.CantRead }
        // Only refusals everywhere: not a Calypso card we can read.
        if (!select.ok && files.none { it.outcome == FileOutcome.READ }) return CardReport.CantRead
        return CardReport.Answered(select, cla, files, (nanoTime() - start) / 1_000_000)
    }

    /** Records 1, 2, … until the card says there are no more, at most [MAX_RECORDS]. Null: a malformed answer. */
    private fun readFile(file: CardFile, cla: Int): FileRead? {
        check(file.sfi != CardFile.HOLDER_SFI)
        val records = ArrayList<ByteArray>()
        var last = StatusWord(0x9000)
        for (n in 1..MAX_RECORDS) {
            var answer = transceive(Apdu.readRecord(n, file.sfi, cla))
            var sw = StatusWord.of(answer) ?: return null
            if (sw.wrongLength) {
                answer = transceive(Apdu.readRecord(n, file.sfi, cla, le = sw.sw2))
                sw = StatusWord.of(answer) ?: return null
            }
            last = sw
            if (!sw.ok) break
            records += answer.copyOf(answer.size - 2)
        }
        return FileRead(file, last, records)
    }

    private fun send(command: ByteArray): StatusWord? = StatusWord.of(transceive(command))

    companion object {
        /** Calypso files hold a handful of records; this only stops a card that never says "no more". */
        const val MAX_RECORDS = 16
    }
}

/** Upper-case hex in groups of four bytes, for the lab screen ("05A1 2B00 …"). */
fun ByteArray.hexGroups(): String =
    joinToString("") { "%02X".format(it) }.chunked(8).joinToString(" ")
