package il.transit.planner.uitest

import android.graphics.Rect
import androidx.compose.ui.geometry.Rect as CRect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import il.transit.planner.R
import il.transit.planner.ui.UiTags
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Violation(val rule: String, val where: String, val detail: String) {
    fun json(): JSONObject = JSONObject().put("rule", rule).put("where", where).put("detail", detail)
}

/**
 * Layout rules every screen must meet on every phone profile. Report-only for now: it
 * writes `<state>.json` next to the screenshot and never fails the test, so a first run
 * lists every finding; a rule becomes blocking once its findings are fixed
 * (see .claude/skills/ui-testing).
 *
 *  R1 overlap      the search card (top) and the bottom panel don't overlap
 *  R2 reachable    every tappable thing is inside the screen minus status/nav bars, cutout, keyboard
 *  R3 text         no clipped text; no word broken across lines; times/prices/durations never "…"
 *  R4 target       every tappable thing is at least 48×48 dp
 *  R5 a11y         Accessibility Test Framework findings (labels, contrast, …)
 *  R6 rtl          in Hebrew, row labels sit on the right of their values
 *  R7 route        the drawn route lies in the map area the panels leave visible
 */
class LayoutAudit(private val d: AppDriver) {
    private val density get() = d.activity.resources.displayMetrics.density

    fun run(state: String, screenshot: File?): List<Violation> {
        val out = mutableListOf<Violation>()
        val merged = roots(useUnmergedTree = false)
        val unmerged = roots(useUnmergedTree = true)
        val decor = d.activity.window.decorView
        var safe = Rect()
        var ime = 0
        d.inst.runOnMainSync {
            val insets = ViewCompat.getRootWindowInsets(decor)
            val bars = insets?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            ime = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            safe = Rect(bars?.left ?: 0, bars?.top ?: 0, decor.width - (bars?.right ?: 0), decor.height - maxOf(bars?.bottom ?: 0, ime))
        }
        val mainSize = Pair(decor.width, decor.height)

        // R1
        val top = find(merged, UiTags.TOP)
        val bottom = find(merged, UiTags.BOTTOM)
        if (top != null && bottom != null) {
            val a = top.boundsInWindow
            val b = bottom.boundsInWindow
            val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            if (overlap > 1f) out += Violation("R1", "top/bottom", "search card and bottom panel overlap by ${dp(overlap)} dp")
        }

        for (root in merged) {
            val isMain = root.boundsInWindow.width.toInt() >= mainSize.first - 2 && root.boundsInWindow.height.toInt() >= mainSize.second - 2
            val area = if (isMain) safe else root.boundsInWindow.toRect()
            walk(root) { n, scrolled ->
                if (SemanticsActions.OnClick !in n.config) return@walk
                val r = n.boundsInWindow
                if (r.width <= 0f || r.height <= 0f) return@walk
                val name = label(n)
                // R2 (a node scrolled out of its list is fine: the list scrolls)
                if (!scrolled && !area.contains(r.toRect())) {
                    out += Violation("R2", name, "tappable at ${r.toRect().toShortString()} leaves the usable area ${area.toShortString()}" + if (ime > 0) " (keyboard open)" else "")
                }
                // R4
                val minPx = 48 * density - 1
                if (r.width < minPx || r.height < minPx) {
                    out += Violation("R4", name, "touch target ${dp(r.width)}×${dp(r.height)} dp (< 48×48)")
                }
            }
        }

        // R3
        val layouts = mutableListOf<Pair<SemanticsNode, TextLayoutResult>>()
        for (root in unmerged) walk(root) { n, _ ->
            val get = n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return@walk
            val list = mutableListOf<TextLayoutResult>()
            d.inst.runOnMainSync { get(list) }
            list.firstOrNull()?.let { layouts += n to it }
        }
        for ((n, t) in layouts) {
            val text = t.layoutInput.text.text
            if (text.isBlank()) continue
            val ellipsis = t.layoutInput.overflow == TextOverflow.Ellipsis
            val cut = t.lineCount > 0 && t.isLineEllipsized(t.lineCount - 1)
            if (t.hasVisualOverflow && !ellipsis) out += Violation("R3", quote(text), "text is clipped")
            if (cut && IMPORTANT.containsMatchIn(text)) out += Violation("R3", quote(text), "a time/price/duration is cut off with …")
            val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
            if (t.lineCount > maxOf(words, 1) || (t.lineCount >= 2 && n.boundsInWindow.width < 40 * density)) {
                out += Violation("R3", quote(text), "squeezed: ${t.lineCount} lines in ${dp(n.boundsInWindow.width)} dp")
            }
        }

        // R5
        out += Atf.check(d)

        // R6
        if (Run.locale == "he" || d.activity.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) {
            val label = layouts.firstOrNull { it.second.layoutInput.text.text == d.str(R.string.from) }?.first
            if (label != null) {
                val row = label.boundsInWindow
                val value = layouts.map { it.first }.firstOrNull { it !== label && kotlin.math.abs(it.boundsInWindow.center.y - row.center.y) < 4 * density }
                if (value != null && row.left < value.boundsInWindow.left) {
                    out += Violation("R6", "from row", "label is left of its value in RTL")
                }
            }
        }

        // R7
        var route: android.graphics.RectF? = null
        d.inst.runOnMainSync { route = d.activity.routeOnScreen() }
        val rr = route
        if (rr != null && top != null && bottom != null) {
            val freeTop = top.boundsInWindow.bottom
            val freeBottom = bottom.boundsInWindow.top
            val free = freeBottom - freeTop
            if (free < 0.25f * mainSize.second) {
                out += Violation("R7", "map", "only ${dp(free.coerceAtLeast(0f))} dp of map left between the panels")
            }
            val slack = 4 * density
            if (rr.top < freeTop - slack || rr.bottom > freeBottom + slack || rr.left < -slack || rr.right > mainSize.first + slack) {
                out += Violation("R7", "route", "route ${rr.toShortString()} is not inside the visible map ${freeTop.toInt()}..${freeBottom.toInt()}")
            }
        }

        write(state, screenshot, out)
        return out
    }

