package il.transit.planner.uitest

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import il.transit.planner.data.ApkInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase 11 T6: the one FileProvider (`<pkg>.updates`, `res/xml/update_paths.xml`). It serves the update
 * APK to Android's installer and the monthly report to the app the user picks, and nothing else:
 * `getUriForFile` refuses every other file, a path that climbs out of a served folder is refused, another
 * app (the shell) can't read a URI it was not granted, and a report is gone after a day.
 */
@RunWith(AndroidJUnit4::class)
class FileProviderTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val authority get() = "${ctx.packageName}.updates"

    private fun shell(cmd: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun uri(f: File): Uri = FileProvider.getUriForFile(ctx, authority, f)

    private fun refused(f: File) {
        try {
            val u = uri(f)
            fail("$f was given a URI: $u")
        } catch (expected: IllegalArgumentException) {
            // FileProvider: "Failed to find configured root that contains …"
        }
    }

    @Test fun only_the_updates_and_reports_folders_are_served() {
        val apk = File(ctx.cacheDir, "${ApkInstaller.DIR}/update.apk").apply { parentFile!!.mkdirs(); writeText("apk") }
        val report = File(ctx.cacheDir, "$REPORTS/t6.csv").apply { parentFile!!.mkdirs(); writeText("csv") }
        assertEquals("content://$authority/updates/update.apk", uri(apk).toString())
        assertEquals("content://$authority/reports/t6.csv", uri(report).toString())

        // The DataStore files that exist, and the usual names whether or not they do.
        val datastore = File(ctx.filesDir, "datastore").listFiles()?.toList().orEmpty()
        val others = datastore + listOf(
            File(ctx.filesDir, "crash_log.txt"),
            File(ctx.filesDir, "datastore/user.preferences_pb"),
            File(ctx.filesDir, "anything.txt"),
            File(ctx.cacheDir, "update.apk"), // the cache itself, beside the served folders
            File(ctx.cacheDir, "updates-evil/update.apk"), // a look-alike folder name
            File(ctx.cacheDir, "updates/../crash.txt"), // climbs out of a served folder
            File(ctx.cacheDir, "reports/../../files/crash_log.txt"),
            File(ctx.dataDir, "shared_prefs/x.xml"),
            File(ctx.getExternalFilesDir(null), "ui/x.png"),
            File("/data/local/tmp/x"),
            File("/sdcard/Download/x"),
        )
        for (f in others) refused(f)
        apk.delete()
        report.delete()
    }

    /** A URI built by hand to climb out of a served folder is refused by the provider itself. */
    @Test fun a_crafted_uri_cannot_climb_out() {
        File(ctx.filesDir, "crash_log.txt").let { if (!it.exists()) it.writeText("t6") }
        for (path in listOf("updates/..%2F..%2Ffiles%2Fcrash_log.txt", "updates/%2E%2E/%2E%2E/files/crash_log.txt", "reports/../../files/crash_log.txt", "root/data/data/${ctx.packageName}/files/crash_log.txt")) {
            val u = Uri.parse("content://$authority/$path")
            val opened = runCatching { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }
            assertTrue("$u was opened: ${opened.getOrNull()?.size} bytes", opened.isFailure || opened.getOrNull() == null)
        }
    }

    /** The provider is not exported: without a grant, another app (here the shell) reads nothing. */
    @Test fun another_app_cannot_read_without_a_grant() {
        val secret = "T6-NOT-FOR-THE-SHELL-${SystemClock.elapsedRealtime()}"
        val apk = File(ctx.cacheDir, "${ApkInstaller.DIR}/update.apk").apply { parentFile!!.mkdirs(); writeText(secret) }
        val report = File(ctx.cacheDir, "$REPORTS/t6.csv").apply { parentFile!!.mkdirs(); writeText(secret) }
        try {
            for (f in listOf(apk, report)) {
                val out = shell("content read --uri ${uri(f)}")
                assertFalse("the shell read $f: $out", out.contains(secret))
                assertTrue("refused with a reason: $out", out.contains("Permission Denial", true) || out.contains("SecurityException") || out.contains("not exported", true))
            }
            val q = shell("content query --uri ${uri(apk)}")
            assertFalse("the shell queried it: $q", q.contains("update.apk") && q.contains("_size"))
        } finally {
            apk.delete()
            report.delete()
        }
    }

    /** Reports older than a day go when the app starts; a fresh one stays. */
    @Test fun a_report_is_gone_after_a_day() {
        val dir = File(ctx.cacheDir, REPORTS).apply { mkdirs() }
        val old = File(dir, "t6-old.csv").apply { writeText("old") }
        val fresh = File(dir, "t6-fresh.csv").apply { writeText("fresh") }
        val now = System.currentTimeMillis()
        assertTrue(old.setLastModified(now - 25 * 3_600_000L))
        assertTrue(fresh.setLastModified(now - 23 * 3_600_000L))
        AppDriver(compose).launch().use {
            val until = SystemClock.elapsedRealtime() + 10_000
            while (old.exists() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(200)
            assertFalse("a report of yesterday is still served", old.exists())
            assertTrue("today's report was deleted", fresh.exists())
        }
        fresh.delete()
    }

    companion object {
        /** `ReportFiles.DIR` (internal to the app): the report CSVs, under cacheDir. */
        const val REPORTS = "reports"
    }
}
