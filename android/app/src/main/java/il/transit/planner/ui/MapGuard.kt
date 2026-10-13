package il.transit.planner.ui

import android.util.Log

/**
 * Runs map work that MapLibre calls back into (style loaded, clicks, camera idle) or that
 * touches a style which may already be replaced. An exception thrown inside a callback that
 * native code invoked becomes `jni::PendingJavaException` and aborts the whole process with no
 * Java stack, so nothing may escape: it is logged under [TAG] (never silently dropped) and the
 * block answers null.
 */
object MapGuard {
    const val TAG = "MapGuard"

    /** Where a caught exception goes; JVM tests swap it, the app logs to logcat. */
    @Volatile var log: (what: String, error: Throwable) -> Unit = { what, e -> Log.e(TAG, "$what failed", e) }

    inline fun <T> run(what: String, block: () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            log(what, e)
            null
        }
}
