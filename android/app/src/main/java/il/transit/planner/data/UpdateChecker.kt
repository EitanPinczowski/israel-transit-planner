package il.transit.planner.data

import il.transit.core.api.MotisClient
import il.transit.core.update.CheckResult
import il.transit.core.update.LatestRelease
import il.transit.core.update.UpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Asks GitHub Releases whether a newer version exists: on its own at most once a day, and when
 * the user taps Settings → "Check for updates" (the ViewModel debounces that to once a minute).
 * Free and keyless. Decisions live in core's UpdateCheck, which is tested. [installer] downloads
 * and installs the APK; null in tests.
 */
open class UpdateChecker(
    private val store: UserStore,
    val currentVersion: String,
    open val installer: ApkInstaller? = null,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build(),
) {
    /** The daily check: a newer release, or null for anything else (throttled, up to date, offline). */
    open suspend fun check(now: Instant = Instant.now()): LatestRelease? {
        val last = store.lastUpdateCheck.first()?.let(Instant::ofEpochSecond)
        if (!UpdateCheck.shouldCheck(last, now)) return null
        return (checkNow(now) as? CheckResult.Newer)?.release
    }

    /** Asks now, ignoring the daily throttle; tells "up to date" from "couldn't check". */
    open suspend fun checkNow(now: Instant = Instant.now()): CheckResult {
        val body = withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url(UpdateCheck.LATEST_URL)
                    .header("User-Agent", MotisClient.USER_AGENT)
                    .header("Accept", "application/vnd.github+json")
                    .build()
                http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
            }.getOrNull()
        }
        store.setLastUpdateCheck(now.epochSecond)
        return UpdateCheck.result(currentVersion, body)
    }
}
