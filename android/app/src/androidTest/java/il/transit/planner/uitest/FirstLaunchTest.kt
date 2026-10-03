package il.transit.planner.uitest

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import il.transit.planner.R
import il.transit.planner.UiTestApp.Companion.TEL_AVIV
import il.transit.planner.ui.UiError
import il.transit.planner.ui.UiTags
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * J1, first launch: the user says no to location. The app must stay usable and say why a
 * search from "my location" cannot run. Runs first on a fresh install (the CI script calls
 * it before anything grants permissions); skipped when location is already granted.
 */
@RunWith(AndroidJUnit4::class)
class FirstLaunchTest {
    @get:Rule val compose = createEmptyComposeRule()

    /** Closes an "X has stopped" dialog of some other process; true if there was one. */
    private fun dismissSystemCrash(device: UiDevice): Boolean {
        val close = device.findObject(By.res("android:id/aerr_close")) ?: device.findObject(By.text(Pattern.compile("Close app|Close", Pattern.CASE_INSENSITIVE)))
        close?.click()
        device.waitForIdle()
        return close != null
    }

    @Test fun j01_deny_location_and_still_search() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            "location already granted; run on a fresh install",
            ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED,
        )
        AppDriver(compose).launch().use { d ->
            val device = UiDevice.getInstance(d.inst)
            val denyButton = By.res(Pattern.compile(".*:id/permission_deny(_and_dont_ask_again)?_button"))
            var deny = device.wait(Until.findObject(denyButton), 20_000)
            if (deny == null && dismissSystemCrash(device)) {
                // Old emulator images crash SystemUI now and then ("System UI has stopped"),
                // taking the prompt with it. That's the image, not the app: ask again.
                d.relaunch()
                deny = device.wait(Until.findObject(denyButton), 20_000)
            }
            if (deny == null) {
                // Keep what was on screen, to tell a missing prompt from a slow or crashed one.
                d.shell("uiautomator dump /sdcard/Download/j01.xml")
                java.io.File(Run.outDir, "j01-window.xml").writeText(d.shell("cat /sdcard/Download/j01.xml"))
                d.screenshot("00-first-launch-prompt-missing")
            }
            Findings.expect("J1", deny != null, "no location permission prompt on first launch")
            deny?.click()
            // Back in the app before any Compose call: the prompt is another app's window.
            device.wait(Until.hasObject(By.pkg(d.app.packageName).depth(0)), 10_000)
            device.waitForIdle()
            compose.waitUntil(10_000) { runCatching { compose.onAllNodes(hasTestTag(UiTags.TOP)).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
            compose.onNode(hasText(d.str(R.string.to)) and hasClickAction() and hasAnyAncestor(hasTestTag(UiTags.TOP))).performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodes(hasText(TEL_AVIV.name) and hasClickAction() and hasAnyAncestor(hasTestTag(UiTags.SUGGESTIONS))).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodes(hasText(TEL_AVIV.name) and hasClickAction() and hasAnyAncestor(hasTestTag(UiTags.SUGGESTIONS))).onFirst().performClick()
            compose.waitUntil(5_000) { d.vm.state.value.error != null }
            assertEquals(UiError.NO_LOCATION, d.vm.state.value.error)
            compose.onNode(hasText(d.str(R.string.err_no_location))).assertExists()
            d.screenshot("00-first-launch-no-location")
        }
    }
}
