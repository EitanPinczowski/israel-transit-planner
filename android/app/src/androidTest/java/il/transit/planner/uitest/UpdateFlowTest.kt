package il.transit.planner.uitest

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.hasFlag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import il.transit.core.update.CheckResult
import il.transit.core.update.UpdateFailure
import il.transit.core.update.UpdateFlow
import il.transit.core.update.UpdateState
import il.transit.core.user.SavedPlace
import il.transit.planner.MainActivity
import il.transit.planner.R
import il.transit.planner.UiTestApp
import il.transit.planner.data.ApkInstaller
import il.transit.planner.data.UpdateChecker
import il.transit.planner.uitest.mock.FakeReleaseServer
import il.transit.planner.uitest.mock.FakeReleaseServer.ApkMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

/** What the update tests share: the fake server wired into a real [UpdateChecker] and [ApkInstaller]. */
internal object UpdateRig {
    val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    val app get() = ctx.applicationContext as UiTestApp
    val updatesDir get() = File(ctx.cacheDir, ApkInstaller.DIR)

    /** The version the app pretends to be: the uitest build's own ("0.0.0-dev-uitest") never asks for updates. */
    const val CURRENT = "0.0.1"

    fun leftovers(): List<File> = updatesDir.listFiles()?.toList().orEmpty()