    private fun write(state: String, screenshot: File?, v: List<Violation>) {
        val o = JSONObject()
            .put("state", state).put("profile", Run.profile).put("locale", Run.locale).put("theme", Run.theme)
            .put("sdk", AppDriver.sdk).put("screenshot", screenshot?.name)
            .put("violations", JSONArray(v.map { it.json() }))
        File(Run.outDir, "$state.json").writeText(o.toString(2))
    }

    private fun roots(useUnmergedTree: Boolean): List<SemanticsNode> =
        d.compose.onAllNodes(isRoot(), useUnmergedTree).fetchSemanticsNodes(atLeastOneRootRequired = false)

    private fun find(roots: List<SemanticsNode>, tag: String): SemanticsNode? {
        var found: SemanticsNode? = null
        for (r in roots) walk(r) { n, _ -> if (found == null && n.config.getOrNull(SemanticsProperties.TestTag) == tag) found = n }
        return found
    }

    /** Depth first; `scrolled` = some ancestor scrolls and the node lies outside it. */
    private fun walk(n: SemanticsNode, scrollBox: CRect? = null, visit: (SemanticsNode, Boolean) -> Unit) {
        val outside = scrollBox != null && !scrollBox.overlaps(n.boundsInWindow)
        visit(n, outside || (scrollBox != null && !scrollBox.containsRect(n.boundsInWindow)))
        val scrolls = SemanticsProperties.VerticalScrollAxisRange in n.config || SemanticsProperties.HorizontalScrollAxisRange in n.config
        val box = if (scrolls) n.boundsInWindow else scrollBox
        n.children.forEach { walk(it, box, visit) }
    }

    private fun label(n: SemanticsNode): String {
        val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val desc = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        val tag = n.config.getOrNull(SemanticsProperties.TestTag)
        return quote(text ?: desc ?: tag ?: "node#${n.id}")
    }

    private fun quote(s: String) = "\"" + s.replace("\n", " ").take(60) + "\""

    private fun dp(px: Float) = Math.round(px / density)

    private fun CRect.toRect() = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

    private fun CRect.containsRect(o: CRect) = o.left >= left - 1 && o.top >= top - 1 && o.right <= right + 1 && o.bottom <= bottom + 1

    companion object {
        /** Text that must never be truncated: clock times, shekels, minute counts. */
        private val IMPORTANT = Regex("""\d{1,2}:\d{2}|₪|\d+\s?(′|min|דק)""")
    }
}
