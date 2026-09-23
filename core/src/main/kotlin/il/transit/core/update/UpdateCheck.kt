package il.transit.core.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

/** The newest published release, as far as the app cares. */
data class LatestRelease(val version: String, val pageUrl: String, val apkUrl: String?)

/**
 * "Is there a newer version?" against GitHub Releases. Pure: the app fetches
 * `/repos/<owner>/<repo>/releases/latest` and hands the body here.
 */
object UpdateCheck {
    const val LATEST_URL = "https://api.github.com/repos/EitanPinczowski/israel-transit-planner/releases/latest"
    val INTERVAL: Duration = Duration.ofHours(24)

    @Serializable
    private data class Asset(val name: String = "", val browser_download_url: String = "")

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
        val apk = r.assets.firstOrNull { it.name.endsWith(".apk") }?.browser_download_url?.takeIf { it.isNotBlank() }
        return LatestRelease(r.tag_name.removePrefix("v"), r.html_url, apk)
    }

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