    fun shell(cmd: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun shellBytes(cmd: String): ByteArray {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    fun installer(fake: FakeReleaseServer) = ApkInstaller(app, fake.client(ApkInstaller.client()))

    fun checker(fake: FakeReleaseServer) = UpdateChecker(app.store, CURRENT, installer(fake), fake.client(UpdateChecker.client()))

    /**
     * The app on a clean slate ([places] saved) whose update check answers [json] from [fake], allowed to
     * install apps, started. The daily check runs as the Activity starts.
     */
    fun launch(compose: ComposeTestRule, fake: FakeReleaseServer, json: String, places: List<SavedPlace> = UiTestApp.PLACES): AppDriver {
        val d = AppDriver(compose, places)
        runBlocking { d.app.store.setLastUpdateCheck(0) } // the fixed clock would otherwise read it as "checked today"
        fake.latestJson = json
        d.app.releaseChecker = checker(fake)
        shell("appops set ${ctx.packageName} REQUEST_INSTALL_PACKAGES allow")
        return d.launch()
    }

    fun awaitState(compose: ComposeTestRule, d: AppDriver, timeoutMs: Long = 20_000, what: (UpdateState) -> Boolean) {
        compose.waitUntil(timeoutMs) { what(d.vm.state.value.updateState) }
        compose.waitForIdle()
    }

    /** The banner's Update button, once the check has found the release. */
    fun tapUpdate(compose: ComposeTestRule, d: AppDriver) {
        compose.waitUntil(15_000) { d.vm.state.value.update != null }
        d.awaitNode(hasText(d.str(R.string.update_action)) and hasClickAction())
        val button = hasText(d.str(R.string.update_action)) and hasClickAction()
        compose
            .onAllNodes(button)
            .onFirst()
            .performClick()
    }
}

/**
 * Phase 11 T6: the in-app update against [FakeReleaseServer] (GitHub Releases on the emulator's loopback;
 * 0 requests to the internet), through the app's real `UpdateChecker`, `ApkInstaller` and `UpdateFlow`.
 * What the update refuses, each pinned here: an APK on a host off the allowlist, plain HTTP, another port
 * or user info; a redirect off the allowlist; a digest that doesn't match (the file is deleted); a download
 * cut halfway (a clear error, nothing kept, the next try starts clean); a version that isn't newer (no
 * banner). A refused host is never even looked up.
 */
@RunWith(AndroidJUnit4::class)
class UpdateFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @get:Rule val permissions = Permissions.grantAll()

    @get:Rule val fake = FakeReleaseServer()

    private fun download(url: String): ApkInstaller.Download = runBlocking { UpdateRig.installer(fake).download(url) { _, _ -> } }

    private val sha get() = FakeReleaseServer.sha256(fake.apk)

    @Test fun an_apk_off_the_allowlist_is_never_contacted() {
        for (url in listOf(
            "https://evil.example/israel-transit-planner.apk",
            "https://github.com.evil.example/x.apk",
            "https://github.com@evil.example/x.apk",
            "https://evil.example\\@github.com/x.apk",
            "https://github.com:8443/x.apk",
            "http://github.com/x.apk",
            "http://objects.githubusercontent.com/x.apk",
            "ftp://github.com/x.apk",
            "file:///sdcard/Download/x.apk",
            "content://media/external/file/1",
            "javascript:alert(1)",
            "",
        )) {
            fake.clear()
            assertEquals(url, ApkInstaller.Download.Failed(UpdateFailure.BAD_URL), download(url))
            assertTrue("$url: ${fake.lookups} ${fake.connects} ${fake.requests}", fake.lookups.isEmpty() && fake.connects.isEmpty() && fake.requests.isEmpty())
            assertTrue("$url left ${UpdateRig.leftovers()}", UpdateRig.leftovers().isEmpty())
        }
    }

    /** A release whose APK link leaves GitHub: Update fails at once, before any download. */
    @Test fun a_release_pointing_elsewhere_is_refused_before_download() {
        fake.latestJson = FakeReleaseServer.release("v0.0.2", "https://evil.example/israel-transit-planner.apk", "sha256:$sha")
        val found = runBlocking { UpdateRig.checker(fake).checkNow() }
        assertTrue("$found", found is CheckResult.Newer)
        val release = (found as CheckResult.Newer).release
        assertEquals(UpdateState.Failed(release, UpdateFailure.BAD_URL), UpdateFlow.onUpdate(UpdateState.Available(release), canInstall = true))
        assertEquals(listOf("api.github.com/repos/EitanPinczowski/israel-transit-planner/releases/latest"), fake.requests.toList())
        assertFalse(fake.touched("evil.example"))
    }

    @Test fun a_redirect_off_the_allowlist_is_refused() {
        for (to in listOf(
            "https://evil.example/x.apk",
            "//evil.example/x.apk",
            "http://${FakeReleaseServer.ASSET_HOST}${FakeReleaseServer.ASSET_PATH}",
            "https://${FakeReleaseServer.ASSET_HOST}:444${FakeReleaseServer.ASSET_PATH}",
            "https://user:pass@${FakeReleaseServer.ASSET_HOST}${FakeReleaseServer.ASSET_PATH}",
            "file:///data/local/tmp/x.apk",
        )) {
            fake.clear()
            fake.apkMode = ApkMode.REDIRECT
            fake.redirectTo = to
            assertEquals(to, ApkInstaller.Download.Failed(UpdateFailure.BAD_URL), download(FakeReleaseServer.APK_URL))
            assertEquals("$to: only the first hop", listOf("github.com:443"), fake.connects.toList())
            assertFalse(to, fake.touched("evil.example"))
            assertTrue("$to left ${UpdateRig.leftovers()}", UpdateRig.leftovers().isEmpty())
        }
        // GitHub's own redirect, to its asset host, is followed.
        fake.clear()
        fake.apkMode = ApkMode.REDIRECT
        val done = download(FakeReleaseServer.APK_URL)
        assertTrue("$done", done is ApkInstaller.Download.Done)
        assertEquals(sha, (done as ApkInstaller.Download.Done).sha256)
        assertEquals(listOf("github.com", FakeReleaseServer.ASSET_HOST), fake.requests.map { it.substringBefore('/') })
        done.file.delete()
    }

    /** Cut halfway: an error, no partial file; the next try starts from nothing and gets the whole file. */
    @Test fun a_cut_download_keeps_nothing_and_the_next_try_starts_clean() {
        fake.apkMode = ApkMode.CUT
        assertEquals(ApkInstaller.Download.Failed(UpdateFailure.NETWORK), download(FakeReleaseServer.APK_URL))
        assertTrue("left ${UpdateRig.leftovers()}", UpdateRig.leftovers().isEmpty())
        fake.apkMode = ApkMode.SERVE
        val done = download(FakeReleaseServer.APK_URL) as ApkInstaller.Download.Done
        assertEquals(sha, done.sha256)
        assertEquals(fake.apk.size.toLong(), done.file.length())
        done.file.delete()
    }

    /** Through the banner: the cut is said in one line, and nothing is kept. */
    @Test fun a_cut_download_from_the_banner() {
        fake.apkMode = ApkMode.CUT
        UpdateRig.launch(compose, fake, FakeReleaseServer.release("v0.0.2", digest = "sha256:$sha")).use { d ->
            UpdateRig.tapUpdate(compose, d)
            UpdateRig.awaitState(compose, d) { it is UpdateState.Failed }
            assertEquals(UpdateFailure.NETWORK, (d.vm.state.value.updateState as UpdateState.Failed).reason)
            d.awaitNode(hasText(d.str(R.string.update_failed_network)))
            assertTrue("left ${UpdateRig.leftovers()}", UpdateRig.leftovers().isEmpty())
        }
    }

    /** Through the banner: a digest that isn't GitHub's is refused, and the downloaded file deleted. */
    @Test fun a_digest_that_does_not_match_is_refused_and_deleted() {
        for (digest in listOf("sha256:" + "0".repeat(64), "sha512:$sha", null)) {
            UpdateRig.launch(compose, fake, FakeReleaseServer.release("v0.0.2", digest = digest)).use { d ->
                UpdateRig.tapUpdate(compose, d)
                UpdateRig.awaitState(compose, d) { it is UpdateState.Failed }
                assertEquals("$digest", UpdateFailure.CHECKSUM, (d.vm.state.value.updateState as UpdateState.Failed).reason)
                d.awaitNode(hasText(d.str(R.string.update_failed_checksum)))
                compose.waitUntil(5_000) { UpdateRig.leftovers().isEmpty() }
                assertEquals("the APK was fetched once", 1, fake.requests.count { it.contains(".apk") })
            }
            fake.clear()
        }
    }

    /** The same or an older version: the check answers, and no banner shows. */
    @Test fun a_version_not_newer_shows_no_banner() {
        for (tag in listOf("v${UpdateRig.CURRENT}", "v0.0.0", "v0.0.1-rc1", "garbage")) {
            UpdateRig.launch(compose, fake, FakeReleaseServer.release(tag, digest = "sha256:$sha")).use { d ->
                compose.waitUntil(15_000) { fake.requests.any { it.endsWith("/releases/latest") } }
                SystemClock.sleep(1_000)
                compose.waitForIdle()
                assertEquals(tag, null, d.vm.state.value.update)
                val buttons = compose.onAllNodes(hasText(d.str(R.string.update_action))).fetchSemanticsNodes()
                assertTrue(tag, buttons.isEmpty())
            }
            fake.clear()
        }
    }
}

/**
 * Phase 11 T6 item 4, end to end on API 35, in two steps around a real install that the instrumentation
 * can't outlive (Android stops the app's process when the app is replaced). `tools/ui_intent_fuzz.sh` runs
 * them in order, never as part of a class run:
 *  1. [step1_banner_to_installer]: this `uitest` APK as the "previous version" finds the fake release
 *     (the next APK, `versionCode + 1`, pushed to /data/local/tmp by the script), Download, digest OK,
 *     Android's installer opens on our provider's URI. The script then taps Install.
 *  2. [step2_after_install]: the new version runs, with its data (owner decision 30).
 */
@RunWith(AndroidJUnit4::class)
class UpdateInstallSteps {
    @get:Rule val compose = createEmptyComposeRule()

