package il.transit.planner.data

import android.os.Build
import il.transit.core.diag.CrashLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * The last [CrashLog.MAX_ENTRIES] crashes, as plain text in `filesDir`. Written by the
 * uncaught-exception handler, read only when the user taps "Share crash log". Never uploaded.
 */
class CrashLogStore(private val file: File, private val appVersion: String) {
    private val _count = MutableStateFlow(0)

    /** How many crashes are kept; Settings shows Share/Clear only when there are some. */
    val count: StateFlow<Int> = _count.asStateFlow()

    private val searchFile = File(file.parentFile, "last_empty_search.txt")
    private val _lastSearch = MutableStateFlow<String?>(null)

    /**
     * The last search that showed nothing, as one line of its stages (F11, [il.transit.core.search.SearchTrace]): the query
     * and counts, no location. Shared with the crashes; Clear removes it too.
     */
    val lastSearch: StateFlow<String?> = _lastSearch.asStateFlow()

    /** Replaces the last empty search's line; a write failure is never the search's problem. */
    fun noteSearch(line: String) {
        _lastSearch.value = line
        Thread { runCatching { synchronized(this) { searchFile.writeText(line) } } }.start()
    }

    /** Records the crash, then lets the previous handler (Android's "app stopped") run as usual. */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Synchronous on purpose: the process dies right after. Never let logging hide the crash.
            runCatching { append(error, thread.name) }
            previous?.uncaughtException(thread, error)
        }
    }

    internal fun append(error: Throwable, thread: String) = synchronized(this) {
        val android = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL})"
        val entry = CrashLog.format(error, Instant.now(), ZoneId.systemDefault(), appVersion, android, thread)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(CrashLog.append(readOrNull(), entry))
        tmp.renameTo(file)
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _count.value = CrashLog.entries(readOrNull()).size
        _lastSearch.value = runCatching { searchFile.takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null } }.getOrNull()
    }

    /** The text for the share sheet, newest first; null when there is nothing. */
    suspend fun report(): String? = withContext(Dispatchers.IO) {
        val search = _lastSearch.value?.let { "Last search that found nothing: $it\n" }
        val crashes = CrashLog.report(readOrNull(), appVersion)
        when {
            crashes == null && search == null -> null
            crashes == null -> "Israel Transit Planner $appVersion\n$search"
            search == null -> crashes
            else -> "$crashes\n$search"
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(this@CrashLogStore) {
            file.delete()
            searchFile.delete()
        }
        _count.value = 0
        _lastSearch.value = null
    }

    private fun readOrNull(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()
}
