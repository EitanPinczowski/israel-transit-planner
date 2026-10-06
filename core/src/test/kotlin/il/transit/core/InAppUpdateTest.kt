package il.transit.core

import il.transit.core.update.CheckResult
import il.transit.core.update.LatestRelease
import il.transit.core.update.UpdateCheck
import il.transit.core.update.UpdateFailure
import il.transit.core.update.UpdateFlow
import il.transit.core.update.UpdateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** One-tap in-app update: digest, URL allow-list, the "check now" debounce, the banner states. */
class InAppUpdateTest {
    private val sha = "53cf599e67b96c00ca24027d331231b094943c1903f415ee49c543281f1cf10b"
    private val url = "https://github.com/EitanPinczowski/israel-transit-planner/releases/download/v0.7.0/israel-transit-planner-v0.7.0.apk"
    private val release = LatestRelease("0.8.0", "https://github.com/x/y/releases/tag/v0.8.0", url, sha)

    @Test fun `reads the APK digest from a recorded GitHub release`() {
        val body = javaClass.getResource("/fixtures/github_release_latest.json")!!.readText()
        val r = UpdateCheck.parseLatest(body)!!
        assertEquals("0.7.0", r.version)
        assertEquals(url, r.apkUrl)
        assertEquals(sha, r.apkSha256)
        assertEquals(CheckResult.Newer(r), UpdateCheck.result("0.6.0", body))
        assertEquals(CheckResult.UpToDate, UpdateCheck.result("0.7.0", body))
        assertEquals(CheckResult.Failed, UpdateCheck.result("0.7.0", null))
        assertEquals(CheckResult.Failed, UpdateCheck.result("0.7.0", """{"message":"API rate limit exceeded"}"""))
    }

    @Test fun `only a sha256 digest of the right shape counts`() {
        assertEquals("ab".repeat(32), UpdateCheck.sha256Of("sha256:" + "AB".repeat(32)))
        assertNull(UpdateCheck.sha256Of("sha512:" + "ab".repeat(32)))
        assertNull(UpdateCheck.sha256Of("sha256:" + "ab".repeat(31)))
        assertNull(UpdateCheck.sha256Of("sha256:" + "zz".repeat(32)))
        val noDigest = """{"tag_name":"v1.0.0","html_url":"u","assets":[{"name":"a.apk","browser_download_url":"$url"}]}"""
        assertNull(UpdateCheck.parseLatest(noDigest)!!.apkSha256)
    }

    @Test fun `APKs come only over https from GitHub`() {
        assertTrue(UpdateCheck.isAllowedApkUrl(url))
        assertTrue(UpdateCheck.isAllowedApkUrl("https://objects.githubusercontent.com/github-production-release-asset/1/2?x=y"))
        assertTrue(UpdateCheck.isAllowedApkUrl("https://release-assets.githubusercontent.com/github-production-release-asset/1/2"))
        assertFalse(UpdateCheck.isAllowedApkUrl(url.replace("https", "http")))
        assertFalse(UpdateCheck.isAllowedApkUrl("https://github.com.evil.example/app.apk"))
        assertFalse(UpdateCheck.isAllowedApkUrl("https://evil.example/github.com/app.apk"))
        assertFalse(UpdateCheck.isAllowedApkUrl("https://github.com@evil.example/app.apk"))
        assertFalse(UpdateCheck.isAllowedApkUrl("https://github.com:8443/app.apk"))
        assertFalse(UpdateCheck.isAllowedApkUrl("not a url"))
    }

    @Test fun `check now at most once a minute`() {
        val now = Instant.parse("2026-10-06T09:00:00Z")
        assertTrue(UpdateCheck.canCheckNow(null, now))
        assertFalse(UpdateCheck.canCheckNow(now.minusSeconds(59), now))
        assertTrue(UpdateCheck.canCheckNow(now.minusSeconds(60), now))
    }

    @Test fun `a check lands on latest, available or couldn't check`() {
        assertEquals(UpdateState.UpToDate("0.7.0"), UpdateFlow.afterCheck("0.7.0", CheckResult.UpToDate))
        assertEquals(UpdateState.Available(release), UpdateFlow.afterCheck("0.7.0", CheckResult.Newer(release)))
        assertEquals(UpdateState.CheckFailed(), UpdateFlow.afterCheck("0.7.0", CheckResult.Failed))
    }

    @Test fun `tap, download with progress, verify, ready`() {
        var s: UpdateState = UpdateState.Available(release)
        s = UpdateFlow.onUpdate(s, canInstall = true)
        assertEquals(UpdateState.Downloading(release, 0), s)
        assertSame(s, UpdateFlow.onUpdate(s, canInstall = true)) // a second tap does not restart it
        s = UpdateFlow.progress(s, 400, 1000)
        assertEquals(UpdateState.Downloading(release, 40), s)
        assertSame(s, UpdateFlow.progress(s, 401, 1000))
        assertEquals(UpdateState.Downloading(release, null), UpdateFlow.progress(s, 5, -1))
        assertEquals(UpdateState.Ready(release, "/c/u.apk"), UpdateFlow.downloaded(s, "/c/u.apk", sha.uppercase()))
    }

    @Test fun `a file that does not match GitHub's digest is never installed`() {
        val s = UpdateState.Downloading(release, 99)
        assertEquals(UpdateState.Failed(release, UpdateFailure.CHECKSUM), UpdateFlow.downloaded(s, "/c/u.apk", "0".repeat(64)))
        val noDigest = UpdateState.Downloading(release.copy(apkSha256 = null), 99)
        assertEquals(UpdateFailure.CHECKSUM, (UpdateFlow.downloaded(noDigest, "/c/u.apk", sha) as UpdateState.Failed).reason)
    }

