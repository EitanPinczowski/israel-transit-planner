package il.transit.planner.uitest

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import il.transit.core.present.LegKind
import il.transit.core.present.Speech
import il.transit.core.present.Spoken
import il.transit.core.present.hhmm
import il.transit.core.present.tightTransfers
import il.transit.planner.R
import il.transit.planner.UiTestApp.Companion.BGU
import il.transit.planner.UiTestApp.Companion.TEL_AVIV
import il.transit.planner.ui.AppLocale
import il.transit.planner.ui.UiTags
import il.transit.planner.ui.screens.TIGHT_CHIP
import il.transit.planner.ui.screens.legChipTag
import il.transit.planner.ui.screens.say
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapView

/**
 * Phase 11 E5: what TalkBack would read, on API 35 in Hebrew and in English (ui_behaviour_run.sh).
 * GitHub's emulator images have no TalkBack (it ships with Google's Accessibility Suite, not in
 * `google_apis`), so the owner checks the real thing on the Pixel (owner decision 8) and CI checks the
 * semantics TalkBack reads: search → results → select → trip sheet → stop sheet → Settings → reminder
 * check. At each step, all blocking:
 *  - Google's Accessibility Test Framework, with a screenshot (errors; warnings are logged);
 *  - every tappable node says something;
 *  - no two neighbouring nodes read the same;
 *  - each leg chip of the selected option says exactly what core's `Speech` says for its leg;
 *  - nodes side by side are read in the layout direction (right to left in Hebrew).
 */
@RunWith(AndroidJUnit4::class)
class A11yJourneysTest {
    val compose = createEmptyComposeRule()

    @get:Rule val leaks = Leaks.around(compose)

    @get:Rule val permissions = Permissions.grantAll()

    @Test fun a11y_journey_hebrew() = journey("he")

    @Test fun a11y_journey_english() = journey("en")

