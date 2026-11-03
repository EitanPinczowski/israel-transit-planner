package il.transit.core.search

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What each stage of one search answered (F11, owner's bug bash A4: "TLV", "Rehovot railway station" and "Tel Aviv
 * Central Bus Station" found nothing on the Pixel, and nothing in CI reproduced it). [PlaceSearch] fills it; the app
 * keeps the [line] of the last search that showed nothing, and Settings → Crash log shares it with the crashes.
 *
 * It holds the query and counts only: no location (not even whether it was near), no other typed text.
 */
class SearchTrace(val query: String, val language: String, val hasFix: Boolean) {
    /** Bundled train stations that answered (0 requests). */
    var stations: Int = 0

    /** Transitous's answers before the Israel and town filters; null = not asked (a stop number, a town choice). */
    var transitRaw: Int? = null

    /** Transitous's answers kept (in Israel, in the typed town). */
    var transitKept: Int? = null

    /** Transitous's error class ("SocketTimeoutException", "TransitHttpException"); null = it answered. */
    var transitError: String? = null

    /** The backup geocoder: null = not asked, "timeout", the error class, or how many it answered. */
    var backup: String? = null

    /** The backup's answers kept. */
    var backupKept: Int? = null

    /** Saved places and recents that matched (the phone's own, 0 requests). */
    var local: Int = 0

    /** How it ended: "answer", "failed", "closed before the answer" (the search box closed or a pick was made). */
    var outcome: String = "answer"

    /** Rows the list showed. */
    var shown: Int = 0

    var ms: Long = 0

    /** One line, for the crash log's share text. */
    fun line(at: Instant, zone: ZoneId): String = buildString {
        append(TIME.format(at.atZone(zone))).append(" · \"").append(query.take(MAX_QUERY)).append("\" · ").append(language)
        append(" · fix ").append(if (hasFix) "yes" else "no")
        append(" · Transitous ")
        append(
            when {
                transitError != null -> transitError
                transitRaw == null -> "not asked"
                else -> "$transitRaw ($transitKept kept)"
            },
        )
        append(" · stations ").append(stations)
        append(" · local ").append(local)
        append(" · backup ").append(backup?.let { b -> backupKept?.let { "$b ($it kept)" } ?: b } ?: "not asked")
        append(" · ").append(ms).append(" ms · ").append(outcome).append(" · shown ").append(shown)
    }

    companion object {
        /** A pasted paragraph stays a line. */
        const val MAX_QUERY = 80
        private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
