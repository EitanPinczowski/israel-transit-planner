package il.transit.planner.data

import il.transit.core.api.MotisClient
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
 * Asks GitHub Releases, at most once a day, whether a newer version exists. Free and keyless;
 * any failure (no network, rate limit, the 404 GitHub returns before the first release) is
 * simply "no update". Decisions live in core's UpdateCheck, which is tested.
 */
class UpdateChecker(
    private val store: UserStore,
    private val currentVersion: String,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build(),
) {
    suspend fun check(now: Instant = Instant.now()): LatestRelease? {
        val last = store.lastUpdateCheck.first()?.let(Instant::ofEpochSecond)
        if (!UpdateCheck.shouldCheck(last, now)) return null
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
        val latest = body?.let(UpdateCheck::parseLatest) ?: return null
        return latest.takeIf { UpdateCheck.isNewer(currentVersion, it.version) }
    }
}