    @get:Rule val permissions = Permissions.grantAll()

    @get:Rule val fake = FakeReleaseServer()

    @Test fun step1_banner_to_installer() {
        val next = UpdateRig.shellBytes("cat $NEXT_APK")
        assertTrue("the next APK is at $NEXT_APK (${next.size} bytes)", next.size > 1_000_000)
        fake.apk = next
        val sha = FakeReleaseServer.sha256(next)
        val pkg = UpdateRig.ctx.packageName
        val before = UpdateRig.ctx.packageManager.getPackageInfo(pkg, 0)
        Log.i(TAG, "installed: ${before.versionName} (${before.longVersionCode}); next sha256 $sha")
        Intents.init()
        try {
            // Through GitHub's redirect to its asset host, as a real release is served.
            fake.apkMode = ApkMode.REDIRECT
            UpdateRig.launch(compose, fake, FakeReleaseServer.release("v0.0.2", digest = "sha256:$sha"), UiTestApp.PLACES + MARKER).use { d ->
                runBlocking { d.app.store.setSettings(d.app.store.settings.first().copy(walkSpeed = MARKER_SPEED)) }
                UpdateRig.tapUpdate(compose, d)
                UpdateRig.awaitState(compose, d, 60_000) { it is UpdateState.Ready || it is UpdateState.Failed }
                val st = d.vm.state.value.updateState
                assertTrue("$st", st is UpdateState.Ready)
                assertEquals(sha, FakeReleaseServer.sha256(File((st as UpdateState.Ready).file).readBytes()))
                // UpdateLaunchers opens Android's prompt by itself, with a read grant on our provider's URI.
                val dev = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                val installer = Pattern.compile("com(\\.google)?\\.android\\.packageinstaller")
                assertTrue("Android's installer opened", dev.wait(Until.hasObject(By.pkg(installer)), 30_000))
                Intents.intended(
                    allOf(
                        hasAction("android.intent.action.INSTALL_PACKAGE"),
                        hasData(android.net.Uri.parse("content://$pkg.updates/updates/update.apk")),
                        hasFlag(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    ),
                )
                assertEquals(listOf("api.github.com", "github.com", FakeReleaseServer.ASSET_HOST), fake.requests.map { it.substringBefore('/') })
                Log.i(TAG, "installer open for ${d.vm.state.value.update?.version}; leaving it to the script")
                // Give the installer time to copy the APK before this test (and its process) ends.
                SystemClock.sleep(3_000)
            }
        } finally {
            Intents.release()
        }
    }

    @Test fun step2_after_install() {
        val pm = UpdateRig.ctx.packageManager
        @Suppress("DEPRECATION") // API 35 only, but one call for every level
        val info = pm.getPackageInfo(UpdateRig.ctx.packageName, 0)
        Log.i(TAG, "after: ${info.versionName} (${info.longVersionCode})")
        assertEquals("the new version is installed", NEXT_CODE, info.longVersionCode)
        assertEquals("0.0.2-uitest", info.versionName)
        val store = UpdateRig.app.store
        assertTrue("saved places kept", runBlocking { store.places.first() }.contains(MARKER))
        assertEquals("settings kept", MARKER_SPEED, runBlocking { store.settings.first() }.walkSpeed)
        // And it starts: no reset, the user's data on screen.
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            compose.waitUntil(15_000) { s.state == Lifecycle.State.RESUMED }
            var places = emptyList<SavedPlace>()
            s.onActivity { a -> places = androidx.lifecycle.ViewModelProvider(a)[il.transit.planner.ui.MainViewModel::class.java].state.value.savedPlaces }
            compose.waitUntil(10_000) {
                s.onActivity { a -> places = androidx.lifecycle.ViewModelProvider(a)[il.transit.planner.ui.MainViewModel::class.java].state.value.savedPlaces }
                MARKER in places
            }
        }
    }

    companion object {
        const val TAG = "UpdateE2E"
        const val NEXT_APK = "/data/local/tmp/t6-next.apk"
        const val NEXT_CODE = 2L
        val MARKER = SavedPlace("T6 עדכון", 31.2622, 34.8013)
        val MARKER_SPEED = il.transit.core.user.WalkSpeed.SLOW
    }
}
