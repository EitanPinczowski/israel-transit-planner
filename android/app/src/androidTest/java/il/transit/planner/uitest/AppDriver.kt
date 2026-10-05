package il.transit.planner.uitest

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import il.transit.core.user.SavedPlace
import il.transit.planner.MainActivity
import il.transit.planner.R
import il.transit.planner.UiTestApp
import il.transit.planner.ui.Field
import il.transit.planner.ui.MainViewModel
import il.transit.planner.ui.Suggestion
import il.transit.planner.ui.UiTags
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.io.File

/**
 * Which device profile this run is (set by the CI script with `-e profile P4 -e locale he
 * -e theme dark`); only used to name the output. Defaults describe a local run.
 */
object Run {
    private val args get() = InstrumentationRegistry.getArguments()
    val profile: String get() = args.getString("profile") ?: "local"
    val locale: String get() = args.getString("locale") ?: "device"
    val theme: String get() = args.getString("theme") ?: "device"
    val id: String get() = "$profile-$locale-$theme"

    /** Screenshots and audit JSON: /sdcard/Android/data/<pkg>/files/ui/<run id>/. */
    val outDir: File
        get() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext
            return File(app.getExternalFilesDir(null), "ui/$id").apply { mkdirs() }
        }
}

/** Starts the app on a clean slate and reaches into it the way a test needs to. */
class AppDriver(val compose: ComposeTestRule, places: List<SavedPlace> = UiTestApp.PLACES) : Closeable {
    val inst = InstrumentationRegistry.getInstrumentation()
    val app = inst.targetContext.applicationContext as UiTestApp
    private var scenario: ActivityScenario<MainActivity>? = null
    lateinit var activity: MainActivity
        private set

    init {
        runBlocking { app.reset(places) }
    }

    /** The Activity's own ViewModel (it exists once onCreate ran). */
    val vm: MainViewModel get() = ViewModelProvider(activity)[MainViewModel::class.java]

    fun launch(): AppDriver {
        scenario = ActivityScenario.launch(MainActivity::class.java).also { s -> s.onActivity { activity = it } }
        compose.waitForIdle()
        return this
    }

    /** Closes the Activity and starts it again (state that lives in stores survives). */
    fun relaunch(): AppDriver {
        // After a SystemUI crash the old Activity can be half gone; closing it may throw.
        runCatching { scenario?.close() }
        return launch()
    }

    /** Re-reads the Activity after a configuration change recreated it. */
    fun refreshActivity() {
        scenario?.onActivity { activity = it }
    }

    val scenarioState get() = scenario?.state

    fun onMain(block: (MainViewModel) -> Unit) {
        inst.runOnMainSync { block(vm) }
    }

    fun str(id: Int, vararg args: Any): String = activity.getString(id, *args)

    fun place(field: Field, p: SavedPlace) = onMain {
        it.startEditing(field)
        it.pick(Suggestion(p.name, null, p.latLon, saved = true, isStop = false))
    }

    /** Until a search has finished, with results or an error. */
    fun awaitSearch(timeoutMs: Long = 15_000) {
        compose.waitUntil(timeoutMs) {
            val s = vm.state.value
            !s.loading && (s.hasResults || s.error != null)
        }
        compose.waitForIdle()
    }

    /**
     * Taps the results header's ★ "Save trip". It only appears once the trip's results are
     * in (and the header is composed), so wait for it rather than tapping right after the search.
     */
    fun tapSaveTrip() {
        val star = hasContentDescription(str(R.string.save_trip)) and hasClickAction()
        compose.waitUntil(5_000) { compose.onAllNodes(star).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(star).performClick()
    }

    /**
     * Opens the search card's ⋮ menu and taps [labelRes] once the item has appeared (the menu
     * is a popup: it is composed a frame after the tap). With results shown on a short screen
     * the card is folded to one line, without the ⋮: unfold it first.
     */
    fun tapMenuItem(labelRes: Int) {
        val dots = hasContentDescription(str(R.string.more_options)) and hasClickAction()
        if (compose.onAllNodes(dots).fetchSemanticsNodes().isEmpty()) {
            compose.onNode(hasContentDescription(str(R.string.edit_search)), useUnmergedTree = true).performClick()
            compose.waitUntil(5_000) { compose.onAllNodes(dots).fetchSemanticsNodes().isNotEmpty() }
        }
        compose.onNode(dots).performClick()
        val item = hasText(str(labelRes)) and hasClickAction()
        compose.waitUntil(5_000) { compose.onAllNodes(item).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(item).performClick()
    }

    /**
     * Taps [text] in the results. The sheet takes at most half the screen, so on a 640 dp phone
     * the selected option's buttons can be below its fold: scroll its list there first.
     */
    fun tapInResults(text: String) {
        val inSheet = hasAnyAncestor(hasTestTag(UiTags.BOTTOM) or hasTestTag(UiTags.TOP))
        val target = hasText(text) and hasClickAction() and inSheet
        val lists = compose.onAllNodes(hasScrollToNodeAction() and inSheet).fetchSemanticsNodes().size
        for (i in 0 until lists) {
            if (runCatching { compose.onAllNodes(hasScrollToNodeAction() and inSheet)[i].performScrollToNode(target) }.isSuccess) break
        }
        compose.onAllNodes(target).onFirst().performClick()
    }

    /** With results on a short screen the search card is folded to one line: unfold it. */
    fun unfoldSearch() {
        val fold = hasContentDescription(str(R.string.edit_search))
        if (compose.onAllNodes(fold, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(fold, useUnmergedTree = true).performClick()
            compose.waitForIdle()
        }
    }

    /** Lets the map camera (600 ms animation) and Compose settle before a screenshot. */
    fun settle() {
        compose.waitForIdle()
        SystemClock.sleep(900)
        compose.waitForIdle()
    }

    fun screenshot(name: String): File {
        val f = File(Run.outDir, "$name.png")
        val bmp: Bitmap = inst.uiAutomation.takeScreenshot() ?: return f
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    fun shell(cmd: String): String {
        val pfd = inst.uiAutomation.executeShellCommand(cmd)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    override fun close() {
        scenario?.close()
    }

    companion object {
        val sdk: Int get() = Build.VERSION.SDK_INT
    }
}
