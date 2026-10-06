package il.transit.core.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

/**
 * The newest published release, as far as the app cares. [apkSha256] is GitHub's own digest of
 * the APK asset (lower-case hex), or null when the answer had none.
 */
data class LatestRelease(val version: String, val pageUrl: String, val apkUrl: String?, val apkSha256: String? = null)

/** What a "check now" found. */
sealed interface CheckResult {
    data object UpToDate : CheckResult
    data class Newer(val release: LatestRelease) : CheckResult
    /** No connection, a rate limit, or an answer we could not read. */
    data object Failed : CheckResult
}

/**
 * "Is there a newer version?" against GitHub Releases. Pure: the app fetches
 * `/repos/<owner>/<repo>/releases/latest` and hands the body here.
 */
object UpdateCheck {
    const val LATEST_URL = "https://api.github.com/repos/EitanPinczowski/israel-transit-planner/releases/latest"
    val INTERVAL: Duration = Duration.ofHours(24)

    /** Settings → "Check for updates" asks GitHub at most this often. */
    val MANUAL_INTERVAL: Duration = Duration.ofMinutes(1)

    /** The only hosts an APK may come from, redirects included. https only. */
    val APK_HOSTS = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

    @Serializable
    private data class Asset(val name: String = "", val browser_download_url: String = "", val digest: String? = null)

    @Serializable
    private data class Release(
        val tag_name: String = "",
        val html_url: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Null for anything unusable: garbage, a draft, a pre-release, or no tag. */
    fun parseLatest(body: String): LatestRelease? {
        val r = runCatching { json.decodeFromString(Release.serializer(), body) }.getOrNull() ?: return null
        if (r.draft || r.prerelease || r.tag_name.isBlank()) return null
        val asset = r.assets.firstOrNull { it.name.endsWith(".apk") && it.browser_download_url.isNotBlank() }
        return LatestRelease(r.tag_name.removePrefix("v"), r.html_url, asset?.browser_download_url, asset?.digest?.let(::sha256Of))
    }

    /** `sha256:<64 hex>` → the hex in lower case; any other algorithm or shape → null. */
    fun sha256Of(digest: String): String? {
        val hex = digest.trim().takeIf { it.startsWith("sha256:", ignoreCase = true) }?.substring(7)?.lowercase() ?: return null
        return hex.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    /** The answer to a check that ignores the daily throttle. A null body is a failed fetch. */
    fun result(current: String, body: String?): CheckResult {
        val latest = body?.let(::parseLatest) ?: return CheckResult.Failed
        return if (isNewer(current, latest.version)) CheckResult.Newer(latest) else CheckResult.UpToDate
    }

    /** https on one of [APK_HOSTS] (no other port, no user info), else the download is refused. */
    fun isAllowedApkUrl(url: String): Boolean {
        val u = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        return u.scheme.equals("https", ignoreCase = true) && u.rawUserInfo == null &&
            (u.port == -1 || u.port == 443) && u.host?.lowercase() in APK_HOSTS
    }

    fun canCheckNow(lastManual: Instant?, now: Instant): Boolean =
        lastManual == null || !now.isBefore(lastManual.plus(MANUAL_INTERVAL))

    /**
     * Numeric compare of `major.minor.patch` (a leading `v` is ignored). A `-dev` or other
     * suffixed current version is a local build and never nags.
     */
    fun isNewer(current: String, latest: String): Boolean {
        if ('-' in current) return false
        val a = parts(current) ?: return false
        val b = parts(latest) ?: return false
        for (i in 0 until 3) if (a[i] != b[i]) return b[i] > a[i]
        return false
    }

    fun shouldCheck(lastCheck: Instant?, now: Instant): Boolean =
        lastCheck == null || !now.isBefore(lastCheck.plus(INTERVAL))

    private fun parts(v: String): List<Int>? {
        val nums = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: return null }
        return if (nums.isEmpty() || nums.size > 3) null else nums + List(3 - nums.size) { 0 }
    }
}