    @Test fun `no install permission asks first, and coming back continues`() {
        val asked = UpdateFlow.onUpdate(UpdateState.Available(release), canInstall = false)
        assertEquals(UpdateState.NeedsPermission(release), asked)
        assertEquals(asked, UpdateFlow.onUpdate(asked, canInstall = false)) // came back without allowing
        assertEquals(UpdateState.Downloading(release, 0), UpdateFlow.onUpdate(asked, canInstall = true))
        // Permission revoked after the download: the verified file is kept.
        val ready = UpdateState.Ready(release, "/c/u.apk")
        val again = UpdateFlow.onUpdate(ready, canInstall = false)
        assertEquals(UpdateState.NeedsPermission(release, "/c/u.apk"), again)
        assertEquals(ready, UpdateFlow.onUpdate(again, canInstall = true))
    }

    @Test fun `an APK link off GitHub fails before downloading`() {
        val off = release.copy(apkUrl = "https://evil.example/app.apk")
        assertEquals(UpdateState.Failed(off, UpdateFailure.BAD_URL), UpdateFlow.onUpdate(UpdateState.Available(off), true))
        val none = release.copy(apkUrl = null)
        assertEquals(UpdateState.Failed(none, UpdateFailure.BAD_URL), UpdateFlow.onUpdate(UpdateState.Available(none), true))
    }

    @Test fun `failures keep the release and Try again downloads again`() {
        val failed = UpdateFlow.failed(UpdateState.Downloading(release, 40), UpdateFailure.NETWORK)
        assertEquals(UpdateState.Failed(release, UpdateFailure.NETWORK), failed)
        assertEquals(UpdateState.Downloading(release, 0), UpdateFlow.onUpdate(failed, canInstall = true))
        assertSame(UpdateState.Idle, UpdateFlow.failed(UpdateState.Idle, UpdateFailure.NETWORK))
        assertSame(UpdateState.Idle, UpdateFlow.onUpdate(UpdateState.Idle, canInstall = true))
    }

    @Test fun `Android's answer - cancel keeps Ready, a signature clash says different build`() {
        val ready = UpdateState.Ready(release, "/c/u.apk")
        assertSame(ready, UpdateFlow.installResult(ready, UpdateFlow.RESULT_CANCELED, null))
        assertEquals(
            UpdateState.Failed(release, UpdateFailure.DIFFERENT_BUILD),
            UpdateFlow.installResult(ready, 1, UpdateFlow.INSTALL_FAILED_UPDATE_INCOMPATIBLE),
        )
        assertEquals(UpdateState.Failed(release, UpdateFailure.INSTALL), UpdateFlow.installResult(ready, 1, -4))
        assertEquals(UpdateState.Failed(release, UpdateFailure.INSTALL), UpdateFlow.installResult(ready, 1, null))
    }

    @Test fun `each screen opens once per tap, not again on rotation or when the user came back`() {
        val ready = UpdateState.Ready(release, "/c/u.apk")
        assertTrue(UpdateFlow.needsScreen(ready))
        val opened = UpdateFlow.shown(ready)
        assertFalse(UpdateFlow.needsScreen(opened))
        assertSame(opened, UpdateFlow.installResult(opened, UpdateFlow.RESULT_CANCELED, null)) // cancel: no re-prompt
        assertTrue(UpdateFlow.needsScreen(UpdateFlow.onUpdate(opened, canInstall = true))) // Install tap: again
        val asked = UpdateFlow.shown(UpdateState.NeedsPermission(release))
        assertFalse(UpdateFlow.needsScreen(UpdateFlow.onPermissionReturn(asked, canInstall = false)))
        assertEquals(UpdateState.Downloading(release, 0), UpdateFlow.onPermissionReturn(asked, canInstall = true))
        assertTrue(UpdateFlow.needsScreen(UpdateFlow.onUpdate(asked, canInstall = false))) // Allow tap: again
    }

    @Test fun `X stops a download, and check now never throws away an update in progress`() {
        assertEquals(UpdateState.Available(release), UpdateFlow.dismissed(UpdateState.Downloading(release, 40)))
        val ready = UpdateState.Ready(release, "/c/u.apk", shown = true)
        assertSame(ready, UpdateFlow.dismissed(ready))
        assertFalse(UpdateFlow.canCheck(ready))
        assertFalse(UpdateFlow.canCheck(UpdateState.Downloading(release, 1)))
        assertFalse(UpdateFlow.canCheck(UpdateState.NeedsPermission(release)))
        assertFalse(UpdateFlow.canCheck(UpdateState.Checking))
        assertTrue(UpdateFlow.canCheck(UpdateState.Available(release)))
        assertTrue(UpdateFlow.canCheck(UpdateState.Failed(release, UpdateFailure.NETWORK)))
        assertTrue(UpdateFlow.canCheck(UpdateState.CheckFailed()))
    }

    @Test fun `a second check within the minute says it checked a moment ago`() {
        assertEquals(UpdateState.UpToDate("0.7.0", tooSoon = true), UpdateFlow.tooSoon(UpdateState.UpToDate("0.7.0")))
        assertEquals(UpdateState.CheckFailed(tooSoon = true), UpdateFlow.tooSoon(UpdateState.CheckFailed()))
        val available = UpdateState.Available(release)
        assertSame(available, UpdateFlow.tooSoon(available)) // the Update button is what it offers
    }
}
