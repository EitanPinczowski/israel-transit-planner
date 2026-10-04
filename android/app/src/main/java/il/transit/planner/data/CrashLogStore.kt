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

    suspend fun refresh() = withContext(Dispatchers.IO) { _count.value = CrashLog.entries(readOrNull()).size }

    /** The text for the share sheet, newest first; null when there is nothing. */
    suspend fun report(): String? = withContext(Dispatchers.IO) { CrashLog.report(readOrNull(), appVersion) }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(this@CrashLogStore) { file.delete() }
        _count.value = 0
    }

    private fun readOrNull(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()
}