    private fun journey(lang: String) = AppDriver(compose).launch().use { d ->
        try {
            d.language(lang)
            val rtl = lang == "he"

            // Search: the From row turns into the text field.
            d.tapRow(R.string.from)
            d.step("search", rtl)
            d.tapSuggestion(BGU.name)
            d.tapRow(R.string.to)
            val field = compose.onNode(hasSetTextAction())
            field.performClick()
            field.performTextInput("תל")
            d.tapSuggestion(TEL_AVIV.name)
            d.awaitSearch()
            d.step("results", rtl)

            // Select an option with a tight change (so its chip is checked), else one with a train.
            val options = d.ui.options
            val buffer = d.ui.settings.transferBufferMin
            val tightAt = options.indexOfFirst { tightTransfers(it, buffer).isNotEmpty() }
            val trainAt = options.indexOfFirst { o -> o.legs.any { it.mode.endsWith("RAIL") } }
            val i = listOf(tightAt, trainAt, 0).first { it >= 0 }
            d.onMain { it.select(i) }
            compose.waitForIdle()
            d.step("selected", rtl)
            val itin = d.vm.state.value.selectedItinerary!!
            val inCard = hasAnyAncestor(hasTestTag(UiTags.SELECTED))
            var chips = 0
            itin.legs.forEachIndexed { li, leg ->
                val nodes = compose.onAllNodes(hasTestTag(legChipTag(li)) and inCard, useUnmergedTree = true).fetchSemanticsNodes()
                for (n in nodes) {
                    val said = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                    val want = Speech.leg(leg).render { say(d.activity.resources, it) }
                    assertEquals("$lang: leg chip $li", want, said)
                    chips++
                }
            }
            assertTrue("$lang: no leg chip on the selected card", chips > 0)
            d.selectedCardChecks(lang, buffer)

            // Trip sheet: a tap on the first ride's chip.
            val ride = itin.legs.indexOfFirst { it.isTransit }
            compose.onAllNodes(hasTestTag(legChipTag(ride)) and inCard and hasClickAction(), useUnmergedTree = true).onFirst().performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasText(d.str(R.string.trip_board), substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            d.step("trip sheet", rtl)
            assertTrue("$lang: the trip sheet has no heading", compose.onAllNodes(isHeading).fetchSemanticsNodes().isNotEmpty())
            Espresso.pressBack()
            compose.waitForIdle()

            // Stop sheet.
            d.onMain { it.openStop(FaultMatrixTest.STOP_ID, "תחנה מרכזית") }
            compose.waitUntil(10_000) { d.ui.stopSheet?.loading == false }
            d.step("stop sheet", rtl)
            d.stopSheetChecks(lang)
            d.onMain { it.closeStop() }
            compose.waitForIdle()

            // The map: hidden from TalkBack while results show, a sighted long-press still drops a pin.
            d.mapChecks(lang)

            // Rotated with the keyboard up (R5 found 2 errors only there, in the layout audit).
            d.rotate(landscape = true)
            d.tapRow(R.string.from)
            d.step("search landscape", rtl)
            d.onMain { it.cancelEditing() }
            d.rotate(landscape = false)

            // Settings, then its reminder check.
            d.tapMenuItem(R.string.settings)
            compose.waitUntil(5_000) { d.vm.state.value.showSettings }
            d.awaitNode(hasScrollAction() and hasAnyAncestor(isDialog()))
            d.step("settings", rtl)
            assertTrue("$lang: Settings has no heading", compose.onAllNodes(isHeading and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty())
            val list = hasScrollAction() and hasAnyAncestor(isDialog())
            val row = hasTestTag("check_reminders")
            repeat(30) {
                if (compose.onAllNodes(row).fetchSemanticsNodes().isNotEmpty()) return@repeat
                compose.onAllNodes(list).onFirst().performTouchInput {
                    val step = height / 6f
                    swipe(Offset(centerX, centerY + step), Offset(centerX, centerY - step), 600)
                }
                compose.waitForIdle()
            }
            d.awaitNode(row)
            compose.onNode(row).performSemanticsAction(SemanticsActions.OnClick)
            d.awaitNode(hasTestTag("check_test"))
            d.step("reminder check", rtl)
            assertTrue("the ATF check after every step never ran", (d.a11y?.checks ?: 0) > 0)
        } finally {
            d.onMain { it.cancelReminder() }
            d.inst.runOnMainSync {
                d.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                AppLocale.apply(d.activity, null)
            }
        }
    }

    private val AppDriver.ui get() = vm.state.value

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun desc(n: SemanticsNode): String {
        val said = n.config.getOrNull(SemanticsProperties.ContentDescription)
        return said.orEmpty().joinToString(", ")
    }

    private fun text(n: SemanticsNode): String {
        val texts = n.config.getOrNull(SemanticsProperties.Text)
        return texts.orEmpty().joinToString { it.text }
    }

    private fun AppDriver.say(s: Spoken) = s.render { say(activity.resources, it) }

    /**
     * The selected card: it says "selected", its tight-change chips say core's words, and a reminder
     * set on it is announced by itself (a polite live region).
     */
    private fun AppDriver.selectedCardChecks(lang: String, buffer: Int?) {
        val card = compose.onNode(hasTestTag(UiTags.SELECTED)).fetchSemanticsNode("the selected card")
        assertEquals("$lang: the selected card says so", true, card.config.getOrNull(SemanticsProperties.Selected))
        val itin = ui.selectedItinerary!!
        val want = tightTransfers(itin, buffer).map { (t, w) -> say(Speech.tight(t, w)) }
        val inCard = hasAnyAncestor(hasTestTag(UiTags.SELECTED))
        val got = compose.onAllNodes(hasTestTag(TIGHT_CHIP) and inCard, useUnmergedTree = true).fetchSemanticsNodes().map(::desc)
        assertEquals("$lang: tight-change chips", want, got)
        Log.i(TAG, "$lang: ${got.size} tight-change chips checked")

        tapInResults(str(R.string.action_remind))
        compose.waitUntil(10_000) { ui.reminder != null }
        val leave = hhmm(ui.reminder!!.leaveAt)
        val live = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
        compose.waitUntil(5_000) { compose.onAllNodes(live).fetchSemanticsNodes().isNotEmpty() }
        val said = compose.onAllNodes(live).fetchSemanticsNodes().map(::text)
        assertTrue("$lang: the reminder row is not a live region: $said", said.any { leave in it })
    }

    /** The first departure reads as core's sentence; its ☆ says which line it pins. Headings are there. */
    private fun AppDriver.stopSheetChecks(lang: String) {
        val r = ui.stopSheet!!.rows.first()
        val sentence = say(Speech.departure(r)) + r.alerts.joinToString("") { ", ⚠ ${it.header}" }
        val rowSays = SemanticsMatcher("says \"$sentence\"") { desc(it) == sentence }
        assertTrue("$lang: no departure row says \"$sentence\"", compose.onAllNodes(rowSays).fetchSemanticsNodes().isNotEmpty())
        val line = r.line.ifBlank { str(kindRes(r.kind)) }
        val stars = setOf(str(R.string.a11y_pin_line, line), str(R.string.a11y_unpin_line, line))
        val star = SemanticsMatcher("a ☆ for $line") { desc(it) in stars }
        assertTrue("$lang: no ☆ says one of $stars", compose.onAllNodes(star).fetchSemanticsNodes().isNotEmpty())
        assertTrue("$lang: the stop sheet has no heading", compose.onAllNodes(isHeading).fetchSemanticsNodes().isNotEmpty())
    }

    private fun kindRes(k: LegKind) = when (k) {
        LegKind.BUS -> R.string.kind_bus
        LegKind.TRAIN -> R.string.kind_train
        LegKind.LIGHT_RAIL -> R.string.kind_light_rail
        else -> R.string.kind_other
    }

    /** The MapView, found in the Activity's window. */
    private fun AppDriver.mapView(): MapView {
        var found: MapView? = null
        fun walk(v: View) {
            if (v is MapView) found = v
            if (v is ViewGroup) for (c in 0 until v.childCount) walk(v.getChildAt(c))
        }
        inst.runOnMainSync { walk(activity.window.decorView) }
        return checkNotNull(found) { "no MapView on screen" }
    }

    private fun AppDriver.mapState(): Pair<Int, CharSequence?> {
        val map = mapView()
        var out: Pair<Int, CharSequence?> = 0 to null
        inst.runOnMainSync { out = map.importantForAccessibility to map.contentDescription }
        return out
    }

    /**
     * With results the map is hidden from TalkBack (and says what it is); a sighted long-press on it
     * still drops a pin; with the results gone TalkBack reaches the map again.
     */
    private fun AppDriver.mapChecks(lang: String) {
        assertTrue("$lang: no results to hide the map behind", ui.hasResults)
        assertEquals("$lang: the map while results show", View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS to str(R.string.a11y_map), mapState().let { it.first to it.second?.toString() })
        // The band of map between the search card and the results (R7 keeps it at least 25% of the screen).
        val top = compose.onNode(hasTestTag(UiTags.TOP)).fetchSemanticsNode().boundsInWindow
        val bottom = compose.onNode(hasTestTag(UiTags.BOTTOM)).fetchSemanticsNode().boundsInWindow
        val x = ((top.left + top.right) / 2).toInt()
        val y = ((top.bottom + bottom.top) / 2).toInt()
        UiDevice.getInstance(inst).swipe(x, y, x, y, 200) // held about a second: a long-press
        compose.waitUntil(10_000) { ui.places.pin != null }
        onMain { it.closePin() }
        onMain { it.clearResults() }
        compose.waitForIdle()
        assertEquals("$lang: the map after the results closed", View.IMPORTANT_FOR_ACCESSIBILITY_YES, mapState().first)
    }

    /** Turns the phone and waits for the recreated Activity in that orientation. */
    private fun AppDriver.rotate(landscape: Boolean) {
        inst.runOnMainSync {
            activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val want = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        val until = SystemClock.uptimeMillis() + 15_000
        while (true) {
            SystemClock.sleep(300) // polling a condition, not waiting out a time
            refreshActivity()
            compose.waitForIdle()
            var now = 0
            inst.runOnMainSync { now = activity.resources.configuration.orientation }
            if (now == want) return
            assertTrue("the Activity never turned (want $want, has $now)", SystemClock.uptimeMillis() < until)
        }
    }

    private fun AppDriver.tapRow(labelRes: Int) =
        compose.onNode(hasText(str(labelRes)) and hasClickAction() and hasAnyAncestor(hasTestTag(UiTags.TOP))).performClick()

    private fun AppDriver.tapSuggestion(name: String) {
        val m = hasText(name) and hasClickAction() and hasAnyAncestor(hasTestTag(UiTags.SUGGESTIONS))
        compose.waitUntil(5_000) { compose.onAllNodes(m).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(m).onFirst().performClick()
    }

    /** The app in [lang] (the per-app language, as Settings → Appearance sets it), Activity recreated. */
    private fun AppDriver.language(lang: String) {
        inst.runOnMainSync { AppLocale.apply(activity, lang) }
        val until = SystemClock.uptimeMillis() + 15_000
        while (true) {
            SystemClock.sleep(300) // polling a condition, not waiting out a time
            refreshActivity()
            compose.waitForIdle()
            var now = ""
            inst.runOnMainSync {
                val locales = activity.resources.configuration.locales
                val code = locales[0].language
                now = if (code == "iw") "he" else code
            }
            if (now == lang) return
            assertTrue("the app never switched to $lang (has $now)", SystemClock.uptimeMillis() < until)
        }
    }

    /** All the checks of one step; a failure names the step and the language. */
    private fun AppDriver.step(name: String, rtl: Boolean) {
        awaitStableLayout()
        val where = "${if (rtl) "he" else "en"} / $name"
        val problems = mutableListOf<String>()
        var atf = emptyList<Atf.Finding>()
        inst.runOnMainSync { atf = Atf.run(this, screenshots = true) }
        atf.filter { !it.error }.forEach { Log.w(TAG, "$where: $it") }
        atf.filter { it.error }.forEach { problems += "ATF $it" }

        val nodes = readingOrder()
        var shown = 0
        for (n in nodes) {
            val said = spoken(n)
            if (SemanticsActions.OnClick in n.config && said.isBlank()) problems += "a tappable node says nothing at ${n.boundsInWindow}"
            if (said.isNotBlank()) shown++
        }
        val readable = nodes.filter { spoken(it).isNotBlank() }
        for ((a, b) in readable.zipWithNext()) {
            if (spoken(a) == spoken(b)) problems += "two neighbours both read \"${spoken(a).take(60)}\""
            if (sameLine(a.boundsInWindow, b.boundsInWindow) && !inLayoutOrder(a.boundsInWindow, b.boundsInWindow, rtl)) {
                problems += "read out of the layout direction: \"${spoken(a).take(40)}\" ${a.boundsInWindow} before \"${spoken(b).take(40)}\" ${b.boundsInWindow}"
            }
        }
        Log.i(TAG, "$where: ${readable.size} nodes read, ${atf.size} ATF results, ${problems.size} problems")
        screenshot("a11y-${if (rtl) "he" else "en"}-${name.replace(' ', '-')}")
        assertTrue("$where:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    /**
     * The nodes TalkBack stops at, in tree order (Compose sorts them for TalkBack the same way inside a
     * row): the merged tree of the reachable windows (the dialog when one is open).
     */
    private fun AppDriver.readingOrder(): List<SemanticsNode> {
        val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes(atLeastOneRootRequired = false)
        val out = mutableListOf<SemanticsNode>()
        inst.runOnMainSync {
            val decor = activity.window.decorView
            fun isMain(r: SemanticsNode) = r.boundsInWindow.width.toInt() >= decor.width - 2 && r.boundsInWindow.height.toInt() >= decor.height - 2
            val dialogs = roots.filter { !isMain(it) }
            fun walk(n: SemanticsNode) {
                if (n.boundsInWindow.width > 0f && n.boundsInWindow.height > 0f) out += n
                n.children.forEach(::walk)
            }
            // The topmost dialog only (roots come in the order their windows opened): the one TalkBack reaches.
            (dialogs.lastOrNull()?.let { listOf(it) } ?: roots).forEach(::walk)
        }
        return out
    }

    private fun spoken(n: SemanticsNode): String {
        val desc = n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        val texts = n.config.getOrNull(SemanticsProperties.Text).orEmpty()
        val text = texts.map { it.text }
        val edit = listOfNotNull(n.config.getOrNull(SemanticsProperties.EditableText)?.text)
        return (desc + text + edit).filter { it.isNotBlank() }.joinToString(" ")
    }

    /** Side by side: they overlap by at least half the shorter one's height, and neither holds the other. */
    private fun sameLine(a: Rect, b: Rect): Boolean {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        val nested = (a.left <= b.left && a.right >= b.right && a.top <= b.top && a.bottom >= b.bottom) ||
            (b.left <= a.left && b.right >= a.right && b.top <= a.top && b.bottom >= a.bottom)
        return !nested && overlap >= 0.5f * minOf(a.height, b.height)
    }

    private fun inLayoutOrder(a: Rect, b: Rect, rtl: Boolean): Boolean = if (rtl) a.right >= b.right - 1f else a.left <= b.left + 1f

    private companion object {
        const val TAG = "A11yJourney"
    }
}
