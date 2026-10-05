package il.transit.core.diag

import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The local crash log: the last [MAX_ENTRIES] crashes as plain text, newest last. The app
 * appends from its uncaught-exception handler and the user shares it by hand from Settings.
 * Nothing here (or in the app) sends it anywhere.
 *
 * Each entry starts with a [HEADER] line, so the file splits back into entries without a
 * parser that could itself fail while the process is dying.
 */
object CrashLog {
    const val MAX_ENTRIES = 5

    /** A trace longer than this is cut, so five deep Compose traces still make a small file. */
    const val MAX_TRACE_CHARS = 12_000

    const val HEADER = "===== crash "
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx")

    /** One entry: time, app version, Android version, thread, then the stack trace. */
    fun format(
        error: Throwable,
        at: Instant,
        zone: ZoneId,
        appVersion: String,
        androidVersion: String,
        thread: String,
    ): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().trimEnd()
        val cut = if (trace.length > MAX_TRACE_CHARS) trace.take(MAX_TRACE_CHARS) + "\n\t… (cut)" else trace
        return buildString {
            append(HEADER).append(TIME.format(at.atZone(zone))).append(" =====\n")
            append("app: ").append(appVersion).append('\n')
            append("android: ").append(androidVersion).append('\n')
            append("thread: ").append(thread).append('\n')
            append(cut).append('\n')
        }
    }

    /** The entries in [text], oldest first. Anything before the first header is dropped. */
    fun entries(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (line in text.lineSequence()) {
            if (line.startsWith(HEADER)) {
                if (current.isNotEmpty()) out += current.toString().trimEnd() + "\n"
                current.clear()
            }
            if (line.startsWith(HEADER) || current.isNotEmpty()) current.append(line).append('\n')
        }
        if (current.isNotEmpty()) out += current.toString().trimEnd() + "\n"
        return out
    }

    /** [existing] file text plus [entry], keeping only the newest [MAX_ENTRIES]. */
    fun append(existing: String?, entry: String): String =
        (entries(existing) + entry).takeLast(MAX_ENTRIES).joinToString("\n")

    /** What the share sheet gets: newest crash first, or null when there is nothing to share. */
    fun report(text: String?, appVersion: String): String? {
        val list = entries(text)
        if (list.isEmpty()) return null
        return buildString {
            append("Israel Transit Planner ").append(appVersion).append(" — crash log (")
            append(list.size).append(if (list.size == 1) " crash" else " crashes").append(", newest first)\n\n")
            append(list.asReversed().joinToString("\n"))
        }
    }
}
